package org.randomcat.agorabot.commands

import org.randomcat.agorabot.commands.impl.*

class ChooseCommand(strategy: BaseCommandStrategy) : BaseCommand(strategy) {
    override fun BaseCommandImplReceiver.impl() {
        args(RemainingStringArgs("choices")) { (choices) ->
            when (choices.size) {
                0 -> respond("I need something to choose from.")
                1 -> respond("You already know the result.")
                else -> {
                    respond("Choice: ${choices.random()}")
                }
            }
        }
    }
}
