package org.randomcat.agorabot.commands

import org.randomcat.agorabot.commands.impl.*
import org.randomcat.agorabot.irc.IrcUserListConfig
import org.randomcat.agorabot.irc.MutableIrcUserListMessageMap
import org.randomcat.agorabot.permissions.GuildScope

private val USER_LIST_PERMISSION = GuildScope.command("irc").action("user_list_manage")

class IrcCommand(
    strategy: BaseCommandStrategy,
    private val lookupConnectedIrcChannel: (guildId: String, channelId: String) -> String?,
    private val persistentWhoMessageMap: MutableIrcUserListMessageMap,
) : BaseCommand(strategy) {
    override fun TopLevelArgumentDescriptionReceiver<ExecutionReceiverImpl, PermissionsExtensionMarker>.impl() {
        subcommands {
            subcommand("user_list") {
                subcommand("create") {
                    noArgs().permissions(USER_LIST_PERMISSION) { _ ->
                        requiresGuild { guildInfo ->
                            val connectedIrcChannel = lookupConnectedIrcChannel(guildInfo.guildId, currentChannel().id)

                            if (connectedIrcChannel == null) {
                                respond("There is no IRC channel connected to this Discord channel.")
                                return@permissions
                            }

                            currentChannel().sendMessage("**IRC USER LIST TO BE FILLED**").queue { message ->
                                persistentWhoMessageMap.addConfigForGuild(
                                    guildId = guildInfo.guildId,
                                    config = IrcUserListConfig(
                                        discordChannelId = currentChannel().id,
                                        discordMessageId = message.id,
                                        ircChannelName = connectedIrcChannel,
                                    ),
                                )
                            }
                        }
                    }
                }

                subcommand("remove_all") {
                    noArgs().permissions(USER_LIST_PERMISSION) { _ ->
                        requiresGuild { guildInfo ->
                            persistentWhoMessageMap.clearConfigsForGuild(guildInfo.guildId)

                            respond(
                                "Persistent who messages will no longer update. You probably want to delete them now."
                            )
                        }
                    }
                }
            }
        }
    }
}
