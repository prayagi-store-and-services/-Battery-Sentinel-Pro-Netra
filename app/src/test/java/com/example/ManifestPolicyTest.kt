package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Merged-source manifest policy after the updater removal: the install permission must be gone
 * and the report FileProvider must stay. Parses the real XML (unit-test working dir is the
 * module directory), not a text grep.
 */
class ManifestPolicyTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"

    private fun manifest(): org.w3c.dom.Document {
        val f = java.io.File("src/main/AndroidManifest.xml")
        assertTrue("manifest must exist at ${f.absolutePath}", f.isFile)
        val dbf = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return dbf.newDocumentBuilder().parse(f)
    }

    private fun elementsNamed(doc: org.w3c.dom.Document, tag: String): List<org.w3c.dom.Element> {
        val list = doc.getElementsByTagName(tag)
        return (0 until list.length).map { list.item(it) as org.w3c.dom.Element }
    }

    @Test
    fun installPackagesPermissionRemoved() {
        val perms = elementsNamed(manifest(), "uses-permission")
            .map { it.getAttributeNS(androidNs, "name") }
        assertFalse(
            "REQUEST_INSTALL_PACKAGES must not be declared after the updater removal",
            perms.contains("android.permission.REQUEST_INSTALL_PACKAGES")
        )
    }

    @Test
    fun reportFileProviderRetained() {
        val providers = elementsNamed(manifest(), "provider")
            .map { it.getAttributeNS(androidNs, "name") }
        assertTrue(
            "the FileProvider used for generated battery reports must remain",
            providers.contains("androidx.core.content.FileProvider")
        )
    }

    @Test
    fun noExportedInstallerReceiverRemains() {
        val receivers = elementsNamed(manifest(), "receiver")
        val exported = receivers.filter { it.getAttributeNS(androidNs, "exported") == "true" }
            .map { it.getAttributeNS(androidNs, "name") }
        assertFalse(
            "no receiver may reference the removed updater",
            exported.any { it.contains("update", ignoreCase = true) }
        )
    }
}
