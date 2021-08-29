package org.randomcat.agorabot.commands

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.randomcat.agorabot.commands.impl.BaseCommand
import org.randomcat.agorabot.commands.impl.BaseCommandImplReceiver
import org.randomcat.agorabot.commands.impl.BaseCommandStrategy
import org.randomcat.agorabot.commands.impl.noArgs
import org.slf4j.LoggerFactory
import java.net.URI

class RuleCommand(
    strategy: BaseCommandStrategy,
    private val ruleIndexUri: URI,
) : BaseCommand(strategy) {
    companion object {
        private val logger = LoggerFactory.getLogger(RuleCommand::class.java)
    }

    override fun BaseCommandImplReceiver.impl() {
        subcommands {
            subcommand("random") {
                noArgs {
                    @Suppress("BlockingMethodInNonBlockingContext")
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val ruleIndexText = ruleIndexUri.toURL().openStream().use {
                                it.bufferedReader(Charsets.UTF_8).readText()
                            }

                            val ruleIndexJson = Json.parseToJsonElement(ruleIndexText).jsonObject
                            val enactedRuleNumbers = ruleIndexJson.getValue("enacted_rules").jsonArray

                            val randomRuleNumber = enactedRuleNumbers.random().jsonPrimitive.content

                            val randomRuleTitle = ruleIndexJson.getValue("known_rules").jsonArray.single {
                                it.jsonObject.getValue("id").jsonPrimitive.content == randomRuleNumber
                            }.jsonObject.getValue("title").jsonPrimitive.content

                            respond("Selected: Rule $randomRuleNumber ($randomRuleTitle)")
                        } catch (e: Exception) {
                            logger.error("Error while trying to read index", e)
                            respond("Could not read rule index.")
                        }
                    }
                }
            }
        }
    }
}
