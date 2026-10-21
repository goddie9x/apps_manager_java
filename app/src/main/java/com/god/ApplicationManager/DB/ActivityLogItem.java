package com.god.ApplicationManager.DB;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** One recorded user/app action (freeze, unfreeze, notification block, uninstall...) for the in-app history. */
@Entity(tableName = "activity_log")
public class ActivityLogItem {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "package_name")
    public String packageName = "";

    @NonNull
    @ColumnInfo(name = "action")
    public String action = "";

    @NonNull
    @ColumnInfo(name = "backend")
    public String backend = "";

    @ColumnInfo(name = "success")
    public boolean success;

    @ColumnInfo(name = "at")
    public long at;
}
