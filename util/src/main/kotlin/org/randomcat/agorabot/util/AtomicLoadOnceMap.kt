package org.randomcat.agorabot.util

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import java.util.concurrent.atomic.AtomicReference

class AtomicLoadOnceMap<K, V : Any> {
    private class LoadOnceValue<V>(init: () -> V) {
        val value by lazy(LazyThreadSafetyMode.SYNCHRONIZED, init)
    }

    private val data: AtomicReference<PersistentMap<K, LoadOnceValue<V>>> = AtomicReference(persistentMapOf())

    fun getOrPut(key: K, initOnce: () -> V): V {
        run {
            val origMap = data.get()
            val loadOnceValue = origMap[key]

            // This null test is safe because V is constrained to derive from Any (and is therefore non-nullable).
            if (loadOnceValue != null) return loadOnceValue.value
        }

        return data.updateAndGet {
            if (it.containsKey(key))
                it
            else
            // If multiple threads get here, they will race to return a new map with a value. Because this is an atomic
            // operation, then only one of them will succeed (returning and initializing the value), and the rest will
            // get to try again. They will find that it already contains the key, and return the existing map, ensuring
            // that only one LoadOnceValue escapes to the outside world. That LoadOnceValue will then thread-safely
            // initialize the value once, ensuring that initOnce is called exactly once for each key (assuming it never
            // throws an exception).
                it.put(key, LoadOnceValue<V>(initOnce))
        }.getValue(key).value
    }
}
