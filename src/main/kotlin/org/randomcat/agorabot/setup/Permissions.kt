package org.randomcat.agorabot.setup

import org.randomcat.agorabot.config.ConfigPersistService
import org.randomcat.agorabot.config.PermissionsConfig
import org.randomcat.agorabot.config.readPermissionsConfig
import org.randomcat.agorabot.permissions.JsonGuildPermissionMap
import org.randomcat.agorabot.permissions.JsonPermissionMap
import org.randomcat.agorabot.permissions.MutableGuildPermissionMap
import org.randomcat.agorabot.permissions.MutablePermissionMap
import org.slf4j.LoggerFactory
import java.nio.file.Path

class PermissionsSetupResult(
    val config: PermissionsConfig,
    val botMap: MutablePermissionMap,
    val guildMap: MutableGuildPermissionMap,
)

private fun BotDataPaths.permissionsStorageDir(): Path {
    return storageDir().resolve("permissions")
}

private fun BotDataPaths.botPermissionStoragePath(): Path = permissionsStorageDir().resolve("bot.json")
private fun BotDataPaths.guildPermissionsStorageDir(): Path = permissionsStorageDir().resolve("guild")

private fun BotDataPaths.permissionsConfigPath(): Path {
    return when (this) {
        is BotDataPaths.Version0 -> basePath.resolve("permissions").resolve("config.json")
        is BotDataPaths.Version1 -> configPath.resolve("permissions.json")
    }
}

private val logger = LoggerFactory.getLogger("AgoraBotPermissionsLoader")

fun setupPermissions(paths: BotDataPaths, persistService: ConfigPersistService): PermissionsSetupResult {
    val config = readPermissionsConfig(paths.permissionsConfigPath()) ?: run {
        logger.warn("Unable to setup permissions config! Check for errors above. Using default permissions config.")
        PermissionsConfig(botAdminList = emptyList())
    }

    val botMap = JsonPermissionMap(paths.botPermissionStoragePath())
    botMap.schedulePersistenceOn(persistService)

    val guildMap = JsonGuildPermissionMap(paths.guildPermissionsStorageDir(), persistService)

    return PermissionsSetupResult(
        config = config,
        botMap = botMap,
        guildMap = guildMap,
    )
}
