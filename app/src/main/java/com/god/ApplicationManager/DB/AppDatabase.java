package com.god.ApplicationManager.DB;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Room database replacing the abandoned Sugar ORM.
 *
 * <p>On first creation it performs a one-time, best-effort import of the legacy
 * Sugar database ({@code god.db.ApplicationManager}) so existing users keep their
 * freeze / notification rules and settings. The legacy file is renamed to
 * {@code .bak} after a successful import and is never read again.
 *
 * <p>Main-thread queries are NOT allowed: legacy synchronous call sites route
 * through {@link #dbIo} (a single-thread executor that also serialises writes
 * before reads), and the settings row is preloaded at process start.
 */
@Database(entities = {AppInfoDB.class, SettingsDB.class, FreezeQueueItem.class, ActivityLogItem.class, BlockedNotificationItem.class, FreezeProfileItem.class}, version = 3, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static final String TAG = "AppDatabase";
    private static final String DB_NAME = "apps-manager.db";
    private static final String LEGACY_SUGAR_DB_NAME = "god.db.ApplicationManager";
    private static final String PREFS_NAME = "db-migration";
    private static final String PREF_LEGACY_IMPORTED = "legacy_sugar_imported";

    private static volatile AppDatabase instance;

    /** Serialises all legacy synchronous DB access; also orders writes before later reads. */
    public static final ExecutorService dbIo = Executors.newSingleThreadExecutor();

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `freeze_queue` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `package_name` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `enqueued_at` INTEGER NOT NULL)");
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `activity_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `package_name` TEXT NOT NULL, `action` TEXT NOT NULL, `backend` TEXT NOT NULL, `success` INTEGER NOT NULL, `at` INTEGER NOT NULL)");
            db.execSQL("CREATE TABLE IF NOT EXISTS `blocked_notifications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `package_name` TEXT NOT NULL, `title` TEXT, `at` INTEGER NOT NULL)");
            db.execSQL("CREATE TABLE IF NOT EXISTS `freeze_profiles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `packages` TEXT NOT NULL)");
            db.execSQL("ALTER TABLE `settings` ADD COLUMN `store_blocked_notifications` INTEGER NOT NULL DEFAULT 1");
            db.execSQL("ALTER TABLE `settings` ADD COLUMN `screen_off_freeze_delay_seconds` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `settings` ADD COLUMN `unused_app_threshold_days` INTEGER NOT NULL DEFAULT 60");
        }
    };

    public abstract AppInfoDao appInfoDao();

    public abstract SettingsDao settingsDao();

    public abstract FreezeQueueDao freezeQueueDao();

    public abstract FeatureDao featureDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    Context appContext = context.getApplicationContext();
                    instance = Room.databaseBuilder(appContext, AppDatabase.class, DB_NAME)
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                            .build();
                    importLegacySugarIfNeeded(appContext, instance);
                }
            }
        }
        return instance;
    }

    /** Returns the singleton; requires {@link #getInstance(Context)} to have run (done in App.onCreate). */
    public static AppDatabase getInstance() {
        if (instance == null) {
            throw new IllegalStateException("AppDatabase not initialised; App.onCreate must call getInstance(Context) first");
        }
        return instance;
    }

    private static void importLegacySugarIfNeeded(Context context, AppDatabase db) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(PREF_LEGACY_IMPORTED, false)) {
            return;
        }
        File legacyFile = context.getDatabasePath(LEGACY_SUGAR_DB_NAME);
        if (legacyFile == null || !legacyFile.exists()) {
            prefs.edit().putBoolean(PREF_LEGACY_IMPORTED, true).apply();
            return;
        }
        SQLiteDatabase legacy = null;
        try {
            legacy = SQLiteDatabase.openDatabase(legacyFile.getPath(), null, SQLiteDatabase.OPEN_READONLY);
            importAppInfoTable(legacy, db);
            importSettingsTable(legacy, db);
            legacy.close();
            legacy = null;
            File backup = new File(legacyFile.getPath() + ".bak");
            if (!legacyFile.renameTo(backup)) {
                Log.w(TAG, "Could not rename legacy Sugar DB to .bak; import flag still set to avoid loops");
            }
            prefs.edit().putBoolean(PREF_LEGACY_IMPORTED, true).apply();
            Log.i(TAG, "Legacy Sugar data imported into Room");
        } catch (Exception e) {
            // Never let a migration problem break app startup; the new DB simply starts empty.
            Log.e(TAG, "Legacy Sugar import failed; continuing with an empty database", e);
            if (legacy != null) {
                try {
                    legacy.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static boolean tableExists(SQLiteDatabase db, String table) {
        try (Cursor c = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?", new String[]{table})) {
            return c.moveToFirst();
        }
    }

    private static Set<String> columnsOf(SQLiteDatabase db, String table) {
        Set<String> columns = new HashSet<>();
        try (Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            int nameIndex = c.getColumnIndex("name");
            while (c.moveToNext()) {
                if (nameIndex >= 0) {
                    columns.add(c.getString(nameIndex).toUpperCase());
                }
            }
        }
        return columns;
    }

    private static boolean boolColumn(Cursor cursor, Set<String> columns, String column) {
        if (!columns.contains(column)) return false;
        int index = cursor.getColumnIndex(column);
        return index >= 0 && !cursor.isNull(index) && cursor.getInt(index) != 0;
    }

    private static String stringColumn(Cursor cursor, Set<String> columns, String column) {
        if (!columns.contains(column)) return null;
        int index = cursor.getColumnIndex(column);
        return index >= 0 && !cursor.isNull(index) ? cursor.getString(index) : null;
    }

    private static void importAppInfoTable(SQLiteDatabase legacy, AppDatabase db) {
        if (!tableExists(legacy, "APP_INFO_DB")) return;
        Set<String> columns = columnsOf(legacy, "APP_INFO_DB");
        try (Cursor c = legacy.rawQuery("SELECT * FROM APP_INFO_DB", null)) {
            while (c.moveToNext()) {
                String packageName = stringColumn(c, columns, "PACKAGE_NAME");
                if (packageName == null || packageName.isEmpty()) continue;
                AppInfoDB entity = new AppInfoDB();
                entity.packageName = packageName;
                entity.appName = stringColumn(c, columns, "APP_NAME");
                entity.isHaveToBeFreeze = boolColumn(c, columns, "IS_HAVE_TO_BE_FREEZE");
                entity.isHaveToTurnOffNotif = boolColumn(c, columns, "IS_HAVE_TO_TURN_OFF_NOTIF");
                db.appInfoDao().upsert(entity);
            }
        }
    }

    private static void importSettingsTable(SQLiteDatabase legacy, AppDatabase db) {
        if (!tableExists(legacy, "SETTINGS_DB")) return;
        Set<String> columns = columnsOf(legacy, "SETTINGS_DB");
        try (Cursor c = legacy.rawQuery("SELECT * FROM SETTINGS_DB LIMIT 1", null)) {
            if (!c.moveToFirst()) return;
            SettingsDB settings = new SettingsDB();
            settings.isShowSystemInfo = !columns.contains("IS_SHOW_SYSTEM_INFO")
                    || boolColumn(c, columns, "IS_SHOW_SYSTEM_INFO");
            settings.isEnableDarkMode = boolColumn(c, columns, "IS_ENABLE_DARK_MODE");
            if (columns.contains("FREEZE_MODE")) {
                int index = c.getColumnIndex("FREEZE_MODE");
                if (index >= 0 && !c.isNull(index)) settings.freezeMode = c.getInt(index);
            }
            settings.doNotShowTermOfUse = boolColumn(c, columns, "DO_NOT_SHOW_TERM_OF_USE");
            settings.doShowRootWarning = boolColumn(c, columns, "DO_SHOW_ROOT_WARNING");
            settings.doNotShowAccessibilityWarning = boolColumn(c, columns, "DO_NOT_SHOW_ACCESSIBILITY_WARNING");
            settings.doNotShowGuild = boolColumn(c, columns, "DO_NOT_SHOW_GUILD");
            settings.doNotWarningSystemApp = boolColumn(c, columns, "DO_NOT_WARNING_SYSTEM_APP");
            settings.doNotShowFreezeWarning = boolColumn(c, columns, "DO_NOT_SHOW_FREEZE_WARNING");
            settings.isDisableTurnOffNotification = boolColumn(c, columns, "IS_DISABLE_TURN_OFF_NOTIFICATION");
            db.settingsDao().upsert(settings);
        }
    }
}
