package org.randomcat.agorabot.digest

import net.dv8tion.jda.api.entities.Message

fun Message.toDigestMessage() = DigestMessage(
    senderUsername = this.author.name,
    senderNickname = this.member?.nickname,
    id = this.id,
    content = this.contentRaw,
    date = this.timeCreated,
)
