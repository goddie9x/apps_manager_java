package com.god.ApplicationManager.Permission;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.god.ApplicationManager.Service.FreezeService;
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


    public void getUseAccessibilityService() {
        if (!FreezeService.isEnabled&&ContextCompat.checkSelfPermission(activity,
                Manifest.permission.BIND_ACCESSIBILITY_SERVICE) == PackageManager.PERMISSION_DENIED) {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            activity.startActivity(intent);
        }
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_QUERY_ALL_PACKAGES_CODE: {
                if (grantResults.length < 1
                        || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    DialogUtils.showAlertDialog(activity,
                            "Permission require warning",
                            "You should provide permission to get full power",
                            (dialog, which) -> getQueryAllPackagePermission(),
                            (dialog, which) -> {
                            }
                    );
                }
                break;
            }
        }
    }

    public void getManagerNotificationPermission() {
        if (activity.checkSelfPermission(Manifest.permission.ACCESS_NOTIFICATION_POLICY)
                == PackageManager.PERMISSION_DENIED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activity.requestPermissions(new String[]{android.Manifest.permission.ACCESS_NOTIFICATION_POLICY}, 1);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!NotificationManagerCompat.getEnabledListenerPackages(activity).contains(activity.getPackageName())) {
                Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                activity.startActivity(intent);
            }
        }
    }

    public void getQueryAllPackagePermission() {
        if (activity.checkSelfPermission(Manifest.permission.QUERY_ALL_PACKAGES)
                != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.requestPermissions(new String[]{Manifest.permission.QUERY_ALL_PACKAGES},
                        PERMISSION_QUERY_ALL_PACKAGES_CODE);
            }
        }
    }
}
