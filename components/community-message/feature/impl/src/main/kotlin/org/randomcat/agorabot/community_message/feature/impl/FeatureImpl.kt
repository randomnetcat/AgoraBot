package org.randomcat.agorabot.community_message.feature.impl

import org.randomcat.agorabot.*
import org.randomcat.agorabot.community_message.feature.CommunityMessageStorageTag
import org.randomcat.agorabot.community_message.impl.JsonCommunityMessageStorage
import java.nio.file.Path
import kotlin.io.path.createDirectories

private data class CommunityMessageStorageConfig(
    val storageDir: Path,
)

private object CommunityMessageStorageCacheKey

@FeatureSourceFactory
fun communityMessageStorageFeature() = object : FeatureSource {
    override val featureName: String
        get() = "community_message_storage_default"

    override fun readConfig(context: FeatureSetupContext): CommunityMessageStorageConfig {
        return CommunityMessageStorageConfig(storageDir = context.paths.storagePath.resolve("community_message"))
    }

    override fun createFeature(config: Any?): Feature {
        config as CommunityMessageStorageConfig

        return object : Feature {
            override fun <T> query(context: FeatureContext, tag: FeatureElementTag<T>): FeatureQueryResult<T> {
                if (tag is CommunityMessageStorageTag) return tag.result(context.cache(CommunityMessageStorageCacheKey) {
                    config.storageDir.createDirectories()

                    context.alwaysCloseObject(
                        {
                            JsonCommunityMessageStorage(baseDir = config.storageDir)
                        },
                        {
                            it.close()
                        },
                    )
                })

                return FeatureQueryResult.NotFound
            }
        }
    }
}
