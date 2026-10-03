package com.greenfodor.ppremotece.core.domain.trigger

import java.util.concurrent.ConcurrentHashMap

/** Runs triggers by key, ignoring a call for a key whose earlier call is still in flight. */
class InFlightTriggers {
    private val keys: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Runs [block] and returns true, or returns false without running it while an earlier call for
     * [key] is in flight. The key is free again when [block] returns or throws.
     */
    suspend fun run(key: String, block: suspend () -> Unit): Boolean {
        if (!keys.add(key)) return false
        try {
            block()
        } finally {
            keys.remove(key)
        }
        return true
    }
}
