package org.randomcat.agorabot.buttons.feature.impl

import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.serializer
import org.randomcat.agorabot.*
import org.randomcat.agorabot.buttons.ButtonRequestDescriptor
import org.randomcat.agorabot.buttons.feature.ButtonRequestDataMapTag
import org.randomcat.agorabot.buttons.feature.buttonHandlerMap
import org.randomcat.agorabot.buttons.impl.ButtonDataStorageVersion
import org.randomcat.agorabot.buttons.impl.JsonButtonRequestDataMap
import org.randomcat.agorabot.buttons.impl.migrateButtonsStorage
import org.randomcat.agorabot.config.persist.feature.configPersistService
import org.randomcat.agorabot.versioning_storage.feature.api.versioningStorage
import java.nio.file.Path
import java.time.Clock
import kotlin.reflect.KClass
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubclassOf

private fun makeSerializersModule(buttonRequestTypes: Set<KClass<*>>): SerializersModule {
    for (buttonRequestType in buttonRequestTypes) {
        require(buttonRequestType.isSubclassOf(ButtonRequestDescriptor::class)) {
            "Class $buttonRequestType is not derived from ButtonRequestDescriptor"
        }
    }

    @Suppress("UNCHECKED_CAST")
    return SerializersModule {
        polymorphic(ButtonRequestDescriptor::class) {
            for (buttonRequestType in buttonRequestTypes) {
                subclass(
                    buttonRequestType as KClass<ButtonRequestDescriptor>,
                    serializer(buttonRequestType.createType()) as KSerializer<ButtonRequestDescriptor>,
                )
            }
        }
    }
}

private data class ButtonStorageConfig(
    val storagePath: Path,
)

private object ButtonRequestDataMapCacheKey

private const val COMPONENT_VERSION_NAME = "button_storage_default"
private val CURRENT_STORAGE_VERSION = ButtonDataStorageVersion.JSON_VALUES_STRINGS

@FeatureSourceFactory
fun buttonStorageFactory() = object : FeatureSource {
    override val featureName: String
        get() = "button_storage_default"

    override fun readConfig(context: FeatureSetupContext): ButtonStorageConfig {
        return ButtonStorageConfig(
            storagePath = context.paths.storagePath.resolve("buttons_data"),
        )
    }

    override fun createFeature(config: Any?): Feature {
        config as ButtonStorageConfig

        return object : Feature {
            override fun <T> query(context: FeatureContext, tag: FeatureElementTag<T>): FeatureQueryResult<T> {
                if (tag is ButtonRequestDataMapTag) return tag.result(context.cache(ButtonRequestDataMapCacheKey) {
                    val serializersModule =
                        makeSerializersModule(buttonRequestTypes = context.buttonHandlerMap.handledClasses)

                    migrateButtonsStorage(
                        storagePath = config.storagePath,
                        serializersModule = serializersModule,
                        oldVersion = context.versioningStorage
                            .versionFor(COMPONENT_VERSION_NAME)
                            ?.let { ButtonDataStorageVersion.valueOf(it) }
                            ?: ButtonDataStorageVersion.JSON_VALUES_INLINE,
                        newVersion = CURRENT_STORAGE_VERSION,
                    )

                    context.versioningStorage.setVersion(COMPONENT_VERSION_NAME, CURRENT_STORAGE_VERSION.name)

                    JsonButtonRequestDataMap(
                        storagePath = config.storagePath,
                        serializersModule = serializersModule,
                        clock = Clock.systemUTC(),
                        persistService = context.configPersistService,
                    )
                })

                return FeatureQueryResult.NotFound
            }
        }
    }
}
