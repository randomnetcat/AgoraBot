package org.randomcat.agorabot.secrethitler.storage.impl

import kotlinx.collections.immutable.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.randomcat.agorabot.config.persist.AtomicCachedStorage
import org.randomcat.agorabot.config.persist.ConfigPersistService
import org.randomcat.agorabot.config.persist.StorageStrategy
import org.randomcat.agorabot.config.persist.updateValueAndExtract
import org.randomcat.agorabot.secrethitler.model.*
import org.randomcat.agorabot.secrethitler.storage.api.SecretHitlerGameList
import org.randomcat.agorabot.secrethitler.storage.impl.JsonSecretHitlerGameList.StorageType
import org.randomcat.agorabot.secrethitler.storage.impl.JsonSecretHitlerGameList.ValueType
import java.nio.file.Path
import java.util.*

@Serializable
private sealed class GameConfigurationDto {
    companion object {
        fun from(configuration: SecretHitlerGameConfiguration): GameConfigurationDto {
            return Version0(
                liberalWinRequirement = configuration.liberalWinRequirement,
                fascistPowers = (1 until configuration.fascistWinRequirement).map { configuration.fascistPowerAt(it) },
                hitlerChancellorWinRequirement = configuration.hitlerChancellorWinRequirement,
                vetoUnlockRequirement = configuration.vetoUnlockRequirement,
                speedyEnactRequirement = configuration.speedyEnactRequirement,
            )
        }
    }

    abstract fun toConfiguration(): SecretHitlerGameConfiguration

    @Serializable
    @SerialName("GameConfigurationV0")
    data class Version0(
        val liberalWinRequirement: Int,
        val fascistPowers: List<SecretHitlerFascistPower?>,
        val hitlerChancellorWinRequirement: Int,
        val vetoUnlockRequirement: Int,
        val speedyEnactRequirement: Int,
    ) : GameConfigurationDto() {
        override fun toConfiguration(): SecretHitlerGameConfiguration {
            return SecretHitlerGameConfiguration(
                liberalWinRequirement = liberalWinRequirement,
                fascistPowers = fascistPowers.toImmutableList(),
                hitlerChancellorWinRequirement = hitlerChancellorWinRequirement,
                vetoUnlockRequirement = vetoUnlockRequirement,
                speedyEnactRequirement = speedyEnactRequirement,
            )
        }
    }
}

@Serializable
private sealed class PlayerMapDto {
    companion object {
        fun from(playerMap: SecretHitlerPlayerMap): PlayerMapDto {
            return Version0(playerMap.toMap())
        }
    }

    abstract fun toPlayerMap(): SecretHitlerPlayerMap

    @Serializable
    @SerialName("PlayerMapV0")
    data class Version0(
        val map: Map<SecretHitlerPlayerNumber, SecretHitlerPlayerExternalName>,
    ) : PlayerMapDto() {
        override fun toPlayerMap(): SecretHitlerPlayerMap {
            return SecretHitlerPlayerMap(map)
        }
    }
}

private enum class RoleDto {
    LIBERAL,
    PLAIN_FASCIST,
    HITLER,
    ;

    companion object {
        fun from(role: SecretHitlerRole): RoleDto {
            return when (role) {
                is SecretHitlerRole.Liberal -> LIBERAL
                is SecretHitlerRole.PlainFascist -> PLAIN_FASCIST
                is SecretHitlerRole.Hitler -> HITLER
            }
        }
    }

    fun toRole(): SecretHitlerRole {
        return when (this) {
            LIBERAL -> SecretHitlerRole.Liberal
            PLAIN_FASCIST -> SecretHitlerRole.PlainFascist
            HITLER -> SecretHitlerRole.Hitler
        }
    }
}

@Serializable
private sealed class RoleMapDto {
    companion object {
        fun from(roleMap: SecretHitlerRoleMap): RoleMapDto {
            return Version0(roleMap.toMap().mapValues { (_, v) -> RoleDto.from(v) })
        }
    }

    abstract fun toRoleMap(): SecretHitlerRoleMap

    @Serializable
    @SerialName("RoleMapV0")
    data class Version0(
        val map: Map<SecretHitlerPlayerNumber, RoleDto>,
    ) : RoleMapDto() {
        override fun toRoleMap(): SecretHitlerRoleMap {
            return SecretHitlerRoleMap(map.mapValues { (_, v) -> v.toRole() })
        }
    }
}

