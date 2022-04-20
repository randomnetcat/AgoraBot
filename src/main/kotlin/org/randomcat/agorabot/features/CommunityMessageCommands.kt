package org.randomcat.agorabot.features

import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.FeatureSource
import org.randomcat.agorabot.FeatureSourceFactory
import org.randomcat.agorabot.commands.CommunityMessageCommand
import org.randomcat.agorabot.commands.impl.defaultCommandStrategy
import org.randomcat.agorabot.community_message.feature.communityMessageStorage
import org.randomcat.agorabot.ofCommands

@FeatureSourceFactory
fun communityMessageCommandsFeature() =
    FeatureSource.ofConstant("community_message_commands", Feature.ofCommands { context ->
        val commandStrategy = context.defaultCommandStrategy

        mapOf(
            "community_message" to CommunityMessageCommand(commandStrategy, context.communityMessageStorage),
        )
    })
