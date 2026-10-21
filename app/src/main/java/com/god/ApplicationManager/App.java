package com.god.ApplicationManager;

import android.app.Application;

import com.god.ApplicationManager.DB.AppDatabase;
import com.god.ApplicationManager.DB.SettingsDB;
import com.god.ApplicationManager.privilege.PrivilegeManager;
import com.god.ApplicationManager.work.FreezeWorkScheduler;
import com.google.android.material.color.DynamicColors;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Application entry point: initialises the Room database (and the one-time legacy Sugar import). */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Material 3 dynamic colour: tints the UI from the user's wallpaper palette
        // on Android 12+; a no-op elsewhere. The manual light/dark choice still wins
        // for the base theme via MainActivity.getTheme().
        DynamicColors.applyToActivitiesIfAvailable(this);
        initDatabaseOffMainThread();
        PrivilegeManager.refreshRootAsync();
        FreezeWorkScheduler.schedulePeriodicBackstop(this);
    }

    /**
     * Builds the Room database (including the one-time legacy import) and preloads
     * the settings row on a worker thread, waiting briefly so that no later
     * main-thread access ever needs a database query (main-thread queries are
     * disabled). Bounded: a pathological device falls through after 5s and the
     * first real access pays the cost instead.
     */
    private void initDatabaseOffMainThread() {
        Future<?> init = Executors.newSingleThreadExecutor().submit(() -> {
            AppDatabase.getInstance(this);
            SettingsDB.preload();
        });
        try {
            init.get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // Startup must never hang or crash because of database warm-up.
        }
    }
}
