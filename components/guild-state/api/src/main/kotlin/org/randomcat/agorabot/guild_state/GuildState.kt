package org.randomcat.agorabot.guild_state

interface GuildState : StringState

interface GuildStateMap {
    fun stateForGuild(guildId: String): GuildState
}
