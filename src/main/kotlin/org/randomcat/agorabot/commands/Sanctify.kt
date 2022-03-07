package org.randomcat.agorabot.commands

import kotlinx.coroutines.future.await
import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.MessageBuilder
import org.randomcat.agorabot.commands.base.*
import org.randomcat.agorabot.commands.base.requirements.permissions.permissions
import org.randomcat.agorabot.commands.impl.InGuild
import org.randomcat.agorabot.commands.impl.currentGuild
import org.randomcat.agorabot.commands.impl.currentGuildState
import org.randomcat.agorabot.config.get
import org.randomcat.agorabot.config.set
import org.randomcat.agorabot.permissions.GuildScope
import org.randomcat.agorabot.util.await

private val MANAGE_PERMISSION = GuildScope.command("sanctify").action("manage")
private const val STATE_KEY = "sanctify"

@Serializable
private sealed class SanctifyStateDto {
    @Serializable
    data class V0(val targetChannelId: String) : SanctifyStateDto()
}

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

                if (sourceThread.messageCount > 50) {
                    respond("Threads with more than 50 messages are not supported.")
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

                // There should be less than 50 messages, so taking the 50 most recent and reversing is fine.
                val messages = sourceThread.iterableHistory.take(50).asReversed()

                val targetThread = targetChannel.createThreadChannel("Sanctified: " + sourceThread.name).await()

                for (message in messages) {
                    val action = targetThread.sendMessage(MessageBuilder(message).build())

                    try {
                        for (file in message.attachments) {
                            action.addFile(file.retrieveInputStream().await(), file.fileName)
                        }
                    } catch (e: Exception) {
                        action.clearFiles { stream ->
                            stream.close()
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
