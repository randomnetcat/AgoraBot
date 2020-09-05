package org.randomcat.agorabot

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class JsonPrefixMap(private val default: String, storagePath: Path) : MutableGuildPrefixMap {
    companion object {
        private val FILE_CHARSET = Charsets.UTF_8

        fun readFromFile(path: Path): Map<String, String> {
            if (Files.notExists(path)) return emptyMap()

            val content = Files.readString(path, FILE_CHARSET)
            return Json.decodeFromString<Map<String, String>>(content)
        }

        fun writeToFile(path: Path, data: Map<String, String>) {
            withTempFile { tempFile ->
                val content = Json.encodeToString<Map<String, String>>(data)

                Files.writeString(
                    tempFile,
                    content,
                    FILE_CHARSET,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.CREATE,
                )

                Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private val map: AtomicReference<ImmutableMap<String, String>> =
        AtomicReference(readFromFile(storagePath).toImmutableMap())

    private val executor = Executors.newSingleThreadScheduledExecutor()

    init {
        executor.scheduleAtFixedRate(object : Runnable {
            private var lastMap: ImmutableMap<String, String>? = null

            override fun run() {
                val newMap: ImmutableMap<String, String> = map.get()

                if (newMap != lastMap) {
                    writeToFile(storagePath, newMap)
                }
            }
        }, 0, 5, TimeUnit.SECONDS)

        Runtime.getRuntime().addShutdownHook(Thread {
            writeToFile(storagePath, map.get())
        })
    }

    override fun setPrefixForGuild(guildId: String, prefix: String) {
        map.updateAndGet { oldMap ->
            val mutableMap = oldMap.toMutableMap()
            mutableMap[guildId] = prefix
            mutableMap.toImmutableMap()
        }
    }

    override fun prefixForGuild(guildId: String): String {
        return map.get().getOrDefault(guildId, default)
    }
}
