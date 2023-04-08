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
    public static void startServices(Context context) {
            startNotificationService(context);
            startFreezeService(context);
    }
    public static void startNotificationService(Context context) {
        if (!isMyServiceRunning(NotificationService.class, context)) {
            Intent serviceIntent = new Intent(context, NotificationService.class);
            context.startService(serviceIntent);
        }
    }

    public static void startFreezeService(Context context) {
        if (!isMyServiceRunning(FreezeService.class, context)) {
            Intent serviceIntent = new Intent(context, FreezeService.class);
            context.startService(serviceIntent);
        }
    }

    public static boolean isMyServiceRunning(Class<?> serviceClass, Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
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
