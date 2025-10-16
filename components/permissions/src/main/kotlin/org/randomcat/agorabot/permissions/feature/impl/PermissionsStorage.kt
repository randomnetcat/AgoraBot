package org.randomcat.agorabot.permissions.feature.impl

import org.randomcat.agorabot.*
import org.randomcat.agorabot.config.persist.feature.ConfigPersistServiceTag
import org.randomcat.agorabot.permissions.JsonGuildPermissionMap
import org.randomcat.agorabot.permissions.JsonPermissionMap
import org.randomcat.agorabot.permissions.feature.BotPermissionMapTag
import org.randomcat.agorabot.permissions.feature.GuildPermissionMapTag
import org.randomcat.agorabot.util.exceptionallyClose
import org.randomcat.agorabot.util.sequentiallyClose
import java.nio.file.Path

private data class PermissionsStorageConfig(
    val botStoragePath: Path,
    val guildStorageDir: Path,
)

private val persistServiceDep = FeatureDependency.Single(ConfigPersistServiceTag)

@FeatureSourceFactory
fun permissionsStorageFactory(): FeatureSource<*> = object : FeatureSource<PermissionsStorageConfig> {
    override val featureName: String
        get() = "permissions_storage_default"

    override val dependencies: List<FeatureDependency<*>>
        get() = listOf(persistServiceDep)

    override val provides: List<FeatureElementTag<*>>
        get() = listOf(BotPermissionMapTag, GuildPermissionMapTag)

    override fun readConfig(context: FeatureSetupContext): PermissionsStorageConfig {
        return PermissionsStorageConfig(
            botStoragePath = context.paths.storagePath.resolve("permissions").resolve("bot.json"),
            guildStorageDir = context.paths.storagePath.resolve("permissions").resolve("guild"),
        )
    }

    override fun createFeature(config: PermissionsStorageConfig, context: FeatureSourceContext): Feature {
        val persistService = context[persistServiceDep]

        var botMap: JsonPermissionMap? = null
        var guildMap: JsonGuildPermissionMap? = null

        try {
            botMap = JsonPermissionMap(config.botStoragePath, persistService = persistService)
            guildMap = JsonGuildPermissionMap(config.guildStorageDir, persistService)

            return object : Feature {
                override fun <T> query(tag: FeatureElementTag<T>): List<T> {
                    if (tag is BotPermissionMapTag) return tag.values(botMap)
                    if (tag is GuildPermissionMapTag) return tag.values(guildMap)

                    invalidTag(tag)
                }

                override fun close() {
                    sequentiallyClose({ botMap.close() }, { guildMap.close() })
                }
            }
        } catch (e: Exception) {
            exceptionallyClose(e, { botMap?.close() }, { guildMap?.close() })
        }
    }
}
