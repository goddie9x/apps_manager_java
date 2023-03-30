package com.god.ApplicationManager.Tasking;

import android.app.ProgressDialog;
import android.content.pm.ActivityInfo;
import android.os.Handler;
import android.os.Looper;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.god.ApplicationManager.DB.AppInfoDB;
import com.god.ApplicationManager.Entity.AppInfo;
import com.god.ApplicationManager.Enum.MenuContextType;
import com.god.ApplicationManager.Facade.AppManagerFacade;
import com.god.ApplicationManager.Util.AsyncTaskBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TaskingHandler {


    public interface SetListAppToRecycleView{
        void callback(List<AppInfo>listApp);
    }
    private SetListAppToRecycleView setListAppToRecycleView;
    private List<AppInfo> listApp;
    private MenuContextType crrMenuContext;

    public List<AppInfo> getListApp() {
        return listApp;
    }
    public void setListApp(List<AppInfo> listApp){
        this.listApp = listApp;
        if(!checkCallBackSetListAppToRecycleViewInitialed())return;
        setListAppToRecycleView.callback(listApp);
    }
    public void setCallbackSetListAppToRecycleView(SetListAppToRecycleView setListAppToRecycleView) {
        this.setListAppToRecycleView = setListAppToRecycleView;
    }
    private AppCompatActivity activity;
    private AppManagerFacade appManagerFacade;
    public TaskingHandler(AppCompatActivity activity){
        this.activity = activity;
        appManagerFacade = AppManagerFacade.GetInstance(this.activity);
    }
    private boolean checkCallBackSetListAppToRecycleViewInitialed(){
        if(setListAppToRecycleView==null){
            Toast.makeText(activity,
                    "setListAppToRecycleView has bean null",Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }
    public void execTaskForSearchApp(String queryText) {
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

    public void handleTaskSetListAppToRecycleView() {
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

    public void execTaskHandleGetListService(
            LinearLayout listServiceLayout,
            String crrPackageName
    ) {
        if (listServiceLayout != null) {
            AsyncTaskBuilder<String, Void, Void> taskHandleGetListService = new AsyncTaskBuilder<>();
            taskHandleGetListService.setDoInBackgroundFunc(packageName -> {
                ActivityInfo[] listService = appManagerFacade
                        .getServices((String) packageName[0]);
                if (listService != null) {
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(() -> {
                        listServiceLayout.removeAllViews();

                        for (ActivityInfo crrService : listService) {
                            CheckBox crrServiceCheckBox = new CheckBox(activity);
                            crrServiceCheckBox.setText(crrService.processName);
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


    public void execTaskUninstallApp(AppInfo appInfo) {
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
        taskUninstallApp.setDoInBackgroundFunc(ts -> appManagerFacade.uninstallApp(appInfo));
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

    public void execTaskGetAllInstalledApp(MenuContextType crrMenuContext) {
        if(!checkCallBackSetListAppToRecycleViewInitialed())return;
        AsyncTaskBuilder<Void, Void, Void> taskGetAllInstalledApp = new AsyncTaskBuilder<>();
        ProgressDialog progressDialog = new ProgressDialog(activity);
        this.crrMenuContext = crrMenuContext;

        taskGetAllInstalledApp.setDoInBackgroundFunc(ts -> {
            listApp = appManagerFacade.GetAllInstalledApp();
            final List<AppInfo>newListApp;
            switch (this.crrMenuContext){
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

    private List<AppInfo> getListAppHasPackageNameInBothList(List<AppInfo> listApp, List<AppInfoDB> listFreezeApp) {
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

    public void handleSelectAllItemInRecycleView(){

    }
}
