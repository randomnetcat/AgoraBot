package org.randomcat.agorabot.digest

import java.time.format.DateTimeFormatter

interface DigestFormat {
    fun format(digest: Digest): String
}

class DefaultDigestFormat : DigestFormat {
    override fun format(digest: Digest): String {
        return digest.messages().sortedBy { it.date }.joinToString("\n\n") {
            val nickname = it.senderNickname
            val includeNickname = (nickname != null) && (nickname != it.senderUsername)

            val attachmentFootnote =
                if (it.attachmentUrls.isNotEmpty())
                    "\n\nThis message has attachments:\n" + it.attachmentUrls.joinToString("\n")
                else
                    ""

            "MESSAGE ${it.id}\n" +
                    "FROM ${it.senderUsername}${if (includeNickname) " ($nickname)" else ""} " +
                    "ON ${DateTimeFormatter.ISO_LOCAL_DATE.format(it.date)} " +
                    "AT ${DateTimeFormatter.ISO_LOCAL_TIME.format(it.date)}:" +
                    "\n" +
                    it.content +
                    attachmentFootnote
        }
    }
}
