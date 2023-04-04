package org.randomcat.agorabot

import org.randomcat.agorabot.config.PrefixStorageTag
import org.randomcat.agorabot.listener.BotListener
import org.randomcat.agorabot.listener.GuildPrefixCommandParser
import org.randomcat.agorabot.listener.MentionPrefixCommandParser

private val commandRegistryDep = FeatureDependency.Single(BotCommandRegistryTag)
private val prefixMapDep = FeatureDependency.Single(PrefixStorageTag)

@FeatureSourceFactory
fun botCommandListenerSource() = FeatureSource.NoConfig.ofCloseable(
    name = "bot_command_listener",
    element = JdaListenerTag,
    dependencies = listOf(commandRegistryDep, prefixMapDep),
    create = { context ->
        val commandRegistry = context[commandRegistryDep]
        val prefixMap = context[prefixMapDep]

        BotListener(
            MentionPrefixCommandParser(GuildPrefixCommandParser(prefixMap)),
            commandRegistry,
        )
    },
    close = { it.stop() },
)
