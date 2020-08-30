package org.randomcat.agorabot.digest

import java.time.format.DateTimeFormatter

interface DigestFormat {
    fun format(digest: MessageDigest): String
}

class DefaultDigestFormat : DigestFormat {
    override fun format(digest: MessageDigest): String {
        return digest.messages().sortedBy { it.date }.joinToString("\n\n") {
            val nickname = it.senderNickname
            val includeNickname = (nickname != null) && (nickname != it.senderUsername)

            "MESSAGE ${it.id}\n" +
                    "FROM ${it.senderUsername}${if (includeNickname) " ($nickname)" else ""} " +
                    "ON ${DateTimeFormatter.ISO_LOCAL_DATE.format(it.date)} " +
                    "AT ${DateTimeFormatter.ISO_LOCAL_TIME.format(it.date)}:" +
                    "\n" +
                    it.content
        }
    }
}
