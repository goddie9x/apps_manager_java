package com.god.ApplicationManager.DB;

import com.orm.SugarRecord;

import java.util.List;

public class SettingsDB extends SugarRecord {
    public boolean isEnableDarkMode;
    public int freezeMode;
    public boolean doNotShowTermOfUse;
    public boolean doShowRootWarning;
    public boolean doNotShowAccessibilityWarning;
    public boolean doNotShowGuild;
    public boolean doNotWarningSystemApp;
    public boolean doNotShowFreezeWarning;
    public boolean isDisableTurnOffNotification;
    private static SettingsDB instance;
    private SettingsDB(){

    }
    public static SettingsDB getInstance() {
        if (instance == null) {
            List<SettingsDB> settings = SettingsDB.listAll(SettingsDB.class);
            if(settings.isEmpty()){
                instance = new SettingsDB();
                instance.save();
            }
            else{
                instance = settings.get(0);
            }
        }
        return instance;
    }
    public static void saveSettings(){
        instance.save();
    }
}
