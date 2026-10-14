package com.example.receiver

import android.app.admin.DeviceAdminReceiver

/** Device Admin hook. It declares one policy only (force-lock): the app can lock the screen, nothing else. */
class NetraDeviceAdmin : DeviceAdminReceiver()
