package org.randomcat.agorabot

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.slf4j.LoggerFactory
import org.slf4j.MDC

private val logger = LoggerFactory.getLogger("FeatureGraph")

class FeatureInitException(
    source: FeatureSource<*>,
    cause: Exception,
) : Exception("Error while initializing $source: ${cause.message}", cause)

private class FeatureInitState(private val setupContext: FeatureSetupContext) {
    private val errorsBySource = mutableMapOf<FeatureSource<*>, Exception>()
    private val featuresBySource = mutableMapOf<FeatureSource<*>, Feature>()
    private val initOrder = mutableListOf<Feature>()

    private var isClosed = false

    private fun throwInitError(source: FeatureSource<*>): Nothing {
        check(errorsBySource.containsKey(source))

        throw FeatureInitException(
            source = source,
            cause = errorsBySource.getValue(source),
        )
    }

    fun <Config> initFromSource(source: FeatureSource<Config>, context: () -> FeatureSourceContext): Feature {
        check(!isClosed)

        if (errorsBySource.containsKey(source)) throwInitError(source)
        if (featuresBySource.containsKey(source)) return featuresBySource.getValue(source)

        try {
            val config = source.readConfig(setupContext)
            val feature = source.createFeature(config, context())

            featuresBySource[source] = feature
            initOrder.add(feature)

            return feature
        } catch (e: Exception) {
            errorsBySource[source] = e

            logger.error("Error initializing feature ${source.featureName}", e)
            throwInitError(source)
        }
    }

    fun closeFeatures() {
        check(!isClosed)

        isClosed = true
        initOrder.asReversed().forEach { it.close() }
    }
}

class FeatureRecursiveDependencyException(
    val backtrace: ImmutableList<FeatureElementTag<*>>,
) : Exception("Found recursive dependency with trace: $backtrace")

private class ElementValueMap {
    private val data = mutableMapOf<FeatureElementTag<*>, ImmutableList<Any?>>()

    fun contains(tag: FeatureElementTag<*>): Boolean = data.containsKey(tag)

    fun <T> add(tag: FeatureElementTag<T>, values: List<T>) {
        check(!data.containsKey(tag)) {
            "Cannot reinitialize tag $tag"
        }

        data[tag] = values.toImmutableList()
    }

    fun <T> get(tag: FeatureElementTag<T>): List<T> {
        check(data.containsKey(tag)) {
            "Attempt to access uninitialized tag $tag"
        }

        // Typing is enforced in add
        @Suppress("UNCHECKED_CAST")
        return data.getValue(tag) as List<T>
    }
}

class FeatureElementInitState(
    setupContext: FeatureSetupContext,
    sources: List<FeatureSource<*>>,
) {
    private val sources = sources.toImmutableList()

    private val featureState = FeatureInitState(setupContext)
    private val elementValueMap = ElementValueMap()

    private var isClosed = false

    private fun dependencyIsSatisfied(dependency: FeatureDependency<*>): Boolean {
        check(elementValueMap.contains(dependency.tag))

        return when (dependency) {
            is FeatureDependency.All -> true
            is FeatureDependency.AtMostOne -> elementValueMap.get(dependency.tag).size <= 1
            is FeatureDependency.Single -> elementValueMap.get(dependency.tag).size == 1
        }
    }

    private fun contextForDependencies(dependencies: List<FeatureDependency<*>>): FeatureSourceContext {
        val safeDependencies = dependencies.toImmutableList()

        fun checkValid(dependency: FeatureDependency<*>) {
            if (!safeDependencies.contains(dependency)) {
                throw IllegalArgumentException("Invalid request for undeclared dependency $dependency")
            }
        }

        return object : FeatureSourceContext {
            override fun <T> get(dependency: FeatureDependency.Single<T>): T {
                checkValid(dependency)
                return elementValueMap.get(dependency.tag).single()
            }

            override fun <T> get(dependency: FeatureDependency.All<T>): List<T> {
                checkValid(dependency)
                return elementValueMap.get(dependency.tag)
            }

            override fun <T : Any> get(dependency: FeatureDependency.AtMostOne<T>): T? {
                checkValid(dependency)

                val values = elementValueMap.get(dependency.tag)
                check(values.size <= 1)

                return values.singleOrNull()
            }
        }
    }

    private fun initializeDependencyElements(
        relevantSources: List<FeatureSource<*>>,
        backtrace: PersistentList<FeatureElementTag<*>>,
    ) {
        val dependentElements = relevantSources.flatMap { it.dependencies }.map { it.tag }.toSet()

        for (element in dependentElements) {
            if (backtrace.contains(element)) {
                throw FeatureRecursiveDependencyException(
                    backtrace = backtrace.add(element),
                )
            }

            doInitializeElement(
                toInit = element,
                backtrace = backtrace,
            )
        }
    }

    private fun <T> initializeElementNoDependencies(
        toInit: FeatureElementTag<T>,
        sources: List<FeatureSource<*>>,
    ): List<T> {
        val featureValues = mutableListOf<T>()

        for (source in sources) {
            val dependencies = source.dependencies.toImmutableList()

            if (dependencies.all { dependencyIsSatisfied(it) }) {
                try {
                    val feature = featureState.initFromSource(source) {
                        contextForDependencies(dependencies)
                    }

                    featureValues.addAll(feature.query(toInit))
                } catch (e: Exception) {
                    logger.warn("Failed to process feature ${source.featureName} for tag $toInit", e)
                }
            } else {
                logger.info("Dependencies not met for source ${source.featureName}")
            }
        }

        elementValueMap.add(toInit, featureValues)
        return featureValues
    }

    private fun <T> doInitializeElement(
        toInit: FeatureElementTag<T>,
        backtrace: PersistentList<FeatureElementTag<*>>,
    ): List<T> {
        if (elementValueMap.contains(toInit)) return elementValueMap.get(toInit)

        val relevantSources = sources.filter { it.provides.contains(toInit) }

        initializeDependencyElements(
            relevantSources = relevantSources,
            backtrace = backtrace.add(toInit),
        )

        return initializeElementNoDependencies(
            toInit = toInit,
            sources = relevantSources,
        )
    }

    fun <T> initializeElement(element: FeatureElementTag<T>): List<T> {
        check(!isClosed)

        return doInitializeElement(
            toInit = element,
            backtrace = persistentListOf(),
        )
    }

    fun closeFeatures() {
        check(!isClosed)

        isClosed = true
        featureState.closeFeatures()
    }
}
