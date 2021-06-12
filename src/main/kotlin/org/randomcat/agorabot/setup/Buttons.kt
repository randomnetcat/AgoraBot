package org.randomcat.agorabot.setup

import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.serializer
import org.randomcat.agorabot.buttons.ButtonRequestDataMap
import org.randomcat.agorabot.buttons.ButtonRequestDescriptor
import org.randomcat.agorabot.buttons.JsonButtonRequestDataMap
import org.randomcat.agorabot.config.ConfigPersistService
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

fun setupButtonDataMap(
    paths: BotDataPaths,
    buttonRequestTypes: Set<KClass<*>>,
    persistService: ConfigPersistService,
): ButtonRequestDataMap {
    val storagePath = paths.storagePath.resolve("buttons_data")
    val serializersModule = makeSerializersModule(buttonRequestTypes = buttonRequestTypes)

    return JsonButtonRequestDataMap(
        storagePath = storagePath,
        serializersModule = serializersModule,
        clock = Clock.systemUTC(),
    ).also { it.schedulePersistenceOn(persistService) }
}
