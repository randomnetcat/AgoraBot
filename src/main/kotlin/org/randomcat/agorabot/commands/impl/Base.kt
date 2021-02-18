package org.randomcat.agorabot.commands.impl

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.randomcat.agorabot.commands.impl.BaseCommandDiscordOutputSink.sendResponse
import org.randomcat.agorabot.config.GuildState
import org.randomcat.agorabot.listener.Command
import org.randomcat.agorabot.listener.CommandInvocation
import org.randomcat.agorabot.permissions.BotPermission
import org.randomcat.agorabot.permissions.BotPermissionContext
import org.randomcat.agorabot.permissions.UserPermissionContext
import org.randomcat.agorabot.util.DiscordPermission
import org.randomcat.agorabot.util.disallowMentions
import org.randomcat.agorabot.util.resolveRoleString

interface BaseCommandArgumentStrategy {
    fun sendArgumentErrorResponse(
        event: MessageReceivedEvent,
        invocation: CommandInvocation,
        errorMessage: String,
        usage: String,
    )
}

interface BaseCommandOutputSink {
    fun sendResponse(event: MessageReceivedEvent, invocation: CommandInvocation, message: String)
    fun sendResponseMessage(event: MessageReceivedEvent, invocation: CommandInvocation, message: Message)

    fun sendResponseAsFile(
        event: MessageReceivedEvent,
        invocation: CommandInvocation,
        fileName: String,
        fileContent: String,
    )

    fun sendResponseTextAndFile(
        event: MessageReceivedEvent,
        invocation: CommandInvocation,
        textResponse: String,
        fileName: String,
        fileContent: String,
    )
}

interface BaseCommandPermissionsStrategy {
    fun onPermissionsError(event: MessageReceivedEvent, invocation: CommandInvocation, permission: BotPermission)
    val permissionContext: BotPermissionContext
}

interface BaseCommandGuildStateStrategy {
    fun guildStateFor(guildId: String): GuildState
}

interface BaseCommandStrategy :
    BaseCommandArgumentStrategy,
    BaseCommandOutputSink,
    BaseCommandPermissionsStrategy,
    BaseCommandGuildStateStrategy

private fun userPermissionContextForEvent(event: MessageReceivedEvent) =
    event.member?.let { UserPermissionContext.InGuild(it) } ?: UserPermissionContext.Guildless(event.author)

private class PendingExecutionReceiverImpl<ExecutionReceiver, Arg> private constructor(
    baseReceiver: ArgumentPendingExecutionReceiver<ExecutionReceiver, Arg>,
    private val state: State,
) : MixinPendingExecutionReceiver<ExecutionReceiver, Arg, PermissionsExtensionMarker>(baseReceiver),
    PermissionsPendingExecutionReceiver<ExecutionReceiver, Arg> {

    private data class State(
        val permissions: PersistentList<BotPermission>,
        val permissionsData: PermissionsReceiverData,
    )

    constructor(
        baseReceiver: ArgumentPendingExecutionReceiver<ExecutionReceiver, Arg>,
        permissionsReceiverData: PermissionsReceiverData,
    ) : this(
        baseReceiver,
        State(
            permissions = persistentListOf(),
            permissionsData = permissionsReceiverData,
        ),
    )

    override val mixins: Iterable<PendingExecutionReceiverMixin>
        get() = listOf(
            PermissionsExecutionMixin(permissions = state.permissions, data = state.permissionsData),
        )

    override fun permissions(vararg newPermissions: BotPermission): PermissionsPendingExecutionReceiver<ExecutionReceiver, Arg> {
        return PendingExecutionReceiverImpl(
            baseReceiver = baseReceiver,
            state = state.copy(permissions = state.permissions.addAll(newPermissions.asList())),
        )
    }
}

private val nullPendingExecutionReceiverImpl = PendingExecutionReceiverImpl(
    baseReceiver = NullPendingExecutionReceiver,
    permissionsReceiverData = PermissionsReceiverData.NeverExecute,
)

abstract class BaseCommand(private val strategy: BaseCommandStrategy) : Command {
    @CommandDslMarker
    class ExecutionReceiverImpl(
        private val strategy: BaseCommandStrategy,
        private val event: MessageReceivedEvent,
        private val invocation: CommandInvocation,
    ) {
        fun respond(message: String) {
            strategy.sendResponse(event, invocation, message)
        }

        fun respond(message: Message) {
            strategy.sendResponseMessage(event, invocation, message)
        }

        fun respondWithFile(fileName: String, fileContent: String) {
            strategy.sendResponseAsFile(event, invocation, fileName, fileContent)
        }

        fun respondWithTextAndFile(text: String, fileName: String, fileContent: String) {
            strategy.sendResponseTextAndFile(event, invocation, text, fileName, fileContent)
        }

        fun currentMessageEvent() = event
        fun currentJda() = event.jda
        fun currentChannel() = currentMessageEvent().channel

        inner class GuildInfo {
            init {
                require(event.isFromGuild)
            }

            val guild by lazy { event.guild }
            val guildId by lazy { guild.id }
            val guildState by lazy { strategy.guildStateFor(guildId = guildId) }
            val senderMember by lazy { event.member ?: error("Expected event to have a Member") }
            fun resolveRole(roleString: String): Role? = guild.resolveRoleString(roleString)
        }

        fun inGuild() = event.isFromGuild
        fun currentGuildInfo(): GuildInfo? = if (inGuild()) GuildInfo() else null

        fun botHasPermission(permission: DiscordPermission): Boolean {
            return currentGuildInfo()?.guild?.selfMember?.hasPermission(permission) ?: false
        }

        fun respondNeedGuild() {
            respond("This command can only be run in a Guild.")
        }

        inline fun requiresGuild(block: (GuildInfo) -> Unit) {
            val guildInfo = currentGuildInfo() ?: run {
                respondNeedGuild()
                return
            }

            return block(guildInfo)
        }


        private val userPermissionContext by lazy { userPermissionContextForEvent(event) }

        fun senderHasPermission(permission: BotPermission): Boolean =
            permission.isSatisfied(strategy.permissionContext, userPermissionContext)
    }

