package org.randomcat.agorabot.setup

import java.nio.file.Path

data class BotDataStandardPaths(
    val configPath: Path,
    val storagePath: Path,
    val tempPath: Path,
)

sealed class BotDataPaths {
    data class Version0(val basePath: Path) : BotDataPaths()

    sealed class WithStandardPaths : BotDataPaths() {
        abstract val paths: BotDataStandardPaths

        val configPath
            get() = paths.configPath

        val storagePath
            get() = paths.storagePath

        val tempPath
            get() = paths.tempPath
    }

    data class Version1(override val paths: BotDataStandardPaths) : WithStandardPaths()
}

fun BotDataPaths.tempDir(): Path {
    return when (this) {
        is BotDataPaths.Version0 -> basePath.resolve("tmp")
        is BotDataPaths.WithStandardPaths -> tempPath
    }
}

fun BotDataPaths.storageDir(): Path {
    return when (this) {
        is BotDataPaths.Version0 -> basePath
        is BotDataPaths.WithStandardPaths -> storagePath
    }
}
