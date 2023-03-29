package com.god.ApplicationManager.UI;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.god.ApplicationManager.Adapeter.ListAppAdapter;
import com.god.ApplicationManager.DB.SettingsDB;
import com.god.ApplicationManager.Entity.AppInfo;
import com.god.ApplicationManager.Enum.BackStackState;
import com.god.ApplicationManager.Enum.GroupAppType;
import com.god.ApplicationManager.Enum.OrderAppType;
import com.god.ApplicationManager.Enum.SortAppType;
import com.god.ApplicationManager.Facade.AppManagerFacade;
import com.god.ApplicationManager.Permission.PermissionHandler;
import com.god.ApplicationManager.R;
import com.god.ApplicationManager.Tasking.TaskingHandler;
import com.god.ApplicationManager.Util.DialogUtils;
import com.god.ApplicationManager.databinding.ActivityMainBinding;
import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MainActivity extends AppCompatActivity {
    private AppManagerFacade appManagerFacade;
    private ListAppAdapter crrListListAppAdapter;
    private GroupAppType selectedGroupAppType = GroupAppType.ALL;
    private OrderAppType selectedOrderAppType = OrderAppType.NAME;
    private SortAppType selectedSortAppType = SortAppType.A_TO_Z;
    private MenuItem prevSelectGroupTypeItem;
    private MenuItem prevSelectOrderTypeItem;
    private MenuItem prevSelectSortTypeItem;
    private TextView appTitleLabel;
    private TextView amountTextLabel;
    private LinearLayout selectOptionBar;
    private Menu optionsMenu;
    private boolean isOpenSearchBar = false;
    private DrawerLayout mainDrawer;
    private Stack<BackStackState> backStack = new Stack<>();
    private TaskingHandler taskingHandler;
    private PermissionHandler permissionHandler;
    private Intent intentSettings;
    private TextView sortAppDes;
    private MenuItem darkModeItem;
    private MenuItem lightModeItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initPermission();
        initAppManagerFacade();
        initTaskingHandler();
        if(savedInstanceState==null) {
            setEnableDarkMode(SettingsDB.getInstance().isEnableDarkMode, false);
        }
        else {
            ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());
            initProperty();
            initLayout(binding);
            initListAppRecycleView();
            initSearchEvent();
            initSelectEvent();
            List<AppInfo> listApp = savedInstanceState.getParcelableArrayList("listApp");
            if (listApp==null) {
                taskingHandler.execTaskGetAllInstalledApp();
            } else {
                selectedGroupAppType = (GroupAppType) savedInstanceState.getSerializable("selectedGroupAppType");
                selectedOrderAppType = (OrderAppType) savedInstanceState.getSerializable("selectedOrderAppType");
                selectedSortAppType = (SortAppType) savedInstanceState.getSerializable("selectedSortAppType");
                isOpenSearchBar = savedInstanceState.getBoolean("isOpenSearchBar", false);
                taskingHandler.setListApp(listApp);
                List<AppInfo> listAppSelected = savedInstanceState.getParcelableArrayList("listAppSelected");
                if (listAppSelected != null) {
                    crrListListAppAdapter.setListAppSelected(listAppSelected);
                }
                if (isOpenSearchBar) {
                    backStack = (Stack<BackStackState>) savedInstanceState.getSerializable("backStack");
                }
            }
        }
    }

    private void initAppManagerFacade() {
        appManagerFacade = AppManagerFacade.GetInstance(this);
        appManagerFacade.getRootPermission();
    }

    private void initPermission() {
        permissionHandler = new PermissionHandler(this);
        permissionHandler.getPermissions();
    }

    private void initTaskingHandler() {
        taskingHandler = new TaskingHandler(this);
        taskingHandler.setCallbackSetListAppToRecycleView(
                this::setListAppToRecycleView);
    }

    private void initLayout(ActivityMainBinding binding) {
        setSupportActionBar(binding.toolbar);
        mainDrawer = binding.drawerLayout;
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, mainDrawer,
                binding.toolbar, R.string.navigation_draw_open, R.string.navigation_drawer_close);
        binding.drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        initDrawableLayoutEvent();
    }

    private void initDrawableLayoutEvent() {
        NavigationView navView = findViewById(R.id.nav_view);
        Menu menu = navView.getMenu();
        darkModeItem= menu.findItem(R.id.action_dark_mode);
        lightModeItem = menu.findItem(R.id.action_light_mode);

        navView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            switch (id) {
                case R.id.refresh_list_app: {
                    taskingHandler.execTaskGetAllInstalledApp();
                    break;
                }
                case R.id.action_dark_mode:
                    item.setChecked(true);
                    setEnableDarkMode(true,true);
                    break;
                case R.id.action_light_mode:
                    item.setChecked(true);
                    setEnableDarkMode(false,true);
                    break;
            }
            mainDrawer.closeDrawer(GravityCompat.START);
            return true;
        });
    }

    private void setEnableDarkMode(boolean isEnableDarkMode,boolean isHaveToUpdateDB) {
        if(darkModeItem!=null&&lightModeItem!=null){
            darkModeItem.setVisible(!isEnableDarkMode);
            lightModeItem.setVisible(isEnableDarkMode);
        }
        if(isHaveToUpdateDB){
            SettingsDB.getInstance().isEnableDarkMode = isEnableDarkMode;
            SettingsDB.saveSettings();
        }
        setTheme(isEnableDarkMode?R.style.Theme_ApplicationManager_DarkMode
                :R.style.Theme_ApplicationManager);
        recreate();
        /*AppCompatDelegate.setDefaultNightMode(isEnableDarkMode?
                AppCompatDelegate.MODE_NIGHT_NO:
                AppCompatDelegate.MODE_NIGHT_YES);*/
        /*getDelegate().setLocalNightMode(isEnableDarkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        recreate();*/
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionHandler.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AppInfo selectedAppInfo = crrListListAppAdapter.getSelectedAppInfo();
        if (selectedAppInfo != null) {
            switch (item.getItemId()) {
                case R.id.force_stop:
                    DialogUtils.showAlertDialog(this,
                            "Confirmation",
                            "Do you want to force stop app: " + selectedAppInfo.packageName,
                            (dialog, which) -> {
                                Toast.makeText(this,
                                        "Force stopping " + selectedAppInfo.packageName,
                                        Toast.LENGTH_SHORT).show();
                                appManagerFacade.forceStopApp(selectedAppInfo.packageName);
                            }
                    );
                    return true;
                case R.id.freeze:
                    //TODO: make freeze app
                    return true;
                case R.id.turn_off_notification:
                    //TODO: turn off notification
                    return true;
                case R.id.uninstall:
                    taskingHandler.execTaskUninstallApp(selectedAppInfo);
                    return true;
                case R.id.open_in_setting:
                    appManagerFacade.openAppSetting(selectedAppInfo.packageName);
                    return true;
                default:
                    return super.onContextItemSelected(item);
            }
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        optionsMenu = menu;
        return true;
    }

    @Override
    public void onBackPressed() {
        handleBackStack();
    }

    private void handleBackStack() {
        if (mainDrawer.isDrawerOpen(GravityCompat.START)) {
            mainDrawer.closeDrawer(GravityCompat.START);
        } else {
            if (backStack.empty()) {
                super.onBackPressed();
            } else {
                BackStackState prevBackStack = backStack.pop();
                switch (prevBackStack) {
                    case SEARCH:
                        setOpenSearchBar(true);
                        break;
                    case SELECT:
                        crrListListAppAdapter.toggleEnableSelect();
                        break;
                }
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if(taskingHandler!=null&&crrListListAppAdapter!=null){
            List<AppInfo> listApp = taskingHandler.getListApp();
            List<AppInfo> listAppSelected = crrListListAppAdapter.getListSelectedApp();
            outState.putParcelableArrayList("listApp", (ArrayList<? extends Parcelable>) listApp);
            outState.putParcelableArrayList("listAppSelected",
                    (ArrayList<? extends Parcelable>)listAppSelected);
            outState.putBoolean("isOpenSearchBar",isOpenSearchBar);
            outState.putSerializable("backStack",backStack);
        }
        outState.putSerializable("selectedGroupAppType",selectedGroupAppType);
        outState.putSerializable("selectedOrderAppType",selectedOrderAppType);
        outState.putSerializable("selectedSortAppType",selectedSortAppType);
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        boolean isHaveToUpdateListApp = false;

        isHaveToUpdateListApp = isHaveToUpdateListApp || handleMenuFilter(item, id);
        isHaveToUpdateListApp = isHaveToUpdateListApp || handleMenuOrder(item, id);
        isHaveToUpdateListApp = isHaveToUpdateListApp || handleMenuSort(item, id);
        if (isHaveToUpdateListApp) {
            item.setChecked(true);
            taskingHandler.handleTaskSetListAppToRecycleView();
        } else {
            handleMenuOther(id);
        }
        return super.onOptionsItemSelected(item);
    }

    private void initProperty() {
        appTitleLabel = findViewById(R.id.app_name_title);
        selectOptionBar = findViewById(R.id.select_options_bar);
        sortAppDes = findViewById(R.id.app_group_description);
    }

    private void handleMenuOther(int id) {
        switch (id) {
            case R.id.open_search_bar:
                handleOpenSearchBar();
                break;
            case R.id.select_multiple:
                toggleMultipleSelect();
                break;
        }
    }

    private void toggleMultipleSelect() {
        crrListListAppAdapter.toggleEnableSelect();
        if (crrListListAppAdapter.getSelectionState()) {
            selectOptionBar.setVisibility(View.VISIBLE);
            appTitleLabel.setVisibility(View.GONE);
            if (isOpenSearchBar) {
                backStack.push(BackStackState.SELECT);
            }
        } else {
            if (isOpenSearchBar) {
                handleBackStack();
            } else {
                selectOptionBar.setVisibility(View.GONE);
                appTitleLabel.setVisibility(View.VISIBLE);
            }
        }
    }

    private void handleOpenSearchBar() {
        setOpenSearchBar(true);
        appTitleLabel.setVisibility(View.GONE);
        selectOptionBar.setVisibility(View.GONE);
    }

    private void handleCheckEdMenuFilter(MenuItem item, GroupAppType selectedGroupApp) {
        if (prevSelectGroupTypeItem != null && item != prevSelectGroupTypeItem) {
            prevSelectGroupTypeItem.setChecked(false);
        }
        prevSelectGroupTypeItem = item;
        selectedGroupAppType = selectedGroupApp;
    }

    private void handleCheckEdMenuOrder(MenuItem item, OrderAppType selectedOrderApp) {
        if (prevSelectOrderTypeItem != null && item != prevSelectOrderTypeItem) {
            prevSelectOrderTypeItem.setChecked(false);
        }
        prevSelectOrderTypeItem = item;
        selectedOrderAppType = selectedOrderApp;
    }

    private void handleCheckEdMenuSort(MenuItem item, SortAppType selectedSortApp) {
        if (prevSelectSortTypeItem != null && item != prevSelectSortTypeItem) {
            prevSelectSortTypeItem.setChecked(false);
        }
        prevSelectSortTypeItem = item;
        selectedSortAppType = selectedSortApp;
    }

    private boolean handleMenuFilter(MenuItem item, int id) {
        boolean isHaveToUpdateListApp = false;
        switch (id) {
            case R.id.not_filter_app:
                handleCheckEdMenuFilter(item, GroupAppType.ALL);
                isHaveToUpdateListApp = true;
                break;
            case R.id.filter_system_app:
                handleCheckEdMenuFilter(item, GroupAppType.SYSTEM_APP);
                isHaveToUpdateListApp = true;
                break;
            case R.id.filter_user_app:
                handleCheckEdMenuFilter(item, GroupAppType.USER_APP);
                isHaveToUpdateListApp = true;
                break;
        }
        return isHaveToUpdateListApp;
    }

    private boolean handleMenuSort(MenuItem item, int id) {
        boolean isHaveToUpdateListApp = false;
        switch (id) {
            case R.id.sort_a_to_z:
                handleCheckEdMenuSort(item, SortAppType.A_TO_Z);
                isHaveToUpdateListApp = true;
                break;
            case R.id.sort_z_to_a:
                handleCheckEdMenuSort(item, SortAppType.Z_To_A);
                isHaveToUpdateListApp = true;
                break;
        }
        return isHaveToUpdateListApp;
    }

    private boolean handleMenuOrder(MenuItem item, int id) {
        boolean isHaveToUpdateListApp = false;
        switch (id) {
            case R.id.order_by_app_name:
                handleCheckEdMenuOrder(item, OrderAppType.NAME);
                isHaveToUpdateListApp = true;
                break;
            case R.id.order_by_package_name:
                handleCheckEdMenuOrder(item, OrderAppType.PACKAGE_NAME);
                isHaveToUpdateListApp = true;
                break;
        }
        return isHaveToUpdateListApp;
    }


    private void initListAppRecycleView() {
        RecyclerView listAppRecycleView = findViewById(R.id.list_app);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);

        listAppRecycleView.setLayoutManager(layoutManager);
        crrListListAppAdapter = new ListAppAdapter(new ArrayList<>());
        crrListListAppAdapter.setOnTouchEvent((listServiceLayout,
                                               appInfo)
                -> taskingHandler.execTaskHandleGetListService(listServiceLayout, appInfo.packageName));
        listAppRecycleView.setAdapter(crrListListAppAdapter);
        registerForContextMenu(listAppRecycleView);
        listAppRecycleView.setVerticalScrollBarEnabled(true);
    }

    private void setListAppToRecycleView(List<AppInfo> crrListApp) {
        if(crrListApp!=null){
            Stream<AppInfo> listAppStream = crrListApp.stream();
            listAppStream = handleFilterListAppStream(listAppStream);
            listAppStream = handleSortListAppStream(listAppStream);
            crrListApp = listAppStream.collect(Collectors.toList());
            crrListListAppAdapter.setListApp(crrListApp);
            sortAppDes.setText(sortAppDes.getText().toString() + crrListApp.size() + ")");
        }
    }

    private Stream handleSortListAppStream(Stream<AppInfo> listAppStream) {
        switch (selectedSortAppType) {
            case Z_To_A:
                return handleSortZtoAListAppStream(listAppStream);
            default:
                return handleSortAToZListAppStream(listAppStream);
        }
    }

    private Stream handleSortZtoAListAppStream(Stream<AppInfo> listAppStream) {
        switch (selectedOrderAppType) {
            case PACKAGE_NAME:
                return listAppStream.sorted((appInfoPrev, appInfo)
                        -> appInfo.packageName.compareToIgnoreCase(appInfoPrev.packageName));
            case NAME:
            default:
                return listAppStream.sorted((appInfoPrev, appInfo)
                        -> appInfo.appName.compareToIgnoreCase(appInfoPrev.appName));
        }
    }

    private Stream handleSortAToZListAppStream(Stream<AppInfo> listAppStream) {
        switch (selectedOrderAppType) {
            case PACKAGE_NAME:
                return listAppStream.sorted((appInfoPrev, appInfo)
                        -> appInfoPrev.packageName.compareToIgnoreCase(appInfo.packageName));
            case NAME:
            default:
                return listAppStream.sorted((appInfoPrev, appInfo)
                        -> appInfoPrev.appName.compareToIgnoreCase(appInfo.appName));
        }
    }

    private Stream handleFilterListAppStream(Stream<AppInfo> listAppStream) {
        switch (selectedGroupAppType) {
            case SYSTEM_APP:
                sortAppDes.setText(R.string.filter_sytem_app_amount_title);
                return listAppStream.filter(appInfo -> appInfo.isSystemApp);
            case USER_APP:
                sortAppDes.setText(R.string.filter_user_app_amount_title);
                return listAppStream.filter(appInfo -> !appInfo.isSystemApp);
            default:
                sortAppDes.setText(R.string.filter_all_app_amount_title);
                return listAppStream;
        }
    }

    private void initSearchEvent() {
        (findViewById(R.id.close_search_bar_btn)).setOnClickListener((v) -> {
            setOpenSearchBar(false);
            appTitleLabel.setVisibility(View.VISIBLE);
        });
        SearchView searchApp = findViewById(R.id.search_app);
        searchApp.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                taskingHandler.execTaskForSearchApp(newText);
                return false;
            }
        });
    }

    private void setOpenSearchBar(boolean isOpenSearchBar) {
        this.isOpenSearchBar = isOpenSearchBar;
        if (optionsMenu != null) {
            for (int i = 0; i < optionsMenu.size(); i++) {
                MenuItem crrGroup = optionsMenu.getItem(i);
                if (crrGroup.getItemId() != R.id.menu_other_options) {
                    crrGroup.setVisible(!isOpenSearchBar);
                }
            }
        }
        findViewById(R.id.search_bar).setVisibility(isOpenSearchBar ? View.VISIBLE : View.GONE);
        if (isOpenSearchBar) {
            SearchView searchView = findViewById(R.id.search_app);
            searchView.requestFocus();
            searchView.setIconified(false);
            searchView.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(searchView, InputMethodManager.SHOW_IMPLICIT);
            }, 200);
        }
    }


    private void initSelectEvent() {
        amountTextLabel = selectOptionBar.findViewById(R.id.amount_selected);

        crrListListAppAdapter.setOnChangeAmountSelectionEvent(listAppSelected -> {
            amountTextLabel.setText(listAppSelected.size() + "");
        });
        selectOptionBar.findViewById(R.id.close_select_options_bar).setOnClickListener(v -> {
            toggleMultipleSelect();
        });
        selectOptionBar.findViewById(R.id.select_all).setOnClickListener(v -> {
            crrListListAppAdapter.selectAll();
        });
        selectOptionBar.findViewById(R.id.deselect_all).setOnClickListener(v -> {
            crrListListAppAdapter.clearSelect();
        });
    }
}