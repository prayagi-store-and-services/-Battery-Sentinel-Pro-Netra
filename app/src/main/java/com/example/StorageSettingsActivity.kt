package com.example

import android.app.Activity
import android.os.Bundle
import com.example.service.JunkWatcher

/** Fixed internal notification destination. Never forwards caller-supplied intent data. */
class StorageSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!JunkWatcher.openStorageSettings(this)) {
            android.widget.Toast.makeText(this, "Android storage settings are unavailable on this device.", android.widget.Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