@Serializable
private sealed class DeckStateDto {
    companion object {
        fun from(deckState: SecretHitlerDeckState): DeckStateDto {
            return Version0(
                drawDeckPolicies = deckState.drawDeck.allPolicies(),
                discardDeckPolicies = deckState.discardDeck.allPolicies(),
            )
        }
    }

    abstract fun toDeckState(): SecretHitlerDeckState

    @Serializable
    @SerialName("DeckStateV0")
    data class Version0(
        val drawDeckPolicies: List<SecretHitlerPolicyType>,
        val discardDeckPolicies: List<SecretHitlerPolicyType>,
    ) : DeckStateDto() {
        override fun toDeckState(): SecretHitlerDeckState {
            return SecretHitlerDeckState(
                drawDeck = SecretHitlerDrawDeckState(drawDeckPolicies),
                discardDeck = SecretHitlerDiscardDeckState(discardDeckPolicies),
            )
        }
    }
}

@Serializable
private sealed class PoliciesStateDto {
    companion object {
        fun from(policiesState: SecretHitlerPoliciesState): PoliciesStateDto {
            return Version0(
                liberalPoliciesEnacted = policiesState.liberalPoliciesEnacted,
                fascistPoliciesEnacted = policiesState.fascistPoliciesEnacted,
            )
        }
    }

    abstract fun toPoliciesState(): SecretHitlerPoliciesState

    @Serializable
    @SerialName("PoliciesStateV0")
    data class Version0(
        val liberalPoliciesEnacted: Int,
        val fascistPoliciesEnacted: Int,
    ) : PoliciesStateDto() {
        override fun toPoliciesState(): SecretHitlerPoliciesState {
            return SecretHitlerPoliciesState(
                liberalPoliciesEnacted = liberalPoliciesEnacted,
                fascistPoliciesEnacted = fascistPoliciesEnacted,
            )
        }
    }
}

@Serializable
private sealed class ElectionStateDto {
    companion object {
        fun from(electionState: SecretHitlerElectionState): ElectionStateDto {
            return Version0(
                currentPresidentTicker = electionState.currentPresidentTicker,
                termLimitedPlayers = electionState
                    .termLimitState
                    .termLimitedGovernment
                    ?.let { GovernmentMembersDto.from(it) },
                electionTrackerState = electionState.electionTrackerState,
            )
        }
    }

    abstract fun toElectionState(): SecretHitlerElectionState

    @Serializable
    @SerialName("ElectionStateV0")
    data class Version0(
        val currentPresidentTicker: SecretHitlerPlayerNumber,
        val termLimitedPlayers: GovernmentMembersDto?,
        val electionTrackerState: Int,
    ) : ElectionStateDto() {
        override fun toElectionState(): SecretHitlerElectionState {
            return SecretHitlerElectionState(
                currentPresidentTicker = currentPresidentTicker,
                termLimitState = SecretHitlerTermLimitState(termLimitedPlayers?.toGovernmentMembers()),
                electionTrackerState = electionTrackerState,
            )
        }
    }
}

@Serializable
private sealed class PowersStateDto {
    companion object {
        fun from(powersState: SecretHitlerPowersState): PowersStateDto {
            return Version0(
                previouslyInvestigatedPlayers = powersState.previouslyInvestigatedPlayers,
            )
        }
    }

    abstract fun toPowersState(): SecretHitlerPowersState

    @Serializable
    @SerialName("PowersStateV0")
    data class Version0(
        val previouslyInvestigatedPlayers: Set<SecretHitlerPlayerNumber>,
    ) : PowersStateDto() {
        override fun toPowersState(): SecretHitlerPowersState {
            return SecretHitlerPowersState(
                previouslyInvestigatedPlayers = previouslyInvestigatedPlayers.toImmutableSet(),
            )
        }
    }
}

@Serializable
private sealed class GlobalStateDto {
    companion object {
        fun from(globalGameState: SecretHitlerGlobalGameState): GlobalStateDto {
            return Version0(
                configuration = GameConfigurationDto.from(globalGameState.configuration),
                playerMap = PlayerMapDto.from(globalGameState.playerMap),
                roleMap = RoleMapDto.from(globalGameState.roleMap),
                deckState = DeckStateDto.from(globalGameState.boardState.deckState),
                policiesState = PoliciesStateDto.from(globalGameState.boardState.policiesState),
                electionState = ElectionStateDto.from(globalGameState.electionState),
                powersState = PowersStateDto.from(globalGameState.powersState),
            )
        }
    }

