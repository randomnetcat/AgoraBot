package org.randomcat.agorabot.commands

import org.randomcat.agorabot.commands.impl.*
import org.randomcat.agorabot.listener.MutableGuildPrefixMap
import org.randomcat.agorabot.permissions.GuildScope

private val PROHIBITED_CATEGORIES = listOf(
    CharCategory.CONTROL,
    CharCategory.FORMAT,
    CharCategory.LINE_SEPARATOR,
    CharCategory.UNASSIGNED,
    CharCategory.NON_SPACING_MARK,
    CharCategory.PRIVATE_USE,
)

class PrefixCommand(
    strategy: BaseCommandStrategy,
    private val prefixMap: MutableGuildPrefixMap,
) : BaseCommand(strategy) {
    override fun BaseCommandImplReceiver.impl() {
        subcommands {
            subcommand("list") {
                noArgs().requiresGuild() {
                    val guildId = currentGuildInfo.guildId

                    val prefixes = prefixMap.prefixesForGuild(guildId).joinToString { "`${it}`" }
                    respond("The following prefixes can be used: ${prefixes}")
                }
            }

            subcommand("add") {
                args(
                    StringArg("new_prefix"),
                ).requiresGuild(
                ).permissions(
                    GuildScope.command("prefix").action("set"),
                ) { (newPrefix) ->
                    val guildId = currentGuildInfo.guildId

                    if (newPrefix.isBlank()) {
                        respond("The prefix cannot be empty. Stop it.")
                        return@permissions
                    }

                    if (newPrefix.any { PROHIBITED_CATEGORIES.contains(it.category) }) {
                        respond("The specified prefix contains an illegal character.")
                        return@permissions
                    }

                    if (prefixMap.prefixesForGuild(guildId).contains(newPrefix)) {
                        respond("That's already a prefix.")
                        return@permissions
                    }

                    prefixMap.addPrefixForGuild(guildId, newPrefix)
                    respond("The prefix has been added.")
                }
            }

            subcommand("remove") {
                args(
                    StringArg("prefix"),
                ).requiresGuild(
                ).permissions(
                    GuildScope.command("prefix").action("set"),
                ) { (newPrefix) ->
                    val guildId = currentGuildInfo.guildId

                    if (!prefixMap.prefixesForGuild(guildId).contains(newPrefix)) {
                        respond("That's not a prefix.")
                        return@permissions
                    }

                    prefixMap.removePrefixForGuild(guildId, newPrefix)
                    respond("The prefix has been removed.")
                }
            }

            subcommand("clear") {
                noArgs().requiresGuild().permissions(GuildScope.command("prefix").action("set")) { _ ->
                    prefixMap.clearPrefixesForGuild(currentGuildInfo.guildId)
                    respond("All prefixes have been removed. You can still @mention the bot to run commands.")
                }
            }
        }
    }
}
