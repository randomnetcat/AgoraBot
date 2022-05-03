package org.randomcat.agorabot

import org.randomcat.agorabot.setup.BotDataPaths

interface FeatureContext {
    /**
     * Inserts or retrieves from the cache, using the provided object as a cache key. If an object equal to the key
     * already exists in the cache, it is returned (no type verification is performed). Otherwise, [producer] is invoked
     * to produce a new value, which is stored in the cache and returned.
     *
     * Cache accesses are thread-safe. Recursive accesses to the cache are permitted.
     */
    fun <T> cache(cacheKey: Any, producer: () -> T): T

    /**
     * Runs a query on all available features (including the one calling this). Returns a map from feature names to the
     * successful query results (features which do not return a result are not included).
     *
     * Care must be taken to ensure queries are not recursive.
     *
     * Exceptions in queries are propagated to the caller.
     */
    fun <T> queryAll(tag: FeatureElementTag<T>): Map<String, T>

    /**
     * Attempts to run a query on all available features (including the one calling this). Returns a map from feature
     * names to the query results.
     *
     * Care must be taken to ensure queries are not recursive.
     *
     * Exceptions in queries are caught and returned as failed Results.
     */
    fun <T> tryQueryAll(tag: FeatureElementTag<T>): Map<String, Result<FeatureQueryResult<T>>>

    /**
     * Registers a function to be run in order to close a resource.
     * Closing functions will be run in reverse order of addition.
     */
    fun onClose(block: () -> Unit)
}

inline fun <T : Any> FeatureContext.alwaysCloseObject(producer: () -> T, crossinline closer: (T) -> Unit): T {
    var obj: T? = null

    try {
        obj = producer()
        onClose { closer(obj) }
        return obj
    } catch (e: Exception) {
        obj?.let(closer)
        throw e
    }
}

/**
 * Runs a query on all available features. If a single query is successful, returns its value. Otherwise, throws an
 * Exception.
 *
 * Exceptions thrown by queries are ignored, and such queries are considered unsuccessful.
 */
fun <T> FeatureContext.queryExpectOne(tag: FeatureElementTag<T>): T {
    val allResults = tryQueryAll(tag)

    val successResults =
        allResults
            .values
            .filter { it.isSuccess }
            .map { it.getOrThrow() }
            .filterIsInstance<FeatureQueryResult.Found<T>>()

    if (successResults.size == 1) return successResults.single().value

    if (successResults.isEmpty()) {
        throw IllegalArgumentException("Unable to find feature element with tag $tag").also { e ->
            allResults.values.filter { it.isFailure }.forEach {
                e.addSuppressed(it.exceptionOrNull() ?: error("Should have exception"))
            }
        }
    } else {
        throw IllegalArgumentException("Found multiple feature elements with tag $tag: $successResults")
    }
}

fun <T> FeatureContext.tryQueryExpectOne(tag: FeatureElementTag<T>): FeatureQueryResult<T> {
    val results = tryQueryAll(tag)
        .values
        .filter { it.isSuccess }
        .map { it.getOrThrow() }
        .filterIsInstance<FeatureQueryResult.Found<T>>()

    return if (results.size == 1) results.single() else FeatureQueryResult.NotFound
}

interface FeatureElementTag<T>

@Suppress("unused", "UNCHECKED_CAST")
fun <To, From> FeatureElementTag<From>.result(value: From): FeatureQueryResult<To> {
    // Deliberately unchecked cast. This verifies the input type while casting to the unknown return type of the query
    // function.
    return FeatureQueryResult.Found(value) as FeatureQueryResult<To>
}

sealed class FeatureQueryResult<out T> {
    data class Found<T>(val value: T) : FeatureQueryResult<T>()
    object NotFound : FeatureQueryResult<Nothing>()
}

inline fun <R, T : R> FeatureQueryResult<T>.valueOrElse(block: () -> R): R {
    return when (this) {
        is FeatureQueryResult.Found -> value
        is FeatureQueryResult.NotFound -> block()
    }
}

fun <T> FeatureQueryResult<T>.valueOrNull(): T? {
    return valueOrElse { null }
}

data class FeatureSetupContext(
    val paths: BotDataPaths,
)

interface FeatureSource {
    companion object {
        fun ofConstant(name: String, feature: Feature): FeatureSource {
            return object : FeatureSource {
                override val featureName: String
                    get() = name

                override fun readConfig(context: FeatureSetupContext): Any? {
                    return Unit
                }

                override fun createFeature(config: Any?): Feature {
                    return feature
                }
            }
        }
    }

    val featureName: String

    // The config object is returned for introspection (logging), but must be passed exactly into createFeature.
    fun readConfig(context: FeatureSetupContext): Any?
    fun createFeature(config: Any?): Feature
}

@Retention(AnnotationRetention.RUNTIME)
annotation class FeatureSourceFactory(val enable: Boolean = true)

interface Feature {
    fun <T> query(context: FeatureContext, tag: FeatureElementTag<T>): FeatureQueryResult<T>

    companion object
}
