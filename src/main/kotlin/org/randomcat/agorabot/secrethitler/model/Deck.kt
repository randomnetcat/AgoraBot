package org.randomcat.agorabot.secrethitler.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

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

    fun allPolicies(): List<SecretHitlerPolicyType> {
        return policies
    }
}
