package com.god.ApplicationManager.admin

import android.app.admin.DeviceAdminReceiver

/**
 * Device-admin receiver. The app only uses Device Owner capabilities (package
 * suspension) when the user has explicitly provisioned it as Device Owner
 * (e.g. `adb shell dpm set-device-owner com.god.ApplicationManager/.admin.AppAdminReceiver`);
 * it never requests device admin activation in-app.
 */
class AppAdminReceiver : DeviceAdminReceiver()
