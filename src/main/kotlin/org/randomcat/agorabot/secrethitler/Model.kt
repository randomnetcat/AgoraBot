package org.randomcat.agorabot.secrethitler

import kotlinx.collections.immutable.*
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class SecretHitlerGameId(val raw: String)

@JvmInline
value class SecretHitlerPlayerNumber(val raw: Int)

@JvmInline
value class SecretHitlerPlayerExternalName(val raw: String)

data class SecretHitlerPlayerMap(
    private val players: ImmutableList<SecretHitlerPlayerExternalName>,
) {
    constructor(players: List<SecretHitlerPlayerExternalName>) : this(players.toImmutableList())

    fun playerByNumber(number: SecretHitlerPlayerNumber): SecretHitlerPlayerExternalName {
        return players[number.raw]
    }

    fun numberByPlayer(playerName: SecretHitlerPlayerExternalName): SecretHitlerPlayerNumber {
        return SecretHitlerPlayerNumber(players.indexOf(playerName))
    }

    val validNumbers: Set<SecretHitlerPlayerNumber> = players.indices.map { SecretHitlerPlayerNumber(it) }.toSet()
}

enum class SecretHitlerParty {
    FASCIST,
    LIBERAL,
}

sealed class SecretHitlerRole(val party: SecretHitlerParty) {
    object Liberal : SecretHitlerRole(SecretHitlerParty.LIBERAL)

    sealed class FascistParty : SecretHitlerRole(SecretHitlerParty.FASCIST)
    object PlainFascist : FascistParty()
    object Hitler : FascistParty()
}

data class SecretHitlerRoleMap(
    private val rolesByPlayer: ImmutableMap<SecretHitlerPlayerNumber, SecretHitlerRole>,
) {
    constructor(rolesByPlayer: Map<SecretHitlerPlayerNumber, SecretHitlerRole>) : this(rolesByPlayer.toImmutableMap())

    init {
        val hitlerPlayers = rolesByPlayer.filter { it.value is SecretHitlerRole.Hitler }.map { it.key }

        require(hitlerPlayers.count() == 1) {
            "There should be exactly one Hitler but actually got: $hitlerPlayers"
        }
    }

    val assignedPlayers: Set<SecretHitlerPlayerNumber> = rolesByPlayer.keys

    fun roleOf(playerNumber: SecretHitlerPlayerNumber): SecretHitlerRole {
        return rolesByPlayer.getValue(playerNumber)
    }

    private inline fun <reified Role : SecretHitlerRole> playersWithRole(): Set<SecretHitlerPlayerNumber> {
        return rolesByPlayer.filter { it.value is Role }.keys
    }

    val liberalPlayers: Set<SecretHitlerPlayerNumber> = playersWithRole<SecretHitlerRole.Liberal>()
    val allFascistPlayers: Set<SecretHitlerPlayerNumber> = playersWithRole<SecretHitlerRole.FascistParty>()
    val plainFascistPlayers: Set<SecretHitlerPlayerNumber> = playersWithRole<SecretHitlerRole.PlainFascist>()

    val hitlerPlayer: SecretHitlerPlayerNumber = playersWithRole<SecretHitlerRole.Hitler>().single()
}

fun SecretHitlerRoleMap.playerIsLiberal(player: SecretHitlerPlayerNumber) = liberalPlayers.contains(player)
fun SecretHitlerRoleMap.playerIsPlainFascist(player: SecretHitlerPlayerNumber) = plainFascistPlayers.contains(player)
fun SecretHitlerRoleMap.playerIsAnyFascist(player: SecretHitlerPlayerNumber) = allFascistPlayers.contains(player)
fun SecretHitlerRoleMap.playerIsHitler(player: SecretHitlerPlayerNumber) = hitlerPlayer == player

enum class SecretHitlerPolicyType {
    FASCIST,
    LIBERAL,
}

data class SecretHitlerDeckState(private val policies: ImmutableList<SecretHitlerPolicyType>) {
    constructor(policies: List<SecretHitlerPolicyType>) : this(policies.toImmutableList())

    init {
        require(policies.size >= STANDARD_DRAW_AMOUNT)
    }

    interface ShuffleProvider {
        fun newDeck(): SecretHitlerDeckState
    }

    object RandomShuffleProvider : ShuffleProvider {
        override fun newDeck(): SecretHitlerDeckState {
            return SecretHitlerDeckState.random()
        }
    }

    companion object {
        private const val FASCIST_COUNT = 11
        private const val LIBERAL_COUNT = 6

        fun random(): SecretHitlerDeckState {
            return SecretHitlerDeckState(
                @OptIn(ExperimentalStdlibApi::class)
                buildList {
                    repeat(FASCIST_COUNT) { add(SecretHitlerPolicyType.FASCIST) }
                    repeat(LIBERAL_COUNT) { add(SecretHitlerPolicyType.LIBERAL) }
                }.shuffled(),
            )
        }

        const val STANDARD_DRAW_AMOUNT = 3
    }

    private data class DrawAnyResult(
        val drawnCards: ImmutableList<SecretHitlerPolicyType>,
        val newDeck: SecretHitlerDeckState,
        val shuffled: Boolean,
    ) {
        fun asStandard(): StandardDrawResult {
            return StandardDrawResult(
                drawnCards = drawnCards,
                newDeck = newDeck,
                shuffled = shuffled,
            )
        }

        fun asSingle(): SingleDrawResult {
            return SingleDrawResult(
                drawnCard = drawnCards.single(),
                newDeck = newDeck,
                shuffled = shuffled,
            )
        }
    }

