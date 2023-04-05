package com.god.ApplicationManager.Facade;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.ActivityManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.god.ApplicationManager.DB.AppInfoDB;
import com.god.ApplicationManager.Entity.AppInfo;
import com.god.ApplicationManager.Enum.FreezeServiceNextAction;
import com.god.ApplicationManager.Enum.MenuContextType;
import com.god.ApplicationManager.Service.FreezeService;
import com.god.ApplicationManager.UI.FreezeShortcutActivity;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;

public class AppManagerFacade {

    public interface EventVoid {
        void callback();
    }

    private static PackageManager packageManager;
    private static ActivityManager activityManager;
    private static AppCompatActivity activity;
    private static NotificationManager notificationManager;
    private static StatusBarNotification[] activeNotifications;
    public static final int SDK_VERSION = android.os.Build.VERSION.SDK_INT;
    private static final String TAG = "app manager facade";
    public static boolean hasRootPermission = false;

    public static void setActivity(AppCompatActivity activ) {
        activity = activ;
        packageManager = activity.getPackageManager();
        activityManager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
        notificationManager = (NotificationManager) activity
                .getSystemService(Context.NOTIFICATION_SERVICE);
        activeNotifications = notificationManager.getActiveNotifications();
    }

    public static List<AppInfo> GetAllInstalledApp() {
        List<AppInfo> appList = new ArrayList<>();
        List<ApplicationInfo> installedApplications = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo applicationInfo : installedApplications) {
            Drawable appIcon = applicationInfo.loadIcon(packageManager);
            String appName = applicationInfo.loadLabel(packageManager).toString();
            String packageName = applicationInfo.packageName;
            AppInfo appInfo = new AppInfo(appIcon,
                    appName, packageName,
                    (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0,
                    (applicationInfo.flags & ApplicationInfo.FLAG_STOPPED) == 0,
                    (applicationInfo.flags & ApplicationInfo.FLAG_SUSPENDED) != 0);
            appList.add(appInfo);
        }
        return appList;
    }

    public static List<ActivityManager.RunningAppProcessInfo> GetAllRunningApp() {
        return activityManager.getRunningAppProcesses();
    }

