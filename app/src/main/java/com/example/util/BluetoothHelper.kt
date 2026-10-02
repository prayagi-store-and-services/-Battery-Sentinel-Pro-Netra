package com.example.util

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.model.BluetoothDeviceItem

/**
 * Returns only currently connected Bluetooth devices.
 *
 * Bonded/paired but disconnected devices are intentionally excluded.
 * Battery level is best effort: Android has no public battery-level API for classic
 * Bluetooth devices, so the value comes from the platform's getBatteryLevel() when the
 * phone and the device report it. Many phones/devices do not, in which case
 * batteryPercent is null and the UI shows "Battery: Unavailable". A value is never invented.
 */
object BluetoothHelper {

    @Volatile
    private var a2dpProxy: BluetoothProfile? = null
    @Volatile
    private var headsetProxy: BluetoothProfile? = null
    @Volatile
    private var hearingAidProxy: BluetoothProfile? = null
    @Volatile
    private var leAudioProxy: BluetoothProfile? = null

    // Profile ids from public BluetoothProfile constants (HEARING_AID API 29, LE_AUDIO API 33).
    private const val PROFILE_HEARING_AID = 21
    private const val PROFILE_LE_AUDIO = 22

    fun initialize(context: Context) {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        try {
            adapter.getProfileProxy(context.applicationContext, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                    if (profile == BluetoothProfile.A2DP) {
                        a2dpProxy = proxy
                    }
                }
                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.A2DP) {
                        a2dpProxy = null
                    }
                }
            }, BluetoothProfile.A2DP)

            adapter.getProfileProxy(context.applicationContext, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                    if (profile == BluetoothProfile.HEADSET) {
                        headsetProxy = proxy
                    }
                }
                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.HEADSET) {
                        headsetProxy = null
                    }
                }
            }, BluetoothProfile.HEADSET)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                adapter.getProfileProxy(context.applicationContext, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                        if (profile == PROFILE_HEARING_AID) hearingAidProxy = proxy
                    }
                    override fun onServiceDisconnected(profile: Int) {
                        if (profile == PROFILE_HEARING_AID) hearingAidProxy = null
                    }
                }, PROFILE_HEARING_AID)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                adapter.getProfileProxy(context.applicationContext, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                        if (profile == PROFILE_LE_AUDIO) leAudioProxy = proxy
                    }
                    override fun onServiceDisconnected(profile: Int) {
                        if (profile == PROFILE_LE_AUDIO) leAudioProxy = null
                    }
                }, PROFILE_LE_AUDIO)
            }
        } catch (_: Exception) {}
    }

    fun getBluetoothDevices(context: Context): List<BluetoothDeviceItem> {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            Manifest.permission.BLUETOOTH
        }
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }

        return try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                ?: return emptyList()
            val adapter = manager.adapter ?: BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
            if (!adapter.isEnabled) return emptyList()

            // Addresses of Bluetooth audio outputs Android currently routes to (public AudioManager API).
            // Covers phones/OEM builds where the profile proxies are not connected yet or not reported.
            val audioAddresses = connectedAudioAddresses(context)

            adapter.bondedDevices
                .asSequence()
                .filter { device ->
                    // One device failing a check must never hide the others.
                    runCatching {
                        isDeviceConnected(manager, device) ||
                            (try { device.address } catch (_: SecurityException) { null })
                                ?.uppercase()?.let { it in audioAddresses } == true
                    }.getOrDefault(false)
                }
                .mapNotNull { device ->
                    val name = try {
                        device.name?.takeIf { it.isNotBlank() } ?: "Bluetooth Device"
                    } catch (_: SecurityException) {
                        "Bluetooth Device"
                    }
                    val address = try { device.address } catch (_: SecurityException) { return@mapNotNull null }
                    val deviceClass = try { device.bluetoothClass?.majorDeviceClass ?: 0 } catch (_: SecurityException) { 0 }

                    // Fetch battery level using standard API via safe visibility invocation
                    val battery = try {
                        val method = device.javaClass.getMethod("getBatteryLevel")
                        val lvl = method.invoke(device) as Int
                        if (lvl in 0..100) lvl else null
                    } catch (_: Exception) {
                        null
                    }

                    BluetoothDeviceItem(
                        name = name,
                        address = address,
                        isConnected = true,
                        isPaired = true,
                        deviceType = deviceType(deviceClass),
                        batteryPercent = battery,
                        profile = profileLabel(deviceClass)
                    )
                }
                .toList()
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun isDeviceConnected(manager: BluetoothManager, device: BluetoothDevice): Boolean {
        val proxies = listOf(a2dpProxy, headsetProxy, hearingAidProxy, leAudioProxy)
        for (proxy in proxies) {
            val hit = try {
                proxy?.connectedDevices?.contains(device) == true
            } catch (_: Exception) {
                false
            }
            if (hit) return true
        }
        // BluetoothManager.getConnectionState only supports GATT / GATT_SERVER. Passing other profiles
        // throws IllegalArgumentException, which used to discard the whole device list.
        return try {
            manager.getConnectedDevices(BluetoothProfile.GATT).contains(device) ||
                manager.getConnectionState(device, BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED
        } catch (_: Exception) {
            false
        }
    }

    private fun connectedAudioAddresses(context: Context): Set<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return emptySet()
        return try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return emptySet()
            audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .filter {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            (it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER))
                }
                .map { it.address.uppercase() }
                .filter { it.isNotBlank() }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun deviceType(majorClass: Int): String = when (majorClass) {
        1024 -> "Audio / Headphones / Speaker"
        1792 -> "Wearable / Smartwatch"
        1280 -> "Input Device / Keyboard / Mouse"
        512 -> "Phone / Tablet"
        else -> "Bluetooth Peripheral"
    }

    private fun profileLabel(majorClass: Int): String =
        if (majorClass == 1024) "A2DP / HFP" else "HID / Generic"
}
