package com.god.ApplicationManager.DB;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** A named set of packages that can be frozen together (work / sleep / gaming...). */
@Entity(tableName = "freeze_profiles")
public class FreezeProfileItem {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "name")
    public String name = "";

    /** Comma-separated package names. */
    @NonNull
    @ColumnInfo(name = "packages")
    public String packages = "";
}
