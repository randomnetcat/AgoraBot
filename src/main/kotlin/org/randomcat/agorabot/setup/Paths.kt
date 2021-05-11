package org.randomcat.agorabot.setup

import java.nio.file.Path

sealed class BotDataPaths {
    data class Version0(val basePath: Path) : BotDataPaths()
}
