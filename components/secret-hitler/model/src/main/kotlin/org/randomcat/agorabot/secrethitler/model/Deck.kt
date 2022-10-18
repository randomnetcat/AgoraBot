package org.randomcat.agorabot.secrethitler.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import java.util.*

data class SecretHitlerDiscardDeckState(private val policies: ImmutableList<SecretHitlerPolicyType>) {
    constructor(policies: List<SecretHitlerPolicyType>) : this(policies.toImmutableList())

    companion object {
        private val EMPTY = SecretHitlerDiscardDeckState(persistentListOf())

        fun empty(): SecretHitlerDiscardDeckState {
            return EMPTY
        }
    }

    fun allPolicies(): List<SecretHitlerPolicyType> {
        return policies
    }

    fun afterDiscarding(policyType: SecretHitlerPolicyType): SecretHitlerDiscardDeckState {
        return SecretHitlerDiscardDeckState(policies.toPersistentList().add(policyType))
    }

    fun afterDiscardingAll(policyTypes: Collection<SecretHitlerPolicyType>): SecretHitlerDiscardDeckState {
        return SecretHitlerDiscardDeckState(policies.toPersistentList().addAll(policyTypes))
    }
}

data class SecretHitlerDrawDeckState(
    /**
     * The list of policies that wil be drawn, in the order they are to be drawn (i.e. index 0 will be drawn first,
     * then index 1, and so on).
     */
    private val policies: ImmutableList<SecretHitlerPolicyType>,
) {
    constructor(policies: List<SecretHitlerPolicyType>) : this(policies.toImmutableList())

    companion object {
        const val STANDARD_DRAW_AMOUNT = 3
    }

    /**
     * A result of a draw that does not check any constraints on the number of drawn cards.
     */
    private data class DrawAnyResult(
        val drawnCards: ImmutableList<SecretHitlerPolicyType>,
        val newDeck: SecretHitlerDrawDeckState,
        val newDeckNeedsShuffle: Boolean,
    ) {
        fun asStandard(): StandardDrawResult {
            return StandardDrawResult(
                drawnCards = drawnCards,
                newDeck = newDeck,
                newDeckNeedsShuffle = newDeckNeedsShuffle,
            )
        }

        fun asSingle(): SingleDrawResult {
            return SingleDrawResult(
                drawnCard = drawnCards.single(),
                newDeck = newDeck,
                newDeckNeedsShuffle = newDeckNeedsShuffle,
            )
        }
    }

    data class StandardDrawResult(
        val drawnCards: ImmutableList<SecretHitlerPolicyType>,
        val newDeck: SecretHitlerDrawDeckState,
        val newDeckNeedsShuffle: Boolean,
    ) {
        init {
            require(drawnCards.size == STANDARD_DRAW_AMOUNT)
        }
    }

    data class StandardPeekResult(
        val peekedCards: ImmutableList<SecretHitlerPolicyType>,
    ) {
        init {
            require(peekedCards.size == STANDARD_DRAW_AMOUNT)
        }
    }

    data class SingleDrawResult(
        val drawnCard: SecretHitlerPolicyType,
        val newDeck: SecretHitlerDrawDeckState,
        val newDeckNeedsShuffle: Boolean,
    )

    private fun drawAny(count: Int): DrawAnyResult {
        require(count > 0)
        check(policies.size >= count)

        val drawnCards = policies.subList(0, count)
        val remainingCards = policies.subList(count, policies.size)
        val newDeckNeedsShuffle = remainingCards.size < STANDARD_DRAW_AMOUNT


        return DrawAnyResult(
            drawnCards = drawnCards,
            newDeck = SecretHitlerDrawDeckState(remainingCards),
            newDeckNeedsShuffle = newDeckNeedsShuffle,
        )
    }

    fun drawStandard(): StandardDrawResult {
        return drawAny(count = STANDARD_DRAW_AMOUNT).asStandard()
    }

    fun peekStandard(): StandardPeekResult {
        return StandardPeekResult(policies.subList(0, STANDARD_DRAW_AMOUNT).toImmutableList())
    }

    fun drawSingle(): SingleDrawResult {
        return drawAny(count = 1).asSingle()
    }

    val policyCount: Int
        get() = policies.size

    fun allPolicies(): List<SecretHitlerPolicyType> {
        return policies
    }
}

data class SecretHitlerDeckState(
    val drawDeck: SecretHitlerDrawDeckState,
    val discardDeck: SecretHitlerDiscardDeckState,
) {
    interface ShuffleProvider {
        fun initialDeck(): SecretHitlerDrawDeckState

        fun shuffleDecks(
            remainingDrawPile: List<SecretHitlerPolicyType>,
            discardPile: List<SecretHitlerPolicyType>,
        ): SecretHitlerDrawDeckState
    }

    companion object {
        const val TOTAL_FASCIST_COUNT = 11
        const val TOTAL_LIBERAL_COUNT = 6
    }

    data class RandomShuffleProvider(private val rng: () -> Random) : ShuffleProvider {
        override fun initialDeck(): SecretHitlerDrawDeckState {
            return SecretHitlerDrawDeckState(buildList {
                repeat(TOTAL_FASCIST_COUNT) { add(SecretHitlerPolicyType.FASCIST) }
                repeat(TOTAL_LIBERAL_COUNT) { add(SecretHitlerPolicyType.LIBERAL) }
                shuffle(rng())
            })
        }

        override fun shuffleDecks(
            remainingDrawPile: List<SecretHitlerPolicyType>,
            discardPile: List<SecretHitlerPolicyType>,
        ): SecretHitlerDrawDeckState {
            return SecretHitlerDrawDeckState(buildList {
                addAll(remainingDrawPile)
                addAll(discardPile)
                shuffle(rng())
            })
        }
    }

    fun shuffledIfDrawPileSmall(shuffleProvider: ShuffleProvider): SecretHitlerDeckState {
        return if (drawDeck.policyCount >= SecretHitlerDrawDeckState.STANDARD_DRAW_AMOUNT) {
            this
        } else {
            SecretHitlerDeckState(
                drawDeck = shuffleProvider.shuffleDecks(
                    remainingDrawPile = drawDeck.allPolicies(),
                    discardPile = discardDeck.allPolicies(),
                ),
                discardDeck = SecretHitlerDiscardDeckState.empty(),
            )
        }
    }
}
