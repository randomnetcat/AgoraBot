package org.randomcat.agorabot.features

import org.randomcat.agorabot.AbstractFeature
import org.randomcat.agorabot.FeatureContext
import org.randomcat.agorabot.commands.DigestCommand
import org.randomcat.agorabot.digest.DigestFormat
import org.randomcat.agorabot.digest.DigestSendStrategy
import org.randomcat.agorabot.digest.GuildMutableDigestMap
import org.randomcat.agorabot.digest.digestEmoteListener
import org.randomcat.agorabot.listener.Command

private const val DIGEST_ADD_EMOTE = "\u2B50" // Discord :star:
private const val DIGEST_SUCCESS_EMOTE = "\u2705" // Discord :white_check_mark:

fun digestFeature(
    digestMap: GuildMutableDigestMap,
    sendStrategy: DigestSendStrategy?,
    format: DigestFormat,
) = object : AbstractFeature() {
    override fun commandsInContext(context: FeatureContext): Map<String, Command> {
        return mapOf(
            "digest" to DigestCommand(
                strategy = context.defaultCommandStrategy,
                digestMap = digestMap,
                sendStrategy = sendStrategy,
                digestFormat = format,
                digestAddedReaction = DIGEST_SUCCESS_EMOTE,
            ),
        )
    }

    override fun jdaListeners(): List<Any> {
        return listOf(
            digestEmoteListener(
                digestMap = digestMap,
                targetEmoji = DIGEST_ADD_EMOTE,
                successEmoji = DIGEST_SUCCESS_EMOTE,
            ),
        )
    }
}
