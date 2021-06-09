package org.randomcat.agorabot.digest

import kotlinx.collections.immutable.ImmutableList
import java.time.Instant

data class DigestMessage(
    val senderUsername: String,
    val senderNickname: String?,
    val id: String,
    val channelName: String?,
    val content: String,
    val messageDate: Instant,
    val attachmentUrls: ImmutableList<String>,
)

interface Digest {
    fun messages(): ImmutableList<DigestMessage>
    val size: Int get() = messages().size
}

interface MutableDigest : Digest {
    fun add(messages: Iterable<DigestMessage>)
    fun add(message: DigestMessage) = add(listOf(message))
    fun clear()

    /**
     * Adds the provided messages to the digest. Returns the number of messages that were actually added.
     */
    fun addCounted(messages: Iterable<DigestMessage>): Int

    /**
     * Adds the provided message to the digest. Returns 1 if it was added, and 0 otherwise.
     */
    fun addCounted(message: DigestMessage): Int = addCounted(listOf(message))
}

interface GuildDigestMap {
    fun digestForGuild(guildId: String): Digest
}

interface GuildMutableDigestMap : GuildDigestMap {
    override fun digestForGuild(guildId: String): MutableDigest
}
