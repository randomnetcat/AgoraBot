package org.randomcat.agorabot.features

import org.randomcat.agorabot.*
import org.randomcat.agorabot.config.RelayConnectedEndpointMapTag
import org.randomcat.agorabot.irc.initializeIrcRelay
import org.slf4j.LoggerFactory

private val relayMapDep = FeatureDependency.Single(RelayConnectedEndpointMapTag)
private val ircDep = FeatureDependency.Single(IrcSetupTag)
private val commandRegistryDep = FeatureDependency.Single(BotCommandRegistryTag)

private val logger = LoggerFactory.getLogger("AgoraBotRelayConnect")

@FeatureSourceFactory
fun relayConnectBlockFeatureSource(): FeatureSource<*> = object : FeatureSource.NoConfig {
    override val featureName: String
        get() = "relay_connect_block"

    override val dependencies: List<FeatureDependency<*>>
        get() = listOf(relayMapDep, ircDep, commandRegistryDep)

    override val provides: List<FeatureElementTag<*>>
        get() = listOf(StartupBlockTag)

    override fun createFeature(context: FeatureSourceContext): Feature {
        val relayMap = context[relayMapDep]
        val ircSetup = context[ircDep]
        val commandRegistry = context[commandRegistryDep]

        return Feature.singleTag(StartupBlockTag, {
            try {
                initializeIrcRelay(
                    config = ircSetup.config.relayConfig.relayEntriesConfig,
                    connectedEndpointMap = relayMap,
                    commandRegistry = commandRegistry,
                )

                logger.info("Relay initialized.")
            } catch (e: Exception) {
                for (client in ircSetup.clients.clients) {
                    client.shutdown("Exception during connection setup")
                }

                logger.error("Exception during IRC relay setup", e)
            }
        })
    }
}
