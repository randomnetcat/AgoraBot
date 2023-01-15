package org.randomcat.agorabot.permissions.feature.impl

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

private val logger = LoggerFactory.getLogger("PermissionsConfig")

internal data class PermissionsConfig(
    val botAdmins: ImmutableList<String>,
) {
    constructor(botAdminList: List<String>) : this(botAdminList.toImmutableList())
}

internal fun readPermissionsConfig(configPath: Path): PermissionsConfig? {
    if (Files.notExists(configPath)) {
        logger.warn("Permissions config path $configPath does not exist!")
        return null
    }

    val json = Json.parseToJsonElement(Files.readString(configPath, Charsets.UTF_8))
    if (json !is JsonObject) {
        logger.error("Permissions config should be a JSON object!")
        return null
    }

    val adminList = json["admins"] ?: return PermissionsConfig(botAdminList = emptyList())

    if (adminList !is JsonArray || adminList.any { it !is JsonPrimitive }) {
        logger.error("permissions.admins should be an array of user IDs!")
        return null
    }

    return PermissionsConfig(
        botAdminList = adminList.map { (it as JsonPrimitive).content }
    )
}
