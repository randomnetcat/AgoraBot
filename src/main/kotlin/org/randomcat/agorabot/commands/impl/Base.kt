package org.randomcat.agorabot.commands.impl

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.randomcat.agorabot.config.GuildState
import org.randomcat.agorabot.config.GuildStateMap
import org.randomcat.agorabot.listener.Command
import org.randomcat.agorabot.listener.CommandEventSource
import org.randomcat.agorabot.listener.CommandInvocation
import org.randomcat.agorabot.listener.tryRespondWithText
import org.randomcat.agorabot.permissions.BotPermission
import org.randomcat.agorabot.permissions.BotPermissionContext
import org.randomcat.agorabot.permissions.UserPermissionContext
import org.randomcat.agorabot.util.DiscordPermission
import org.randomcat.agorabot.util.resolveRoleString

interface BaseCommandArgumentStrategy {
    fun sendArgumentErrorResponse(
        source: CommandEventSource,
        invocation: CommandInvocation,
        errorMessage: String,
        usage: String,
    )
}

interface BaseCommandOutputStrategy {
    fun sendResponse(source: CommandEventSource, invocation: CommandInvocation, message: String)
    fun sendResponseMessage(source: CommandEventSource, invocation: CommandInvocation, message: Message)

    fun sendResponseAsFile(
        source: CommandEventSource,
        invocation: CommandInvocation,
        fileName: String,
        fileContent: String,
    )

    fun sendResponseTextAndFile(
        source: CommandEventSource,
        invocation: CommandInvocation,
        textResponse: String,
        fileName: String,
        fileContent: String,
    )
}

interface BaseCommandPermissionsStrategy {
    fun onPermissionsError(source: CommandEventSource, invocation: CommandInvocation, permission: BotPermission)
    val permissionContext: BotPermissionContext
}

interface BaseCommandGuildStateStrategy {
    fun guildStateFor(guildId: String): GuildState

    companion object {
        fun fromMap(guildStateMap: GuildStateMap): BaseCommandGuildStateStrategy {
            return object : BaseCommandGuildStateStrategy {
                override fun guildStateFor(guildId: String): GuildState {
                    return guildStateMap.stateForGuild(guildId)
                }
            }
        }
    }
}

interface BaseCommandStrategy :
    BaseCommandArgumentStrategy,
    BaseCommandOutputStrategy,
    BaseCommandPermissionsStrategy,
    BaseCommandGuildStateStrategy

private fun userPermissionContextForSource(source: CommandEventSource): UserPermissionContext {
    return when (source) {
        is CommandEventSource.Discord -> {
            val event = source.event

            event.member?.let { UserPermissionContext.Authenticated.InGuild(it) }
                ?: UserPermissionContext.Authenticated.Guildless(event.author)
        }

        is CommandEventSource.Irc -> UserPermissionContext.Unauthenticated
    }
}

interface BaseCommandExecutionReceiverMarker :
    PermissionsExtensionMarker,
    DiscordExtensionMarker<BaseCommandExecutionReceiverDiscord>,
    GuildExtensionMarker<BaseCommandExecutionReceiverGuilded>

private class PendingExecutionReceiverImpl<
        ExecutionReceiver : BaseCommandExecutionReceiver,
        DiscordExecutionReceiver : BaseCommandExecutionReceiverDiscord,
        GuildedExecutionReceiver : BaseCommandExecutionReceiverGuilded,
        Arg
        >
