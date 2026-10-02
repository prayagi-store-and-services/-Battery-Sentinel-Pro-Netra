package com.example

import com.example.service.NetworkDownswitchPolicy
import com.example.service.NetworkDownswitchPolicy.Decision
import com.example.service.RadioClass
import org.junit.Assert.*
import org.junit.Test

class NetworkDownswitchPolicyTest {
    private val mb = 1024L * 1024L

    @Test fun fiveGGoesToFourGAndFourGToThreeG() {
        assertEquals(Decision.Suggest(RadioClass.NR_5G, RadioClass.LTE_4G), NetworkDownswitchPolicy.decide(RadioClass.NR_5G, 10 * mb))
        assertEquals(Decision.Suggest(RadioClass.LTE_4G, RadioClass.THREE_G_OR_LOWER), NetworkDownswitchPolicy.decide(RadioClass.LTE_4G, 0L))
    }

    @Test fun heavyDataSkipsAtThreshold() {
        assertTrue(NetworkDownswitchPolicy.decide(RadioClass.NR_5G, 200 * mb) is Decision.Skip)
        assertTrue(NetworkDownswitchPolicy.decide(RadioClass.NR_5G, 199 * mb) is Decision.Suggest)
    }

    @Test fun unreadableUsageOrNetworkNeverSuggests() {
        assertTrue(NetworkDownswitchPolicy.decide(RadioClass.NR_5G, null) is Decision.Skip)
        assertTrue(NetworkDownswitchPolicy.decide(RadioClass.UNKNOWN, 0L) is Decision.Skip)
        assertTrue(NetworkDownswitchPolicy.decide(RadioClass.THREE_G_OR_LOWER, 0L) is Decision.Skip)
    }

    @Test fun modeMappingOnlyForKnownValues() {
        assertEquals(9, NetworkDownswitchPolicy.downMode(26))
        assertEquals(10, NetworkDownswitchPolicy.downMode(27))
        assertEquals(11, NetworkDownswitchPolicy.downMode(24))
        assertEquals(22, NetworkDownswitchPolicy.downMode(33))
        assertEquals(0, NetworkDownswitchPolicy.downMode(9))
        assertNull(NetworkDownswitchPolicy.downMode(1))
        assertNull(NetworkDownswitchPolicy.downMode(-1))
    }
}
