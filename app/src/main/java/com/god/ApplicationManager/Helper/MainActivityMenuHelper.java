package com.god.ApplicationManager.Helper;

import com.god.ApplicationManager.Enum.GroupAppType;
import com.god.ApplicationManager.Enum.OrderAppType;
import com.god.ApplicationManager.Enum.SortAppType;
import com.god.ApplicationManager.R;

public class MainActivityMenuHelper {

    public static GroupAppType getFilterType(int id) {
        if (id == R.id.not_filter_app) return GroupAppType.ALL;
        if (id == R.id.filter_system_app) return GroupAppType.SYSTEM_APP;
        if (id == R.id.filter_user_app) return GroupAppType.USER_APP;
        return null;
    }

    public static SortAppType getSortType(int id) {
        if (id == R.id.sort_a_to_z) return SortAppType.A_TO_Z;
        if (id == R.id.sort_z_to_a) return SortAppType.Z_To_A;
        return null;
    }

    public static OrderAppType getOrderType(int id) {
        if (id == R.id.order_by_app_name) return OrderAppType.NAME;
        if (id == R.id.order_by_package_name) return OrderAppType.PACKAGE_NAME;
        return null;
    }
}