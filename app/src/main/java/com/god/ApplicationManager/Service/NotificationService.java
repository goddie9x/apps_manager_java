package com.god.ApplicationManager.Service;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.god.ApplicationManager.DB.SettingsDB;
import com.god.ApplicationManager.Facade.AppManagerFacade;

import java.util.List;

public class NotificationService extends NotificationListenerService {
    private static List<String> listPackageNameHaveToTurnOffNotif;

    private static boolean isDisableTurnOffNotification;
    private static final String TAG="NotificationService";

    public static void getListTurnOffNotificationFromDB() {
        listPackageNameHaveToTurnOffNotif = AppManagerFacade
                .getListPackageNameHaveToTurnOffNotif();
    }
    public static void getDisableNotificationStatus(){
        isDisableTurnOffNotification = SettingsDB.getInstance()
                .isDisableTurnOffNotification;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();
        if (listPackageNameHaveToTurnOffNotif.contains(packageName)&&!isDisableTurnOffNotification) {
            String key = sbn.getKey();
            cancelNotification(key);
            Log.i(TAG,key);
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        getListTurnOffNotificationFromDB();
        StatusBarNotification[] activeNotifications = getActiveNotifications();
        for (StatusBarNotification notification : activeNotifications) {
            if (listPackageNameHaveToTurnOffNotif.contains(notification.getPackageName())) {
                cancelNotification(notification.getKey());
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        getListTurnOffNotificationFromDB();
    }
}