package com.god.ApplicationManager.DB;

import com.orm.SugarRecord;

public class AppInfo extends SugarRecord {
    public String appName;
    public String packageName;
    public boolean isHaveToBeFreeze;
    public boolean isHaveToTurnOffNotif;
    public AppInfo(){
    }
}
