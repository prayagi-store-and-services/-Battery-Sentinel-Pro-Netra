package com.example.update

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlainFailureTest {
    @Test fun ownMessageIsKept() {
        assertEquals("update source answered 404", plainFailure(IllegalStateException("update source answered 404"), "x"))
        assertEquals("Invalid APK.", plainFailure(SecurityException("Invalid APK."), "x"))
    }
    @Test fun networkErrorHidesSystemText() {
        val m = plainFailure(UnknownHostException("Unable to resolve host \"api.github.com\""), "x")
        assertFalse(m.contains("resolve host"))
        assertEquals(m, plainFailure(SocketTimeoutException("timeout"), "x"))
    }
    @Test fun unknownUsesFallback() {
        assertEquals("Update failed.", plainFailure(RuntimeException("boom"), "Update failed."))
    }
}