    abstract fun toGlobalState(): SecretHitlerGlobalGameState

    @Serializable
    @SerialName("GlobalStateV0")
    data class Version0(
        val configuration: GameConfigurationDto,
        val playerMap: PlayerMapDto,
        val roleMap: RoleMapDto,
        val deckState: DeckStateDto,
        val policiesState: PoliciesStateDto,
        val electionState: ElectionStateDto,
        val powersState: PowersStateDto,
    ) : GlobalStateDto() {
        override fun toGlobalState(): SecretHitlerGlobalGameState {
            return SecretHitlerGlobalGameState(
                configuration = configuration.toConfiguration(),
                playerMap = playerMap.toPlayerMap(),
                roleMap = roleMap.toRoleMap(),
                boardState = SecretHitlerBoardState(
                    deckState = deckState.toDeckState(),
                    policiesState = policiesState.toPoliciesState(),
                ),
                electionState = electionState.toElectionState(),
                powersState = powersState.toPowersState(),
            )
        }
    }
}

@Serializable
private sealed class GovernmentMembersDto {
    companion object {
        fun from(governmentMembers: SecretHitlerGovernmentMembers): GovernmentMembersDto {
            return Version0(
                president = governmentMembers.president,
                chancellor = governmentMembers.chancellor,
            )
        }
    }

    abstract fun toGovernmentMembers(): SecretHitlerGovernmentMembers

    @Serializable
    @SerialName("GovernmentMembersV0")
    data class Version0(
        val president: SecretHitlerPlayerNumber,
        val chancellor: SecretHitlerPlayerNumber,
    ) : GovernmentMembersDto() {
        override fun toGovernmentMembers(): SecretHitlerGovernmentMembers {
            return SecretHitlerGovernmentMembers(
                president = president,
                chancellor = chancellor,
            )
        }
    }
}

@Serializable
private data class VoteMapDto(val votesByPlayer: Map<SecretHitlerPlayerNumber, SecretHitlerEphemeralState.VoteKind>) {
    companion object {
        fun from(voteMap: SecretHitlerEphemeralState.VoteMap): VoteMapDto {
            return VoteMapDto(
                votesByPlayer = voteMap.toMap(),
            )
        }
    }

    fun toVoteMap(): SecretHitlerEphemeralState.VoteMap {
        return SecretHitlerEphemeralState.VoteMap(votesByPlayer = votesByPlayer)
    }
}

@Serializable
private sealed class EphemeralStateDto {
    companion object {
        fun from(state: SecretHitlerEphemeralState): EphemeralStateDto {
            return when (state) {
                is SecretHitlerEphemeralState.ChancellorSelectionPending -> {
                    ChancellorSelectionPending.from(state)
                }

                is SecretHitlerEphemeralState.VotingOngoing -> {
                    VotingOngoing.from(state)
                }

                is SecretHitlerEphemeralState.PresidentPolicyChoicePending -> {
                    PresidentPolicyChoicePending.from(state)
                }

                is SecretHitlerEphemeralState.ChancellorPolicyChoicePending -> {
                    ChancellorPolicyChoicePending.from(state)
                }
                is SecretHitlerEphemeralState.PolicyPending.InvestigateParty -> {
                    InvestigatePending.from(state)
                }

                is SecretHitlerEphemeralState.PolicyPending.SpecialElection -> {
                    SpecialElectionPending.from(state)
                }

                is SecretHitlerEphemeralState.PolicyPending.Execution -> {
                    ExecutionPending.from(state)
                }
            }
        }
    }

    abstract fun toEphemeralState(): SecretHitlerEphemeralState

    @Serializable
    @SerialName("ChancellorSelectionPendingV0")
    data class ChancellorSelectionPending(val presidentCandidate: SecretHitlerPlayerNumber) : EphemeralStateDto() {
        companion object {
            fun from(state: SecretHitlerEphemeralState.ChancellorSelectionPending): ChancellorSelectionPending {
                return ChancellorSelectionPending(
                    presidentCandidate = state.presidentCandidate,
                )
            }
        }

        override fun toEphemeralState(): SecretHitlerEphemeralState {
            return SecretHitlerEphemeralState.ChancellorSelectionPending(
                presidentCandidate = presidentCandidate,
            )
        }
    }

