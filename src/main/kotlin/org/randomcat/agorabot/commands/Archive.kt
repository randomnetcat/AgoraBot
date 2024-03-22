@file:OptIn(ExperimentalPathApi::class)

package org.randomcat.agorabot.commands

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.concrete.Category
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.utils.SplitUtil
import org.randomcat.agorabot.commands.base.*
import org.randomcat.agorabot.commands.base.requirements.discord.BaseCommandExecutionReceiverGuilded
import org.randomcat.agorabot.commands.base.requirements.discord.currentChannel
import org.randomcat.agorabot.commands.base.requirements.discord.currentGuild
import org.randomcat.agorabot.commands.base.requirements.discord.currentMessageEvent
import org.randomcat.agorabot.commands.base.requirements.discord_ext.InGuild
import org.randomcat.agorabot.commands.base.requirements.permissions.permissions
import org.randomcat.agorabot.commands.base.requirements.permissions.senderHasPermission
import org.randomcat.agorabot.permissions.BotScope
import org.randomcat.agorabot.permissions.GuildScope
import org.randomcat.agorabot.permissions.LogicalOrPermission
import org.randomcat.agorabot.util.DiscordPermission
import org.randomcat.agorabot.util.await
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively

private val ARCHIVE_PERMISSION = GuildScope.command("archive")
private val FORCE_PERMISSION = BotScope.admin()
private val OVERALL_PERMISSION = LogicalOrPermission(listOf(ARCHIVE_PERMISSION, FORCE_PERMISSION))

private val LOGGER = LoggerFactory.getLogger("AgoraBotArchiveCommand")

interface DiscordArchiver {
    suspend fun createArchiveFrom(
        guild: Guild,
        channelIds: Set<String>,
    ): Path

    /**
     * The file extension of the resulting archive format, or null if the archiver produces a directory rather than a
     * file.
     */
    val archiveExtension: String?
}

private fun formatCurrentDate(): String {
    return DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneOffset.UTC).format(Instant.now())
}

private sealed interface RecursiveChannelsResult {
    data class Success(val channels: ImmutableList<GuildChannel>) : RecursiveChannelsResult
    data class NotFound(val channelId: String) : RecursiveChannelsResult
}

private fun recursiveChannels(guild: Guild, channelIds: Set<String>): RecursiveChannelsResult {
    val out = mutableListOf<GuildChannel>()

    for (channelId in channelIds) {
        val channel = guild.getGuildChannelById(channelId) ?: return RecursiveChannelsResult.NotFound(channelId)
        out.add(channel)

        if (channel is Category) {
            when (val subResult = recursiveChannels(guild, channel.channels.map { it.id }.toSet())) {
                is RecursiveChannelsResult.Success -> out.addAll(subResult.channels)
                is RecursiveChannelsResult.NotFound -> return subResult
            }
        }
    }

    return RecursiveChannelsResult.Success(out.distinctBy { it.id }.toImmutableList())
}

