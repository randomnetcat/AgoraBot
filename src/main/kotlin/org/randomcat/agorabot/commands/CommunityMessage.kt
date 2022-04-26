package org.randomcat.agorabot.commands

import org.randomcat.agorabot.commands.base.*
import org.randomcat.agorabot.commands.base.help.help
import org.randomcat.agorabot.commands.base.requirements.discord.*
import org.randomcat.agorabot.commands.base.requirements.discord_ext.InGuild
import org.randomcat.agorabot.commands.base.requirements.permissions.permissions
import org.randomcat.agorabot.commands.base.requirements.permissions.senderHasPermission
import org.randomcat.agorabot.community_message.CommunityMessageMetadata
import org.randomcat.agorabot.community_message.CommunityMessageRevisionMetadata
import org.randomcat.agorabot.community_message.CommunityMessageStorage
import org.randomcat.agorabot.permissions.BotPermission
import org.randomcat.agorabot.permissions.GuildScope
import org.randomcat.agorabot.permissions.LogicalOrPermission
import org.randomcat.agorabot.util.await
import org.randomcat.agorabot.util.disallowMentions
import java.time.Instant

private const val UNGROUPED = "ungrouped"

private const val PERMISSION_COMMAND = "community_message"
private const val PERMISSION_ACTION_CREATE = "create"
private const val PERMISSION_ACTION_EDIT = "edit"
private const val PERMISSION_ACTION_REMOVE = "remove"
private const val PERMISSION_ACTION_GROUP = "group"
private const val PERMISSION_ACTION_MANAGE = "manage"

private val GLOBAL_MANAGE_PERMISSION = GuildScope.command(PERMISSION_COMMAND).action(PERMISSION_ACTION_MANAGE)

private fun globalCommandPermission(action: String): BotPermission {
    return LogicalOrPermission(
        listOf(
            GLOBAL_MANAGE_PERMISSION,
            GuildScope.command(PERMISSION_COMMAND).action(action),
        ),
    )
}

private fun groupCommandPermission(action: String, group: String?): BotPermission {
    return LogicalOrPermission(
        listOf(
            globalCommandPermission(action),
            GuildScope.command(PERMISSION_COMMAND).action(action + "/" + (group ?: UNGROUPED)),
        ),
    )
}

class CommunityMessageCommand(
    strategy: BaseCommandStrategy,
    private val globalStorage: CommunityMessageStorage,
) : BaseCommand(strategy) {
    override fun BaseCommandImplReceiver.impl() {
        subcommands {
            subcommand("create") {
                args(StringArg("name"), StringArg("channel_id"))
                    .requires(InGuildSimple)
                    .permissions(globalCommandPermission(PERMISSION_ACTION_CREATE)) { (name, targetChannelId) ->
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
                                group = null,
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
                    .requires(InGuild) { (name, sourceId) ->
                        val guildStorage = globalStorage.storageForGuild(currentGuildId)

                        val messageMetadata = guildStorage.messageMetadata(name)

                        if (messageMetadata == null) {
                            respond("Unable to find a community message with that name.")
                            return@requires
                        }

                        val sourceMessage =
                            runCatching { currentChannel.retrieveMessageById(sourceId).await() }.getOrDefault(null)

                        if (sourceMessage == null) {
                            respond("Unable to find source message in \\*this\\* channel.")
                            return@requires
                        }

                        val permission = groupCommandPermission(PERMISSION_ACTION_EDIT, messageMetadata.group)

                        if (!senderHasPermission(permission)) {
                            respond("You do not have permission to remove that message; need ${permission.readable()}")
                            return@requires
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
                            return@requires
                        }

                        val targetMessage = runCatching {
                            currentGuild
                                .getTextChannelById(messageMetadata.channelId)
                                ?.retrieveMessageById(messageMetadata.messageId)
                                ?.await()
                        }.getOrDefault(null)

                        if (targetMessage == null) {
                            respond("Unable to find target message.")
                            return@requires
                        }

                        try {
                            targetMessage.editMessage(content).disallowMentions().await()
                        } catch (e: Exception) {
                            respond("Unable to edit target message.")
                            return@requires
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
                        "**${name}**: Revision ${metadata.maxRevision?.value?.toString() ?: "[none]"}, Group: ${metadata.group ?: "[none]"}, Message: ${metadata.channelId}/${metadata.messageId}"
                    }

                    respond(list)
                }
            }

            subcommand("remove") {
                args(StringArg("name")).requires(InGuild) { (name) ->
                    val guildStorage = globalStorage.storageForGuild(currentGuildId)

                    val metadata = guildStorage.messageMetadata(name)

                    if (metadata == null) {
                        respond("Unable to find the specified message.")
                        return@requires
                    }

                    val permission = groupCommandPermission(action = PERMISSION_ACTION_REMOVE, group = metadata.group)
                    if (!senderHasPermission(permission)) {
                        respond("You do not have permission to remove that message; need: ${permission.readable()}")
                        return@requires
                    }

                    if (guildStorage.removeMessage(name)) {
                        try {
                            currentGuild
                                .getTextChannelById(metadata.channelId)
                                ?.retrieveMessageById(metadata.messageId)
                                ?.await()
                                ?.delete()
                                ?.await()

                            respond("Removed entry and deleted message.")
                            return@requires
                        } catch (e: Exception) {
                            // Ignored.
                        }

                        respond("Removed entry, but could not delete message.")
                    } else {
                        respond("Could not remove.")
                    }
                }
            }

            subcommand("group") {
                args(StringArg("name"), StringArg("new_group")).requires(InGuild) { (name, newGroup) ->
                    if (newGroup == UNGROUPED) {
                        respond("Cannot de-group an grouped message.")
                        return@requires
                    }

                    val guildStorage = globalStorage.storageForGuild(currentGuildId)

                    val metadata = guildStorage.messageMetadata(name)

                    if (metadata == null) {
                        respond("Unable to find the specified message.")
                        return@requires
                    }

                    val expectedGroup = metadata.group

                    val newGroupPermission = groupCommandPermission(action = PERMISSION_ACTION_GROUP, group = newGroup)
                    if (!senderHasPermission(newGroupPermission)) {
                        respond("You do not have permission to add that message to that group; need: ${newGroupPermission.readable()}")
                        return@requires
                    }

                    val degroupPermission =
                        groupCommandPermission(action = PERMISSION_ACTION_GROUP, group = metadata.group)
                    if (!senderHasPermission(degroupPermission)) {
                        respond("You do not have permission to remove that message from that group; need: ${degroupPermission.readable()}")
                        return@requires
                    }

                    var didUpdate = false

                    val updateResult = guildStorage.updateMetadata(name = name) { oldMetadata ->
                        if (oldMetadata.group == expectedGroup) {
                            didUpdate = true
                            oldMetadata.copy(group = newGroup)
                        } else {
                            didUpdate = false
                            oldMetadata
                        }
                    }

                    if (!didUpdate || !updateResult) {
                        respond("Could not update message metadata.")
                        return@requires
                    }

                    respond("Done.")
                }
            }
        }
    }
}
