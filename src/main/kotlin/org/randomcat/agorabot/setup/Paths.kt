package org.randomcat.agorabot.setup

import java.nio.file.Path

sealed class BotDataPaths {
    data class Version0(val basePath: Path) : BotDataPaths()

    data class Version1(
        val configPath: Path,
        val storagePath: Path,
        val tempPath: Path,
    ) : BotDataPaths()
}

fun BotDataPaths.tempDir(): Path {
    return when (this) {
        is BotDataPaths.Version0 -> basePath.resolve("tmp")
        is BotDataPaths.Version1 -> tempPath
    }
}

fun BotDataPaths.storageDir(): Path {
    return when (this) {
        is BotDataPaths.Version0 -> basePath
        is BotDataPaths.Version1 -> storagePath
    }
}
