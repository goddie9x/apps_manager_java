package com.god.ApplicationManager.DB;

import androidx.annotation.Keep;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.god.ApplicationManager.Service.NotificationService;

/**
 * Single-row app settings, persisted with Room (table {@code settings}, id = 1).
 * The getInstance/saveSettings surface is kept for existing call sites.
 */
@Keep
@Entity(tableName = "settings")
public class SettingsDB {
    @PrimaryKey
    public long id = 1;

    @ColumnInfo(name = "is_show_system_info")
    public boolean isShowSystemInfo = true;

    @ColumnInfo(name = "is_enable_dark_mode")
    public boolean isEnableDarkMode;

    @ColumnInfo(name = "freeze_mode")
    public int freezeMode;

    @ColumnInfo(name = "do_not_show_term_of_use")
    public boolean doNotShowTermOfUse;

    @ColumnInfo(name = "do_show_root_warning")
    public boolean doShowRootWarning;

    @ColumnInfo(name = "do_not_show_accessibility_warning")
    public boolean doNotShowAccessibilityWarning;

    @ColumnInfo(name = "do_not_show_guild")
    public boolean doNotShowGuild;

    @ColumnInfo(name = "do_not_warning_system_app")
    public boolean doNotWarningSystemApp;

    @ColumnInfo(name = "do_not_show_freeze_warning")
    public boolean doNotShowFreezeWarning;

    @ColumnInfo(name = "is_disable_turn_off_notification")
    public boolean isDisableTurnOffNotification;

    private static SettingsDB instance;

    public SettingsDB() {
    }

    public static SettingsDB getInstance() {
        if (instance == null) {
            SettingsDB stored = AppDatabase.getInstance().settingsDao().getSettings();
            if (stored == null) {
                stored = new SettingsDB();
                AppDatabase.getInstance().settingsDao().upsert(stored);
            }
            instance = stored;
        }
        return instance;
    }

    public void save() {
        AppDatabase.getInstance().settingsDao().upsert(this);
    }

    public static void saveSettings() {
        getInstance().save();
    }

    public static boolean toggleDisableAutoTurnOffNotificationState() {
        SettingsDB settings = getInstance();
        settings.isDisableTurnOffNotification = !settings.isDisableTurnOffNotification;
        NotificationService.getDisableNotificationStatus();
        settings.save();
        return settings.isDisableTurnOffNotification;
    }
}