    @Serializable
    @SerialName("VotingOngoingV0")
    data class VotingOngoing(
        val governmentMembers: GovernmentMembersDto,
        val voteMap: VoteMapDto,
    ) : EphemeralStateDto() {
        companion object {
            fun from(state: SecretHitlerEphemeralState.VotingOngoing): VotingOngoing {
                return VotingOngoing(
                    governmentMembers = GovernmentMembersDto.from(state.governmentMembers),
                    voteMap = VoteMapDto.from(state.voteMap),
                )
            }
        }

        override fun toEphemeralState(): SecretHitlerEphemeralState {
            return SecretHitlerEphemeralState.VotingOngoing(
                governmentMembers = governmentMembers.toGovernmentMembers(),
                voteMap = voteMap.toVoteMap(),
            )
        }
    }

    @Serializable
    @SerialName("PresidentPolicyChoicePendingV0")
    data class PresidentPolicyChoicePending(
        val governmentMembers: GovernmentMembersDto,
        val policyOptions: List<SecretHitlerPolicyType>,
    ) : EphemeralStateDto() {
        companion object {
            fun from(state: SecretHitlerEphemeralState.PresidentPolicyChoicePending): PresidentPolicyChoicePending {
                return PresidentPolicyChoicePending(
                    governmentMembers = GovernmentMembersDto.from(state.governmentMembers),
                    policyOptions = state.options.policies,
                )
            }
        }

        override fun toEphemeralState(): SecretHitlerEphemeralState {
            return SecretHitlerEphemeralState.PresidentPolicyChoicePending(
                governmentMembers = governmentMembers.toGovernmentMembers(),
                options = SecretHitlerEphemeralState.PresidentPolicyOptions(policyOptions),
            )
        }
    }

    @Serializable
    @SerialName("ChancellorPolicyChoicePendingV0")
    data class ChancellorPolicyChoicePending(
        val governmentMembers: GovernmentMembersDto,
        val policyOptions: List<SecretHitlerPolicyType>,
        val vetoState: SecretHitlerEphemeralState.VetoRequestState,
    ) : EphemeralStateDto() {
        companion object {
            fun from(state: SecretHitlerEphemeralState.ChancellorPolicyChoicePending): ChancellorPolicyChoicePending {
                return ChancellorPolicyChoicePending(
                    governmentMembers = GovernmentMembersDto.from(state.governmentMembers),
                    policyOptions = state.options.policies,
                    vetoState = state.vetoState,
                )
            }
        }

        override fun toEphemeralState(): SecretHitlerEphemeralState {
            return SecretHitlerEphemeralState.ChancellorPolicyChoicePending(
                governmentMembers = governmentMembers.toGovernmentMembers(),
                options = SecretHitlerEphemeralState.ChancellorPolicyOptions(policyOptions),
                vetoState = vetoState,
            )
        }
    }

    @Serializable
    @SerialName("InvestigatePendingV0")
    data class InvestigatePending(
        val presidentNumber: SecretHitlerPlayerNumber,
    ) : EphemeralStateDto() {
        companion object {
            fun from(state: SecretHitlerEphemeralState.PolicyPending.InvestigateParty): InvestigatePending {
                return InvestigatePending(
                    presidentNumber = state.presidentNumber,
                )
            }
        }

        override fun toEphemeralState(): SecretHitlerEphemeralState {
            return SecretHitlerEphemeralState.PolicyPending.InvestigateParty(
                presidentNumber = presidentNumber,
            )
        }
    }

    @Serializable
    @SerialName("SpecialElectionPendingV0")
    data class SpecialElectionPending(
        val presidentNumber: SecretHitlerPlayerNumber,
    ) : EphemeralStateDto() {
        companion object {
            fun from(state: SecretHitlerEphemeralState.PolicyPending.SpecialElection): SpecialElectionPending {
                return SpecialElectionPending(
                    presidentNumber = state.presidentNumber,
                )
            }
        }

        override fun toEphemeralState(): SecretHitlerEphemeralState {
            return SecretHitlerEphemeralState.PolicyPending.SpecialElection(
                presidentNumber = presidentNumber,
            )
        }
    }

