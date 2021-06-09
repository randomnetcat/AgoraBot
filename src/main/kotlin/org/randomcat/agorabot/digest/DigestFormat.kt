package org.randomcat.agorabot.digest

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

interface DigestFormat {
    fun format(digest: Digest): String
}

private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC)
private val TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_TIME.withZone(ZoneOffset.UTC)

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

            val messageInstant = message.messageDate

            "MESSAGE ${message.id}\n" +
                    "FROM ${message.senderUsername}${if (includeNickname) " ($nickname)" else ""} " +
                    (message.channelName?.let { "IN #$it " } ?: "") +
                    "ON ${DATE_FORMAT.format(messageInstant)} " +
                    "AT ${TIME_FORMAT.format(messageInstant)}:" +
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
