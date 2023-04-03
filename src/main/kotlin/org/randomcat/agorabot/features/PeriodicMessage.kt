package org.randomcat.agorabot.features

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.randomcat.agorabot.*
import org.randomcat.agorabot.setup.features.featureConfigDir
import org.randomcat.agorabot.util.await
import org.randomcat.agorabot.util.insecureRandom
import org.randomcat.agorabot.util.userFacingRandom
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoField
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import java.nio.file.NoSuchFileException as NioNoSuchFileException

private enum class PeriodicMessageInterval(val configName: String) {
    WEEKLY("weekly"),
    ;

    companion object {
        private val configToValues = values().associateBy { it.configName }

        fun fromConfigName(configName: String): PeriodicMessageInterval {
            return configToValues.getValue(configName)
        }
    }
}

@Serializable
private data class PeriodicMessageConfig(
    @SerialName("discord_channel_id")
    val discordChannelId: String,
    val options: List<String>,
    @SerialName("random_interval")
    val randomIntervalString: String,
) {
    init {
        require(options.isNotEmpty()) {
            "Periodic message options cannot be empty"
        }
    }

    val randomInterval = PeriodicMessageInterval.fromConfigName(randomIntervalString)
}

@Serializable
private data class PeriodicMessageListConfig(
    val messages: Map<String, PeriodicMessageConfig>,
)

private data class PeriodicMessageFeatureConfig(
    val list: PeriodicMessageListConfig,
    val storagePath: Path,
)

@Serializable
private sealed class PeriodicMessageStateDto {
    @Serializable
    @SerialName("v0")
    data class V0(
        val scheduledInstant: String,
    ) : PeriodicMessageStateDto()
}

private data class PeriodicMessageState(
    val scheduledTime: Instant,
) {
    companion object {
        fun from(dto: PeriodicMessageStateDto): PeriodicMessageState {
            return when (dto) {
                is PeriodicMessageStateDto.V0 -> PeriodicMessageState(
                    scheduledTime = Instant.parse(dto.scheduledInstant),
                )
            }
        }
    }

    fun toDto(): PeriodicMessageStateDto {
        return PeriodicMessageStateDto.V0(scheduledInstant = scheduledTime.toString())
    }
}

@Serializable
private sealed class PeriodicMessageFeatureStateDto {
    @Serializable
    @SerialName("v0")
    data class V0(
        val messages: Map<String, PeriodicMessageStateDto>,
    ) : PeriodicMessageFeatureStateDto()
}

private data class PeriodicMessageFeatureState(
    val messages: PersistentMap<String, PeriodicMessageState>,
) {
    companion object {
        fun from(dto: PeriodicMessageFeatureStateDto): PeriodicMessageFeatureState {
            return when (dto) {
                is PeriodicMessageFeatureStateDto.V0 -> PeriodicMessageFeatureState(
                    messages = dto.messages.mapValues { (_, v) -> PeriodicMessageState.from(v) }.toPersistentMap(),
                )
            }
        }
    }

    fun toDto(): PeriodicMessageFeatureStateDto {
        return PeriodicMessageFeatureStateDto.V0(
            messages = messages.mapValues { (_, v) -> v.toDto() },
        )
    }
}

private val logger = LoggerFactory.getLogger("PeriodicMessages")

private fun randomNextInterval(baseTime: Instant, interval: PeriodicMessageInterval): Instant {
    return when (interval) {
        PeriodicMessageInterval.WEEKLY -> {
            val startOfNextWeek =
                OffsetDateTime
                    .ofInstant(baseTime, ZoneOffset.UTC)
                    .plusWeeks(1)
                    .with(ChronoField.DAY_OF_WEEK, 1)

            val startOfWeekAfterNext = startOfNextWeek.plusWeeks(1)

            Instant.ofEpochSecond(
                insecureRandom().nextLong(
                    startOfNextWeek.toEpochSecond(),
                    startOfWeekAfterNext.toEpochSecond(),
                ),
            )
        }
    }
}

private val coroutineScopeDep = FeatureDependency.Single(CoroutineScopeTag)
private val jdaDep = FeatureDependency.Single(JdaTag)

@FeatureSourceFactory
fun periodicMessageSource(): FeatureSource<*> = object : FeatureSource<PeriodicMessageFeatureConfig> {
    override val featureName: String
        get() = "periodic_messages"

    override fun readConfig(context: FeatureSetupContext): PeriodicMessageFeatureConfig {
        return PeriodicMessageFeatureConfig(
            list = Json.decodeFromString(context.paths.featureConfigDir.resolve("periodic_messages.json").readText()),
            storagePath = context.paths.storagePath.resolve("periodic_messages_state"),
        )
    }

    override val dependencies: List<FeatureDependency<*>>
        get() = listOf(coroutineScopeDep)

    override val provides: List<FeatureElementTag<*>>
        get() = listOf(StartupBlockTag)

    override fun createFeature(config: PeriodicMessageFeatureConfig, context: FeatureSourceContext): Feature {
        val coroutineScope = context[coroutineScopeDep]
        val jda = context[jdaDep]

        return object : Feature {
            override fun <T> query(tag: FeatureElementTag<T>): List<T> {
                if (tag is StartupBlockTag) return tag.values({
                    coroutineScope.launch {
                        var currentState = try {
                            PeriodicMessageFeatureState.from(
                                Json.decodeFromString<PeriodicMessageFeatureStateDto>(config.storagePath.readText())
                            )
                        } catch (e: NioNoSuchFileException) {
                            PeriodicMessageFeatureState(messages = persistentMapOf())
                        }

                        while (true) {
                            ensureActive()

                            try {
                                for ((id, messageConfig) in config.list.messages) {
                                    val checkTime = Instant.now()
                                    val previousScheduled = currentState.messages[id]?.scheduledTime

                                    if (previousScheduled == null || checkTime >= previousScheduled) {
                                        try {
                                            jda
                                                .getTextChannelById(messageConfig.discordChannelId)
                                                ?.sendMessage(messageConfig.options.random(userFacingRandom()))
                                                ?.await()

                                            currentState = currentState.copy(
                                                messages = currentState.messages.put(
                                                    id,
                                                    PeriodicMessageState(
                                                        scheduledTime = randomNextInterval(
                                                            checkTime,
                                                            messageConfig.randomInterval,
                                                        ),
                                                    ),
                                                ),
                                            )
                                        } catch (e: Exception) {
                                            logger.error("Error handling periodic message", e)
                                        }
                                    }

                                    ensureActive()
                                }
                            } finally {
                                withContext(NonCancellable) {
                                    config.storagePath.writeText(Json.encodeToString(currentState.toDto()))
                                }
                            }

                            @OptIn(ExperimentalTime::class)
                            delay(10.seconds)
                        }
                    }
                })

                invalidTag(tag)
            }
        }
    }
}