    @Serializable
    @SerialName("ExecutionPendingV0")
    data class ExecutionPending(
        val presidentNumber: SecretHitlerPlayerNumber,
    ) : EphemeralStateDto() {
        companion object {
            fun from(state: SecretHitlerEphemeralState.PolicyPending.Execution): ExecutionPending {
                return ExecutionPending(
                    presidentNumber = state.presidentNumber,
                )
            }
        }

        override fun toEphemeralState(): SecretHitlerEphemeralState {
            return SecretHitlerEphemeralState.PolicyPending.Execution(
                presidentNumber = presidentNumber,
            )
        }
    }
}

@Serializable
private sealed class GameStateDto {
    companion object {
        fun from(gameState: SecretHitlerGameState): GameStateDto {
            return when (gameState) {
                is SecretHitlerGameState.Joining -> {
                    Joining(names = gameState.playerNames.toList())
                }

                is SecretHitlerGameState.Running -> {
                    Running.from(gameState)
                }

                is SecretHitlerGameState.Completed -> {
                    Completed
                }
            }
        }
    }

    abstract fun toGameState(): SecretHitlerGameState

    @Serializable
    @SerialName("JoiningV0")
    data class Joining(val names: List<SecretHitlerPlayerExternalName>) : GameStateDto() {
        override fun toGameState(): SecretHitlerGameState {
            return SecretHitlerGameState.Joining(playerNames = names.toImmutableSet())
        }
    }

    @Serializable
    @SerialName("RunningV0")
    data class Running(
        val globalState: GlobalStateDto,
        val ephemeralState: EphemeralStateDto,
    ) : GameStateDto() {
        companion object {
            fun from(gameState: SecretHitlerGameState.Running): Running {
                return Running(
                    globalState = GlobalStateDto.from(gameState.globalState),
                    ephemeralState = EphemeralStateDto.from(gameState.ephemeralState),
                )
            }
        }

        override fun toGameState(): SecretHitlerGameState {
            return SecretHitlerGameState.Running(
                globalState = globalState.toGlobalState(),
                ephemeralState = ephemeralState.toEphemeralState(),
            )
        }
    }

    @Serializable
    @SerialName("CompletedV0")
    object Completed : GameStateDto() {
        override fun toGameState(): SecretHitlerGameState {
            return SecretHitlerGameState.Completed
        }
    }
}


// It'll be fiiiiineee....
@Suppress("TOPLEVEL_TYPEALIASES_ONLY")
class JsonSecretHitlerGameList(
    storagePath: Path,
    persistService: ConfigPersistService,
) : SecretHitlerGameList {
    private typealias ValueType = PersistentMap<SecretHitlerGameId, SecretHitlerGameState>
    private typealias StorageType = Map<SecretHitlerGameId, GameStateDto>

    private object Strategy : StorageStrategy<ValueType> {
        override fun defaultValue(): ValueType {
            return persistentMapOf()
        }

        private fun ValueType.toStorage(): StorageType {
            return mapValues { (_, v) -> GameStateDto.from(v) }
        }

        private fun StorageType.toValue(): ValueType {
            return mapValues { (_, v) -> v.toGameState() }.toPersistentMap()
        }

        override fun encodeToString(value: ValueType): String {
            return Json.encodeToString<StorageType>(value.toStorage())
        }

        override fun decodeFromString(text: String): ValueType {
            return Json.decodeFromString<StorageType>(text).toValue()
        }
    }

    private val impl = AtomicCachedStorage(storagePath = storagePath, strategy = Strategy, persistService = persistService)

    override fun gameById(id: SecretHitlerGameId): SecretHitlerGameState? {
        return impl.getValue()[id]
    }

    private fun generateId(): SecretHitlerGameId {
        return SecretHitlerGameId(UUID.randomUUID().toString())
    }

    override fun createGame(state: SecretHitlerGameState): SecretHitlerGameId {
        var id = generateId()

        impl.updateValue {
            while (it.containsKey(id)) {
                id = generateId()
            }

            it.put(id, state)
        }

        return id
    }

    override fun removeGameIfExists(id: SecretHitlerGameId) {
        impl.updateValue {
            it.remove(id)
        }
    }

    override fun updateGame(id: SecretHitlerGameId, mapper: (SecretHitlerGameState) -> SecretHitlerGameState): Boolean {
        return impl.updateValueAndExtract {
            val old = it[id]

            if (old != null) {
                it.put(id, mapper(old)) to true
            } else {
                it to false
            }
        }
    }

    fun close() {
        impl.close()
    }
}
