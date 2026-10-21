package com.god.ApplicationManager.DB;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.god.ApplicationManager.Entity.AppInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Per-app rule (freeze list / notification-off list), persisted with Room.
 *
 * <p>The static helpers keep the small call-site surface the app already uses;
 * they are implemented on top of {@link AppInfoDao}. New code should use the DAO
 * (or the repository introduced in the architecture batch) directly.
 */
@Keep
@Entity(tableName = "app_info")
public class AppInfoDB {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "package_name")
    public String packageName = "";

    @ColumnInfo(name = "app_name")
    public String appName;

    @ColumnInfo(name = "is_have_to_be_freeze")
    public boolean isHaveToBeFreeze;

    @ColumnInfo(name = "is_have_to_turn_off_notif")
    public boolean isHaveToTurnOffNotif;

    public AppInfoDB() {
    }

    private static AppInfoDao dao() {
        return AppDatabase.getInstance().appInfoDao();
    }

    public void save() {
        dao().upsert(this);
    }

    public static AppInfoDB find(String packageName) {
        if (packageName == null) return null;
        return dao().findByPackage(packageName);
    }

    /**
     * Compatibility shim for the old Sugar-style queries used by existing call sites.
     * Supports the clauses the app actually issues: an optional
     * {@code package_name=?} term plus optional {@code is_have_to_be_freeze = 1} /
     * {@code is_have_to_turn_off_notif = 1} flag terms.
     */
    public static List<AppInfoDB> find(Class<AppInfoDB> clazz, String whereClause, String... whereArgs) {
        String clause = whereClause == null ? "" : whereClause.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        List<AppInfoDB> result;
        if (clause.contains("package_name=?") && whereArgs != null && whereArgs.length > 0) {
            result = new ArrayList<>();
            AppInfoDB one = dao().findByPackage(whereArgs[0]);
            if (one != null) result.add(one);
        } else {
            result = new ArrayList<>(dao().getAll());
        }
        if (clause.contains("is_have_to_be_freeze")) {
            result.removeIf(app -> !app.isHaveToBeFreeze);
        }
        if (clause.contains("is_have_to_turn_off_notif")) {
            result.removeIf(app -> !app.isHaveToTurnOffNotif);
        }
        return result;
    }

    public static List<AppInfoDB> listAll(Class<AppInfoDB> clazz) {
        return dao().getAll();
    }

    public static AppInfoDB findOrCreateByPackageName(String packageName,
                                                      AppInfoDB defaultDataIfNotFound) {
        AppInfoDB existing = find(packageName);
        if (existing != null) {
            return existing;
        }
        defaultDataIfNotFound.save();
        return defaultDataIfNotFound;
    }

    public void Clone(AppInfoDB appInfoDB) {
        this.appName = appInfoDB.appName;
        this.packageName = appInfoDB.packageName;
        this.isHaveToBeFreeze = appInfoDB.isHaveToBeFreeze;
        this.isHaveToTurnOffNotif = appInfoDB.isHaveToTurnOffNotif;
    }

    public void getAppInfoDbFromAppInfo(AppInfo appInfo) {
        appName = appInfo.appName;
        packageName = appInfo.packageName;
    }

    public void CloneJustDefinedFields(AppInfoDB appInfoDB) {
        if (appInfoDB.appName != null && !appInfoDB.appName.isEmpty()) {
            this.appName = appInfoDB.appName;
        }
        if (appInfoDB.packageName != null && !appInfoDB.packageName.isEmpty()) {
            this.packageName = appInfoDB.packageName;
        }
        if (appInfoDB.isHaveToBeFreeze != this.isHaveToBeFreeze) {
            this.isHaveToBeFreeze = appInfoDB.isHaveToBeFreeze;
        }
        if (appInfoDB.isHaveToTurnOffNotif != this.isHaveToTurnOffNotif) {
            this.isHaveToTurnOffNotif = appInfoDB.isHaveToTurnOffNotif;
        }
    }
}
