package org.randomcat.agorabot.digest

import kotlinx.collections.immutable.toImmutableList
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.requests.RestAction

private fun digestMessageWithForcedMember(message: Message, realMember: Member): DigestMessage {
    val nickname = realMember.nickname

    return DigestMessage(
        senderUsername = message.author.name,
        senderNickname = nickname,
        id = message.id,
        content = message.contentDisplay,
        date = message.timeCreated,
        attachmentUrls = message.attachments.map { it.url }.toImmutableList()
    )
}

fun Message.digestMessageAction(): RestAction<DigestMessage> {
    val message = this // just for clarity

    // Use retrieveMessage to force update of nickname
    return message.guild.retrieveMember(message.author).map { retrievedMember ->
        digestMessageWithForcedMember(message = message, realMember = retrievedMember)
    }
}

fun List<Message>.digestMessageActions(): RestAction<List<DigestMessage>> {
    val messages = this // for clarity

    // Unload all of the members in order to force nickname updates
    messages.mapNotNull { it.member }.distinctBy { it.idLong }.forEach { it.guild.unloadMember(it.idLong) }

    return RestAction.allOf(messages.map { message ->
        // The false parameter tells JDA not to fetch the member again if it is already in the cache. Since we just
        // cleared the cache, this will fetch each member once (thus getting an updated nickname), then keep them in
        // cache (so that we don't have to make a request for each and every message).
        message.guild.retrieveMember(message.author, false).map { retrievedMember ->
            digestMessageWithForcedMember(message = message, realMember = retrievedMember)
        }
    })
}