    public static void getRootPermission() {
        if (!hasRootAccess()) {
            try {
                Runtime.getRuntime().exec("su");
            } catch (IOException e) {
                Handler handler = new Handler(Looper.getMainLooper());

                handler.post(() -> {
                    Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }
    }

    public static boolean hasRootAccess() {
        try {
            java.util.Scanner s = new java.util.Scanner(Runtime.getRuntime()
                    .exec(new String[]{"/system/bin/su", "-c", "cd / && ls"})
                    .getInputStream()).useDelimiter("\\A");
            hasRootPermission = !(s.hasNext() ? s.next() : "").equals("");
            return AppManagerFacade.hasRootPermission;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static void uninstallAppWithRoot(String packageName) {
        executeCommandWithSuShell("pm uninstall  --user 0 " + packageName,
                "Uninstall " + packageName + " success",
                "Uninstall " + packageName + " failed");
    }

    public static boolean uninstallApp(AppInfo appInfo) {
        if (appInfo.isSystemApp) {
            if (hasRootPermission) {
                uninstallAppWithRoot(appInfo.packageName);
                return true;
            } else {
                Handler handler = new Handler(Looper.getMainLooper());

                handler.post(() -> {
                    Toast.makeText(activity,
                            "You cannot uninstall system app without root", Toast.LENGTH_LONG).show();
                });
                return false;
            }
        } else {
            Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
            intent.setData(Uri.parse("package:" + appInfo.packageName));
            activity.startActivity(intent);
            return true;
        }
    }

    public static void forceStopAppWithRootPermission(String packageName) {
        executeCommandWithSuShell("am force-stop " + packageName,
                "Kill " + packageName + " success",
                "Kill " + packageName + " failed");
    }

    public static void forceStopApp(AppInfo appInfo) {
        Log.i(TAG, "forceStopApp");
        if (hasRootPermission) {
            forceStopAppWithRootPermission(appInfo.packageName);
        } else {
            //since android 10 (sdk 19) we cannot force stop app, then we open setting of this app
            if (SDK_VERSION < 29) {
                activityManager.killBackgroundProcesses(appInfo.packageName);
            } else {
                FreezeService.setNextAction(FreezeServiceNextAction.PRESS_FORCE_STOP);
            }
        }
    }

    public static void freezeAppWithRootPermission(String packageName) {
        executeCommandWithSuShell("pm disable " + packageName,
                "Freeze " + packageName + " success",
                "Freeze " + packageName + " failed");
    }

    public static void freezeApp(AppInfo appInfo) {
        if (hasRootPermission) {
            freezeAppWithRootPermission(appInfo.packageName);
        } else {
            FreezeShortcutActivity.freezeApp(appInfo.packageName, activity);
        }
    }

    public static List<Integer> getAllNotifIdOfPackage(String packageName) {
        List<Integer> appNotificationIds = new ArrayList<>();
        for (StatusBarNotification notification : activeNotifications) {
            if (notification.getPackageName().equals(packageName)) {
                appNotificationIds.add(notification.getId());
            }
        }
        return appNotificationIds;
    }

    public static void setNotificationStateForApp(AppInfo appInfo,boolean isEnable) {
        if(!isEnable){
            turnOffNotif(getAllNotifIdOfPackage(appInfo.packageName),appInfo.packageName);
        }
        NotificationManager crrAppNotifManager = null;
        Handler handler = new Handler(Looper.getMainLooper());
        try {
            crrAppNotifManager = (NotificationManager) activity
                    .createPackageContext(appInfo.packageName, 0)
                    .getSystemService(Context.NOTIFICATION_SERVICE);
        } catch (PackageManager.NameNotFoundException e) {
            handler.post(() -> {
                Toast.makeText(activity,
                        "Get notification chanel" + appInfo.packageName + " failed", Toast.LENGTH_SHORT).show();
            });
            Log.e(TAG, e.getMessage());
            return;
        }

        // Get a list of all the notification channels
        List<NotificationChannel> channels = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                channels = crrAppNotifManager.getNotificationChannels();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
        } else {
            try {
                // Get the INotificationManager service
                Class<?> serviceManager = Class.forName("android.os.ServiceManager");
                Method getService = serviceManager.getDeclaredMethod("getService", String.class);
                IBinder binder = (IBinder) getService.invoke(null, "notification");
                Class<?> notificationManagerStub = Class.forName("android.app.INotificationManager$Stub");
                Method asInterface = notificationManagerStub.getDeclaredMethod("asInterface", IBinder.class);
                Object iNotificationManager = asInterface.invoke(null, binder);

                // Call the INotificationManager.getNotificationChannelsForPackage method
                Method getNotificationChannelsForPackage = iNotificationManager.getClass()
                        .getDeclaredMethod("getNotificationChannelsForPackage", String.class, int.class);
                channels = (List<NotificationChannel>) getNotificationChannelsForPackage.invoke(iNotificationManager,
                        appInfo.packageName, Process.myUid() / 100000);
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
        }

        if(isEnable){
            for (NotificationChannel channel : channels) {
                crrAppNotifManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    crrAppNotifManager.createNotificationChannel(channel);
                }
            }
        }
        else{
            for (NotificationChannel channel : channels) {
                crrAppNotifManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    crrAppNotifManager.deleteNotificationChannel(channel.getId());
                }
            }
        }

        AppInfoDB appInfoUpdate = AppInfoDB.find(appInfo.packageName);
        if (appInfoUpdate == null) {
            appInfoUpdate = new AppInfoDB();
            appInfoUpdate.getAppInfoDbFromAppInfo(appInfo);
        }
        appInfoUpdate.isHaveToTurnOffNotif = !isEnable;
        appInfoUpdate.save();
        handler.post(() -> {
            Toast.makeText(activity, "Turn "+(isEnable?"on":"off")
                    + "notification of "+appInfo.packageName+" success", Toast.LENGTH_SHORT).show();
        });

    }

    public static List<String> getListPackageNameHaveToTurnOffNotif() {
        List<AppInfoDB> listAppTurnOffNotif = AppInfoDB.find(AppInfoDB.class,
                "is_have_to_be_freeze =1");
        List<String> listPackageName = new ArrayList<>();
        for (AppInfoDB app :
                listAppTurnOffNotif
        ) {
            listPackageName.add(app.packageName);
        }
        return listPackageName;
    }

    public static void turnOffNotif(List<Integer> notifIds,String packageName) {
        for (int notifId : notifIds) {
            notificationManager.cancel(packageName,notifId);
        }
    }

    public static boolean executeCommandWithSuShell(String command) {
        Log.i(TAG, "exec command");
        Handler handler = new Handler(Looper.getMainLooper());

        try {
            Shell.Threaded shell = Shell.Pool.SU.get();
            shell.run(command);
            handler.post(() -> {
                Toast.makeText(activity, "Done ", Toast.LENGTH_SHORT).show();
            });
            return true;
        } catch (Exception e) {
            handler.post(() -> {
                Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
            });
            return false;
        }
    }

    public static boolean executeCommandWithSuShell(String command, String textSuccess, String textFail) {
        Handler handler = new Handler(Looper.getMainLooper());
        try {
            Shell.Threaded shell = Shell.Pool.SU.get();
            shell.run(command);
            handler.post(() -> {
                Toast.makeText(activity, textSuccess, Toast.LENGTH_SHORT).show();
            });
            return true;
        } catch (Exception e) {
            Log.e(TAG, "ShellDiedException, probably we did not have root access. (???)");
            handler.post(() -> {
                Toast.makeText(activity, textFail, Toast.LENGTH_SHORT).show();
            });
            return false;
        }
    }

    public static boolean freezeAppUsingRoot(String packageName,
                                             Context context,
                                             boolean putScreenOffAfterFreezing) {
        String crrPackageName = context.getPackageName();
        try {
            Shell.Threaded shell = Shell.Pool.SU.get();
            if (packageName == crrPackageName) {
                if (putScreenOffAfterFreezing) {
                    shell.run("input keyevent 26");
                }
            } else {
                shell.run("am set-inactive " + packageName + " true");
                shell.run("am force-stop " + packageName);
                shell.run("am kill " + packageName);
            }
            return true;
        } catch (Shell.ShellDiedException e) {
            Log.e(TAG, "ShellDiedException, probably we did not have root access. (???)");
            return false;
        }
    }

    public static boolean freezeListAppUsingRoot(List<String> packages,
                                                 Context context,
                                                 boolean putScreenOffAfterFreezing) {
        try {
            Shell.Threaded shell = Shell.Pool.SU.get();
            packages.forEach(it -> {
                if (it == context.getPackageName()) {
                    if (putScreenOffAfterFreezing) {
                        try {
                            shell.run("input keyevent 26");
                        } catch (Shell.ShellDiedException e) {
                            Log.e(TAG, e.getMessage());
                        }
                    }
                } else {
                    try {
                        shell.run("am set-inactive " + it + " true");
                        shell.run("am force-stop " + it);
                        shell.run("am kill " + it);
                    } catch (Shell.ShellDiedException e) {
                        Log.e(TAG, e.getMessage());
                    }
                }
            });
            if (putScreenOffAfterFreezing) shell.run("input keyevent 26");
            return true;
        } catch (Shell.ShellDiedException e) {
            Log.e(TAG, "ShellDiedException, probably we did not have root access. (???)");
            return false;
        }
    }

    public static boolean executeCommandWithSuShell(String command,
                                                    EventVoid onSuccess,
                                                    EventVoid onFailed) {
        Log.i(TAG, "exec command");
        try {
            Shell.Threaded shell = Shell.Pool.SU.get();
            shell.run(command);
            onSuccess.callback();
            return true;
        } catch (Exception e) {
            onFailed.callback();
            Handler handler = new Handler(Looper.getMainLooper());

            handler.post(() -> {
                Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
            });
            return false;
        }
    }

    public static void openAppSetting(AppInfo appInfo) {
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + appInfo.packageName));
        activity.startActivity(intent);
    }

    public static AppCompatActivity getActivity() {
        return activity;
    }

    public static List<ActivityManager.RunningServiceInfo> getRunningServices() {
        return activityManager
                .getRunningServices(Integer.MAX_VALUE);
    }

    public static List<AppInfoDB> getListAppFromDB() {
        return AppInfoDB.listAll(AppInfoDB.class);
    }

    public static ActivityInfo[] getServices(String packageName) {
        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(
                    packageName, PackageManager.GET_ACTIVITIES);
            return packageInfo.activities;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean hasUseAccessibilityServicePermission(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();

            AccessibilityManager accessibilityManager = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (accessibilityManager == null) {
                return false;
            }

            // Check if the package name is enabled in the Accessibility settings
            for (AccessibilityServiceInfo serviceInfo : accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
                if (serviceInfo.getResolveInfo().serviceInfo.packageName.equals(context.getPackageName())) {
                    return true;
                }
            }
            // Check if the package has the UseAccessibilityService permission
            String[] requestedPermissions = packageManager.getPackageInfo(context.getPackageName(),
                    PackageManager.GET_PERMISSIONS).requestedPermissions;
            if (requestedPermissions != null) {
                for (String permission : requestedPermissions) {
                    if (permission.equals(android.Manifest.permission.BIND_ACCESSIBILITY_SERVICE)) {
                        return true;
                    }
                }
            }

        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, e.getMessage());
        }

        return false;
    }

    public static void removeAppFromList(String packageName, MenuContextType crrMenuContext) {
        AppInfoDB appInfoUpdate = AppInfoDB.find(packageName);
        if (appInfoUpdate != null) {
            if (crrMenuContext == MenuContextType.FREEZE_MENU) {
                appInfoUpdate.isHaveToBeFreeze = false;
            } else {
                appInfoUpdate.isHaveToTurnOffNotif = false;
            }
            appInfoUpdate.save();
        }
    }
    public static boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
