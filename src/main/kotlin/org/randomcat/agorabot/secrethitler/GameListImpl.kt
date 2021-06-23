package org.randomcat.agorabot.secrethitler

import kotlinx.collections.immutable.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.randomcat.agorabot.config.AtomicCachedStorage
import org.randomcat.agorabot.config.ConfigPersistService
import org.randomcat.agorabot.config.StorageStrategy
import org.randomcat.agorabot.config.updateValueAndExtract
import org.randomcat.agorabot.secrethitler.JsonSecretHitlerGameList.StorageType
import org.randomcat.agorabot.secrethitler.JsonSecretHitlerGameList.ValueType
import org.randomcat.agorabot.secrethitler.model.*
import java.nio.file.Path
import java.util.*

@Serializable
private data class GameConfigurationDto(
    val liberalWinRequirement: Int,
    val fascistPowers: List<SecretHitlerFascistPower?>,
    val hitlerChancellorWinRequirement: Int,
    val vetoUnlockRequirement: Int,
) {
    companion object {
        fun from(configuration: SecretHitlerGameConfiguration): GameConfigurationDto {
            return GameConfigurationDto(
                liberalWinRequirement = configuration.liberalWinRequirement,
                fascistPowers = (1 until configuration.fascistWinRequirement).map { configuration.fascistPowerAt(it) },
                hitlerChancellorWinRequirement = configuration.hitlerChancellorWinRequirement,
                vetoUnlockRequirement = configuration.vetoUnlockRequirement,
            )
        }
    }

    fun toConfiguration(): SecretHitlerGameConfiguration {
        return SecretHitlerGameConfiguration(
            liberalWinRequirement = liberalWinRequirement,
            fascistPowers = fascistPowers.toImmutableList(),
            hitlerChancellorWinRequirement = hitlerChancellorWinRequirement,
            vetoUnlockRequirement = vetoUnlockRequirement,
        )
    }
}

