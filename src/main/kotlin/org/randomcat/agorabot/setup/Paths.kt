package org.randomcat.agorabot.setup

import java.nio.file.Path

sealed class BotDataPaths {
    data class Version0(val basePath: Path) : BotDataPaths()
}

fun BotDataPaths.tempDir(): Path {
    return when (this) {
        is BotDataPaths.Version0 -> basePath.resolve("tmp")
    }
}

fun BotDataPaths.storageDir(): Path {
    return when (this) {
        is BotDataPaths.Version0 -> basePath
    }
}
