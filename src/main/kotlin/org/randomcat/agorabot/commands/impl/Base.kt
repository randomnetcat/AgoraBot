package org.randomcat.agorabot.commands.impl

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
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

interface BaseCommandExecutionReceiverMarker :
    PermissionsExtensionMarker, GuildExtensionMarker<BaseCommandExecutionReceiverGuilded>

private class PendingExecutionReceiverImpl<ExecutionReceiver : BaseCommandExecutionReceiver, GuildedExecutionReceiver : BaseCommandExecutionReceiverGuilded, Arg> private constructor(
    baseReceiver: ArgumentPendingExecutionReceiver<ExecutionReceiver, Arg>,
    private val guildedBaseReceiver: ArgumentPendingExecutionReceiver<GuildedExecutionReceiver, Arg>,
    private val state: State,
) : MixinPendingExecutionReceiver<ExecutionReceiver, Arg, BaseCommandExecutionReceiverMarker>(baseReceiver),
    PermissionsPendingExecutionReceiver<ExecutionReceiver, Arg, BaseCommandExecutionReceiverMarker>,
    GuildExtensionPendingExecutionReceiver<ExecutionReceiver, GuildedExecutionReceiver, Arg, BaseCommandExecutionReceiverMarker> {

    private data class State(
        val permissions: PersistentList<BotPermission>,
        val permissionsData: PermissionsReceiverData,
        val guildRequired: Boolean,
        val event: MessageReceivedEvent?,
    )

    sealed class ExecutionData {
        object NoExecution : ExecutionData() {
            override val permissionsData: PermissionsReceiverData
                get() = PermissionsReceiverData.NeverExecute
        }

        data class AllowExecution(
            override val permissionsData: PermissionsReceiverData.AllowExecution,
            val event: MessageReceivedEvent,
        ) : ExecutionData()

        abstract val permissionsData: PermissionsReceiverData
    }

    constructor(
        baseReceiver: ArgumentPendingExecutionReceiver<ExecutionReceiver, Arg>,
        guildedBaseReceiver: ArgumentPendingExecutionReceiver<GuildedExecutionReceiver, Arg>,
        executionData: ExecutionData,
    ) : this(
        baseReceiver,
        guildedBaseReceiver,
        State(
            permissions = persistentListOf(),
            permissionsData = executionData.permissionsData,
            guildRequired = false,
            event = when (executionData) {
                is ExecutionData.NoExecution -> null
                is ExecutionData.AllowExecution -> executionData.event
            },
        ),
    )

    override val mixins: Iterable<PendingExecutionReceiverMixin>
        get() = listOfNotNull(
            if (state.guildRequired) GuildExtensionExecutionMixin(state.event) else null,
            PermissionsExecutionMixin(permissions = state.permissions, data = state.permissionsData),
        )

    override fun permissions(vararg newPermissions: BotPermission): PermissionsPendingExecutionReceiver<ExecutionReceiver, Arg, BaseCommandExecutionReceiverMarker> {
        return PendingExecutionReceiverImpl(
            baseReceiver = baseReceiver,
            guildedBaseReceiver = guildedBaseReceiver,
            state = state.copy(permissions = state.permissions.addAll(newPermissions.asList())),
        )
    }

    override fun requiresGuild(): ExtendableArgumentPendingExecutionReceiver<GuildedExecutionReceiver, Arg, BaseCommandExecutionReceiverMarker> {
        return PendingExecutionReceiverImpl(
            baseReceiver = guildedBaseReceiver,
            guildedBaseReceiver = guildedBaseReceiver,
            state = state.copy(guildRequired = true),
        )
    }
}

private val nullPendingExecutionReceiverImpl = PendingExecutionReceiverImpl(
    baseReceiver = NullPendingExecutionReceiver,
    guildedBaseReceiver = NullPendingExecutionReceiver,
    executionData = PendingExecutionReceiverImpl.ExecutionData.NoExecution,
)

class GuildInfo(
    private val event: MessageReceivedEvent,
    private val guildStateStrategy: BaseCommandGuildStateStrategy,
) {
    init {
        require(event.isFromGuild)
    }

    val guild by lazy { event.guild }
    val guildId by lazy { guild.id }
    val guildState by lazy { guildStateStrategy.guildStateFor(guildId = guildId) }
    val senderMember by lazy { event.member ?: error("Expected event to have a Member") }

    fun resolveRole(roleString: String): Role? = guild.resolveRoleString(roleString)
}

@CommandDslMarker
interface BaseCommandExecutionReceiver {
    val userPermissionContext: UserPermissionContext

    fun respond(message: String)
    fun respond(message: Message)
    fun respondWithFile(fileName: String, fileContent: String)
    fun respondWithTextAndFile(text: String, fileName: String, fileContent: String)
    fun respondNeedGuild()

    fun currentMessageEvent(): MessageReceivedEvent

    fun currentGuildInfo(): GuildInfo?

    fun senderHasPermission(permission: BotPermission): Boolean
}

