package org.randomcat.agorabot.secrethitler.storage.feature.impl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.randomcat.agorabot.*
import org.randomcat.agorabot.config.persist.feature.ConfigPersistServiceTag
import org.randomcat.agorabot.secrethitler.storage.api.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.storage.feature.api.SecretHitlerImpersonationMapTag
import org.randomcat.agorabot.secrethitler.storage.feature.api.SecretHitlerRepositoryTag
import org.randomcat.agorabot.secrethitler.storage.impl.JsonSecretHitlerChannelGameMap
import org.randomcat.agorabot.secrethitler.storage.impl.JsonSecretHitlerGameList
import org.randomcat.agorabot.secrethitler.storage.impl.JsonSecretHitlerImpersonationMap
import org.randomcat.agorabot.util.exceptionallyClose
import org.randomcat.agorabot.util.sequentiallyClose
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText

private data class SecretHitlerFeatureConfig(
    val baseStoragePath: Path,
    val enableImpersonation: Boolean,
)

@Serializable
private data class SecretHitlerConfigDto(
    @SerialName("enable_impersonation") val enableImpersonation: Boolean = false,
)

private val logger = LoggerFactory.getLogger("AgoraBotSecretHitlerDefaultStorageFeature")

private val persistServiceDep = FeatureDependency.Single(ConfigPersistServiceTag)

@FeatureSourceFactory
fun secretHitlerStorageFactory(): FeatureSource<*> = object : FeatureSource<SecretHitlerFeatureConfig> {
    override val featureName: String
        get() = "secret_hitler_storage_default"

    override fun readConfig(context: FeatureSetupContext): SecretHitlerFeatureConfig {
        val defaultConfig = SecretHitlerConfigDto()

        val fileConfig = try {
            Json.decodeFromString<SecretHitlerConfigDto>(
                context.paths.configPath.resolve("features").resolve("secret_hitler.json").readText(),
            )
        } catch (e: NoSuchFileException) {
            logger.warn("Secret Hitler config file not found")
            defaultConfig
        } catch (e: IOException) {
            logger.warn("Error loading Secret Hitler config file", e)
            defaultConfig
        }

        return SecretHitlerFeatureConfig(
            baseStoragePath = context.paths.storagePath.resolve("secret_hitler"),
            enableImpersonation = fileConfig.enableImpersonation,
        )
    }

    override val dependencies: List<FeatureDependency<*>>
        get() = listOf(persistServiceDep)

    override val provides: List<FeatureElementTag<*>>
        get() = listOf(SecretHitlerRepositoryTag, SecretHitlerImpersonationMapTag)

    override fun createFeature(config: SecretHitlerFeatureConfig, context: FeatureSourceContext): Feature {
        val persistService = context[persistServiceDep]

        var gameList: JsonSecretHitlerGameList? = null
        var channelMap: JsonSecretHitlerChannelGameMap? = null
        var impersonationMap: JsonSecretHitlerImpersonationMap? = null

        try {
            gameList = JsonSecretHitlerGameList(
                storagePath = config.baseStoragePath.createDirectories().resolve("games"),
                persistService = persistService,
            )

            channelMap = JsonSecretHitlerChannelGameMap(
                storagePath = config.baseStoragePath.createDirectories().resolve("games_by_channel"),
                persistService = persistService,
            )

            impersonationMap = JsonSecretHitlerImpersonationMap(
                config.baseStoragePath.createDirectories().resolve("impersonation_data"),
                persistService,
            )

            val repository = SecretHitlerRepository(
                gameList = gameList,
                channelGameMap = channelMap,
            )

            return object : Feature {
                override fun <T> query(tag: FeatureElementTag<T>): List<T> {
                    if (tag is SecretHitlerRepositoryTag) return tag.values(repository)
                    if (tag is SecretHitlerImpersonationMapTag) return tag.values(impersonationMap)

                    invalidTag(tag)
                }

                override fun close() {
                    sequentiallyClose({ gameList.close() }, { channelMap.close() }, { impersonationMap.close() })
                }
            }
        } catch (e: Exception) {
            exceptionallyClose(e, { gameList?.close() }, { channelMap?.close() }, { impersonationMap?.close() })
        }
    }
}
