package com.god.ApplicationManager.DB;

import com.orm.SugarRecord;

public class Settings extends SugarRecord<Settings> {
    public int isEnableDarkMode;
    public int isFreezeWhileScreenOff;
    private static Settings instance;
    private Settings(){

    }
    public static Settings getInstance() {
        if (instance == null) {
            instance = new Settings();
            instance.save();
        }
        return instance;
    }
}
