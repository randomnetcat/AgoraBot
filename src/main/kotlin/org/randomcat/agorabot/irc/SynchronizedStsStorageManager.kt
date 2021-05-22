package org.randomcat.agorabot.irc

import org.kitteh.irc.client.library.feature.sts.StsPolicy
import org.kitteh.irc.client.library.feature.sts.StsStorageManager
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class SynchronizedStsStorageManager(private val impl: StsStorageManager) : StsStorageManager {
    private val lock = ReentrantLock()

    override fun addEntry(hostname: String, duration: Long, policy: StsPolicy) {
        return lock.withLock { impl.addEntry(hostname, duration, policy) }
    }

    override fun getEntry(hostname: String): Optional<StsPolicy> {
        return lock.withLock { impl.getEntry(hostname) }
    }

    override fun hasEntry(hostname: String): Boolean {
        return lock.withLock { impl.hasEntry(hostname) }
    }

    override fun removeEntry(hostname: String) {
        return lock.withLock { impl.removeEntry(hostname) }
    }
}
