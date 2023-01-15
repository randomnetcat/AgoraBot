package org.randomcat.agorabot.features

import org.randomcat.agorabot.FeatureDependency
import org.randomcat.agorabot.FeatureSource
import org.randomcat.agorabot.FeatureSourceFactory
import org.randomcat.agorabot.commands.PrefixCommand
import org.randomcat.agorabot.config.PrefixStorageTag
import org.randomcat.agorabot.ofBaseCommands

private val prefixStorageDep = FeatureDependency.Single(PrefixStorageTag)
private val extraDeps = listOf(prefixStorageDep)

@FeatureSourceFactory
fun prefixCommandsFactory() = FeatureSource.ofBaseCommands("prefix_commands", extraDeps) { strategy, context ->
    mapOf("prefix" to PrefixCommand(strategy, context[prefixStorageDep]))
}
