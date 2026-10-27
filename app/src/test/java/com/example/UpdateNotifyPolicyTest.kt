package com.example
import org.junit.Test
import org.junit.Assert.*
class UpdateNotifyPolicyTest { @Test fun updaterRetired(){assertFalse(com.example.update.AppUpdater::class.java.declaredMethods.any{it.name=="download"||it.name=="install"})} }
