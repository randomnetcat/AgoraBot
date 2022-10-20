package org.randomcat.agorabot.util

import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

private val threadRandom = ThreadLocal.withInitial { SecureRandom().asKotlinRandom() }

fun userFacingRandom(): Random {
    return threadRandom.get()
}

fun insecureRandom(): Random {
    return Random.Default
}