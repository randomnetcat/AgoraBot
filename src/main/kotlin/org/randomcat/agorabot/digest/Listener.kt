package org.randomcat.agorabot.digest

import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import org.randomcat.agorabot.listener.BotEmoteListener
import org.randomcat.agorabot.util.tryAddReaction

fun digestEmoteListener(digestMap: GuildDigestMap, targetEmoji: String, successEmoji: String): BotEmoteListener {
    val functor = object {
        operator fun invoke(event: MessageReactionAddEvent) {
            if (!event.isFromGuild) return

            val emote = event.reactionEmote
            if (!emote.isEmoji) return

            val reactionEmoji = emote.emoji
            if (reactionEmoji == targetEmoji) {
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
