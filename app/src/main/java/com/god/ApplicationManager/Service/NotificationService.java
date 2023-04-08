package com.god.ApplicationManager.Service;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.annotation.Nullable;

import com.god.ApplicationManager.DB.SettingsDB;
import com.god.ApplicationManager.Facade.AppManagerFacade;

import java.util.List;

public class NotificationService extends NotificationListenerService {
    private Thread backgroundThread;
    private boolean isRunning;
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
    public void onCreate() {
        super.onCreate();
        isRunning = false;
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isRunning) {
            isRunning = true;
            startBackgroundThread();
        }
        return START_STICKY;
    }
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if(!SettingsDB.getInstance().isDisableTurnOffNotification) return;
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
    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (backgroundThread != null) {
            backgroundThread.interrupt();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    private void startBackgroundThread() {
        backgroundThread = new Thread(()->{if(isRunning) {
            try {
                getListPkHaveToTurnOffFromDB();
                Thread.sleep(60000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }});
        backgroundThread.start();
    }
}
