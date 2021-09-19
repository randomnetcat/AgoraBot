package org.randomcat.agorabot.setup.features

import org.randomcat.agorabot.setup.BotDataPaths
import java.nio.file.Path

val BotDataPaths.featureConfigDir: Path
    get() = configPath.resolve("features")
