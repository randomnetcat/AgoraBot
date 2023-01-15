package org.randomcat.agorabot.features

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.randomcat.agorabot.*

object CoroutineScopeTag : FeatureElementTag<CoroutineScope>

@FeatureSourceFactory
fun coroutineScopeFactory() = object : FeatureSource.NoConfig {
    override val featureName: String
        get() = "coroutine_scope_provider"

    override val dependencies: List<FeatureDependency<*>>
        get() = emptyList()

    override val provides: List<FeatureElementTag<*>>
        get() = listOf(CoroutineScopeTag)

    override fun createFeature(context: FeatureSourceContext): Feature {
        val job = SupervisorJob()
        val scope = CoroutineScope(job)

        return object : Feature {
            override fun <T> query(tag: FeatureElementTag<T>): List<T> {
                if (tag is CoroutineScopeTag) return tag.values(scope)

                invalidTag(tag)
            }

            override fun close() {
                runBlocking {
                    job.cancelAndJoin()
                }
            }
        }
    }
}