class ArchiveCommand(
    strategy: BaseCommandStrategy,
    private val archiver: DiscordArchiver,
    private val localStorageDir: Path,
) : BaseCommand(strategy) {
    init {
        Files.createDirectories(localStorageDir)
    }

    override fun BaseCommandImplReceiver.impl() {
        matchFirst {
            args(RemainingStringArgs("marker_or_id"))
                .requires(InGuild)
                .permissions(OVERALL_PERMISSION) cmd@{ (args) ->
                    val isStoreLocally = args.contains("store_locally")
                    val isAll = args.contains("all")
                    val isForce = args.contains("force")

                    val rawIds = args - listOf("store_locally", "all", "force")

                    if (!isStoreLocally) {
                        respond("Uploading archives is currently not supported")
                        return@cmd
                    }

                    if (isStoreLocally && !senderHasPermission(BotScope.admin())) {
                        respond("Archives can only be stored locally by bot admins.")
                        return@cmd
                    }

                    if (isForce) {
                        if (!senderHasPermission(FORCE_PERMISSION)) {
                            respond("Only a bot admin can forcibly archive a server.")
                            return@cmd
                        }
                    } else {
                        if (!senderHasPermission(ARCHIVE_PERMISSION)) {
                            respond("You do not have permission: need $ARCHIVE_PERMISSION")
                            return@cmd
                        }
                    }

                    if (rawIds.isEmpty() && !isAll) {
                        respond("Must provide IDs or \"all\".")
                        return@cmd
                    }

                    val channels = if (isAll) {
                        currentGuild.channels
                    } else {
                        when (val channelResult = recursiveChannels(currentGuild, channelIds = rawIds.toSet())) {
                            is RecursiveChannelsResult.Success -> channelResult.channels

                            is RecursiveChannelsResult.NotFound -> {
                                respond("Unknown channel ID: ${channelResult.channelId}")
                                return@cmd
                            }
                        }
                    }

                    doArchive(
                        channels = channels.distinctBy { it.id },
                        storeArchiveResult = { path ->
                            if (isStoreLocally) {
                                withContext(Dispatchers.IO) {
                                    val extension = archiver.archiveExtension

                                    val fileName = if (extension != null) {
                                        "archive_${formatCurrentDate()}.${archiver.archiveExtension}"
                                    } else {
                                        "archive_${formatCurrentDate()}"
                                    }

                                    path.copyToRecursively(localStorageDir.resolve(fileName), followLinks = false)
                                    respond("Stored archive locally at $fileName")
                                    LOGGER.info("Stored archive of guild ${currentGuild.id} (${currentGuild.name}) at $fileName")
                                }
                            } else {
                                TODO("uploading not supported")
                            }
                        },
                        force = isForce,
                    )
                }
        }
    }

    private suspend fun BaseCommandExecutionReceiverGuilded.doArchive(
        channels: List<GuildChannel>,
        storeArchiveResult: suspend BaseCommandExecutionReceiverGuilded.(Path) -> Unit,
        force: Boolean,
    ) {
        val member = currentMessageEvent.member ?: error("Member should exist because this is in a Guild")

        if (!force) {
            for (channel in channels) {
                if (
                    !member.hasPermission(channel, DiscordPermission.VIEW_CHANNEL) ||
                    !member.hasPermission(channel, DiscordPermission.MESSAGE_HISTORY)
                ) {
                    respond("You do not have permission to read in the channel <#${channel.id}>.")
                    return
                }
            }
        }

        val channelNamesString = channels.joinToString(", ") { "<#${it.id}>" }

        val statusParts = SplitUtil.split(
            "Running archive job for channels $channelNamesString...",
            Message.MAX_CONTENT_LENGTH,
            true,
            SplitUtil.Strategy.onChar(',')
        )

        val statusMessage = currentChannel.sendMessage(statusParts.first()).await()

        for (part in statusParts.drop(1)) {
            currentChannel.sendMessage(part).await()
        }

        fun markFailed() {
            statusMessage.editMessage("Archive job failed!").queue()
        }

        fun markFailedWith(e: Throwable) {
            markFailed()
            LOGGER.error("Error while archiving channels $channelNamesString", e)
        }

        try {
            withContext(Dispatchers.IO) {
                try {
                    storeArchiveResult(
                        archiver.createArchiveFrom(
                            guild = currentGuild,
                            channelIds = channels.map { it.id }.toSet(),
                        ),
                    )

                    // Always replace start with a shorter string so that length cannot be an issue
                    statusMessage.editMessage(
                        statusMessage.contentRaw.replace(
                            "Running archive job",
                            "Finished archive"
                        ).replace("...", "!")
                    ).await()
                } catch (t: Throwable) {
                    markFailedWith(t)
                }
            }
        } catch (e: Exception) {
            markFailedWith(e)
        }

        return
    }
}