fun BaseCommandExecutionReceiver.currentJda(): JDA = currentMessageEvent().jda

fun BaseCommandExecutionReceiver.currentChannel(): MessageChannel = currentMessageEvent().channel

fun BaseCommandExecutionReceiver.botHasPermission(permission: DiscordPermission): Boolean {
    return currentGuildInfo()?.guild?.selfMember?.hasPermission(permission) ?: false
}

inline fun BaseCommandExecutionReceiver.requiresGuild(block: (GuildInfo) -> Unit) {
    val guildInfo = currentGuildInfo() ?: run {
        respondNeedGuild()
        return
    }

    return block(guildInfo)
}

interface BaseCommandExecutionReceiverGuilded : BaseCommandExecutionReceiver {
    override fun currentGuildInfo(): GuildInfo
}


typealias BaseCommandPendingExecutionReceiver<Arg> =
        ExtendableArgumentPendingExecutionReceiver<BaseCommandExecutionReceiver, Arg, BaseCommandExecutionReceiverMarker>

const val NEED_GUILD_ERROR_MSG = "This command can only be run in a Guild."

abstract class BaseCommand(private val strategy: BaseCommandStrategy) : Command {
    @CommandDslMarker
    private open class ExecutionReceiverImpl(
        private val strategy: BaseCommandStrategy,
        private val event: MessageReceivedEvent,
        private val invocation: CommandInvocation,
    ) : BaseCommandExecutionReceiver {
        override fun respond(message: String) {
            strategy.sendResponse(event, invocation, message)
        }

        override fun respond(message: Message) {
            strategy.sendResponseMessage(event, invocation, message)
        }

        override fun respondWithFile(fileName: String, fileContent: String) {
            strategy.sendResponseAsFile(event, invocation, fileName, fileContent)
        }

        override fun respondWithTextAndFile(text: String, fileName: String, fileContent: String) {
            strategy.sendResponseTextAndFile(event, invocation, text, fileName, fileContent)
        }

        override fun currentMessageEvent() = event

        override fun currentGuildInfo(): GuildInfo? = if (event.isFromGuild) GuildInfo(event, strategy) else null

        override fun respondNeedGuild() {
            respond(NEED_GUILD_ERROR_MSG)
        }

        override val userPermissionContext by lazy { userPermissionContextForEvent(event) }

        override fun senderHasPermission(permission: BotPermission): Boolean =
            permission.isSatisfied(strategy.permissionContext, userPermissionContext)
    }

    private class ExecutionReceiverGuildedImpl(
        strategy: BaseCommandStrategy,
        event: MessageReceivedEvent,
        invocation: CommandInvocation,
    ) : ExecutionReceiverImpl(strategy, event, invocation), BaseCommandExecutionReceiverGuilded {
        override fun currentGuildInfo(): GuildInfo {
            return super.currentGuildInfo() ?: error("Being in a guild should have already been enforced")
        }
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
            object :
                ExecutingExecutionReceiverProperties<BaseCommandExecutionReceiver, BaseCommandExecutionReceiverMarker> {
                override fun <ParseResult, Arg> receiverOnSuccess(
                    results: List<ParseResult>,
                    mapParsed: (List<ParseResult>) -> Arg,
                ): ExtendableArgumentPendingExecutionReceiver<BaseCommandExecutionReceiver, Arg, BaseCommandExecutionReceiverMarker> {
                    return PendingExecutionReceiverImpl(
                        baseReceiver = simpleInvokingPendingExecutionReceiver { exec ->
                            exec(ExecutionReceiverImpl(strategy, event, invocation), mapParsed(results))
                        },
                        guildedBaseReceiver = simpleInvokingPendingExecutionReceiver { exec ->
                            exec(ExecutionReceiverGuildedImpl(strategy, event, invocation), mapParsed(results))
                        },
                        executionData = PendingExecutionReceiverImpl.ExecutionData.AllowExecution(
                            permissionsData = PermissionsReceiverData.AllowExecution(
                                userContext = userPermissionContextForEvent(event),
                                permissionsContext = strategy.permissionContext,
                                onError = { strategy.onPermissionsError(event, invocation, it) }
                            ),
                            event = event,
                        )
                    )
                }

                override fun receiverOnError(): ExtendableArgumentPendingExecutionReceiver<ExecutionReceiverImpl, Nothing, BaseCommandExecutionReceiverMarker> {
                    return nullPendingExecutionReceiverImpl
                }
            },
        ).impl()
    }

    protected abstract fun TopLevelArgumentDescriptionReceiver<BaseCommandExecutionReceiver, BaseCommandExecutionReceiverMarker>.impl()

    fun usage(): String {
        return UsageTopLevelArgumentDescriptionReceiver(nullPendingExecutionReceiverImpl).apply { impl() }.usage()
    }
}

typealias BaseCommandImplReceiver =
        TopLevelArgumentDescriptionReceiver<BaseCommandExecutionReceiver, BaseCommandExecutionReceiverMarker>

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
