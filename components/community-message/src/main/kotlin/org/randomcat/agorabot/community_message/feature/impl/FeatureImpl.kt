package org.randomcat.agorabot.community_message.feature.impl

import org.randomcat.agorabot.FeatureSetupContext
import org.randomcat.agorabot.FeatureSource
import org.randomcat.agorabot.FeatureSourceFactory
import org.randomcat.agorabot.community_message.feature.CommunityMessageStorageTag
import org.randomcat.agorabot.community_message.impl.JsonCommunityMessageStorage
import java.nio.file.Path
import kotlin.io.path.createDirectories

private data class CommunityMessageStorageConfig(
    val storageDir: Path,
)

private fun readConfig(context: FeatureSetupContext): CommunityMessageStorageConfig {
    return CommunityMessageStorageConfig(storageDir = context.paths.storagePath.resolve("community_message"))
}

@FeatureSourceFactory
fun communityMessageStorageFeature(): FeatureSource<*> = FeatureSource.ofCloseable(
    name = "community_message_storage_default",
    readConfig = ::readConfig,
    element = CommunityMessageStorageTag,
    create = { config, _ ->
        config.storageDir.createDirectories()
        JsonCommunityMessageStorage(baseDir = config.storageDir)
    },
    close = { it.close() },
)