    data class StandardDrawResult(
        val drawnCards: ImmutableList<SecretHitlerPolicyType>,
        val newDeck: SecretHitlerDeckState,
        val shuffled: Boolean,
    ) {
        init {
            require(drawnCards.size == STANDARD_DRAW_AMOUNT)
        }
    }

    data class SingleDrawResult(
        val drawnCard: SecretHitlerPolicyType,
        val newDeck: SecretHitlerDeckState,
        val shuffled: Boolean,
    )

    private fun drawAny(count: Int, shuffleProvider: ShuffleProvider): DrawAnyResult {
        require(count > 0)
        check(policies.size >= count)

        val drawnCards = policies.subList(0, count)
        val remainingSize = policies.size - count

        return if (remainingSize < STANDARD_DRAW_AMOUNT) {
            DrawAnyResult(
                drawnCards = drawnCards,
                newDeck = shuffleProvider.newDeck(),
                shuffled = true,
            )
        } else {
            val remainingCards = policies.subList(count, policies.size)

            DrawAnyResult(
                drawnCards = drawnCards,
                newDeck = SecretHitlerDeckState(remainingCards),
                shuffled = false,
            )
        }
    }

    fun drawStandard(shuffleProvider: ShuffleProvider): StandardDrawResult {
        return drawAny(count = STANDARD_DRAW_AMOUNT, shuffleProvider).asStandard()
    }

    fun drawSingle(shuffleProvider: ShuffleProvider): SingleDrawResult {
        return drawAny(count = 1, shuffleProvider).asSingle()
    }
}

enum class SecretHitlerFascistPower {
    EXAMINE_CARDS,
    INVESTIGATE_PARTY,
    SPECIAL_ELECTION,
    EXECUTE_PLAYER,
}

interface SecretHitlerGameConfiguration {
    val liberalWinRequirement: Int
    val fascistWinRequirement: Int

    val hitlerChancellorWinRequirement: Int
    val vetoUnlockRequirement: Int

    fun fascistPowerAt(fascistPoliciesEnacted: Int): SecretHitlerFascistPower?
}

sealed class SecretHitlerPoliciesEnactmentResult {
    sealed class ImmediateWin : SecretHitlerPoliciesEnactmentResult() {
        object LiberalWin : ImmediateWin()
        object FascistWin : ImmediateWin()
    }

    data class GameContinues(
        val newPolicyState: SecretHitlerPoliciesState,
        val fascistPower: SecretHitlerFascistPower?,
    ) : SecretHitlerPoliciesEnactmentResult()
}

data class SecretHitlerPoliciesState(
    val liberalPoliciesEnacted: Int,
    val fascistPoliciesEnacted: Int,
) {
    constructor() : this(0, 0)

    init {
        require(liberalPoliciesEnacted >= 0)
        require(fascistPoliciesEnacted >= 0)

        // Prevent overflow
        require(liberalPoliciesEnacted < Int.MAX_VALUE)
        require(fascistPoliciesEnacted < Int.MAX_VALUE)
    }

    fun enactFascistPolicy(config: SecretHitlerGameConfiguration): SecretHitlerPoliciesEnactmentResult {
        val newFascistPolicyCount = fascistPoliciesEnacted + 1

        if (newFascistPolicyCount >= config.fascistWinRequirement) {
            return SecretHitlerPoliciesEnactmentResult.ImmediateWin.FascistWin
        }

        return SecretHitlerPoliciesEnactmentResult.GameContinues(
            newPolicyState = this.copy(fascistPoliciesEnacted = newFascistPolicyCount),
            fascistPower = config.fascistPowerAt(newFascistPolicyCount),
        )
    }

    fun enactLiberalPolicy(config: SecretHitlerGameConfiguration): SecretHitlerPoliciesEnactmentResult {
        val newLiberalPolicyCount = liberalPoliciesEnacted + 1

        if (newLiberalPolicyCount >= config.liberalWinRequirement) {
            return SecretHitlerPoliciesEnactmentResult.ImmediateWin.LiberalWin
        }

        return SecretHitlerPoliciesEnactmentResult.GameContinues(
            newPolicyState = this.copy(liberalPoliciesEnacted = newLiberalPolicyCount),
            fascistPower = null,
        )
    }
}

data class SecretHitlerBoardState(
    val deckState: SecretHitlerDeckState,
    val policiesState: SecretHitlerPoliciesState,
)

sealed class SecretHitlerGameState {
    data class Joining(val playerNames: ImmutableSet<SecretHitlerPlayerExternalName>) : SecretHitlerGameState() {
        constructor() : this(persistentSetOf())

        fun withNewPlayer(player: SecretHitlerPlayerExternalName): SecretHitlerGameState.Joining {
            return SecretHitlerGameState.Joining(playerNames = playerNames.toPersistentSet().add(player))
        }

        fun withoutPlayer(player: SecretHitlerPlayerExternalName): SecretHitlerGameState.Joining {
            return SecretHitlerGameState.Joining(playerNames = playerNames.toPersistentSet().remove(player))
        }
    }

    data class Running(
        val configuration: SecretHitlerGameConfiguration,
        val playerMap: SecretHitlerPlayerMap,
        val roleMap: SecretHitlerRoleMap,
        val boardState: SecretHitlerBoardState,
    ) {
        init {
            require(playerMap.validNumbers == roleMap.assignedPlayers) {
                "All player numbers should have exactly one role. " +
                        "Players: ${playerMap.validNumbers}. Assigned roles: ${roleMap.assignedPlayers}"
            }
        }
    }
}
