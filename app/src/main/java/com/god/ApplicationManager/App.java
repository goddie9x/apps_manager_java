package com.god.ApplicationManager;

import android.app.Application;

import com.god.ApplicationManager.DB.AppDatabase;
import com.google.android.material.color.DynamicColors;

/** Application entry point: initialises the Room database (and the one-time legacy Sugar import). */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Material 3 dynamic colour: tints the UI from the user's wallpaper palette
        // on Android 12+; a no-op elsewhere. The manual light/dark choice still wins
        // for the base theme via MainActivity.getTheme().
        DynamicColors.applyToActivitiesIfAvailable(this);
        AppDatabase.getInstance(this);
    }
}
