package com.god.ApplicationManager.Tasking;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Handler;
import android.os.Looper;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.god.ApplicationManager.DB.AppInfoDB;
import com.god.ApplicationManager.DB.SettingsDB;
import com.god.ApplicationManager.Entity.AppInfo;
import com.god.ApplicationManager.Enum.MenuContextType;
import com.god.ApplicationManager.Facade.AppManagerFacade;
import com.god.ApplicationManager.R;
import com.god.ApplicationManager.Service.FreezeService;
import com.god.ApplicationManager.Service.NotificationService;
import com.god.ApplicationManager.Util.AsyncTaskBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TaskingHandler {

    public interface SetListAppToRecycleView{
        void callback(List<AppInfo>listApp);
    }
    public interface ActionForSelectedApp{
        void callback(AppInfo listSelectedApp);
    }

    private static SetListAppToRecycleView setListAppToRecycleView;
    private static  List<AppInfo> listApp;
    private static  MenuContextType crrMenuContext;

    public static AppCompatActivity activity;
    public static  List<AppInfo> getListApp() {
        return listApp;
    }
    public static  void setListApp(List<AppInfo> crrListApp){
        listApp = crrListApp;
        if(!checkCallBackSetListAppToRecycleViewInitialed())return;
        setListAppToRecycleView.callback(listApp);
    }
    public static void setCallbackSetListAppToRecycleView(SetListAppToRecycleView action) {
        setListAppToRecycleView = action;
    }
    public static void setActivity(AppCompatActivity activ){
        activity = activ;
    }
    private static boolean checkCallBackSetListAppToRecycleViewInitialed(){
        if(setListAppToRecycleView==null){
            Toast.makeText(activity,
                    "setListAppToRecycleView has bean null",Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }
    public static void execTaskForSearchApp(String queryText) {
        if(!checkCallBackSetListAppToRecycleViewInitialed())return;
        AsyncTaskBuilder<Void, Void, Void> taskSearchApp = new AsyncTaskBuilder<>();
        taskSearchApp.setDoInBackgroundFunc((val) -> {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> {
                List<AppInfo> newListApp;
                if (!queryText.isEmpty()) {
                    newListApp = listApp.stream().filter(appInfo -> appInfo
                                    .packageName.contains(queryText) || appInfo.appName.toLowerCase()
                                    .contains(queryText))
                            .collect(Collectors.toList());
                } else {
                    newListApp = listApp;
                }
                setListAppToRecycleView.callback(newListApp);
            });
            return null;
        });
        taskSearchApp.execute();
    }

    public static void handleTaskSetListAppToRecycleView() {
        if(!checkCallBackSetListAppToRecycleViewInitialed())return;

        AsyncTaskBuilder<Void, Void, Void> taskSetListAppToRecycleView = new AsyncTaskBuilder<>();
        taskSetListAppToRecycleView.setDoInBackgroundFunc(an -> {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> {
                    setListAppToRecycleView.callback(listApp);
            });
            return null;
        });
        taskSetListAppToRecycleView.execute();
    }

    @SuppressLint("ResourceAsColor")
    public static void execTaskHandleGetListService(
            LinearLayout listServiceLayout,
            String crrPackageName
    ) {
        if (listServiceLayout != null) {
            AsyncTaskBuilder<String, Void, Void> taskHandleGetListService = new AsyncTaskBuilder<>();
            taskHandleGetListService.setDoInBackgroundFunc(packageName -> {
                ActivityInfo[] listService = AppManagerFacade
                        .getServices((String) packageName[0]);
                if (listService != null) {
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(() -> {
                        listServiceLayout.removeAllViews();
                        int textColorCode = SettingsDB.getInstance().isEnableDarkMode?
                                R.color.white:R.color.black;
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
                return null;
            });
            taskHandleGetListService.execute(crrPackageName);
        }
    }


    public static void execTaskUninstallApp(AppInfo appInfo) {
        AsyncTaskBuilder<Void, Void, Boolean> taskUninstallApp = new AsyncTaskBuilder<>();
        ProgressDialog progressDialog = new ProgressDialog(activity);
        taskUninstallApp.setOnPreExecuteFunc(() -> {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> {
                progressDialog.setMessage("Uninstalling...");
                progressDialog.setCancelable(false);
                progressDialog.show();
            });
        });
        taskUninstallApp.setDoInBackgroundFunc(ts -> AppManagerFacade.uninstallApp(appInfo));
        taskUninstallApp.setOnPostExecuteFunc(
                result -> {
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(() -> {
                        if (progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }
                        Toast.makeText(activity,
                                (boolean) result ? "Uninstall success" : "Uninstall failed",
                                Toast.LENGTH_SHORT).show();
                    });
                }
        );

        taskUninstallApp.execute();
    }

    public static void execTaskGetAllInstalledApp(MenuContextType menuContext) {
        if(!checkCallBackSetListAppToRecycleViewInitialed())return;
        if(menuContext==null)menuContext = MenuContextType.MAIN_MENU;
        AsyncTaskBuilder<Void, Void, Void> taskGetAllInstalledApp = new AsyncTaskBuilder<>();
        ProgressDialog progressDialog = new ProgressDialog(activity);
        crrMenuContext = menuContext;

        taskGetAllInstalledApp.setDoInBackgroundFunc(ts -> {
            listApp = AppManagerFacade.GetAllInstalledApp();
            final List<AppInfo>newListApp;
            switch (crrMenuContext){
                case FREEZE_MENU:
                    List<AppInfoDB> listFreezeApp =
                            AppInfoDB.find(AppInfoDB.class,
                                    "is_have_to_be_freeze =1");
                    newListApp = getListAppHasPackageNameInBothList(listApp,listFreezeApp);
                    break;
                case NOTIFICATION_MENU:
                    List<AppInfoDB> listTurnOffNotif =
                            AppInfoDB.find(AppInfoDB.class,
                                    "is_have_to_turn_off_notif =1");
                    newListApp = getListAppHasPackageNameInBothList(listApp,listTurnOffNotif);
                    break;
                default:
                    newListApp = listApp;
            }
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> setListAppToRecycleView.callback(newListApp));
            return null;
        });
        taskGetAllInstalledApp.setOnPreExecuteFunc(() -> {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> {
                progressDialog.setMessage("Loading...");
                progressDialog.setCancelable(false);
                progressDialog.show();
            });
        });
        taskGetAllInstalledApp.setOnPostExecuteFunc(
                result -> {
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(() -> {
                        if (progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }
                    });
                }
        );
        taskGetAllInstalledApp.execute();
    }

    private static List<AppInfo> getListAppHasPackageNameInBothList(List<AppInfo> listApp, List<AppInfoDB> listFreezeApp) {
        List<AppInfo> newListApp = new ArrayList<>();
        for (AppInfo appInfo : listApp) {
            for (AppInfoDB appInfoDB : listFreezeApp) {
                if (appInfo.packageName.equals(appInfoDB.packageName)) {
                    newListApp.add(appInfo);
                    break; // Once a match is found, no need to check further.
                }
            }
        }
        return newListApp;
    }

    public static void handleForSelectedApp(List<AppInfo> listApp, ActionForSelectedApp action,
                                            AppManagerFacade.CallbackVoid onDone){
        AsyncTaskBuilder<Void, Void, Void> taskDoActionEachSelectedApp = new AsyncTaskBuilder<>();
        ProgressDialog progressDialog = new ProgressDialog(activity);

        taskDoActionEachSelectedApp.setDoInBackgroundFunc(ts -> {
            for (AppInfo appInfo:listApp) {
                action.callback(appInfo);
            }
            return null;
        });
        taskDoActionEachSelectedApp.setOnPreExecuteFunc(() -> {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> {
                progressDialog.setMessage("Loading...");
                progressDialog.setCancelable(false);
                progressDialog.show();
            });
        });
        taskDoActionEachSelectedApp.setOnPostExecuteFunc(
                result -> {
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(() -> {
                        if (progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }
                        onDone.callback();
                    });
                }
        );
        taskDoActionEachSelectedApp.execute();
    }
}
