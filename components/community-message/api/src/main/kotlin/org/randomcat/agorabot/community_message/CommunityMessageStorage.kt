package org.randomcat.agorabot.community_message

import java.time.Instant

data class CommunityMessageMetadata(
    val createdBy: String,
    val creationTime: Instant,
    val channelId: String,
    val messageId: String,
    val maxRevision: CommunityMessageRevisionNumber?,
    val group: String?,
)

data class CommunityMessageRevisionMetadata(
    val author: String,
    val revisionTime: Instant,
)

interface CommunityMessageStorage {
    fun storageForGuild(guildId: String): CommunityMessageGuildStorage
}

@JvmInline
value class CommunityMessageRevisionNumber(val value: Long)

interface CommunityMessageGuildStorage {
    /**
     * Stores a record of a community message. The name must be unique. Creation will fail if the name is already used.
     * @return whether creation succeeded
     */
    fun createMessage(name: String, metadata: CommunityMessageMetadata): Boolean

    fun messageMetadata(name: String): CommunityMessageMetadata?

    fun updateMetadata(name: String, map: (CommunityMessageMetadata) -> CommunityMessageMetadata): Boolean

    /**
     * Attempts to store a new revision of a message. Fails if no message with that name exists.
     * @return the revision number of the new revision, or null if storing the revision did not succeed
     */
    fun createRevision(
        name: String,
        metadata: CommunityMessageRevisionMetadata,
        content: String,
    ): CommunityMessageRevisionNumber?

    fun messageNames(): Set<String>

    fun removeMessage(name: String): Boolean
}
