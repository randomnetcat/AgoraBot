package org.randomcat.agorabot.config.persist

interface ConfigPersistService {
    /**
     * Schedules the periodic persistence of the return value of [readState] via [persist]. [persist] should be able to
     * handle being called concurrently. The scheduling is on a best-effort basis, and may only result in a relatively
     * recent value being persisted, rather than the One True Up-To-Date Value.
     *
     * Note that [persist] may be called during JVM shutdown, and thus it should avoid locking things if at all
     * possible.
     */
    fun <T> schedulePersistence(readState: () -> T, persist: (T) -> Unit): PersistInstanceHandle
}

interface PersistInstanceHandle {
    /**
     * Instructs a [ConfigPersistService] to stop persisting the associated state. When this method returns, it is
     * guaranteed that no further persistence will be done.
     *
     * Calling this method a further time after the first has no effect.
     */
    fun stopPersistence()
}
