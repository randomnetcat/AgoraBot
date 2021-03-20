package org.randomcat.agorabot.digest

import org.randomcat.agorabot.util.utcLocalDateTime
import java.time.format.DateTimeFormatter

interface DigestFormat {
    fun format(digest: Digest): String
}

class SimpleDigestFormat : DigestFormat {
    override fun format(digest: Digest): String {
        return digest.messages().sortedBy { it.messageDate }.joinToString("\n\n") { message ->
            val nickname = message.senderNickname
            val includeNickname = (nickname != null) && (nickname != message.senderUsername)

            val attachmentLines = message.attachmentUrls.joinToString("\n")

            val contentPart = if (message.content.isBlank()) {
                if (message.attachmentUrls.isNotEmpty()) {
                    "This message consists only of attachments:\n$attachmentLines"
                } else {
                    "<no content>"
                }
            } else {
                message.content +
                        if (message.attachmentUrls.isNotEmpty())
                            "\n\nThis message has attachments\n$attachmentLines"
                        else
                            ""
            }

            val adjustedDate = message.messageDate.utcLocalDateTime()

            "MESSAGE ${message.id}\n" +
                    "FROM ${message.senderUsername}${if (includeNickname) " ($nickname)" else ""} " +
                    (message.channelName?.let { "IN #$it " } ?: "") +
                    "ON ${DateTimeFormatter.ISO_LOCAL_DATE.format(adjustedDate)} " +
                    "AT ${DateTimeFormatter.ISO_LOCAL_TIME.format(adjustedDate)}:" +
                    "\n" +
                    contentPart
        }
    }
}

data class AffixDigestFormat(
    private val prefix: String,
    private val baseFormat: DigestFormat,
    private val suffix: String,
) : DigestFormat {
    override fun format(digest: Digest): String {
        return "$prefix${baseFormat.format(digest)}$suffix"
    }
}
