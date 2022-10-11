package org.randomcat.agorabot.secrethitler.storage.feature.impl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.randomcat.agorabot.*
import org.randomcat.agorabot.config.persist.feature.configPersistService
import org.randomcat.agorabot.secrethitler.storage.api.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.storage.feature.api.SecretHitlerImpersonationMapTag
import org.randomcat.agorabot.secrethitler.storage.feature.api.SecretHitlerRepositoryTag
import org.randomcat.agorabot.secrethitler.storage.impl.JsonSecretHitlerChannelGameMap
import org.randomcat.agorabot.secrethitler.storage.impl.JsonSecretHitlerGameList
import org.randomcat.agorabot.secrethitler.storage.impl.SecretHitlerJsonImpersonationMap
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.NoSuchFileException
import java.nio.file.Path
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

@FeatureSourceFactory
fun secretHitlerStorageFactory() = object : FeatureSource {
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

    override fun createFeature(config: Any?): Feature {
        val typedConfig = config as SecretHitlerFeatureConfig

        val repositoryCacheKey = Any()
        val impersonationMapCacheKey = Any()

        return object : Feature {
            private val FeatureContext.repository
                get() = cache(repositoryCacheKey) {
                    SecretHitlerRepository(
                        gameList = alwaysCloseObject(
                            {
                                JsonSecretHitlerGameList(
                                    storagePath = typedConfig.baseStoragePath.resolve("games"),
                                    persistService = configPersistService,
                                )
                            },
                            {
                                it.close()
                            },
                        ),
                        channelGameMap = alwaysCloseObject(
                            {
                                JsonSecretHitlerChannelGameMap(
                                    storagePath = typedConfig.baseStoragePath.resolve("games_by_channel"),
                                    persistService = configPersistService,
                                )
                            },
                            {
                                it.close()
                            },
                        ),
                    )
                }

            private val FeatureContext.impersonationMap
                get() = if (typedConfig.enableImpersonation)
                    cache(impersonationMapCacheKey) {
                        SecretHitlerJsonImpersonationMap(
                            typedConfig.baseStoragePath.resolve("impersonation_data"),
                            configPersistService,
                        )
                    }
                else
                    null

            override fun <T> query(context: FeatureContext, tag: FeatureElementTag<T>): FeatureQueryResult<T> {
                if (tag is SecretHitlerRepositoryTag) return tag.result(context.repository)

                if (tag is SecretHitlerImpersonationMapTag) {
                    context.impersonationMap?.let { return tag.result(it) }
                }

                return FeatureQueryResult.NotFound
            }
        }
    }
}