@Serializable
private data class PlayerMapDto(
    val map: Map<SecretHitlerPlayerNumber, SecretHitlerPlayerExternalName>,
) {
    companion object {
        fun from(playerMap: SecretHitlerPlayerMap): PlayerMapDto {
            return PlayerMapDto(playerMap.toMap())
        }
    }

    fun toPlayerMap(): SecretHitlerPlayerMap {
        return SecretHitlerPlayerMap(map)
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
private data class RoleMapDto(
    val map: Map<SecretHitlerPlayerNumber, RoleDto>,
) {
    companion object {
        fun from(roleMap: SecretHitlerRoleMap): RoleMapDto {
            return RoleMapDto(roleMap.toMap().mapValues { (_, v) -> RoleDto.from(v) })
        }
    }

    fun toRoleMap(): SecretHitlerRoleMap {
        return SecretHitlerRoleMap(map.mapValues { (_, v) -> v.toRole() })
    }
}

@Serializable
private data class DeckStateDto(
    val policies: List<SecretHitlerPolicyType>,
) {
    companion object {
        fun from(deckState: SecretHitlerDeckState): DeckStateDto {
            return DeckStateDto(deckState.allPolicies())
        }
    }

    fun toDeckState(): SecretHitlerDeckState {
        return SecretHitlerDeckState(policies = policies)
    }
}

@Serializable
private data class PoliciesStateDto(
    val liberalPoliciesEnacted: Int,
    val fascistPoliciesEnacted: Int,
) {
    companion object {
        fun from(policiesState: SecretHitlerPoliciesState): PoliciesStateDto {
            return PoliciesStateDto(
                liberalPoliciesEnacted = policiesState.liberalPoliciesEnacted,
                fascistPoliciesEnacted = policiesState.fascistPoliciesEnacted,
            )
        }
    }

    fun toPoliciesState(): SecretHitlerPoliciesState {
        return SecretHitlerPoliciesState(
            liberalPoliciesEnacted = liberalPoliciesEnacted,
            fascistPoliciesEnacted = fascistPoliciesEnacted,
        )
    }
}

@Serializable
private data class ElectionStateDto(
    val currentPresidentTicker: SecretHitlerPlayerNumber,
    val termLimitedPlayers: GovernmentMembersDto?,
) {
    companion object {
        fun from(electionState: SecretHitlerElectionState): ElectionStateDto {
            return ElectionStateDto(
                currentPresidentTicker = electionState.currentPresidentTicker,
                termLimitedPlayers = electionState
                    .termLimitState
                    .termLimitedGovernment
                    ?.let { GovernmentMembersDto.from(it) },
            )
        }
    }

    fun toElectionState(): SecretHitlerElectionState {
        return SecretHitlerElectionState(
            currentPresidentTicker = currentPresidentTicker,
            termLimitState = SecretHitlerTermLimitState(termLimitedPlayers?.toGovernmentMembers()),
        )
    }
}

@Serializable
private data class GlobalStateDto(
    val configuration: GameConfigurationDto,
    val playerMap: PlayerMapDto,
    val roleMap: RoleMapDto,
    val deckState: DeckStateDto,
    val policiesState: PoliciesStateDto,
    val electionState: ElectionStateDto,
) {
    companion object {
        fun from(globalGameState: SecretHitlerGlobalGameState): GlobalStateDto {
            return GlobalStateDto(
                configuration = GameConfigurationDto.from(globalGameState.configuration),
                playerMap = PlayerMapDto.from(globalGameState.playerMap),
                roleMap = RoleMapDto.from(globalGameState.roleMap),
                deckState = DeckStateDto.from(globalGameState.boardState.deckState),
                policiesState = PoliciesStateDto.from(globalGameState.boardState.policiesState),
                electionState = ElectionStateDto.from(globalGameState.electionState),
            )
        }
    }

    fun toGlobalState(): SecretHitlerGlobalGameState {
        return SecretHitlerGlobalGameState(
            configuration = configuration.toConfiguration(),
            playerMap = playerMap.toPlayerMap(),
            roleMap = roleMap.toRoleMap(),
            boardState = SecretHitlerBoardState(
                deckState = deckState.toDeckState(),
                policiesState = policiesState.toPoliciesState(),
            ),
            electionState = electionState.toElectionState(),
        )
    }
}

@Serializable
private data class GovernmentMembersDto(
    val president: SecretHitlerPlayerNumber,
    val chancellor: SecretHitlerPlayerNumber,
) {
    companion object {
        fun from(governmentMembers: SecretHitlerGovernmentMembers): GovernmentMembersDto {
            return GovernmentMembersDto(
                president = governmentMembers.president,
                chancellor = governmentMembers.chancellor,
            )
        }
    }

    fun toGovernmentMembers(): SecretHitlerGovernmentMembers {
        return SecretHitlerGovernmentMembers(
            president = president,
            chancellor = chancellor,
        )
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
            }
        }
    }

    abstract fun toEphemeralState(): SecretHitlerEphemeralState

    @Serializable
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
    data class VotingOngoing(val governmentMembers: GovernmentMembersDto) : EphemeralStateDto() {
        companion object {
            fun from(state: SecretHitlerEphemeralState.VotingOngoing): VotingOngoing {
                return VotingOngoing(governmentMembers = GovernmentMembersDto.from(state.governmentMembers))
            }
        }

        override fun toEphemeralState(): SecretHitlerEphemeralState {
            return SecretHitlerEphemeralState.VotingOngoing(governmentMembers = governmentMembers.toGovernmentMembers())
        }
    }

    @Serializable
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

}

@Serializable
private sealed class GameStateDto {
    companion object {
        fun from(gameState: SecretHitlerGameState): GameStateDto {
            return when (gameState) {
                is SecretHitlerGameState.Joining -> {
                    GameStateDto.Joining(names = gameState.playerNames.toList())
                }

                is SecretHitlerGameState.Running -> {
                    GameStateDto.Running.from(gameState)
                }
            }
        }
    }

    abstract fun toGameState(): SecretHitlerGameState

    @Serializable
    data class Joining(val names: List<SecretHitlerPlayerExternalName>) : GameStateDto() {
        override fun toGameState(): SecretHitlerGameState {
            return SecretHitlerGameState.Joining(playerNames = names.toImmutableSet())
        }
    }

    @Serializable
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
}


// It'll be fiiiiineee....
@Suppress("TOPLEVEL_TYPEALIASES_ONLY")
class JsonSecretHitlerGameList(storagePath: Path) : SecretHitlerGameList {
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

    private val impl = AtomicCachedStorage(storagePath = storagePath, strategy = Strategy)

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

    fun schedulePersistenceOn(persistService: ConfigPersistService) {
        impl.schedulePersistenceOn(persistService)
    }
}
