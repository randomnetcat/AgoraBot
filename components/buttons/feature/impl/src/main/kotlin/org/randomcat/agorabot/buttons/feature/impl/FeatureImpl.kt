package org.randomcat.agorabot.buttons.feature.impl

import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.serializer
import org.randomcat.agorabot.*
import org.randomcat.agorabot.buttons.ButtonHandlerMap
import org.randomcat.agorabot.buttons.ButtonRequestDescriptor
import org.randomcat.agorabot.buttons.feature.*
import org.randomcat.agorabot.buttons.impl.ButtonDataStorageVersion
import org.randomcat.agorabot.buttons.impl.JsonButtonRequestDataMap
import org.randomcat.agorabot.buttons.impl.migrateButtonsStorage
import org.randomcat.agorabot.config.persist.feature.ConfigPersistServiceTag
import org.randomcat.agorabot.util.exceptionallyClose
import org.randomcat.agorabot.versioning_storage.feature.api.VersioningStorageTag
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

private const val COMPONENT_VERSION_NAME = "button_storage_default"
private val CURRENT_STORAGE_VERSION = ButtonDataStorageVersion.JSON_VALUES_STRINGS

private val versioningDep = FeatureDependency.Single(VersioningStorageTag)
private val configPersistDep = FeatureDependency.Single(ConfigPersistServiceTag)
private val tagsDep = FeatureDependency.All(ButtonDataTag)

@FeatureSourceFactory
fun buttonStorageFactory(): FeatureSource<*> = object : FeatureSource<ButtonStorageConfig> {
    override val featureName: String
        get() = "button_storage_default"

    override fun readConfig(context: FeatureSetupContext): ButtonStorageConfig {
        return ButtonStorageConfig(
            storagePath = context.paths.storagePath.resolve("buttons_data"),
        )
    }

    override val dependencies: List<FeatureDependency<*>>
        get() = listOf(versioningDep, configPersistDep, tagsDep)
    override val provides: List<FeatureElementTag<*>>
        get() = listOf(ButtonRequestDataMapTag, ButtonHandlerMapTag)

    override fun createFeature(config: ButtonStorageConfig, context: FeatureSourceContext): Feature {
        val versioningStorage = context[versioningDep]
        val configPersistService = context[configPersistDep]

        val handlerMap = run {
            ButtonHandlerMap.mergeDisjointHandlers(
                context[tagsDep].filterIsInstance<FeatureButtonData.RegisterHandlers>().map { it.handlerMap },
            )
        }

        val serializersModule =
            makeSerializersModule(buttonRequestTypes = handlerMap.handledClasses)

        migrateButtonsStorage(
            storagePath = config.storagePath,
            serializersModule = serializersModule,
            oldVersion = versioningStorage
                .versionFor(COMPONENT_VERSION_NAME)
                ?.let { ButtonDataStorageVersion.valueOf(it) }
                ?: ButtonDataStorageVersion.JSON_VALUES_INLINE,
            newVersion = CURRENT_STORAGE_VERSION,
        )

        versioningStorage.setVersion(COMPONENT_VERSION_NAME, CURRENT_STORAGE_VERSION.name)

        var dataMap: JsonButtonRequestDataMap? = null

        try {
            dataMap = JsonButtonRequestDataMap(
                storagePath = config.storagePath,
                serializersModule = serializersModule,
                clock = Clock.systemUTC(),
                persistService = configPersistService,
            )

            return object : Feature {
                override fun <T> query(tag: FeatureElementTag<T>): List<T> {
                    if (tag is ButtonHandlerMapTag) return tag.values(handlerMap)
                    if (tag is ButtonRequestDataMapTag) return tag.values(dataMap)

                    invalidTag(tag)
                }

                override fun close() {
                    dataMap.close()
                }
            }
        } catch (e: Exception) {
            exceptionallyClose(e, { dataMap?.close() })
        }
    }
}
