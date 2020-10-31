package org.randomcat.agorabot.commands.impl

class SubcommandsReceiverChecker {
    enum class State {
        EMPTY, IMPLEMENTATION, SUBCOMMANDS
    }

    private var state: State = State.EMPTY
    private var seenSubcommands = mutableListOf<String>()

    fun reset() {
        state = State.EMPTY
        seenSubcommands = mutableListOf()
    }

    private fun checkImplementation() {
        check(state == State.EMPTY) { "cannot provide two subcommand implementations or implementation and subcommands" }
        state = State.IMPLEMENTATION
    }

    fun checkMatchFirst() {
        checkImplementation()
    }

    fun checkArgsRaw() {
        checkImplementation()
    }

    fun checkSubcommand(subcommand: String) {
        check(state == State.EMPTY || state == State.SUBCOMMANDS) { "cannot provide implementation and subcommands" }
        state = State.SUBCOMMANDS

        check(!seenSubcommands.contains(subcommand)) { "cannot use same subcommand twice" }
        seenSubcommands.add(subcommand)
    }
}

class ParseOnceFlag {
    private var isParsing: Boolean = false

    fun beginParsing() {
        check(!isParsing)
        isParsing = true
    }
}
