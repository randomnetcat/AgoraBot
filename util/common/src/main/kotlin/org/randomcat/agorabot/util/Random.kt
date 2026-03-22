package org.randomcat.agorabot.util

import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

// SecureRandom is documented to be safe to concurrently access.
private val userRandom = SecureRandom().asKotlinRandom()

fun userFacingRandom(): Random {
    return userRandom
}

fun insecureRandom(): Random {
    return Random.Default
}
