package org.randomcat.agorabot.commands

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.randomcat.agorabot.commands.base.BaseCommand
import org.randomcat.agorabot.commands.base.BaseCommandImplReceiver
import org.randomcat.agorabot.commands.base.BaseCommandStrategy
import org.randomcat.agorabot.util.userFacingRandom
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
                    try {
                        val ruleIndexText = withContext(Dispatchers.IO) {
                            ruleIndexUri.toURL().openStream().use {
                                it.bufferedReader(Charsets.UTF_8).readText()
                            }
                        }

                        val ruleIndexJson = Json.parseToJsonElement(ruleIndexText).jsonObject
                        val enactedRuleNumbers = ruleIndexJson.getValue("enacted_rules").jsonArray

                        val randomRuleIndex = enactedRuleNumbers.indices.random(userFacingRandom())
                        val randomRuleNumber = enactedRuleNumbers[randomRuleIndex].jsonPrimitive.content

                        val randomRuleTitle =
                            ruleIndexJson
                                .getValue("known_rules")
                                .jsonArray
                                .single {
                                    it.jsonObject.getValue("id").jsonPrimitive.content == randomRuleNumber
                                }
                                .jsonObject
                                .getValue("title")
                                .jsonPrimitive
                                .content

                        respond("Selected index $randomRuleIndex: Rule $randomRuleNumber ($randomRuleTitle)")
                    } catch (e: Exception) {
                        logger.error("Error while trying to read index", e)
                        respond("Could not read rule index.")
                    }
                }
            }
        }
    }
}
