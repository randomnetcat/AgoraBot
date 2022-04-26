package org.randomcat.agorabot.commands

import kotlinx.coroutines.future.await
import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.MessageBuilder
import org.randomcat.agorabot.commands.base.*
import org.randomcat.agorabot.commands.base.requirements.discord.currentGuild
import org.randomcat.agorabot.commands.base.requirements.discord_ext.InGuild
import org.randomcat.agorabot.commands.base.requirements.discord_ext.currentGuildState
import org.randomcat.agorabot.commands.base.requirements.permissions.permissions
import org.randomcat.agorabot.guild_state.get
import org.randomcat.agorabot.guild_state.set
import org.randomcat.agorabot.permissions.GuildScope
import org.randomcat.agorabot.util.await
import java.io.InputStream

private val MANAGE_PERMISSION = GuildScope.command("sanctify").action("manage")
private const val STATE_KEY = "sanctify"

@Serializable
private sealed class SanctifyStateDto {
    @Serializable
    data class V0(val targetChannelId: String) : SanctifyStateDto()
}

private const val MESSAGE_LIMIT = 20

class SanctifyCommand(
    strategy: BaseCommandStrategy,
) : BaseCommand(strategy) {
    override fun BaseCommandImplReceiver.impl() {
        matchFirst {
            args(LiteralArg("set_channel"), StringArg("channel_id")).requires(InGuild)
                .permissions(MANAGE_PERMISSION) { (_, id) ->
                    currentGuildState.set<SanctifyStateDto>(STATE_KEY, SanctifyStateDto.V0(id))

                    respond("Done.")
                }

            args(StringArg("thread_id")).requires(InGuild) { (threadId) ->
                val sourceThread = currentGuild.getThreadChannelById(threadId)

                if (sourceThread == null) {
                    respond("Could not find a thread with that ID.")
                    return@requires
                }

                val state = currentGuildState.get<SanctifyStateDto>(STATE_KEY)

                if (state == null) {
                    respond("Sanctification has not been enabled in this guild.")
                    return@requires
                }

                val targetChannel = currentGuild.getTextChannelById(
                    when (state) {
                        is SanctifyStateDto.V0 -> state.targetChannelId
                    },
                )

                if (targetChannel == null) {
                    respond("Could not find target channel.")
                    return@requires
                }

                // Reverse means that the history will go earliest -> latest.
                // Take 1 extra message so that we can check if the limit was violated
                val messages = sourceThread.iterableHistory.reverse().take(MESSAGE_LIMIT + 1)

                if (messages.size > MESSAGE_LIMIT) {
                    respond("Threads with more than $MESSAGE_LIMIT messages are not supported.")
                    return@requires
                }

                val targetThread = targetChannel.createThreadChannel("Sanctified: " + sourceThread.name).await()

                for (message in messages) {
                    val attachments = message.attachments
                    val builder = MessageBuilder(message)

                    val (action, remainingAttachments) = run {
                        var inputStreamForClose: InputStream? = null

                        try {
                            if (builder.isEmpty) {
                                // Message content is empty, so message must have an attachment.
                                val firstAttachment = attachments[0]
                                inputStreamForClose = firstAttachment.retrieveInputStream().await()

                                targetThread.sendFile(
                                    inputStreamForClose,
                                    firstAttachment.fileName,
                                ) to attachments.subList(1, attachments.size)
                            } else {
                                targetThread.sendMessage(builder.build()) to attachments
                            }
                        } catch (e: Exception) {
                            inputStreamForClose?.close()
                            throw e
                        }
                    }

                    try {
                        for (file in remainingAttachments) {
                            action.addFile(file.retrieveInputStream().await(), file.fileName)
                        }
                    } catch (e: Exception) {
                        action.clearFiles { stream ->
                            try {
                                stream.close()
                            } catch (closeFailure: Exception) {
                                e.addSuppressed(closeFailure)
                            }
                        }

                        throw e
                    }

                    action.await()
                }

                respond("Sanctification done.")
            }
        }
    }
}