private constructor(
    baseReceiver: ArgumentPendingExecutionReceiver<ExecutionReceiver, Arg>,
    private val discordBaseReceiver: ArgumentPendingExecutionReceiver<DiscordExecutionReceiver, Arg>,
    private val guildedBaseReceiver: ArgumentPendingExecutionReceiver<GuildedExecutionReceiver, Arg>,
    private val state: State,
) : MixinPendingExecutionReceiver<ExecutionReceiver, Arg, BaseCommandExecutionReceiverMarker>(baseReceiver),
    PermissionsPendingExecutionReceiver<ExecutionReceiver, Arg, BaseCommandExecutionReceiverMarker>,
    DiscordExtensionPendingExecutionReceiver<ExecutionReceiver, DiscordExecutionReceiver, Arg, BaseCommandExecutionReceiverMarker>,
    GuildExtensionPendingExecutionReceiver<ExecutionReceiver, GuildedExecutionReceiver, Arg, BaseCommandExecutionReceiverMarker> {

    private enum class DiscordRequirement {
        NONE,
        DISCORD,
        GUILD,
    }

    private data class State(
        val permissions: PersistentList<BotPermission>,
        val permissionsData: PermissionsReceiverData,
        val discordRequirement: DiscordRequirement,
        val source: CommandEventSource?,
    )

    sealed class ExecutionData {
        object NoExecution : ExecutionData() {
            override val permissionsData: PermissionsReceiverData
                get() = PermissionsReceiverData.NeverExecute
        }

        data class AllowExecution(
            override val permissionsData: PermissionsReceiverData.AllowExecution,
            val source: CommandEventSource,
        ) : ExecutionData()

        abstract val permissionsData: PermissionsReceiverData
    }

    constructor(
        baseReceiver: ArgumentPendingExecutionReceiver<ExecutionReceiver, Arg>,
        discordBaseReceiver: ArgumentPendingExecutionReceiver<DiscordExecutionReceiver, Arg>,
        guildedBaseReceiver: ArgumentPendingExecutionReceiver<GuildedExecutionReceiver, Arg>,
        executionData: ExecutionData,
    ) : this(
        baseReceiver = baseReceiver,
        discordBaseReceiver = discordBaseReceiver,
        guildedBaseReceiver = guildedBaseReceiver,
        state = State(
            permissions = persistentListOf(),
            permissionsData = executionData.permissionsData,
            discordRequirement = DiscordRequirement.NONE,
            source = when (executionData) {
                is ExecutionData.NoExecution -> null
                is ExecutionData.AllowExecution -> executionData.source
            },
        ),
    )

    override val mixins: Iterable<PendingExecutionReceiverMixin>
        get() = listOfNotNull(
            if (state.discordRequirement != DiscordRequirement.NONE) DiscordExtensionExecutionMixin(state.source) else null,
            if (state.discordRequirement == DiscordRequirement.GUILD) GuildExtensionExecutionMixin(state.source) else null,
            PermissionsExecutionMixin(permissions = state.permissions, data = state.permissionsData),
        )

    override fun permissions(vararg newPermissions: BotPermission): PermissionsPendingExecutionReceiver<ExecutionReceiver, Arg, BaseCommandExecutionReceiverMarker> {
        return PendingExecutionReceiverImpl(
            baseReceiver = baseReceiver,
            discordBaseReceiver = discordBaseReceiver,
            guildedBaseReceiver = guildedBaseReceiver,
            state = state.copy(permissions = state.permissions.addAll(newPermissions.asList())),
        )
    }

    override fun requiresDiscord(): ExtendableArgumentPendingExecutionReceiver<DiscordExecutionReceiver, Arg, BaseCommandExecutionReceiverMarker> {
        return PendingExecutionReceiverImpl(
            baseReceiver = discordBaseReceiver,
            discordBaseReceiver = discordBaseReceiver,
            guildedBaseReceiver = guildedBaseReceiver,
            state = state,
        )
    }

    override fun requiresGuild(): ExtendableArgumentPendingExecutionReceiver<GuildedExecutionReceiver, Arg, BaseCommandExecutionReceiverMarker> {
        return PendingExecutionReceiverImpl(
            baseReceiver = guildedBaseReceiver,
            discordBaseReceiver = guildedBaseReceiver,
            guildedBaseReceiver = guildedBaseReceiver,
            state = state.copy(discordRequirement = DiscordRequirement.GUILD),
        )
    }
}

private val nullPendingExecutionReceiverImpl = PendingExecutionReceiverImpl(
    baseReceiver = NullPendingExecutionReceiver,
    discordBaseReceiver = NullPendingExecutionReceiver,
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
    fun senderHasPermission(permission: BotPermission): Boolean

    fun respond(message: String)
    fun respond(message: Message)
    fun respondWithFile(fileName: String, fileContent: String)
    fun respondWithTextAndFile(text: String, fileName: String, fileContent: String)
}

@CommandDslMarker
interface BaseCommandExecutionReceiverDiscord : BaseCommandExecutionReceiver {
    fun currentGuildInfo(): GuildInfo?

    fun currentMessageEvent(): MessageReceivedEvent
}

fun BaseCommandExecutionReceiverDiscord.currentJda(): JDA = currentMessageEvent().jda

fun BaseCommandExecutionReceiverDiscord.currentChannel(): MessageChannel = currentMessageEvent().channel

