package org.randomcat.agorabot.secrethitler.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList

data class SecretHitlerGameConfiguration(
    val liberalWinRequirement: Int,
    private val fascistPowers: ImmutableList<SecretHitlerFascistPower?>,
    val hitlerChancellorWinRequirement: Int,
    val vetoUnlockRequirement: Int,
) {
    val fascistWinRequirement: Int = fascistPowers.size + 1

    fun fascistPowerAt(fascistPoliciesEnacted: Int): SecretHitlerFascistPower? {
        return fascistPowers[fascistPoliciesEnacted - 1]
    }
}


data class SecretHitlerRoleConfiguration(
    val liberalCount: Int,
    val plainFascistCount: Int,
    val hitlerKnowsFascists: Boolean,
) {
    init {
        require(liberalCount > 0)
        require(plainFascistCount > 0)
    }

    // Liberals + Plain Fascists + 1 Hitler
    val totalRoles
        get() = liberalCount + plainFascistCount + 1
}

data class SecretHitlerStartConfiguration(
    val roleConfiguration: SecretHitlerRoleConfiguration,
    val gameConfiguration: SecretHitlerGameConfiguration,
)


private val POWERS_5_OR_6 = persistentListOf(
    null,
    null,
    SecretHitlerFascistPower.EXAMINE_CARDS,
    SecretHitlerFascistPower.EXECUTE_PLAYER,
    SecretHitlerFascistPower.EXECUTE_PLAYER,
)

private val POWERS_7_OR_MORE = persistentListOf(
    null,
    SecretHitlerFascistPower.INVESTIGATE_PARTY,
    SecretHitlerFascistPower.SPECIAL_ELECTION,
    SecretHitlerFascistPower.EXECUTE_PLAYER,
    SecretHitlerFascistPower.EXECUTE_PLAYER,
)

private const val LIBERAL_WIN_REQUIREMENT = 5
private const val HITLER_CHANCELLOR_WIN_REQUIREMENT = 3
private const val VETO_REQUIREMENT = 5

private fun makeStandardGameConfiguration(
    fascistPowers: List<SecretHitlerFascistPower?>,
): SecretHitlerGameConfiguration {
    return SecretHitlerGameConfiguration(
        liberalWinRequirement = LIBERAL_WIN_REQUIREMENT,
        fascistPowers = fascistPowers.toImmutableList(),
        hitlerChancellorWinRequirement = HITLER_CHANCELLOR_WIN_REQUIREMENT,
        vetoUnlockRequirement = VETO_REQUIREMENT,
    )
}

private val GAME_CONFIG_MAP = persistentMapOf(
    5 to SecretHitlerStartConfiguration(
        SecretHitlerRoleConfiguration(
            liberalCount = 3,
            plainFascistCount = 1,
            hitlerKnowsFascists = true,
        ),
        makeStandardGameConfiguration(fascistPowers = POWERS_5_OR_6),
    ),
    6 to SecretHitlerStartConfiguration(
        SecretHitlerRoleConfiguration(
            liberalCount = 4,
            plainFascistCount = 1,
            hitlerKnowsFascists = true,
        ),
        makeStandardGameConfiguration(fascistPowers = POWERS_5_OR_6),
    ),
    7 to SecretHitlerStartConfiguration(
        SecretHitlerRoleConfiguration(
            liberalCount = 4,
            plainFascistCount = 2,
            hitlerKnowsFascists = false,
        ),
        makeStandardGameConfiguration(fascistPowers = POWERS_7_OR_MORE),
    ),
    8 to SecretHitlerStartConfiguration(
        SecretHitlerRoleConfiguration(
            liberalCount = 5,
            plainFascistCount = 2,
            hitlerKnowsFascists = false,
        ),
        makeStandardGameConfiguration(fascistPowers = POWERS_7_OR_MORE),
    ),
    9 to SecretHitlerStartConfiguration(
        SecretHitlerRoleConfiguration(
            liberalCount = 5,
            plainFascistCount = 3,
            hitlerKnowsFascists = false,
        ),
        makeStandardGameConfiguration(fascistPowers = POWERS_7_OR_MORE),
    ),
    10 to SecretHitlerStartConfiguration(
        SecretHitlerRoleConfiguration(
            liberalCount = 6,
            plainFascistCount = 3,
            hitlerKnowsFascists = false,
        ),
        makeStandardGameConfiguration(fascistPowers = POWERS_7_OR_MORE),
    ),
)

const val SECRET_HITLER_MIN_START_PLAYERS = 5
const val SECRET_HITLER_MAX_START_PLAYERS = 10

fun SecretHitlerGameState.Joining.startConfiguration(): SecretHitlerStartConfiguration {
    require(playerNames.size >= SECRET_HITLER_MIN_START_PLAYERS)
    require(playerNames.size <= SECRET_HITLER_MAX_START_PLAYERS)

    return GAME_CONFIG_MAP.getValue(playerNames.size).also {
        check(it.roleConfiguration.totalRoles == playerNames.size)
    }
}