    override fun invoke(event: MessageReceivedEvent, invocation: CommandInvocation) {
        TopLevelExecutingArgumentDescriptionReceiver(
            UnparsedCommandArgs(invocation.args),
            onError = { msg ->
                strategy.sendArgumentErrorResponse(
                    event = event,
                    invocation = invocation,
                    errorMessage = msg,
                    usage = usage()
                )
            },
            object : ExecutingExecutionReceiverProperties<ExecutionReceiverImpl, PermissionsExtensionMarker> {
                override fun <ParseResult, Arg> receiverOnSuccess(
                    results: List<ParseResult>,
                    mapParsed: (List<ParseResult>) -> Arg,
                ): ExtendableArgumentPendingExecutionReceiver<ExecutionReceiverImpl, Arg, PermissionsExtensionMarker> {
                    return PendingExecutionReceiverImpl(
                        baseReceiver = simpleInvokingPendingExecutionReceiver { exec ->
                            exec(ExecutionReceiverImpl(strategy, event, invocation), mapParsed(results))
                        },
                        permissionsReceiverData = PermissionsReceiverData.AllowExecution(
                            userContext = userPermissionContextForEvent(event),
                            permissionsContext = strategy.permissionContext,
                            onError = { strategy.onPermissionsError(event, invocation, it) }
                        ),
                    )
                }

                override fun receiverOnError(): ExtendableArgumentPendingExecutionReceiver<ExecutionReceiverImpl, Nothing, PermissionsExtensionMarker> {
                    return nullPendingExecutionReceiverImpl
                }
            },
        ).impl()
    }

    protected abstract fun TopLevelArgumentDescriptionReceiver<ExecutionReceiverImpl, PermissionsExtensionMarker>.impl()

    fun usage(): String {
        return UsageTopLevelArgumentDescriptionReceiver(nullPendingExecutionReceiverImpl).apply { impl() }.usage()
    }
}

typealias BaseCommandImplReceiver =
        TopLevelArgumentDescriptionReceiver<BaseCommand.ExecutionReceiverImpl, PermissionsExtensionMarker>

object BaseCommandDiscordOutputSink : BaseCommandOutputSink {
    override fun sendResponse(event: MessageReceivedEvent, invocation: CommandInvocation, message: String) {
        event.channel.sendMessage(message).disallowMentions().queue()
    }

    override fun sendResponseMessage(event: MessageReceivedEvent, invocation: CommandInvocation, message: Message) {
        event.channel.sendMessage(message).disallowMentions().queue()
    }

    override fun sendResponseAsFile(
        event: MessageReceivedEvent,
        invocation: CommandInvocation,
        fileName: String,
        fileContent: String,
    ) {
        val bytes = fileContent.toByteArray(Charsets.UTF_8)
        event.channel.sendFile(bytes, fileName).disallowMentions().queue()
    }

    override fun sendResponseTextAndFile(
        event: MessageReceivedEvent,
        invocation: CommandInvocation,
        textResponse: String,
        fileName: String,
        fileContent: String,
    ) {
        val bytes = fileContent.toByteArray(Charsets.UTF_8)
        event.channel.sendMessage(textResponse).addFile(bytes, fileName).queue()
    }
}

object BaseCommandDefaultArgumentStrategy : BaseCommandArgumentStrategy {
    override fun sendArgumentErrorResponse(
        event: MessageReceivedEvent,
        invocation: CommandInvocation,
        errorMessage: String,
        usage: String,
    ) {
        sendResponse(event, invocation, "$errorMessage. Usage: ${usage.ifBlank { NO_ARGUMENTS }}")
    }
}

data class BaseCommandMultiOutputSink(
    private val outputs: ImmutableList<BaseCommandOutputSink>,
) : BaseCommandOutputSink {
    constructor(outputs: List<BaseCommandOutputSink>) : this(outputs.toImmutableList())

    override fun sendResponse(event: MessageReceivedEvent, invocation: CommandInvocation, message: String) {
        outputs.forEach {
            it.sendResponse(
                event = event,
                invocation = invocation,
                message = message,
            )
        }
    }

    override fun sendResponseMessage(event: MessageReceivedEvent, invocation: CommandInvocation, message: Message) {
        outputs.forEach {
            it.sendResponseMessage(
                event = event,
                invocation = invocation,
                message = message,
            )
        }
    }

    override fun sendResponseAsFile(
        event: MessageReceivedEvent,
        invocation: CommandInvocation,
        fileName: String,
        fileContent: String,
    ) {
        outputs.forEach {
            it.sendResponseAsFile(
                event = event,
                invocation = invocation,
                fileName = fileName,
                fileContent = fileContent,
            )
        }
    }

    override fun sendResponseTextAndFile(
        event: MessageReceivedEvent,
        invocation: CommandInvocation,
        textResponse: String,
        fileName: String,
        fileContent: String,
    ) {
        outputs.forEach {
            it.sendResponseTextAndFile(
                event = event,
                invocation = invocation,
                textResponse = textResponse,
                fileName = fileName,
                fileContent = fileContent,
            )
        }
    }
}
