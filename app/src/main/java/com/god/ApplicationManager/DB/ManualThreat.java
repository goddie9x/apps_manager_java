package com.god.ApplicationManager.DB;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * One hand-entered threat-intel record (the "manual" source). The user can add
 * entries by hand or import them from JSON; export produces the same schema so
 * lists can be shared between devices.
 *
 * <p>Severity is {@code "malware"} or {@code "suspect"}. Manual entries beat
 * both the bundled and the remote-feed sources when merged.
 */
@Entity(tableName = "manual_threats")
public class ManualThreat {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "package_name")
    public String packageName = "";

    /** "malware" or "suspect". */
    @NonNull
    @ColumnInfo(name = "severity")
    public String severity = "suspect";

    @ColumnInfo(name = "label")
    public String label;

    @ColumnInfo(name = "note")
    public String note;

    @ColumnInfo(name = "added_at")
    public long addedAt;
}
