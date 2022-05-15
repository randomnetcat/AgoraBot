package org.randomcat.agorabot.commands

import kotlinx.coroutines.future.await
import net.dv8tion.jda.api.entities.MessageType
import org.randomcat.agorabot.commands.base.BaseCommand
import org.randomcat.agorabot.commands.base.BaseCommandImplReceiver
import org.randomcat.agorabot.commands.base.BaseCommandStrategy
import org.randomcat.agorabot.commands.base.requirements.discord.InDiscordSimple
import org.randomcat.agorabot.commands.base.requirements.discord.currentMessageEvent
import org.randomcat.agorabot.commands.base.requires
import javax.imageio.ImageIO

class CheckSquareCommand(strategy: BaseCommandStrategy) : BaseCommand(strategy) {
    override fun BaseCommandImplReceiver.impl() {
        noArgs().requires(InDiscordSimple) cmd@{ _ ->
            if (currentMessageEvent.message.type != MessageType.INLINE_REPLY) {
                respond("This command must be sent in a reply to a message.")
                return@cmd
            }

            val targetMessage = currentMessageEvent.message.referencedMessage

            if (targetMessage == null) {
                respond("Unable to load referenced message.")
                return@cmd
            }

            if (targetMessage.attachments.size != 1) {
                respond("The replied to message must have a single attachment.")
                return@cmd
            }

            val attachment = targetMessage.attachments.single()

            val inputStream = try {
                attachment.retrieveInputStream().await()
            } catch (e: Exception) {
                respond("Unable to retrieve attachment.")
                return@cmd
            }

            inputStream.use {
                val image = runCatching { ImageIO.read(it) }.getOrNull()

                if (image == null) {
                    respond("Unable to parse image.")
                    return@cmd
                }

                val height = image.height
                val width = image.width

                if (width != height) {
                    respond("Image is not square! $width by $height")
                } else {
                    respond("Image is square. $width by $height")
                }
            }
        }
    }
}
