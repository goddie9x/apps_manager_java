package com.god.ApplicationManager.Service;

import android.app.NotificationManager;
import android.content.Context;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import com.god.ApplicationManager.Facade.AppManagerFacade;

import java.util.List;

public class NotificationService extends NotificationListenerService {
    private static List<String> listPackageNameHaveToTurnOffNotif;
    public static List<String>getListPackageNameHaveToTurnOffNotif(){
        if(listPackageNameHaveToTurnOffNotif==null){
            getListPkHaveToTurnOffFromDB();
        }
        return listPackageNameHaveToTurnOffNotif;
    }
    public static void getListPkHaveToTurnOffFromDB(){
        listPackageNameHaveToTurnOffNotif = AppManagerFacade
                .getListPackageNameHaveToTurnOffNotif();
    }
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();
        List<String> listPackageName = getListPackageNameHaveToTurnOffNotif();
        if (listPackageName.contains(packageName)) {
            // Get the NotificationManager for the target app
            NotificationManager notificationManager = (NotificationManager) getSystemService(
                    Context.NOTIFICATION_SERVICE);

            // Turn off notifications for the target app
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
        }
    }

}
