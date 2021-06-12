package org.randomcat.agorabot.setup

import java.nio.file.Path

data class BotDataStandardPaths(
    val configPath: Path,
    val storagePath: Path,
    val tempPath: Path,
)

sealed class BotDataPaths private constructor(val version: Int) {
    abstract val paths: BotDataStandardPaths

    val configPath
        get() = paths.configPath

    val storagePath
        get() = paths.storagePath

    val tempPath
        get() = paths.tempPath

    data class Version1(override val paths: BotDataStandardPaths) : BotDataPaths(1)
    data class Version2(override val paths: BotDataStandardPaths) : BotDataPaths(2)
}
