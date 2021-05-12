package org.randomcat.agorabot.setup

import org.randomcat.agorabot.config.JsonVersioningStorage
import org.randomcat.agorabot.config.VersioningStorage
import java.nio.file.Path

private fun BotDataPaths.storageVersionsStoragePath(): Path {
    return storageDir().resolve("storage_versions")
}

fun setupStorageVersioning(paths: BotDataPaths): VersioningStorage {
    return JsonVersioningStorage(paths.storageVersionsStoragePath())
}
