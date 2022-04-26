package org.randomcat.agorabot.permissions.feature.impl

import org.randomcat.agorabot.*
import org.randomcat.agorabot.config.persist.feature.configPersistService
import org.randomcat.agorabot.permissions.JsonGuildPermissionMap
import org.randomcat.agorabot.permissions.JsonPermissionMap
import org.randomcat.agorabot.permissions.feature.BotPermissionMapTag
import org.randomcat.agorabot.permissions.feature.GuildPermissionMapTag
import java.nio.file.Path

private data class PermissionsStorageConfig(
    val botStoragePath: Path,
    val guildStorageDir: Path,
)

private object BotPermissionMapCacheKey
private object GuildPermissionMapCacheKey

@FeatureSourceFactory
fun permissionsStorageFactory() = object : FeatureSource {
    override val featureName: String
        get() = "permissions_storage_default"

    override fun readConfig(context: FeatureSetupContext): PermissionsStorageConfig {
        return PermissionsStorageConfig(
            botStoragePath = context.paths.storagePath.resolve("permissions").resolve("bot.json"),
            guildStorageDir = context.paths.storagePath.resolve("permissions").resolve("guild"),
        )
    }

    override fun createFeature(config: Any?): Feature {
        config as PermissionsStorageConfig

        return object : Feature {
            override fun <T> query(context: FeatureContext, tag: FeatureElementTag<T>): FeatureQueryResult<T> {
                if (tag is BotPermissionMapTag) return tag.result(context.cache(BotPermissionMapCacheKey) {
                    context.alwaysCloseObject(
                        {
                            JsonPermissionMap(config.botStoragePath, persistService = context.configPersistService)
                        },
                        {
                            it.close()
                        },
                    )
                })

                if (tag is GuildPermissionMapTag) return tag.result(context.cache(GuildPermissionMapCacheKey) {
                    context.alwaysCloseObject(
                        {
                            JsonGuildPermissionMap(config.guildStorageDir, context.configPersistService)
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
