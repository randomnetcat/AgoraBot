package org.randomcat.agorabot.setup

import org.randomcat.agorabot.config.ConfigPersistService
import org.randomcat.agorabot.config.GuildStateMap
import org.randomcat.agorabot.config.JsonGuildStateMap
import java.nio.file.Path

private fun BotDataPaths.guildStateStorageDir(): Path {
    return storagePath.resolve("guild_storage")
}

fun setupGuildStateMap(paths: BotDataPaths, persistService: ConfigPersistService): GuildStateMap {
    return JsonGuildStateMap(paths.guildStateStorageDir(), persistService)
}
