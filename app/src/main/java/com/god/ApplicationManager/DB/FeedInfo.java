package com.god.ApplicationManager.DB;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * One remote threat-intel feed (the "online" source). Feeds are fetched over
 * HTTPS on a daily WorkManager schedule and cached on disk in the app's own
 * normalized JSON schema, so a failed refresh never wipes the last good copy.
 */
@Entity(tableName = "threat_feeds")
public class FeedInfo {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "url")
    public String url = "";

    @ColumnInfo(name = "name")
    public String name = "";

    /** "json" (our schema) or "yaml" (stalkerware-indicators ioc.yaml). Auto-detected. */
    @ColumnInfo(name = "format")
    public String format = "json";

    @ColumnInfo(name = "enabled")
    public boolean enabled = true;

    @ColumnInfo(name = "last_fetch")
    public long lastFetch;

    /** "ok", "error: ..." or "never". */
    @ColumnInfo(name = "last_status")
    public String lastStatus = "never";

    @ColumnInfo(name = "entry_count")
    public int entryCount;
}
