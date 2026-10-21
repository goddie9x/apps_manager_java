package com.god.ApplicationManager.Tasking;

import android.app.ProgressDialog;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.god.ApplicationManager.DB.AppInfoDB;
import com.god.ApplicationManager.DB.SettingsDB;
import com.god.ApplicationManager.Entity.AppInfo;
import com.god.ApplicationManager.data.AppListRepository;
import com.god.ApplicationManager.Enum.MenuContextType;
import com.god.ApplicationManager.Facade.AppManagerFacade;
import com.god.ApplicationManager.Interface.IActionForSelectedApp;
import com.god.ApplicationManager.Interface.ICallbackVoid;
import com.god.ApplicationManager.Interface.IHandleListApp;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class TaskingHandler {

    private static IHandleListApp IHandleListApp;
    private static List<AppInfo> listApp = new ArrayList<>();
    private static MenuContextType crrMenuContext;
    public static AppCompatActivity activity;

    private static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors() * 2;
    private static final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(THREAD_COUNT);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static List<AppInfo> getListApp() {
        return listApp;
    }

    public static void setListApp(List<AppInfo> crrListApp) {
        listApp = crrListApp != null ? crrListApp : new ArrayList<>();
        AppListRepository.publish(listApp);
        if (IHandleListApp != null) IHandleListApp.callback(listApp);
    }

    public static void setCallbackSetListAppToRecycleView(IHandleListApp action) {
        IHandleListApp = action;
    }

    public static void setActivity(AppCompatActivity activ) {
        activity = activ;
    }

    private static boolean checkCallBackSetListAppToRecycleViewInitialed() {
        if (IHandleListApp == null) {
            Toast.makeText(activity, "setListAppToRecycleView has bean null", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private static final java.util.concurrent.atomic.AtomicInteger searchSequence =
            new java.util.concurrent.atomic.AtomicInteger();

    /** Lowercases with Locale.ROOT and strips diacritics so "dong bang" finds "Đóng băng". */
    public static String normalizeForSearch(String text) {
        if (text == null) return "";
        String normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{Mn}+", "").toLowerCase(java.util.Locale.ROOT);
    }

    public static void execTaskForSearchApp(String queryText) {
        int mySequence = searchSequence.incrementAndGet();
        CompletableFuture.supplyAsync(() -> {
            if (queryText == null || queryText.isEmpty()) {
                return listApp;
            }
            String needle = normalizeForSearch(queryText);
            return listApp.stream().filter(appInfo ->
                            normalizeForSearch(appInfo.packageName).contains(needle) ||
                                    normalizeForSearch(appInfo.appName).contains(needle))
                    .collect(Collectors.toList());
        }, backgroundExecutor).thenAccept(newListApp -> mainHandler.post(() -> {
            // A newer query superseded this one while it was computing: drop the stale result.
            if (mySequence != searchSequence.get()) return;
            AppListRepository.publish(newListApp);
            if (IHandleListApp != null) IHandleListApp.callback(newListApp);
        }));
    }

    public static void handleTaskSetListAppToRecycleView() {
        AppListRepository.publish(listApp);
        if (IHandleListApp != null) IHandleListApp.callback(listApp);
    }

    public static void execTaskHandleGetListService(LinearLayout listServiceLayout, String crrPackageName) {
        if (listServiceLayout != null) {
            CompletableFuture.supplyAsync(() -> AppManagerFacade.getServices(crrPackageName), backgroundExecutor)
                    .thenAccept(listService -> {
                        if (listService != null) {
                            mainHandler.post(() -> {
                                listServiceLayout.removeAllViews();
                                int textColorCode = SettingsDB.getInstance().isEnableDarkMode ? Color.WHITE : Color.BLACK;
                                for (ActivityInfo crrService : listService) {
                                    CheckBox crrServiceCheckBox = new CheckBox(activity);
                                    crrServiceCheckBox.setText(crrService.processName);
                                    crrServiceCheckBox.setTextColor(textColorCode);
                                    crrServiceCheckBox.setChecked(crrService.enabled);
                                    crrServiceCheckBox.setOnCheckedChangeListener((v, isChecked) -> {
                                    });
                                    listServiceLayout.addView(crrServiceCheckBox);
                                }
                            });
                        }
                    });
        }
    }

    public static void execTaskUninstallApp(AppInfo appInfo) {
        ProgressDialog progressDialog = new ProgressDialog(activity);
        progressDialog.setMessage("Uninstalling...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        CompletableFuture.supplyAsync(() -> AppManagerFacade.uninstallApp(appInfo), backgroundExecutor)
                .thenAccept(result -> mainHandler.post(() -> {
                    if (progressDialog.isShowing()) progressDialog.dismiss();
                    Toast.makeText(activity, result ? "Uninstall success" : "Uninstall failed", Toast.LENGTH_SHORT).show();
                }));
    }

    public static void execTaskGetAllInstalledApp(MenuContextType menuContext, boolean forceReload) {
        if (menuContext == null) menuContext = MenuContextType.MAIN_MENU;
        AppListRepository.setLoading(true);
        crrMenuContext = menuContext;

        ProgressDialog progressDialog = null;
        if (forceReload || listApp == null || listApp.isEmpty()) {
            progressDialog = new ProgressDialog(activity);
            progressDialog.setMessage("Loading...");
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        final ProgressDialog finalProgressDialog = progressDialog;

        CompletableFuture.supplyAsync(() -> {
            if (forceReload || listApp == null || listApp.isEmpty()) {
                listApp = AppManagerFacade.GetAllInstalledApp();
            }
            List<AppInfo> newListApp;
            switch (crrMenuContext) {
                case FREEZE_MENU:
                    List<AppInfoDB> listFreezeApp = AppInfoDB.find(AppInfoDB.class, "is_have_to_be_freeze =1");
                    newListApp = getListAppHasPackageNameInBothList(listApp, listFreezeApp);
                    break;
                case NOTIFICATION_MENU:
                    List<AppInfoDB> listTurnOffNotif = AppInfoDB.find(AppInfoDB.class, "is_have_to_turn_off_notif =1");
                    newListApp = getListAppHasPackageNameInBothList(listApp, listTurnOffNotif);
                    break;
                default:
                    newListApp = listApp;
            }
            return newListApp;
        }, backgroundExecutor).thenAccept(newListApp -> mainHandler.post(() -> {
            if (finalProgressDialog != null && finalProgressDialog.isShowing()) finalProgressDialog.dismiss();
            AppListRepository.publish(newListApp);
            if (IHandleListApp != null) IHandleListApp.callback(newListApp);
        }));
    }

    private static List<AppInfo> getListAppHasPackageNameInBothList(List<AppInfo> listApp, List<AppInfoDB> targetList) {
        List<AppInfo> newListApp = new ArrayList<>();
        for (AppInfo appInfo : listApp) {
            for (AppInfoDB appInfoDB : targetList) {
                if (appInfo.packageName.equals(appInfoDB.packageName)) {
                    newListApp.add(appInfo);
                    break;
                }
            }
        }
        return newListApp;
    }

    public static void handleForSelectedApp(List<AppInfo> listAppTarget, IActionForSelectedApp action, ICallbackVoid onDone) {
        ProgressDialog progressDialog = new ProgressDialog(activity);
        progressDialog.setMessage("Loading...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        CompletableFuture.runAsync(() -> {
            ListIterator<AppInfo> listIteratorApp = listAppTarget.listIterator();
            boolean hasRoot = AppManagerFacade.hasRootPermission;
            while (listIteratorApp.hasNext()) {
                action.callback(listIteratorApp.next());
                if (!hasRoot) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }, backgroundExecutor).thenRun(() -> mainHandler.post(() -> {
            if (progressDialog.isShowing()) progressDialog.dismiss();
            onDone.callback();
        }));
    }
}