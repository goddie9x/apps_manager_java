package com.god.ApplicationManager.Helper;

import com.god.ApplicationManager.Adapter.ListAppAdapter;
import com.god.ApplicationManager.Entity.AppInfo;
import com.god.ApplicationManager.Facade.AppManagerFacade;
import com.god.ApplicationManager.Interface.IActionForSelectedApp;
import com.god.ApplicationManager.Interface.IBooleanReciverCallback;
import com.god.ApplicationManager.Interface.ICallbackVoid;
import com.god.ApplicationManager.Tasking.TaskingHandler;

public class MainActivityActionHelper {

    public static void startActionForSelectedApp(ListAppAdapter adapter, AppInfo selectedApp,
                                                 IActionForSelectedApp action, IBooleanReciverCallback warningCallback) {
        if (adapter.getSelectionState()) {
            TaskingHandler.handleForSelectedApp(adapter.getListSelectedApp(),
                    appInfo -> AppManagerFacade.proxyHandleRunAction(appInfo, action, warningCallback),
                    adapter::clearSelectedAppInfo);
        } else {
            AppManagerFacade.proxyHandleRunAction(selectedApp, action, warningCallback);
        }
    }

    public static void startActionForSelectedApp(ListAppAdapter adapter, AppInfo selectedApp,
                                                 IActionForSelectedApp action, ICallbackVoid onDone,
                                                 IBooleanReciverCallback warningCallback) {
        if (adapter.getSelectionState()) {
            TaskingHandler.handleForSelectedApp(adapter.getListSelectedApp(),
                    appInfo -> AppManagerFacade.proxyHandleRunAction(appInfo, action, warningCallback), () -> {
                        adapter.clearSelectedAppInfo();
                        onDone.callback();
                    });
        } else {
            AppManagerFacade.proxyHandleRunAction(selectedApp, action, warningCallback);
            onDone.callback();
        }
    }

    public static void startActionForSelectedAppWithoutWarning(ListAppAdapter adapter, AppInfo selectedApp,
                                                               IActionForSelectedApp action, ICallbackVoid onDone) {
        if (adapter.getSelectionState()) {
            TaskingHandler.handleForSelectedApp(adapter.getListSelectedApp(),
                    action, () -> {
                        adapter.clearSelectedAppInfo();
                        onDone.callback();
                    });
        } else {
            action.callback(selectedApp);
            onDone.callback();
        }
    }
}