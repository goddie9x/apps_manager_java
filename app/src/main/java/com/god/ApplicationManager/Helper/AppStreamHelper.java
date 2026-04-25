package com.god.ApplicationManager.Helper;

import android.widget.TextView;

import com.god.ApplicationManager.Entity.AppInfo;
import com.god.ApplicationManager.Enum.GroupAppType;
import com.god.ApplicationManager.Enum.OrderAppType;
import com.god.ApplicationManager.Enum.SortAppType;
import com.god.ApplicationManager.R;

import java.util.stream.Stream;

public class AppStreamHelper {

    public static Stream<AppInfo> handleSort(Stream<AppInfo> stream, SortAppType sortType, OrderAppType orderType) {
        if (sortType == SortAppType.Z_To_A) {
            if (orderType == OrderAppType.PACKAGE_NAME) {
                return stream.sorted((a, b) -> b.packageName.compareToIgnoreCase(a.packageName));
            }
            return stream.sorted((a, b) -> b.appName.compareToIgnoreCase(a.appName));
        } else {
            if (orderType == OrderAppType.PACKAGE_NAME) {
                return stream.sorted((a, b) -> a.packageName.compareToIgnoreCase(b.packageName));
            }
            return stream.sorted((a, b) -> a.appName.compareToIgnoreCase(b.appName));
        }
    }

    public static Stream<AppInfo> handleFilter(Stream<AppInfo> stream, GroupAppType groupType, TextView sortAppDes) {
        switch (groupType) {
            case SYSTEM_APP:
                sortAppDes.setText(R.string.filter_system_app_amount_title);
                return stream.filter(appInfo -> appInfo.isSystemApp);
            case USER_APP:
                sortAppDes.setText(R.string.filter_user_app_amount_title);
                return stream.filter(appInfo -> !appInfo.isSystemApp);
            case ALL:
            default:
                sortAppDes.setText(R.string.filter_all_app_amount_title);
                return stream;
        }
    }
}