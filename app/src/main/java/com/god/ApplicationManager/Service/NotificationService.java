package com.god.ApplicationManager.Service;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.annotation.Nullable;

import com.god.ApplicationManager.Facade.AppManagerFacade;

import java.util.List;

public class NotificationService extends NotificationListenerService {
    //we need to run this service in background even the app closed
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
            getListPkHaveToTurnOffFromDB();

            try {
                // Wait for some time before updating the list again
                Thread.sleep(60000); // 1 minute
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }});
        backgroundThread.start();
    }
}
