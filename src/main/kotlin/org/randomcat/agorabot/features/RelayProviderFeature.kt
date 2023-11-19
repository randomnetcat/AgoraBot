package org.randomcat.agorabot.features

import kotlinx.collections.immutable.toPersistentList
import net.dv8tion.jda.api.JDA
import org.randomcat.agorabot.*
import org.randomcat.agorabot.commands.RelayCommand
import org.randomcat.agorabot.config.CommandOutputMappingTag
import org.randomcat.agorabot.config.RelayConnectedEndpointMapTag
import org.randomcat.agorabot.guild_state.feature.UserStateStorageTag
import org.randomcat.agorabot.irc.*

private fun ircAndDiscordMapping(
    jda: JDA,
    relayConnectedEndpointMap: RelayConnectedEndpointMap,
    relayEntriesConfig: IrcRelayEntriesConfig,
): CommandOutputMapping {
    data class IrcChannelLookupKey(val client: IrcClient, val channelName: String)

    val discordToIrcMap: MutableMap<String, MutableList<() -> List<CommandOutputSink>>> = mutableMapOf()
    val ircToDiscordMap: MutableMap<IrcChannelLookupKey, MutableList<() -> List<CommandOutputSink>>> = mutableMapOf()

    for (entry in relayEntriesConfig.entries) {
        val endpoints = entry.endpointNames.map { relayConnectedEndpointMap.getByName(it) }.toPersistentList()

        endpoints.mapIndexed { index, relayConnectedEndpoint ->
            val otherEndpoints = endpoints.removeAt(index)

            @Suppress(
                "UNUSED_VARIABLE",
                "MoveLambdaOutsideParentheses", // Lambda is the value, so it should be in parentheses
            )
            val ensureExhaustive = when (relayConnectedEndpoint) {
                is RelayConnectedDiscordEndpoint -> {
                    require(jda == relayConnectedEndpoint.jda) {
                        "Multiple JDAs are not supported here"
                    }

                    val list = discordToIrcMap.getOrPut(relayConnectedEndpoint.channelId) { mutableListOf() }

                    list.add({
                        otherEndpoints.mapNotNull { it.commandOutputSink() }
                    })
                }

                is RelayConnectedIrcEndpoint -> {
                    val list = ircToDiscordMap.getOrPut(
                        IrcChannelLookupKey(
                            client = relayConnectedEndpoint.client,
                            channelName = relayConnectedEndpoint.channelName,
                        ),
                    ) { mutableListOf() }

                    list.add({
                        otherEndpoints.mapNotNull { it.commandOutputSink() }
                    })
                }
            }
        }
    }

    return CommandOutputMapping(
        sinksForDiscordFun = { source ->
            discordToIrcMap[source.event.channel.id]?.flatMap { it() } ?: emptyList()
        },
        sinksForIrcFun = { source ->
            val key = IrcChannelLookupKey(client = source.event.client, channelName = source.event.channel.name)
            ircToDiscordMap[key]?.flatMap { it() } ?: emptyList()
        },
    )
}

private val ircDep = FeatureDependency.AtMostOne(IrcSetupTag)
private val jdaDep = FeatureDependency.Single(JdaTag)
private val coroutineScopeDep = FeatureDependency.Single(CoroutineScopeTag)
private val userStateMapDep = FeatureDependency.Single(UserStateStorageTag)

@FeatureSourceFactory
fun relayProviderFeatureSource(): FeatureSource<*> = object : FeatureSource.NoConfig {
    override val featureName: String
        get() = "relay_data_provider"

    override val dependencies: List<FeatureDependency<*>>
        get() = listOf(ircDep, jdaDep, coroutineScopeDep, userStateMapDep)

    override val provides: List<FeatureElementTag<*>>
        get() = listOf(CommandOutputMappingTag, RelayConnectedEndpointMapTag)

    override fun createFeature(context: FeatureSourceContext): Feature {
        val jda = context[jdaDep]
        val ircConfig = context[ircDep]
        val coroutineScope = context[coroutineScopeDep]
        val userStateMap = context[userStateMapDep]

        if (ircConfig != null) {
            val relayConnectedEndpointMap = connectToRelayEndpoints(
                endpointsConfig = ircConfig.config.relayConfig.endpointsConfig,
                context = RelayConnectionContext(
                    ircClientMap = ircConfig.clients,
                    jda = jda,
                    coroutineScope = coroutineScope,
                    userStateMap = userStateMap,
                ),
            )

            val commandOutputMapping = ircAndDiscordMapping(
                jda = jda,
                relayConnectedEndpointMap = relayConnectedEndpointMap,
                relayEntriesConfig = ircConfig.config.relayConfig.relayEntriesConfig,
            )

            return object : Feature {
                override fun <T> query(tag: FeatureElementTag<T>): List<T> {
                    if (tag is CommandOutputMappingTag) return tag.values(commandOutputMapping)
                    if (tag is RelayConnectedEndpointMapTag) return tag.values(relayConnectedEndpointMap)

                    invalidTag(tag)
                }
            }
        } else {
            return object : Feature {
                override fun <T> query(tag: FeatureElementTag<T>): List<T> {
                    if (tag is CommandOutputMappingTag) return tag.values(CommandOutputMapping.empty())
                    if (tag is RelayConnectedEndpointMapTag) return tag.values()

                    invalidTag(tag)
                }
            }
        }
    }
}

@FeatureSourceFactory
fun relayCommandsSource(): FeatureSource<*> = FeatureSource.ofBaseCommands(
    name = "relay_commands",
    extraDependencies = listOf(userStateMapDep),
    block = { strategy, context ->
        mapOf(
            "relay" to RelayCommand(
                strategy,
                userStateMap = context[userStateMapDep],
            )
        )
    },
)
