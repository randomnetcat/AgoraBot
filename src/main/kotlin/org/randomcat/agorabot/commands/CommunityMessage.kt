package org.randomcat.agorabot.commands

import org.randomcat.agorabot.commands.base.*
import org.randomcat.agorabot.commands.base.help.help
import org.randomcat.agorabot.commands.base.requirements.discord.*
import org.randomcat.agorabot.commands.base.requirements.permissions.permissions
import org.randomcat.agorabot.community_message.CommunityMessageMetadata
import org.randomcat.agorabot.community_message.CommunityMessageRevisionMetadata
import org.randomcat.agorabot.community_message.CommunityMessageStorage
import org.randomcat.agorabot.permissions.GuildScope
import org.randomcat.agorabot.util.await
import org.randomcat.agorabot.util.disallowMentions
import java.time.Instant

class CommunityMessageCommand(
    strategy: BaseCommandStrategy,
    private val globalStorage: CommunityMessageStorage,
) : BaseCommand(strategy) {
    override fun BaseCommandImplReceiver.impl() {
        subcommands {
            subcommand("create") {
                args(StringArg("name"), StringArg("channel_id"))
                    .requires(InGuildSimple)
                    .permissions(GuildScope.command("community_message").action("create")) { (name, targetChannelId) ->
                        val guildStorage = globalStorage.storageForGuild(currentGuildId)

                        val targetChannel = currentGuild.getTextChannelById(targetChannelId)

                        if (targetChannel == null) {
                            respond("Unable to find a channel with that id.")
                            return@permissions
                        }

                        val senderMember = currentMessageEvent.member

                        if (senderMember == null) {
                            respond("This command does not support webhooks.")
                            return@permissions
                        }

                        if (!targetChannel.canTalk(senderMember)) {
                            respond("You do not have permission to send messages in that channel.")
                            return@permissions
                        }

                        val newMessage = try {
                            targetChannel
                                .sendMessage("**Community Message** $name\n\n[not yet created]")
                                .disallowMentions()
                                .await()
                        } catch (e: Exception) {
                            respond("Unable to create message in that channel.")
                            return@permissions
                        }

                        val storageResult = guildStorage.createMessage(
                            name = name,
                            metadata = CommunityMessageMetadata(
                                createdBy = currentMessageEvent.author.id,
                                creationTime = Instant.now(),
                                channelId = targetChannelId,
                                messageId = newMessage.id,
                                maxRevision = null,
                            )
                        )

                        if (!storageResult) {
                            respond("Unable to store record of message.")
                            return@permissions
                        }

                        respond("Created.")
                    }
            }

            subcommand("revise") {
                args(StringArg("name"), StringArg("source_id"))
                    .help("Sets the content of the specified community message to the content of the specified source message (which must be in the same channel as the command this is run). Does not copy attachments.")
                    .requires(InGuildSimple)
                    .permissions(GuildScope.command("community_message").action("edit")) { (name, sourceId) ->
                        val guildStorage = globalStorage.storageForGuild(currentGuildId)

                        val messageMetadata = guildStorage.messageMetadata(name)

                        if (messageMetadata == null) {
                            respond("Unable to find a community message with that name.")
                            return@permissions
                        }

                        val sourceMessage =
                            runCatching { currentChannel.retrieveMessageById(sourceId).await() }.getOrDefault(null)

                        if (sourceMessage == null) {
                            respond("Unable to find source message in \\*this\\* channel.")
                            return@permissions
                        }

                        val content = sourceMessage.contentRaw

                        val revisionNumber = guildStorage.createRevision(
                            name = name,
                            metadata = CommunityMessageRevisionMetadata(
                                author = currentMessageEvent.author.id,
                                revisionTime = Instant.now(),
                            ),
                            content = content,
                        )

                        if (revisionNumber == null) {
                            respond("Unable to store revision.")
                            return@permissions
                        }

                        val targetMessage = runCatching {
                            currentGuild
                                .getTextChannelById(messageMetadata.channelId)
                                ?.retrieveMessageById(messageMetadata.messageId)
                                ?.await()
                        }.getOrDefault(null)

                        if (targetMessage == null) {
                            respond("Unable to find target message.")
                            return@permissions
                        }

                        try {
                            targetMessage.editMessage(content).disallowMentions().await()
                        } catch (e: Exception) {
                            respond("Unable to edit target message.")
                            return@permissions
                        }

                        respond("Edited. Revision number: ${revisionNumber.value}")
                    }
            }

            subcommand("list") {
                noArgs().requires(InGuildSimple) {
                    val guildStorage = globalStorage.storageForGuild(currentGuildId)

                    val allNames = guildStorage.messageNames()
                    val nameData = allNames.associateWith { guildStorage.messageMetadata(it) }

                    val list = nameData.filterValues { v -> v != null }.entries.joinToString("\n") { (name, metadata) ->
                        metadata!!
                        "**${name}**: Revision ${metadata.maxRevision?.value?.toString() ?: "[none]"}, Message: ${metadata.channelId}/${metadata.messageId}"
                    }

                    respond(list)
                }
            }

            subcommand("remove") {
                args(StringArg("name"))
                    .requires(InGuildSimple)
                    .permissions(GuildScope.command("community_message").action("remove")) { (name) ->
                        val guildStorage = globalStorage.storageForGuild(currentGuildId)

                        val metadata = guildStorage.messageMetadata(name)

                        if (guildStorage.removeMessage(name)) {
                            if (metadata != null) {
                                try {
                                    currentGuild
                                        .getTextChannelById(metadata.channelId)
                                        ?.retrieveMessageById(metadata.messageId)
                                        ?.await()
                                        ?.delete()
                                        ?.await()

                                    respond("Removed entry and deleted message.")
                                    return@permissions
                                } catch (e: Exception) {
                                    // Ignored.
                                }
                            }

                            respond("Removed entry, but could not delete message.")
                        } else {
                            respond("Could not remove.")
                        }
                    }
            }
        }
    }
}
