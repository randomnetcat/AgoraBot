package org.randomcat.agorabot.features

import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.commands.PrefixCommand
import org.randomcat.agorabot.commands.impl.defaultCommandStrategy
import org.randomcat.agorabot.listener.MutableGuildPrefixMap

fun prefixCommandsFeature(prefixMap: MutableGuildPrefixMap) = Feature.ofCommands { context ->
    mapOf("prefix" to PrefixCommand(context.defaultCommandStrategy, prefixMap))
}
