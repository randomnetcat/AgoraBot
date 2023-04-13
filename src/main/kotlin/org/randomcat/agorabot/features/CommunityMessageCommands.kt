package org.randomcat.agorabot.features

import org.randomcat.agorabot.FeatureDependency
import org.randomcat.agorabot.FeatureSource
import org.randomcat.agorabot.FeatureSourceFactory
import org.randomcat.agorabot.commands.CommunityMessageCommand
import org.randomcat.agorabot.community_message.feature.CommunityMessageStorageTag
import org.randomcat.agorabot.ofBaseCommands

private val storageDep = FeatureDependency.Single(CommunityMessageStorageTag)

@FeatureSourceFactory
fun communityMessageCommandsFeature() = FeatureSource.ofBaseCommands(
    name = "community_message_commands",
    extraDependencies = listOf(storageDep),
) { strategy, context ->
    mapOf(
        "community_message" to CommunityMessageCommand(strategy, context[storageDep]),
    )
}
