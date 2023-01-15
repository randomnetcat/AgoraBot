package org.randomcat.agorabot

import org.randomcat.agorabot.setup.BotDataPaths

interface FeatureElementTag<T>

sealed class FeatureDependency<T> {
    abstract val tag: FeatureElementTag<T>

    data class Single<T>(override val tag: FeatureElementTag<T>) : FeatureDependency<T>()
    data class All<T>(override val tag: FeatureElementTag<T>) : FeatureDependency<T>()
    data class AtMostOne<T : Any>(override val tag: FeatureElementTag<T>) : FeatureDependency<T>()
}

@Suppress("unused", "UNCHECKED_CAST")
fun <To, From> FeatureElementTag<From>.values(vararg values: From): List<To> {
    // Deliberately unchecked cast. This verifies the input type while casting to the unknown return type of the query
    // function.
    return values.toList() as List<To>
}

/**
 * Thrown when a [Feature] receives a request for a tag it does not know about.
 */
class InvalidTagException(val tag: FeatureElementTag<*>) : Exception("Invalid request for feature tag: $tag")

fun invalidTag(tag: FeatureElementTag<*>): Nothing {
    throw InvalidTagException(tag)
}

data class FeatureSetupContext(
    val paths: BotDataPaths,
)

interface FeatureSourceContext {
    operator fun <T> get(dependency: FeatureDependency.Single<T>): T
    operator fun <T> get(dependency: FeatureDependency.All<T>): List<T>
    operator fun <T : Any> get(dependency: FeatureDependency.AtMostOne<T>): T?
}

interface FeatureSource<Config> {
    companion object

    val featureName: String

    // The config object is returned for introspection (logging), but must be passed exactly into createFeature.
    fun readConfig(context: FeatureSetupContext): Config
    fun createFeature(config: Config, context: FeatureSourceContext): Feature

    val dependencies: List<FeatureDependency<*>>
    val provides: List<FeatureElementTag<*>>

    interface NoConfig : FeatureSource<Unit> {
        override fun readConfig(context: FeatureSetupContext) {
            return Unit
        }

        override fun createFeature(config: Unit, context: FeatureSourceContext): Feature {
            return createFeature(context)
        }

        fun createFeature(context: FeatureSourceContext): Feature
    }
}

@Retention(AnnotationRetention.RUNTIME)
annotation class FeatureSourceFactory(val enable: Boolean = true)

interface Feature {
    /**
     * Returns zero or more values corresponding to the appropriate tag.
     * @throws InvalidTagException if the tag is unknown
     */
    fun <T> query(tag: FeatureElementTag<T>): List<T>

    /**
     * Performs any closing actions required.
     */
    fun close() {}

    companion object {
        /**
         * Returns a [Feature] that provides [values] for [tag] and handles no other tags.
         */
        fun <Value> singleTag(tag: FeatureElementTag<Value>, vararg values: Value): Feature {
            val usedTag = tag

            return object : Feature {
                override fun <T> query(tag: FeatureElementTag<T>): List<T> {
                    @Suppress("UNCHECKED_CAST")
                    if (tag == usedTag) return (tag as FeatureElementTag<Value>).values(*values)

                    invalidTag(tag)
                }
            }
        }
    }
}
