package org.randomcat.agorabot.features

import org.randomcat.agorabot.*

private val jdaDep = FeatureDependency.Single(JdaTag)
private val listenersDep = FeatureDependency.All(JdaListenerTag)

@FeatureSourceFactory
fun jdaBlockFeature() = object : FeatureSource.NoConfig {
    override val featureName: String
        get() = "jda_listener_block_feature"

    override val dependencies: List<FeatureDependency<*>>
        get() = listOf(jdaDep, listenersDep)

    override val provides: List<FeatureElementTag<*>>
        get() = listOf(StartupBlockTag)

    override fun createFeature(context: FeatureSourceContext): Feature {
        val jda = context[jdaDep]
        val listeners = context[listenersDep]

        return object : Feature {
            override fun <T> query(tag: FeatureElementTag<T>): List<T> {
                if (tag is StartupBlockTag) return tag.values({
                    jda.addEventListener(*listeners.toTypedArray())
                })

                invalidTag(tag)
            }

            override fun close() {
                jda.removeEventListener(*listeners.toTypedArray())
            }
        }
    }
}
