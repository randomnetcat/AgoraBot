package org.randomcat.agorabot.reactionroles

import net.dv8tion.jda.api.entities.MessageReaction
import org.randomcat.agorabot.guild_state.GuildState
import org.randomcat.agorabot.guild_state.get
import org.randomcat.agorabot.guild_state.update

interface ReactionRolesMap {
    fun roleIdFor(guildId: String, messageId: String, reactionName: String): String?
}

interface MutableReactionRolesMap : ReactionRolesMap {
    fun addRoleMapping(guildId: String, messageId: String, reactionName: String, roleId: String)
    fun removeRoleMapping(guildId: String, messageId: String, reactionName: String)
}

private typealias GuildStateValueType = Map<String /* MessageId */, Map<String /* ReactionName */, String /* RoleId */>>

private const val GUILD_STATE_KEY = "reaction_roles.mapping"

data class GuildStateReactionRolesMap(
    private val guildStateFun: (guildId: String) -> GuildState,
) : MutableReactionRolesMap {
    private fun Map<String, Map<String, String>>.deepToMutable(): MutableMap<String, MutableMap<String, String>> {
        return mutableMapOf<String, MutableMap<String, String>>().also {
            mapValuesTo(it) { (_, origReactionToRoleMap) ->
                origReactionToRoleMap.toMutableMap()
            }
        }
    }

    override fun roleIdFor(guildId: String, messageId: String, reactionName: String): String? {
        return guildStateFun(guildId).get<GuildStateValueType>(GUILD_STATE_KEY)?.get(messageId)?.get(reactionName)
    }

    override fun addRoleMapping(guildId: String, messageId: String, reactionName: String, roleId: String) {
        guildStateFun(guildId).update<GuildStateValueType>(GUILD_STATE_KEY) { orig ->
            (orig ?: emptyMap()).deepToMutable().also {
                (it.getOrPut(messageId) { mutableMapOf() }).put(reactionName, roleId)
            }
        }
    }

    override fun removeRoleMapping(guildId: String, messageId: String, reactionName: String) {
        guildStateFun(guildId).update<GuildStateValueType>(GUILD_STATE_KEY) { orig ->
            if (orig == null)
                emptyMap()
            else if (!orig.containsKey(messageId))
                orig
            else
                orig.deepToMutable().also {
                    it[messageId]?.remove(reactionName)
                }
        }
    }
}

val MessageReaction.ReactionEmote.storageName
    get() = when {
        isEmoji -> asCodepoints
        isEmote -> emote.id
        else -> throw IllegalStateException()
    }
