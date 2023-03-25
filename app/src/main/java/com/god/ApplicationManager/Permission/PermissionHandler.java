package com.god.ApplicationManager.Permission;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.god.ApplicationManager.Util.DialogUtils;

public class PermissionHandler {
    private final AppCompatActivity activity;
    private static final int PERMISSION_QUERY_ALL_PACKAGES_CODE = 69;

    public PermissionHandler(AppCompatActivity activity){
        this.activity = activity;
    }
    public void getPermissions() {
        getQueryAllPackagePermission();
        getManagerNotificationPermission();
    }
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults){
        if (requestCode == PERMISSION_QUERY_ALL_PACKAGES_CODE) {
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
        }
    }
    public void getManagerNotificationPermission() {
        if (activity.checkSelfPermission(Manifest.permission.ACCESS_NOTIFICATION_POLICY)
                == PackageManager.PERMISSION_DENIED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activity.requestPermissions(new String[]{android.Manifest.permission.ACCESS_NOTIFICATION_POLICY}, 1);
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
