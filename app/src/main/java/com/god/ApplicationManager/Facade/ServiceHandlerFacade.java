package com.god.ApplicationManager.Facade;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;

import androidx.appcompat.app.AppCompatActivity;

import com.god.ApplicationManager.Service.FreezeService;
import com.god.ApplicationManager.Service.NotificationService;

public class ServiceHandlerFacade {
    public static void startService(AppCompatActivity activity) {
        startNotificationService(activity);
        startFreezeService(activity);
    }
    public static void startNotificationService(AppCompatActivity activity) {
        if (!isMyServiceRunning(NotificationService.class, activity)) {
            Intent serviceIntent = new Intent(activity, NotificationService.class);
            activity.startService(serviceIntent);
        }
    }

    public static void startFreezeService(AppCompatActivity activity) {
        if (!isMyServiceRunning(FreezeService.class, activity)) {
            Intent serviceIntent = new Intent(activity, FreezeService.class);
            activity.startService(serviceIntent);
        }
    }

    public static boolean isMyServiceRunning(Class<?> serviceClass, AppCompatActivity activity) {
        ActivityManager manager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
    public static void bindServices(AppCompatActivity activity, ServiceConnection serviceConnection) {
        Intent intentFreezeService = new Intent(activity, FreezeService.class);
        activity.bindService(intentFreezeService, serviceConnection, Context.BIND_AUTO_CREATE);
        Intent intentNotificationService = new Intent(activity, NotificationService.class);
        activity.bindService(intentNotificationService, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    public static void startForgroundServices(AppCompatActivity activity,Class<?>serviceClass){
        Intent intentFreezeService = new Intent(activity, serviceClass);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.startForegroundService(intentFreezeService);
        }
        else{
            activity.startService(intentFreezeService);
        }
    }
}
