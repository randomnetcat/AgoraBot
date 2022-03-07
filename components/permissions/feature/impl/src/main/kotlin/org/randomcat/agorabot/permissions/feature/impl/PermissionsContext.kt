package org.randomcat.agorabot.permissions.feature.impl

import org.randomcat.agorabot.*
import org.randomcat.agorabot.permissions.*
import org.randomcat.agorabot.permissions.feature.BotPermissionContextTag
import org.randomcat.agorabot.permissions.feature.botPermissionMap
import org.randomcat.agorabot.permissions.feature.guildPermissionMap
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("PermissionsContextFeature")

private object BotPermissionContextCacheKey

@FeatureSourceFactory
fun permissionsContextFactory() = object : FeatureSource {
    override val featureName: String
        get() = "permissions_context_default"

    override fun readConfig(context: FeatureSetupContext): PermissionsConfig? {
        return readPermissionsConfig(context.paths.configPath.resolve("permissions.json"))
    }

    override fun createFeature(config: Any?): Feature {
        val defaultedConfig = config as PermissionsConfig? ?: run {
            logger.warn("Unable to setup permissions config! Check for errors above. Using default permissions config.")
            PermissionsConfig(botAdminList = emptyList())
        }

        return object : Feature {
            override fun <T> query(context: FeatureContext, tag: FeatureElementTag<T>): FeatureQueryResult<T> {
                if (tag is BotPermissionContextTag) return tag.result(context.cache(BotPermissionContextCacheKey) {
                    val botMap = context.botPermissionMap
                    val guildMap = context.guildPermissionMap

                    object : BotPermissionContext {
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
                })

                return FeatureQueryResult.NotFound
            }
        }
    }
}
