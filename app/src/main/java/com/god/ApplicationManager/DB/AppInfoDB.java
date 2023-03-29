package com.god.ApplicationManager.DB;

import com.orm.SugarRecord;

import java.util.List;

public class AppInfoDB extends SugarRecord {
    public String appName;
    public String packageName;
    public boolean isHaveToBeFreeze;
    public boolean isHaveToTurnOffNotif;
    public AppInfoDB(){
    }
    public static AppInfoDB findOrCreateByPackageName(String packageName,
                                                      AppInfoDB defaultDataIfNotFound){
        List<AppInfoDB> listAppInfoDD = AppInfoDB.find(AppInfoDB.class,
                "packageName=?",packageName);
        if(!listAppInfoDD.isEmpty()){
            return listAppInfoDD.get(0);
        }
        defaultDataIfNotFound.save();
        return defaultDataIfNotFound;
    }
    public static AppInfoDB updateOrCreate(AppInfoDB appInfoDB){
        List<AppInfoDB> listAppInfoDD = AppInfoDB.find(AppInfoDB.class,
                "packageName=?",appInfoDB.packageName);
        if(!listAppInfoDD.isEmpty()){
            appInfoDB = listAppInfoDD.get(0);
            appInfoDB.CloneJustDefinedFields(appInfoDB);
        }
        appInfoDB.save();
        return appInfoDB;
    }
    public void Clone(AppInfoDB appInfoDB){
        this.appName = appInfoDB.appName;
        this.packageName = appInfoDB.packageName;
        this.isHaveToBeFreeze = appInfoDB.isHaveToBeFreeze;
        this.isHaveToTurnOffNotif = appInfoDB.isHaveToTurnOffNotif;
    }
    public void CloneJustDefinedFields(AppInfoDB appInfoDB){
        if(!appInfoDB.appName.isEmpty()){
            this.appName = appInfoDB.appName;
        }
        if(!appInfoDB.packageName.isEmpty()){
            this.packageName = appInfoDB.packageName;
        }
        if(appInfoDB.isHaveToBeFreeze!=this.isHaveToBeFreeze){
            this.isHaveToBeFreeze = appInfoDB.isHaveToBeFreeze;
        }
        if(appInfoDB.isHaveToTurnOffNotif!=this.isHaveToTurnOffNotif){
            this.isHaveToTurnOffNotif = appInfoDB.isHaveToTurnOffNotif;
        }
    }
}
