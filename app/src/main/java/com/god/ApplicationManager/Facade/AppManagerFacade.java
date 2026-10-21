package com.god.ApplicationManager.Facade;

import android.app.ActivityManager;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.god.ApplicationManager.DB.AppInfoDB;
import com.god.ApplicationManager.DB.SettingsDB;
import com.god.ApplicationManager.Entity.AppInfo;
import com.god.ApplicationManager.privilege.PrivilegeManager;
import com.god.ApplicationManager.privilege.RootShell;
import com.god.ApplicationManager.Enum.FreezeServiceNextAction;
import com.god.ApplicationManager.Enum.MenuContextType;
import com.god.ApplicationManager.Interface.IActionForSelectedApp;
import com.god.ApplicationManager.Interface.IBooleanReciverCallback;
import com.god.ApplicationManager.Interface.ICallbackVoid;
import com.god.ApplicationManager.R;
import com.god.ApplicationManager.Service.FreezeService;
import com.god.ApplicationManager.UI.FreezeShortcutActivity;
import com.god.ApplicationManager.Util.DialogUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;


public class AppManagerFacade {
    private static PackageManager packageManager;
    private static ActivityManager activityManager;
    private static AppCompatActivity activity;
    private static NotificationManager notificationManager;
    private static StatusBarNotification[] activeNotifications;
    public static final int SDK_VERSION = android.os.Build.VERSION.SDK_INT;
    private static final String TAG = "app manager facade";
    private static final Pattern PACKAGE_NAME_PATTERN =
            Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$");
    private static final ExecutorService rootExecutor = Executors.newSingleThreadExecutor();
    private static volatile boolean rootCheckRunning = false;

    public static boolean isValidPackageName(String packageName) {
        return packageName != null && PACKAGE_NAME_PATTERN.matcher(packageName).matches();
    }

    public interface IEventToggle {
        void callback(boolean state);
    }

    public static boolean hasRootPermission = false;

