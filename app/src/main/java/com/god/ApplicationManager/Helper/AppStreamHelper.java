package com.god.ApplicationManager.Helper;

import androidx.annotation.StringRes;

import com.god.ApplicationManager.Entity.AppInfo;
import com.god.ApplicationManager.Enum.GroupAppType;
import com.god.ApplicationManager.Enum.OrderAppType;
import com.god.ApplicationManager.Enum.SortAppType;
import com.god.ApplicationManager.R;

import java.util.stream.Stream;

public class AppStreamHelper {

    public static Stream<AppInfo> handleSort(Stream<AppInfo> stream, SortAppType sortType, OrderAppType orderType) {
        java.util.Comparator<AppInfo> comparator;
        switch (orderType) {
            case PACKAGE_NAME:
                comparator = (a, b) -> a.packageName.compareToIgnoreCase(b.packageName);
                break;
            case DATE_INSTALL:
                comparator = java.util.Comparator.comparingLong(a -> a.firstInstallTime);
                break;
            case DATE_UPDATED:
                comparator = java.util.Comparator.comparingLong(a -> a.lastUpdateTime);
                break;
            case SIZE:
                comparator = java.util.Comparator.comparingLong(a -> a.sizeBytes);
                break;
            case LAST_USED:
                comparator = java.util.Comparator.comparingLong(a -> a.lastUsedAt);
                break;
            case NAME:
            default:
                comparator = (a, b) -> a.appName.compareToIgnoreCase(b.appName);
                break;
        }
        if (sortType == SortAppType.Z_To_A) {
            comparator = comparator.reversed();
        }
        return stream.sorted(comparator);
    }

    @StringRes
    public static int getGroupTitleRes(GroupAppType groupType) {
        switch (groupType) {
            case SYSTEM_APP:
                return R.string.filter_system_app_amount_title;
            case USER_APP:
                return R.string.filter_user_app_amount_title;
            case ALL:
            default:
                return R.string.filter_all_app_amount_title;
        }
    }

    public static Stream<AppInfo> handleFilter(Stream<AppInfo> stream, GroupAppType groupType) {
        switch (groupType) {
            case SYSTEM_APP:
                return stream.filter(appInfo -> appInfo.isSystemApp);
            case USER_APP:
                return stream.filter(appInfo -> !appInfo.isSystemApp);
            case ALL:
            default:
                return stream;
        }
    }
}