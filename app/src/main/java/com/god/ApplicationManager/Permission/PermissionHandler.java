package com.god.ApplicationManager.Permission;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.god.ApplicationManager.Interface.ICallbackVoid;
import com.god.ApplicationManager.R;
import com.god.ApplicationManager.Util.DialogUtils;

public class PermissionHandler {
    private static final String TAG = "PermissionHandler";
    private final AppCompatActivity activity;
    private final PackageManager packageManager;
    private final String crrPackageName;
    private static final int PERMISSION_QUERY_ALL_PACKAGES_CODE = 69;

    public PermissionHandler(AppCompatActivity activity) {
        this.activity = activity;
        packageManager = activity.getPackageManager();
        crrPackageName = activity.getPackageName();
    }

    public void getPermissions() {
        getQueryAllPackagePermission();
        getManagerNotificationPermission();
        getUseAccessibilityService();
    }

    private boolean isAccessibilityServiceEnabled() {
        String prefString = Settings.Secure.getString(activity.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return prefString != null && prefString.contains(crrPackageName);
    }

    private boolean isNotificationServiceEnabled() {
        String prefString = Settings.Secure.getString(activity.getContentResolver(), "enabled_notification_listeners");
        return prefString != null && prefString.contains(crrPackageName);
    }

    public void getUseAccessibilityService() {
        if (!isAccessibilityServiceEnabled()) {
            showPermissionRequireDialog(activity.getString(R.string.require_accessibility_description), () -> {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                activity.startActivity(intent);
            }, () -> {});
        }
    }

    public void getManagerNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NotificationManager notificationManager = (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
            if (!isNotificationServiceEnabled()) {
                showPermissionRequireDialog(activity.getString(R.string.permission_notification_description), () -> {
                    Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                    activity.startActivity(intent);
                }, () -> {});
            } else if (notificationManager != null && !notificationManager.isNotificationPolicyAccessGranted()) {
                showPermissionRequireDialog(activity.getString(R.string.permission_notification_description), () -> {
                    Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                    activity.startActivity(intent);
                }, () -> {});
            }
        }
    }

    private void showPermissionRequireDialog(String permissionDes, ICallbackVoid grandPermissionAction, ICallbackVoid cancelAction) {
        DialogUtils.showAlertDialog(activity,
                activity.getString(R.string.permission_requirement),
                permissionDes,
                (dialog, which) -> { grandPermissionAction.callback(); },
                (dialog, which) -> { cancelAction.callback(); }
        );
    }

    public void getQueryAllPackagePermission() {
        // QUERY_ALL_PACKAGES is an install-time (normal) permission: it is granted
        // automatically when declared in the manifest and cannot be requested at runtime.
        boolean granted = activity.checkSelfPermission(Manifest.permission.QUERY_ALL_PACKAGES)
                == PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            android.util.Log.w(TAG, "QUERY_ALL_PACKAGES not granted; app list will be filtered by package visibility");
        }
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_QUERY_ALL_PACKAGES_CODE: {
                // No runtime permission is requested anymore; kept for compatibility.
                break;
            }
        }
    }
}