package org.randomcat.agorabot.digest

import kotlinx.collections.immutable.toImmutableList
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.requests.RestAction

fun RestAction<Message>.mapToDigestMessage() = this.flatMap { message -> message.digestMessageAction() }

fun Message.digestMessageAction(): RestAction<DigestMessage> {
    val message = this // just for clarity

    return message.guild.retrieveMember(message.author).map { retrievedMember ->
        val nickname = retrievedMember.nickname

        DigestMessage(
            senderUsername = message.author.name,
            senderNickname = nickname,
            id = message.id,
            content = message.contentRaw,
            date = message.timeCreated,
            attachmentUrls = message.attachments.map { it.url }.toImmutableList()
        )
    }
}
