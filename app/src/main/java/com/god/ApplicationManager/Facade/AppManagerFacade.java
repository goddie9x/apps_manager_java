package com.god.ApplicationManager.Facade;

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
import android.os.IBinder;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.god.ApplicationManager.Entity.AppInfo;
import com.god.ApplicationManager.Util.DialogUtils;

import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;
public class AppManagerFacade {
    public interface EventVoid{
        void callback();
    }
    private final PackageManager packageManager;
    private final ActivityManager activityManager;
    private final AppCompatActivity activity;
    private static AppManagerFacade instance;
    private NotificationManager notificationManager;
    private StatusBarNotification[] activeNotifications;
    public static final int SDK_VERSION = android.os.Build.VERSION.SDK_INT;
    private static final String TAG = "app manager facade";
    public static boolean hasRootPermission=false;
    private Shell.Threaded shell;
    private AppManagerFacade(AppCompatActivity activity){
        this.activity = activity;
        this.packageManager = this.activity.getPackageManager();
        this.activityManager = (ActivityManager) this.activity.getSystemService(Context.ACTIVITY_SERVICE);
        this.notificationManager = (NotificationManager)this.activity
            .getSystemService(Context.NOTIFICATION_SERVICE);
        this.activeNotifications = this.notificationManager.getActiveNotifications();
    }
    public static AppManagerFacade GetInstance(AppCompatActivity activity){
        if(instance==null||instance.activity!=activity){
            instance = new AppManagerFacade((activity));
        }
        return instance;
    }
    public List<AppInfo> GetAllInstalledApp(){
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
    public List<ActivityManager.RunningAppProcessInfo> GetAllRunningApp(){
        return activityManager.getRunningAppProcesses();
    }
    public void getRootPermission(){
        if(!hasRootAccess()){
            try {
                shell = Shell.Pool.SU.get();
                Runtime.getRuntime().exec("su");
            }
            catch (IOException e){
                Toast.makeText(activity,e.getMessage(),Toast.LENGTH_LONG).show();
            } catch (Shell.ShellDiedException e) {
                Toast.makeText(activity,e.getMessage(),Toast.LENGTH_LONG).show();
            }
        }
    }
    public boolean hasRootAccess() {
        try {
            java.util.Scanner s = new java.util.Scanner(Runtime.getRuntime()
                    .exec(new String[]{"/system/bin/su","-c","cd / && ls"})
                    .getInputStream()).useDelimiter("\\A");
            hasRootPermission = !(s.hasNext() ? s.next() : "").equals("");
            return AppManagerFacade.hasRootPermission;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
    public void uninstallAppWithRoot(String packageName){
        executeCommandWithSuShell("pm uninstall  --user 0 "+packageName,
                "Uninstall "+packageName+" success",
                "Uninstall "+packageName+" failed");
    }
    public boolean uninstallApp(AppInfo appInfo){
        if(appInfo.isSystemApp){
            if(hasRootPermission) {
                uninstallAppWithRoot(appInfo.packageName);
                return true;
            }
            else{
                Toast.makeText(activity,
                        "You cannot uninstall system app without root",Toast.LENGTH_LONG).show();
                return false;
            }
        }
        else{
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(Uri.parse("package:" + appInfo.packageName));
            activity.startActivity(intent);
            return true;
        }
    }
    public void forceStopAppWithRootPermission(String packageName){
        executeCommandWithSuShell("am force-stop "+packageName,
                "Kill "+packageName+" success",
                "Kill "+packageName+" failed");
    }
    public void forceStopApp(String packageName) {
        Log.i(TAG, "forceStopApp");
        if(hasRootPermission){
            forceStopAppWithRootPermission(packageName);
        }
        else{
            //since android 10 (sdk 19) we cannot force stop app, then we open setting of this app
            //so we let users do it by their own
            if(SDK_VERSION<29){
                activityManager.killBackgroundProcesses(packageName);
            }
            else{
                DialogUtils.showAlertDialog(activity,"Manual",
                        "Your android version not support or you do not have root permission" +
                                "\n must force stop app as manual",(d,w)->{
                            openAppSetting(packageName);
                        });
            }
        }
    }
    public void freezeAppWithRootPermission(String packageName){
        executeCommandWithSuShell("pm disable "+packageName,
                "Freeze "+packageName+" success",
                "Freeze "+packageName+" failed");
    }
    public void freezeApp(String packageName){
        if(hasRootPermission){
            freezeAppWithRootPermission(packageName);
        }
        else{
            forceStopApp(packageName);
        }
    }
    public List<Integer> getAllNotifIdOfPackage(String packageName){
        List<Integer> appNotificationIds = new ArrayList<>();
        for (StatusBarNotification notification : activeNotifications) {
            if (notification.getPackageName().equals(packageName)) {
                appNotificationIds.add(notification.getId());
            }
        }
        return appNotificationIds;
    }
    public void turnOffNotifPermanenly(String packageName) throws PackageManager.NameNotFoundException {
        NotificationManager crrAppNotifManager = (NotificationManager) activity
                .createPackageContext(packageName, 0)
                .getSystemService(Context.NOTIFICATION_SERVICE);

        // Get a list of all the notification channels
        List<NotificationChannel> channels = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            channels = crrAppNotifManager.getNotificationChannels();
            for (NotificationChannel channel : channels) {
                crrAppNotifManager.deleteNotificationChannel(channel.getId());
            }
        }
        else{
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
                        packageName, android.os.Process.myUid() / 100000);

            } catch (Exception e) {
                Log.e(TAG,e.getMessage());
            }
        }
    }
    public void handleGetNotifs(){
        activeNotifications = notificationManager.getActiveNotifications();
    }
    public void turnOffNotif(int[] notifIds){
        for (int notifId:notifIds) {
            notificationManager.cancel(notifId);
        }
    }
    public void executeCommandWithSuShell(String command){
        try {
            Log.i(TAG, "exec command");
            try {
                shell.run(command);
                Toast.makeText (activity,"Done ",Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText (activity,"Failed",Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void executeCommandWithSuShell(String command,String textSuccess,String textFail){
        try {
            Log.i(TAG, "exec executeCommandWithSuShell");
            try {
                shell.run(command);
                Toast.makeText (activity,textSuccess,Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText (activity,textFail,Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void freezeAppsUsingRoot(List<String>packages,
                                           Context context,
                                           boolean putScreenOffAfterFreezing) {
        try {
            Shell.Threaded shell = Shell.Pool.SU.get();
            //if (Build.VERSION.SDK_INT >= 23) shell.run("dumpsys battery unplug")
            packages.forEach(it->{
                if (Build.VERSION.SDK_INT >= 23) {
                    try {
                        shell.run("am set-inactive $it true");
                        shell.run("am force-stop $it");
                        shell.run("am kill $it");
                    } catch (Shell.ShellDiedException e) {
                        Log.e(TAG,e.getMessage());
                    }
                }
                if (it == context.getPackageName()) {
                    if (putScreenOffAfterFreezing) {
                        try {
                            shell.run("input keyevent 26");
                        } catch (Shell.ShellDiedException e) {
                            Log.e(TAG,e.getMessage());
                        }
                    }
                }
            });
            if (putScreenOffAfterFreezing) shell.run("input keyevent 26");
        } catch (Shell.ShellDiedException e) {
            Log.e(TAG, "ShellDiedException, probably we did not have root access. (???)");
        }
    }
    public void executeCommandWithSuShell(String command,
                                          EventVoid onSuccess,
                                          EventVoid onFailed){
        try {
            Log.i(TAG, "exec command");
            try {
                Process suProcess = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(suProcess.getOutputStream());
                os.writeBytes("adb shell" + "\n");
                os.flush();
                os.writeBytes(command + "\n");
                os.flush();
                onSuccess.callback();
            } catch (IOException e) {
                onFailed.callback();
                Toast.makeText (activity,"Failed",Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            onFailed.callback();
            e.printStackTrace();
        }
    }
    public void openAppSetting(String packageName){
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + packageName));
        activity.startActivity(intent);
    }
    public AppCompatActivity getActivity() {
        return activity;
    }
    public List<ActivityManager.RunningServiceInfo> getRunningServices(){
        return activityManager
                .getRunningServices(Integer.MAX_VALUE);
    }
    public ActivityInfo[]getServices(String packageName) {
        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(
                    packageName, PackageManager.GET_ACTIVITIES);
            return packageInfo.activities;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }
}
