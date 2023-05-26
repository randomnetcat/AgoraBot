package org.randomcat.agorabot.digest

import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import org.randomcat.agorabot.listener.BotEmoteListener
import org.randomcat.agorabot.util.tryAddReaction

fun digestEmoteListener(digestMap: GuildMutableDigestMap, targetEmoji: Emoji, successEmoji: Emoji): BotEmoteListener {
    val functor = object {
        operator fun invoke(event: MessageReactionAddEvent) {
            if (!event.isFromGuild) return
            if (event.userId == event.jda.selfUser.id) return

            if (event.emoji == targetEmoji) {
                val digest = digestMap.digestForGuild(event.guild.id)

                event.retrieveMessage().queue { message ->
                    message.retrieveDigestMessage().queue { digestMessage ->
                        val numAdded = digest.addCounted(digestMessage)

                        if (numAdded > 0) {
                            message.tryAddReaction(successEmoji).queue()
                        }
                    }
                }
            }
        }
    }

    return BotEmoteListener { functor(it) }
}
