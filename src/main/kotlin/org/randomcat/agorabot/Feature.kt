package org.randomcat.agorabot

import net.dv8tion.jda.api.JDA
import org.randomcat.agorabot.commands.impl.BaseCommandStrategy
import org.randomcat.agorabot.listener.Command
import org.randomcat.agorabot.listener.QueryableCommandRegistry

interface FeatureContext {
    // The strategy that should be used for commands if there is no specific other need for a specific command.
    val defaultCommandStrategy: BaseCommandStrategy

    // Returns the global command registry. This should not be invoked during registration, only after commands
    // have started executing.
    fun commandRegistry(): QueryableCommandRegistry
}

interface Feature {
    fun commandsInContext(context: FeatureContext): Map<String, Command>
    fun registerListenersTo(jda: JDA) {}

    companion object {
        fun ofCommands(block: (context: FeatureContext) -> Map<String, Command>): Feature {
            return object : Feature {
                override fun commandsInContext(context: FeatureContext): Map<String, Command> {
                    return block(context)
                }
            }
        }
    }
}