fun BaseCommandExecutionReceiverDiscord.botHasPermission(permission: DiscordPermission): Boolean {
    return currentGuildInfo()?.guild?.selfMember?.hasPermission(permission) ?: false
}

interface BaseCommandExecutionReceiverGuilded : BaseCommandExecutionReceiverDiscord {
    override fun currentGuildInfo(): GuildInfo
}


typealias BaseCommandPendingExecutionReceiver<Arg> =
        ExtendableArgumentPendingExecutionReceiver<BaseCommandExecutionReceiver, Arg, BaseCommandExecutionReceiverMarker>

abstract class BaseCommand(private val strategy: BaseCommandStrategy) : Command {
    @CommandDslMarker
    private open class ExecutionReceiverImpl(
        protected val strategy: BaseCommandStrategy,
        protected val source: CommandEventSource,
        private val invocation: CommandInvocation,
    ) : BaseCommandExecutionReceiver {
        override fun respond(message: String) {
            strategy.sendResponse(source, invocation, message)
        }

        override fun respond(message: Message) {
            strategy.sendResponseMessage(source, invocation, message)
        }

        override fun respondWithFile(fileName: String, fileContent: String) {
            strategy.sendResponseAsFile(source, invocation, fileName, fileContent)
        }

        override fun respondWithTextAndFile(text: String, fileName: String, fileContent: String) {
            strategy.sendResponseTextAndFile(source, invocation, text, fileName, fileContent)
        }

        override val userPermissionContext by lazy { userPermissionContextForSource(source) }

        override fun senderHasPermission(permission: BotPermission): Boolean =
            permission.isSatisfied(strategy.permissionContext, userPermissionContext)
    }

    private open class ExecutionReceiverDiscordImpl(
        strategy: BaseCommandStrategy,
        source: CommandEventSource,
        invocation: CommandInvocation,
    ) : ExecutionReceiverImpl(strategy, source, invocation), BaseCommandExecutionReceiverDiscord {
        override fun currentMessageEvent() = (source as CommandEventSource.Discord).event

        override fun currentGuildInfo(): GuildInfo? {
            val event = currentMessageEvent()
            return if (event.isFromGuild) GuildInfo(event, strategy) else null
        }

    }

    private class ExecutionReceiverGuildedImpl(
        strategy: BaseCommandStrategy,
        source: CommandEventSource,
        invocation: CommandInvocation,
    ) : ExecutionReceiverDiscordImpl(strategy, source, invocation), BaseCommandExecutionReceiverGuilded {
        override fun currentGuildInfo(): GuildInfo {
            return super.currentGuildInfo() ?: error("Being in a guild should have already been enforced")
        }
    }

    override fun invoke(source: CommandEventSource, invocation: CommandInvocation) {
        TopLevelExecutingArgumentDescriptionReceiver(
            UnparsedCommandArgs(invocation.args),
            onError = { msg ->
                strategy.sendArgumentErrorResponse(
                    source = source,
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
                            exec(ExecutionReceiverImpl(strategy, source, invocation), mapParsed(results))
                        },
                        discordBaseReceiver = simpleInvokingPendingExecutionReceiver { exec ->
                            exec(
                                ExecutionReceiverGuildedImpl(
                                    strategy = strategy,
                                    source = source,
                                    invocation = invocation,
                                ),
                                mapParsed(results),
                            )
                        },
                        guildedBaseReceiver = simpleInvokingPendingExecutionReceiver { exec ->
                            exec(
                                ExecutionReceiverGuildedImpl(
                                    strategy = strategy,
                                    source = source,
                                    invocation = invocation,
                                ),
                                mapParsed(results),
                            )
                        },
                        executionData = PendingExecutionReceiverImpl.ExecutionData.AllowExecution(
                            permissionsData = PermissionsReceiverData.AllowExecution(
                                userContext = userPermissionContextForSource(source),
                                permissionsContext = strategy.permissionContext,
                                onError = { strategy.onPermissionsError(source, invocation, it) }
                            ),
                            source = source,
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

object BaseCommandDefaultArgumentStrategy : BaseCommandArgumentStrategy {
    override fun sendArgumentErrorResponse(
        source: CommandEventSource,
        invocation: CommandInvocation,
        errorMessage: String,
        usage: String,
    ) {
        source.tryRespondWithText("$errorMessage. Usage: ${usage.ifBlank { NO_ARGUMENTS }}")
    }
}
