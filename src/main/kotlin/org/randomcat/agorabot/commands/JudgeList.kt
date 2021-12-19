package org.randomcat.agorabot.commands

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.serialization.Serializable
import org.randomcat.agorabot.commands.impl.*
import org.randomcat.agorabot.config.get
import org.randomcat.agorabot.config.update
import org.randomcat.agorabot.permissions.GuildScope

private data class JudgeListState(val judgeNames: ImmutableList<String>) {
    constructor(judgeNames: List<String>) : this(judgeNames.toImmutableList())

    companion object {
        private val DEFAULT = JudgeListState(judgeNames = emptyList())

        fun default() = DEFAULT
    }
}

@Serializable
private sealed class JudgeListStateDto {
    @Serializable
    private data class Version0(private val judgeNames: List<String>) : JudgeListStateDto() {
        override fun build(): JudgeListState {
            return JudgeListState(judgeNames = judgeNames)
        }
    }

    abstract fun build(): JudgeListState

    companion object {
        fun from(state: JudgeListState): JudgeListStateDto {
            return Version0(state.judgeNames)
        }
    }
}

private val EDIT_PERMISSION = GuildScope.command("judge_list").action("edit")

private const val STATE_KEY = "judge_list.state"

private fun BaseCommandExecutionReceiverRequiring<ExtendedGuildRequirement>.getJudgeListState(): JudgeListState {
    return currentGuildState.get<JudgeListStateDto>(STATE_KEY)?.build()
        ?: JudgeListState.default()
}

private fun BaseCommandExecutionReceiverRequiring<ExtendedGuildRequirement>.updateJudgeListState(block: (JudgeListState) -> JudgeListState) {
    currentGuildState.update<JudgeListStateDto>(STATE_KEY) { old ->
        JudgeListStateDto.from(block(old?.build() ?: JudgeListState.default()))
    }
}

class JudgeListCommand(strategy: BaseCommandStrategy) : BaseCommand(strategy) {
    override fun BaseCommandImplReceiver.impl() {
        subcommands {
            subcommand("add") {
                args(StringArg("name")).requires(InGuild).permissions(EDIT_PERMISSION) { (name) ->
                    updateJudgeListState { oldState ->
                        JudgeListState(judgeNames = oldState.judgeNames.toPersistentList().add(name))
                    }

                    respond("Added $name to judge list.")
                }
            }

            subcommand("remove") {
                args(StringArg("name")).requires(InGuild).permissions(EDIT_PERMISSION) { (name) ->
                    var removedAny: Boolean? = null

                    updateJudgeListState { oldState ->
                        JudgeListState(
                            judgeNames = oldState.judgeNames.toPersistentList().mutate { mutJudgeNames ->
                                removedAny = mutJudgeNames.removeAll { it == name }
                            },
                        )
                    }

                    if (checkNotNull(removedAny)) {
                        respond("Removed $name from judge list.")
                    } else {
                        respond("$name is not on the judge list.")
                    }
                }
            }

            subcommand("list") {
                noArgs().requires(InGuild) {
                    val judgeNames = getJudgeListState().judgeNames

                    if (judgeNames.isNotEmpty()) {
                        respond("The judge list is as follows: ${judgeNames.joinToString()}.")
                    } else {
                        respond("The judge list is empty.")
                    }
                }
            }

            subcommand("choose") {
                noArgs().requires(InGuild) {
                    val judgeNames = getJudgeListState().judgeNames

                    if (judgeNames.isNotEmpty()) {
                        respond("Selected: ${judgeNames.random()}")
                    } else {
                        respond("The judge list is empty.")
                    }
                }
            }
        }
    }
}
