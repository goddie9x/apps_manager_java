package com.god.ApplicationManager.DB;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** A notification the listener cancelled, kept in the in-app archive (title/app/time only). */
@Entity(tableName = "blocked_notifications")
public class BlockedNotificationItem {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "package_name")
    public String packageName = "";

    @ColumnInfo(name = "title")
    public String title;

    @ColumnInfo(name = "at")
    public long at;
}
