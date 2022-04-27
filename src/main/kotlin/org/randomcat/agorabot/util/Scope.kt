package org.randomcat.agorabot.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.randomcat.agorabot.FeatureContext

private object CoroutineScopeCacheKey

/**
 * A coroutine scope that will be cancelled on context close. The scope has a [SupervisorJob].
 */
val FeatureContext.coroutineScope
    get() = cache(CoroutineScopeCacheKey) {
        val scope = CoroutineScope(SupervisorJob())

        onClose {
            scope.cancel("FeatureContext closed")
        }

        scope
    }