    public static void setActivity(AppCompatActivity currentActivity) {
        activity = currentActivity;
        packageManager = activity.getPackageManager();
        activityManager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
        notificationManager = (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
        activeNotifications = notificationManager.getActiveNotifications();
    }

    public static List<AppInfo> GetAllInstalledApp() {
        List<AppInfo> appList = new ArrayList<>();
        List<android.content.pm.PackageInfo> installedPackages =
                packageManager.getInstalledPackages(0);
        for (android.content.pm.PackageInfo packageInfo : installedPackages) {
            ApplicationInfo applicationInfo = packageInfo.applicationInfo;
            if (applicationInfo == null) continue;
            // Icons are resolved lazily through IconCache when a row binds;
            // loading every icon here for hundreds of apps was pure scan cost.
            String appName = applicationInfo.loadLabel(packageManager).toString();
            String packageName = applicationInfo.packageName;
            AppInfo appInfo = new AppInfo(null,
                    appName, packageName,
                    (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0,
                    (applicationInfo.flags & ApplicationInfo.FLAG_STOPPED) == 0,
                    (applicationInfo.flags & ApplicationInfo.FLAG_SUSPENDED) != 0);
            appInfo.firstInstallTime = packageInfo.firstInstallTime;
            appInfo.lastUpdateTime = packageInfo.lastUpdateTime;
            appList.add(appInfo);
        }
        enrichWithUsageAndStorage(appList);
        return appList;
    }

    /**
     * Fills lastUsedAt and sizeBytes from UsageStats/StorageStats when Usage
     * Access is granted; both stay at their "unknown" defaults otherwise.
     * Runs on the caller's background thread (the scan path).
     */
    private static void enrichWithUsageAndStorage(List<AppInfo> appList) {
        if (activity == null) return;
        try {
            if (!com.god.ApplicationManager.Helper.UsageStatsHelper.hasUsageAccess(activity)) return;
            java.util.Map<String, com.god.ApplicationManager.Helper.UsageInfo> usage =
                    com.god.ApplicationManager.Helper.UsageStatsHelper.usageByPackage(activity, 30);
            for (AppInfo appInfo : appList) {
                com.god.ApplicationManager.Helper.UsageInfo info = usage.get(appInfo.packageName);
                if (info != null) appInfo.lastUsedAt = info.getLastTimeUsed();
                com.god.ApplicationManager.Helper.StorageInfo storage =
                        com.god.ApplicationManager.Helper.UsageStatsHelper.storageFor(activity, appInfo.packageName);
                if (storage != null) appInfo.sizeBytes = storage.getTotalBytes();
            }
        } catch (Exception e) {
            Log.w(TAG, "Usage/storage enrichment failed: " + e.getMessage());
        }
    }

    public static List<ActivityManager.RunningAppProcessInfo> GetAllRunningApp() {
        return activityManager.getRunningAppProcesses();
    }

    /** Starts an asynchronous root check; never blocks the calling (UI) thread. */
    public static void getRootPermission() {
        if (rootCheckRunning) return;
        rootCheckRunning = true;
        rootExecutor.execute(() -> {
            try {
                hasRootAccess();
            } finally {
                rootCheckRunning = false;
            }
        });
    }

    /** Blocking real root check (runs "id" through su). Call only from a background thread. */
    public static boolean hasRootAccess() {
        hasRootPermission = PrivilegeManager.refreshRootBlocking();
        return hasRootPermission;
    }

    public static void uninstallAppWithRoot(String packageName) {
        if (!isValidPackageName(packageName)) {
            Log.e(TAG, "Refusing root uninstall for invalid package name: " + packageName);
            return;
        }
        executeCommandWithSuShell("pm uninstall --user 0 " + packageName,
                String.format(activity.getString(R.string.uninstall_target_success), packageName),
                String.format(activity.getString(R.string.uninstall_target_failed), packageName));
    }

    public static boolean uninstallApp(AppInfo appInfo) {
        if (appInfo.isSystemApp) {
            if (hasRootPermission) {
                uninstallAppWithRoot(appInfo.packageName);
                return true;
            } else {
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(() -> Toast.makeText(activity,
                        activity.getString(R.string.cannot_uninstall_system_app_without_root), Toast.LENGTH_LONG).show());
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
        if (!isValidPackageName(packageName)) {
            Log.e(TAG, "Refusing root force-stop for invalid package name: " + packageName);
            return;
        }
        executeCommandWithSuShell("am force-stop " + packageName,
                "Kill " + packageName + " success",
                "Kill " + packageName + " failed");
    }

    public static void forceStopApp(AppInfo appInfo) {
        if (hasRootPermission) {
            forceStopAppWithRootPermission(appInfo.packageName);
        } else {
            if (SDK_VERSION < 29) {
                activityManager.killBackgroundProcesses(appInfo.packageName);
            } else {
                FreezeService.setNextAction(FreezeServiceNextAction.PRESS_FORCE_STOP);
                openAppSetting(appInfo);
            }
        }
    }

    public static void freezeAppWithRootPermission(String packageName) {
        if (!isValidPackageName(packageName)) {
            Log.e(TAG, "Refusing root freeze for invalid package name: " + packageName);
            return;
        }
        executeCommandWithSuShell("pm disable " + packageName,
                String.format(activity.getString(R.string.freeze_target_success), packageName),
                String.format(activity.getString(R.string.freeze_target_failed), packageName));
    }

    public static void freezeApp(AppInfo appInfo) {
        Handler handler = new Handler(Looper.getMainLooper());
        if (checkWhetherAppAlreadyInFreezeList(appInfo, handler)) return;
        if (PrivilegeManager.current(activity).getBackend()
                != com.god.ApplicationManager.privilege.PrivilegeBackend.NON_ROOT) {
            freezeAppWithRootPermission(appInfo.packageName);
        } else {
            FreezeShortcutActivity.freezeApp(appInfo.packageName, activity);
        }
        handleSaveAppToFreezeListInDb(appInfo, handler);
    }

    private static void handleSaveAppToFreezeListInDb(AppInfo appInfo, Handler handler) {
        AppInfoDB appInfoUpdate = AppInfoDB.find(appInfo.packageName);
        if (appInfoUpdate == null) {
            appInfoUpdate = new AppInfoDB();
            appInfoUpdate.getAppInfoDbFromAppInfo(appInfo);
        }
        appInfoUpdate.isHaveToBeFreeze = true;
        appInfoUpdate.save();
        handler.post(() -> Toast.makeText(activity, activity.getString(R.string.freeze) + appInfo.packageName + activity.getString(R.string.success), Toast.LENGTH_SHORT).show());
    }

    private static boolean checkWhetherAppAlreadyInFreezeList(AppInfo appInfo, Handler handler) {
        List<AppInfoDB> listAppCheck = AppInfoDB.find(AppInfoDB.class,
                "package_name=? and is_have_to_be_freeze = 1", appInfo.packageName);
        if (listAppCheck.size() > 0) {
            handler.post(() -> Toast.makeText(activity, appInfo.packageName + activity.getString(R.string.already_in_freeze_list), Toast.LENGTH_SHORT).show());
            return true;
        } else {
            return false;
        }
    }

    public static void setNotificationStateForApp(AppInfo appInfo, boolean isEnable) {
        Handler handler = new Handler(Looper.getMainLooper());
        if (!isEnable) {
            List<AppInfoDB> listAppCheck = AppInfoDB.find(AppInfoDB.class,
                    "package_name=? and is_have_to_turn_off_notif = 1", appInfo.packageName);
            if (listAppCheck.size() > 0) {
                handler.post(() -> Toast.makeText(activity, String.format(
                        activity.getString(R.string.already_in_turn_off_notification), appInfo.packageName),
                        Toast.LENGTH_SHORT).show());
                return;
            }
        }
        AppInfoDB appInfoUpdate = AppInfoDB.find(appInfo.packageName);
        if (appInfoUpdate == null) {
            appInfoUpdate = new AppInfoDB();
            appInfoUpdate.getAppInfoDbFromAppInfo(appInfo);
        }
        appInfoUpdate.isHaveToTurnOffNotif = !isEnable;
        appInfoUpdate.save();
        handler.post(() -> Toast.makeText(activity,
                String.format(
                        activity.getString(R.string.has_added_to_the_list_notification), appInfo.packageName),
                Toast.LENGTH_SHORT).show());
    }

    public static List<String> getListPackageNameHaveToTurnOffNotif() {
        List<AppInfoDB> listAppTurnOffNotif = AppInfoDB.find(AppInfoDB.class,
                "is_have_to_turn_off_notif =1");
        List<String> listPackageName = new ArrayList<>();
        for (AppInfoDB app : listAppTurnOffNotif) {
            listPackageName.add(app.packageName);
        }
        return listPackageName;
    }

    public static boolean toggleStateAutoTurnOffNotification(Context context, ICallbackVoid onDone) {
        boolean newState = SettingsDB.toggleDisableAutoTurnOffNotificationState();
        new Thread(() -> {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
                ShortcutInfo shortcut = shortcutManager.getDynamicShortcuts().stream()
                        .filter(s -> s.getId().equals("toggle_notification"))
                        .findFirst()
                        .orElse(null);
                if (shortcut != null) {
                    ShortcutInfo.Builder builder = new ShortcutInfo.Builder(context, shortcut.getId())
                            .setShortLabel(context.getString(newState ? R.string.enable_auto_turn_off_notification :
                                    R.string.disable_auto_turn_off_notification))
                            .setIcon(Icon.createWithResource(context, newState ? R.drawable.ic_notification_shortcut :
                                    R.drawable.ic_notification_off_shortcut));

                    shortcutManager.updateShortcuts(Collections.singletonList(builder.build()));
                }
            }
            new Handler(Looper.getMainLooper()).post(onDone::callback);
        }).start();
        Toast.makeText(
                context, context.getString(newState ? R.string.disable_auto_turn_off_notification :
                        R.string.enable_auto_turn_off_notification
                ), Toast.LENGTH_LONG).show();
        return newState;
    }

    /** Runs the command on a background thread and reports the real outcome. Returns true if dispatched. */
    public static boolean executeCommandWithSuShell(String command) {
        return executeCommandWithSuShell(command,
                activity != null ? activity.getString(R.string.done) : "Done",
                activity != null ? activity.getString(R.string.failed) : "Failed");
    }

    /** Runs the command on a background thread; the toast reflects the actual shell result. */
    public static boolean executeCommandWithSuShell(String command, String textSuccess, String textFail) {
        Handler handler = new Handler(Looper.getMainLooper());
        rootExecutor.execute(() -> {
            boolean ok = activity != null
                    ? PrivilegeManager.runShellCommand(activity, command)
                    : RootShell.run(command);
            handler.post(() -> {
                if (activity != null) {
                    Toast.makeText(activity, ok ? textSuccess : textFail, Toast.LENGTH_SHORT).show();
                }
            });
        });
        return true;
    }

    /** Freezes one app through the strongest privileged backend (root/Shizuku shell or Device Owner). */
    public static boolean freezeAppUsingRoot(String packageName,
                                             Context context,
                                             boolean putScreenOffAfterFreezing) {
        if (!isValidPackageName(packageName)) {
            Log.e(TAG, "Refusing freeze for invalid package name: " + packageName);
            return false;
        }
        if (packageName.equals(context.getPackageName())) {
            if (putScreenOffAfterFreezing) {
                PrivilegeManager.runShellCommand(context, "input keyevent 26");
            }
            return true;
        }
        return PrivilegeManager.current(context).standbyAndStop(context, packageName);
    }

    /** Batch variant of {@link #freezeAppUsingRoot}; false when the backend could not freeze everything. */
    public static boolean freezeListAppUsingRoot(List<String> packages,
                                                 Context context,
                                                 boolean putScreenOffAfterFreezing) {
        boolean allOk = true;
        for (String pkg : packages) {
            if (!freezeAppUsingRoot(pkg, context, false)) {
                allOk = false;
            }
        }
        if (putScreenOffAfterFreezing) {
            PrivilegeManager.runShellCommand(context, "input keyevent 26");
        }
        return allOk;
    }

    /** Callback variant: callbacks fire on the main thread with the real shell outcome. */
    public static boolean executeCommandWithSuShell(String command,
                                                    ICallbackVoid onSuccess,
                                                    ICallbackVoid onFailed) {
        Handler handler = new Handler(Looper.getMainLooper());
        rootExecutor.execute(() -> {
            boolean ok = activity != null
                    ? PrivilegeManager.runShellCommand(activity, command)
                    : RootShell.run(command);
            boolean success = ok;
            handler.post(() -> {
                if (success) {
                    onSuccess.callback();
                } else {
                    onFailed.callback();
                }
            });
        });
        return true;
    }

    public static void openApp(AppInfo appInfo) {
        try {
            Intent intent = packageManager.getLaunchIntentForPackage(appInfo.packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
            } else {
                Toast.makeText(activity, activity.getString(R.string.target_not_install_or_not_have_ui),
                        Toast.LENGTH_SHORT).show();
            }
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, activity.getString(R.string.target_not_install_or_not_have_ui),
                    Toast.LENGTH_SHORT).show();
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
        return activityManager.getRunningServices(Integer.MAX_VALUE);
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

    public static void proxyHandleRunAction(AppInfo appInfo, IActionForSelectedApp action, IBooleanReciverCallback changeDonotWarningCallback) {
        Looper mainLooper = Looper.getMainLooper();
        if (Looper.myLooper() == mainLooper) {
            proxyCheckSystemAppForAnAction(appInfo, action, changeDonotWarningCallback);
        } else {
            new Handler(mainLooper)
                    .post(() -> proxyCheckSystemAppForAnAction(appInfo, action, changeDonotWarningCallback));
        }
    }

    public static void proxyCheckSystemAppForAnAction(AppInfo appInfo,
                                                      IActionForSelectedApp action,
                                                      IBooleanReciverCallback changeDonotWarningCallback) {
        if (appInfo.isSystemApp && !SettingsDB.getInstance().doNotWarningSystemApp) {
            showWarningDialog((dialog, which) -> {
                action.callback(appInfo);
            }, (dialog, which) -> {
            }, (state) -> {
                SettingsDB.getInstance().doNotWarningSystemApp = state;
                SettingsDB.saveSettings();
                changeDonotWarningCallback.callback(state);
            });
        } else {
            action.callback(appInfo);
        }
    }

    public static void showWarningDialog(DialogInterface.OnClickListener positiveListener,
                                         DialogInterface.OnClickListener negativeListener,
                                         IEventToggle onDonotShowAgainCheckBoxChange) {
        LayoutInflater inflater = LayoutInflater.from(activity);
        View customView = inflater.inflate(R.layout.dialog_custom_layout, null);
        CheckBox donnotShowAgainCheckBox = customView.findViewById(R.id.donnot_show_checkbox);

        DialogUtils.showAlertDialog(activity, activity.getString(R.string.warning),
                activity.getString(R.string.do_with_action_app_waning),
                positiveListener,
                builder -> {
                    builder.setView(customView);
                    builder.setOnDismissListener(dialog ->
                            onDonotShowAgainCheckBoxChange.callback(donnotShowAgainCheckBox.isChecked()));
                    builder.setNegativeButton("No", negativeListener);
                });
    }

    public static boolean isAccessibilityServiceEnabled(Context context, Class<?> accessibilityService) {
        ComponentName expectedComponentName = new ComponentName(context, accessibilityService);
        String enabledServicesSetting = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServicesSetting == null) return false;
        TextUtils.SimpleStringSplitter colonSplitter = new TextUtils.SimpleStringSplitter(':');
        colonSplitter.setString(enabledServicesSetting);
        while (colonSplitter.hasNext()) {
            String componentNameString = colonSplitter.next();
            ComponentName enabledService = ComponentName.unflattenFromString(componentNameString);
            if (enabledService != null && enabledService.equals(expectedComponentName)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isNotificationServiceEnabled(Context context) {
        String pkgName = context.getPackageName();
        final String flat = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        if (!TextUtils.isEmpty(flat)) {
            final String[] names = flat.split(":");
            for (String name : names) {
                final ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null && TextUtils.equals(pkgName, cn.getPackageName())) {
                    return true;
                }
            }
        }
        return false;
    }
}