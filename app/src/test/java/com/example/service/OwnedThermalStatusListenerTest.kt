package com.example.service

import com.example.model.NetraCentralState
import com.example.model.ThermalStatusAvailability
import com.example.model.ThermalStatusAvailability.*
import org.junit.Assert.*
import org.junit.Test

class OwnedThermalStatusListenerTest {
    private class FakeListener(val callback: (Int) -> Unit)
    private class Fixture(supported: Boolean = true) {
        val registered = mutableListOf<FakeListener>()
        val removed = mutableListOf<FakeListener>()
        val events = mutableListOf<Pair<Int?, ThermalStatusAvailability>>()
        var registrationFails = false
        var removalFails = false
        var synchronousStatus: Int? = null
        val owner = OwnedThermalStatusListener(
            supported,
            createListener = { FakeListener(it) },
            register = { listener ->
                registered += listener
                synchronousStatus?.let(listener.callback)
                if (registrationFails) throw IllegalStateException("registration refused")
            },
            remove = { listener ->
                removed += listener
                if (removalFails) throw IllegalStateException("removal refused")
            },
            publish = { status, availability -> events += status to availability }
        )
        fun last() = events.last()
    }

    @Test fun initialCentralStatusIsUnavailableNotNormal() {
        assertNull(NetraCentralState().thermalStatus)
        assertEquals(AWAITING_OBSERVATION, NetraCentralState().thermalStatusAvailability)
    }
    @Test fun registersOnceUntilStopped() {
        val f = Fixture(); f.owner.start(); f.owner.start()
        assertEquals(1, f.registered.size)
        assertEquals(null to AWAITING_OBSERVATION, f.last())
    }
    @Test fun realNormalObservationIsAllowed() {
        val f = Fixture(); f.owner.start(); f.registered.single().callback(0)
        assertEquals(0 to OBSERVED, f.last())
    }
    @Test fun severeObservationIsPreserved() {
        val f = Fixture(); f.owner.start(); f.registered.single().callback(3)
        assertEquals(3 to OBSERVED, f.last())
    }
    @Test fun synchronousRegistrationCallbackIsAccepted() {
        val f = Fixture(); f.synchronousStatus = 4; f.owner.start()
        assertEquals(4 to OBSERVED, f.last())
    }
    @Test fun invalidObservationDoesNotInventNormal() {
        val f = Fixture(); f.owner.start(); f.registered.single().callback(7)
        assertEquals(null to INVALID_OBSERVATION, f.last())
    }
    @Test fun unsupportedNeverRegisters() {
        val f = Fixture(false); f.owner.start()
        assertTrue(f.registered.isEmpty())
        assertEquals(null to UNSUPPORTED, f.last())
    }
    @Test fun registrationRefusalIsUnavailableAndCleansExactListener() {
        val f = Fixture(); f.registrationFails = true; f.owner.start()
        assertEquals(null to REGISTRATION_FAILED, f.last())
        assertSame(f.registered.single(), f.removed.single())
    }
    @Test fun callbackAfterRefusedRegistrationIsIgnored() {
        val f = Fixture(); f.registrationFails = true; f.owner.start()
        val count = f.events.size; f.registered.single().callback(0)
        assertEquals(count, f.events.size)
    }
    @Test fun teardownRemovesExactListenerOnlyOnce() {
        val f = Fixture(); f.owner.start(); f.owner.stop(); f.owner.stop()
        assertEquals(1, f.removed.size)
        assertSame(f.registered.single(), f.removed.single())
        assertEquals(null to STOPPED, f.last())
    }
    @Test fun destroyBeforeStartIsSafe() {
        val f = Fixture(); f.owner.stop()
        assertTrue(f.removed.isEmpty())
        assertEquals(null to STOPPED, f.last())
    }
    @Test fun callbackAfterTeardownIsIgnored() {
        val f = Fixture(); f.owner.start(); f.owner.stop()
        val count = f.events.size; f.registered.single().callback(0)
        assertEquals(count, f.events.size)
    }
    @Test fun restartUsesNewListenerAndIgnoresOldEpoch() {
        val f = Fixture(); f.owner.start(); val old = f.registered.single()
        f.owner.stop(); f.owner.start(); val count = f.events.size
        old.callback(0); assertEquals(count, f.events.size)
        f.registered.last().callback(5); assertEquals(5 to OBSERVED, f.last())
        assertNotSame(old, f.registered.last())
    }
    @Test fun removalFailureRemainsOwnedAndBlocksDuplicateRegistration() {
        val f = Fixture(); f.owner.start(); f.removalFails = true; f.owner.stop()
        assertEquals(null to REMOVAL_FAILED, f.last())
        f.owner.start(); assertEquals(1, f.registered.size)
        val count = f.events.size; f.registered.single().callback(0)
        assertEquals(count, f.events.size)
        f.removalFails = false; f.owner.stop(); f.owner.start()
        assertEquals(2, f.registered.size)
        assertSame(f.removed[0], f.removed[1])
    }
    @Test fun failedRegistrationWithFailedCleanupCannotRegisterAgain() {
        val f = Fixture(); f.registrationFails = true; f.removalFails = true
        f.owner.start(); f.owner.start(); assertEquals(1, f.registered.size)
        assertEquals(null to REGISTRATION_FAILED, f.last())
        f.removalFails = false; f.owner.stop(); f.registrationFails = false
        f.owner.start(); assertEquals(2, f.registered.size)
    }
}
