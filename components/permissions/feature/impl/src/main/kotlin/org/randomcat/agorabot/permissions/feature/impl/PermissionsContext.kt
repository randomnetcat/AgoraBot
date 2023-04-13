package org.randomcat.agorabot.permissions.feature.impl

import org.randomcat.agorabot.*
import org.randomcat.agorabot.permissions.*
import org.randomcat.agorabot.permissions.feature.BotPermissionContextTag
import org.randomcat.agorabot.permissions.feature.BotPermissionMapTag
import org.randomcat.agorabot.permissions.feature.GuildPermissionMapTag
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("PermissionsContextFeature")

private val botMapDep = FeatureDependency.Single(BotPermissionMapTag)
private val guildMapDep = FeatureDependency.Single(GuildPermissionMapTag)

@FeatureSourceFactory
fun permissionsContextFactory(): FeatureSource<*> = object : FeatureSource<PermissionsConfig?> {
    override val featureName: String
        get() = "permissions_context_default"

    override fun readConfig(context: FeatureSetupContext): PermissionsConfig? {
        return readPermissionsConfig(context.paths.configPath.resolve("permissions.json"))
    }

    override val dependencies: List<FeatureDependency<*>>
        get() = listOf(botMapDep, guildMapDep)

    override val provides: List<FeatureElementTag<*>>
        get() = listOf(BotPermissionContextTag)

    override fun createFeature(config: PermissionsConfig?, context: FeatureSourceContext): Feature {
        val defaultedConfig = config ?: run {
            logger.warn("Unable to setup permissions config! Check for errors above. Using default permissions config.")
            PermissionsConfig(botAdminList = emptyList())
        }

        val botMap = context[botMapDep]
        val guildMap = context[guildMapDep]

        val permissionContext = object : BotPermissionContext {
            override fun isBotAdmin(userId: String): Boolean {
                return defaultedConfig.botAdmins.contains(userId)
            }

            override fun checkGlobalPath(userId: String, path: PermissionPath): BotPermissionState {
                return botMap.stateForUser(path = path, userId = userId) ?: BotPermissionState.DEFER
            }

            override fun checkUserGuildPath(
                guildId: String,
                userId: String,
                path: PermissionPath,
            ): BotPermissionState {
                return guildMap
                    .mapForGuild(guildId = guildId)
                    .stateForUser(path = path, userId = userId)
                    ?: BotPermissionState.DEFER
            }

            override fun checkRoleGuildPath(
                guildId: String,
                roleId: String,
                path: PermissionPath,
            ): BotPermissionState {
                return guildMap
                    .mapForGuild(guildId = guildId)
                    .stateForRole(path = path, roleId = roleId)
                    ?: BotPermissionState.DEFER
            }
        }

        return object : Feature {
            override fun <T> query(tag: FeatureElementTag<T>): List<T> {
                if (tag is BotPermissionContextTag) return tag.values(permissionContext)

                invalidTag(tag)
            }
        }
    }
}
