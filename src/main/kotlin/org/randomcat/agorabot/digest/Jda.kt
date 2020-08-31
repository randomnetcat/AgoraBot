package org.randomcat.agorabot.digest

import kotlinx.collections.immutable.toImmutableList
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.requests.RestAction

fun RestAction<Message>.mapToDigestMessage() = this.flatMap { message -> message.digestMessageAction() }

fun Message.digestMessageAction(): RestAction<DigestMessage> {
    val message = this // just for clarity

    // The false parameter tells JDA to not update the cache, regardless of cache coherency. This is okay because it's
    // only a nickname and this is just a digest, so it's no big deal if the digest shows a slightly outdated nickname.
    return message.guild.retrieveMember(message.author, false).map { retrievedMember ->
        val nickname = retrievedMember.nickname

        DigestMessage(
            senderUsername = message.author.name,
            senderNickname = nickname,
            id = message.id,
            content = message.contentDisplay,
            date = message.timeCreated,
            attachmentUrls = message.attachments.map { it.url }.toImmutableList()
        )
    }
}
