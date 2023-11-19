package org.randomcat.agorabot.commands

import org.randomcat.agorabot.commands.base.BaseCommand
import org.randomcat.agorabot.commands.base.BaseCommandImplReceiver
import org.randomcat.agorabot.commands.base.BaseCommandStrategy
import org.randomcat.agorabot.commands.base.help.help
import org.randomcat.agorabot.commands.base.requirements.discord.InGuildSimple
import org.randomcat.agorabot.commands.base.requirements.discord.currentGuildId
import org.randomcat.agorabot.commands.base.requirements.discord.currentMessageEvent
import org.randomcat.agorabot.commands.base.requires
import org.randomcat.agorabot.guild_state.UserState
import org.randomcat.agorabot.guild_state.UserStateMap
import org.randomcat.agorabot.guild_state.update
import org.randomcat.agorabot.irc.RELAY_USER_STATE_KEY
import org.randomcat.agorabot.irc.RelayUserState

private fun UserState.updateRelayState(mapper: (RelayUserState) -> RelayUserState) {
    update<RelayUserState>(RELAY_USER_STATE_KEY) {
        mapper(it ?: RelayUserState.DEFAULT)
    }
}

class RelayCommand(
    strategy: BaseCommandStrategy,
    private val userStateMap: UserStateMap,
) : BaseCommand(strategy) {
    override fun BaseCommandImplReceiver.impl() {
        subcommands {
            subcommand("self") {
                subcommand("disable") {
                    noArgs().help("Disables forwarding of messages from this account in this Guild")
                        .requires(InGuildSimple) {
                            val guildId = currentGuildId

                            userStateMap.stateForUser(currentMessageEvent.author.id).updateRelayState { old ->
                                when (old) {
                                    is RelayUserState.Version0 -> {
                                        RelayUserState.Version0(disabledGuilds = buildSet {
                                            addAll(old.disabledGuilds)
                                            add(guildId)
                                        })
                                    }
                                }
                            }

                            respond("Disabled relay forwarding for your account in this Guild")
                        }
                }

                subcommand("enable") {
                    noArgs().help("Enables forwarding of messages from this account in this Guild")
                        .requires(InGuildSimple) {
                            val guildId = currentGuildId

                            userStateMap.stateForUser(currentMessageEvent.author.id).updateRelayState { old ->
                                when (old) {
                                    is RelayUserState.Version0 -> {
                                        RelayUserState.Version0(disabledGuilds = buildSet {
                                            addAll(old.disabledGuilds)
                                            remove(guildId)
                                        })
                                    }
                                }
                            }

                            respond("Enabled relay forwarding for your account in this Guild")
                        }
                }
            }
        }
    }
}
