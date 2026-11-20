package com.example.service

import com.example.model.ThermalStatusAvailability

/** Owns one platform registration. A failed removal blocks another registration. */
internal class OwnedThermalStatusListener<L : Any>(
    private val supported: Boolean,
    private val createListener: ((Int) -> Unit) -> L,
    private val register: (L) -> Unit,
    private val remove: (L) -> Unit,
    private val publish: (Int?, ThermalStatusAvailability) -> Unit
) {
    private var listener: L? = null
    private var generation = 0L
    private var accepting = false

    @Synchronized fun start() {
        if (listener != null) return
        if (!supported) {
            publish(null, ThermalStatusAvailability.UNSUPPORTED)
            return
        }
        val epoch = ++generation
        accepting = true
        publish(null, ThermalStatusAvailability.AWAITING_OBSERVATION)
        val owned = try {
            createListener { status -> onStatus(epoch, status) }
        } catch (_: RuntimeException) {
            accepting = false
            ++generation
            publish(null, ThermalStatusAvailability.REGISTRATION_FAILED)
            return
        }
        listener = owned
        try {
            register(owned)
        } catch (_: RuntimeException) {
            accepting = false
            ++generation
            // Also try cleanup if a vendor registered before throwing.
            try {
                remove(owned)
                listener = null
            } catch (_: RuntimeException) {
                // Retain ownership so a later stop can retry exact removal.
            }
            publish(null, ThermalStatusAvailability.REGISTRATION_FAILED)
        }
    }

    @Synchronized private fun onStatus(epoch: Long, status: Int) {
        if (!accepting || epoch != generation) return
        if (status in 0..6) publish(status, ThermalStatusAvailability.OBSERVED)
        else publish(null, ThermalStatusAvailability.INVALID_OBSERVATION)
    }

    @Synchronized fun stop() {
        accepting = false
        ++generation
        val owned = listener
        if (owned != null) {
            try {
                remove(owned)
                listener = null
            } catch (_: RuntimeException) {
                publish(null, ThermalStatusAvailability.REMOVAL_FAILED)
                return
            }
        }
        publish(null, ThermalStatusAvailability.STOPPED)
    }
}
