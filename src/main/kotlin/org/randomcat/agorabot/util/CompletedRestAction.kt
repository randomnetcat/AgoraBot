package org.randomcat.agorabot.util

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.exceptions.RateLimitedException
import net.dv8tion.jda.api.requests.RestAction
import java.util.concurrent.CompletableFuture
import java.util.function.BooleanSupplier
import java.util.function.Consumer

/**
 * A [RestAction] that always returns the same value, provided in the constructor.
 *
 * Semantically equivalent to [net.dv8tion.jda.internal.requests.CompletedRestAction], which is not supported.
 */
class CompletedRestAction<T> private constructor(private val jda: JDA, private val state: State<T>) : RestAction<T> {
    private sealed class State<T> {
        abstract fun queue(success: Consumer<in T>?, failure: Consumer<in Throwable>?)
        abstract fun complete(): T
        abstract fun submit(): CompletableFuture<T>

        data class Success<T>(private val value: T) : State<T>() {
            override fun queue(success: Consumer<in T>?, failure: Consumer<in Throwable>?) {
                if (success != null) success.accept(value) else RestAction.getDefaultSuccess().accept(success)
            }

            override fun complete(): T {
                return value
            }

            override fun submit(): CompletableFuture<T> {
                return CompletableFuture.completedFuture(value)
            }
        }

        data class Failure<T>(private val error: Throwable) : State<T>() {
            override fun queue(success: Consumer<in T>?, failure: Consumer<in Throwable>?) {
                if (failure != null) failure.accept(error) else RestAction.getDefaultFailure().accept(failure)
            }

            override fun complete(): Nothing {
                // Have to interoperate with java code, so must be careful with exception handling.
                // This implementation is equivalent to the implementation in the current version of JDA
                // (as of writing).

                if (error is RateLimitedException || error is RuntimeException || error is Error) {
                    throw error
                }

                throw IllegalStateException(error)
            }

            override fun submit(): CompletableFuture<T> {
                return CompletableFuture.failedFuture(error)
            }
        }
    }

    companion object {
        fun <T> ofSuccess(jda: JDA, value: T): CompletedRestAction<T> {
            return CompletedRestAction(jda, State.Success(value))
        }

        fun <T> ofFailure(jda: JDA, error: Throwable): CompletedRestAction<T> {
            return CompletedRestAction(jda, State.Failure(error))
        }
    }

    override fun getJDA(): JDA {
        return jda
    }

    override fun setCheck(checks: BooleanSupplier?): RestAction<T> {
        // Nothing to do - no request will be sent. Sanctioned by CompletedRestAction in current JDA version.
        return this
    }

    override fun queue(success: Consumer<in T>?, failure: Consumer<in Throwable>?) {
        return state.queue(success = success, failure = failure)
    }

    override fun complete(shouldQueue: Boolean): T {
        return state.complete()
    }

    override fun submit(shouldQueue: Boolean): CompletableFuture<T> {
        return state.submit()
    }
}
