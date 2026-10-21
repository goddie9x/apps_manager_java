package com.god.ApplicationManager.DB;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * One durable freeze request. Freeze actions enqueue rows here and a
 * WorkManager worker drains them, so a freeze-all survives process death,
 * reboots and the caller activity finishing.
 */
@Entity(tableName = "freeze_queue")
public class FreezeQueueItem {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "package_name")
    public String packageName = "";

    @ColumnInfo(name = "attempts")
    public int attempts;

    @ColumnInfo(name = "enqueued_at")
    public long enqueuedAt;
}
