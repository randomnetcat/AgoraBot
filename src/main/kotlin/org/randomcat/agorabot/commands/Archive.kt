package org.randomcat.agorabot.commands

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.entities.Guild
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
import org.randomcat.agorabot.util.DiscordPermission
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val ARCHIVE_PERMISSION = GuildScope.command("archive")

private val LOGGER = LoggerFactory.getLogger("AgoraBotArchiveCommand")

interface DiscordArchiver {
    suspend fun createArchiveFrom(
        guild: Guild,
        channelIds: Set<String>,
    ): Path

    val archiveExtension: String
}

private fun formatCurrentDate(): String {
    return DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneOffset.UTC).format(Instant.now())
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
                .permissions(ARCHIVE_PERMISSION) { (args) ->
                    val isStoreLocally = args.contains("store_locally")
                    val isCategoryIds = args.contains("categories")

                    val rawIds = args - listOf("store_locally", "categories")

                    if (isStoreLocally && !senderHasPermission(BotScope.admin())) {
                        respond("Archives can only be stored locally by bot admins.")
                        return@permissions
                    }

                    val channelIds = if (isCategoryIds) {
                        val categories =
                            rawIds.mapNotNull { id ->
                                currentGuild.getCategoryById(id).also {
                                    if (it == null) {
                                        respond("Unable to find category by id $it")
                                        return@permissions
                                    }
                                }
                            }

                        categories.flatMap { it.textChannels }.map { it.id }
                    } else {
                        rawIds
                    }

                    doArchive(
                        channelIds = channelIds.distinct(),
                        storeArchiveResult = { path ->
                            if (isStoreLocally) {
                                val fileName = "archive_${formatCurrentDate()}.${archiver.archiveExtension}"
                                Files.copy(path, localStorageDir.resolve(fileName))
                                respond("Stored file locally at $fileName")
                            } else {
                                currentChannel
                                    .sendMessage("Archive for channels $channelIds")
                                    .addFile(
                                        path.toFile(),
                                        "archive_${formatCurrentDate()}.${archiver.archiveExtension}",
                                    )
                                    .queue()
                            }
                        },
                    )
                }
        }
    }

    private suspend fun BaseCommandExecutionReceiverGuilded.doArchive(
        channelIds: List<String>,
        storeArchiveResult: suspend BaseCommandExecutionReceiverGuilded.(Path) -> Unit,
    ) {
        val member = currentMessageEvent.member ?: error("Member should exist because this is in a Guild")

        val distinctChannelIds = channelIds.toSet()

        for (channelId in channelIds) {
            val channel = currentGuild.getTextChannelById(channelId)

            if (channel == null) {
                respond("The channel id $channelId does not exist.")
                return
            }

            if (
                !member.hasPermission(channel, DiscordPermission.VIEW_CHANNEL) ||
                !member.hasPermission(channel, DiscordPermission.MESSAGE_HISTORY)
            ) {
                respond("You do not have permission to read in the channel <#$channelId>.")
                return
            }
        }

        val channelNamesString = channelIds.joinToString(", ") { "<#$it>" }

        currentChannel.sendMessage("Running archive job for channels $channelNamesString...").queue { statusMessage ->
            fun markFailed() {
                statusMessage.editMessage("Archive job failed!").queue()
            }

            fun markFailedWith(e: Throwable) {
                markFailed()
                LOGGER.error("Error while archiving channels $channelNamesString", e)
            }

            try {
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        storeArchiveResult(
                            archiver.createArchiveFrom(
                                guild = currentGuild,
                                channelIds = distinctChannelIds,
                            ),
                        )

                        statusMessage
                            .editMessage("Archive done for channels $channelNamesString.")
                            .queue()
                    } catch (t: Throwable) {
                        markFailedWith(t)
                    }
                }
            } catch (e: Exception) {
                markFailedWith(e)
            }
        }

        return
    }
}
