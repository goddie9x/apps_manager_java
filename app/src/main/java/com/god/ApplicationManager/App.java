package com.god.ApplicationManager;

import android.app.Application;

import com.god.ApplicationManager.DB.AppDatabase;

/** Application entry point: initialises the Room database (and the one-time legacy Sugar import). */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppDatabase.getInstance(this);
    }
}
