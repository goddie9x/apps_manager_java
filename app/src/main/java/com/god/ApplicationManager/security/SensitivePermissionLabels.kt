package com.god.ApplicationManager.security

/**
 * User-facing labels for the sensitive permissions tracked by the scanner.
 * Keys are permission names; the UI resolves labels via string resources.
 */
object SensitivePermissionLabels {
    val PERMISSIONS = listOf(
        "android.permission.READ_SMS",
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.READ_CALL_LOG",
        "android.permission.READ_CONTACTS",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.RECORD_AUDIO",
        "android.permission.CAMERA",
        "android.permission.READ_PHONE_STATE",
        "android.permission.CALL_PHONE",
        "android.permission.REQUEST_INSTALL_PACKAGES",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.PACKAGE_USAGE_STATS",
        "android.permission.QUERY_ALL_PACKAGES",
        "android.permission.BIND_DEVICE_ADMIN",
        "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
        "android.permission.BIND_ACCESSIBILITY_SERVICE",
        "android.permission.MANAGE_EXTERNAL_STORAGE",
    )
}
