package com.god.ApplicationManager.DB;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * A flag the user attached to a package from the security scanner.
 *
 * <p>Kinds: {@code JUNK} (user-marked junk), {@code DANGEROUS} (user-marked
 * dangerous), {@code IGNORED} (whitelist - the package is excluded from future
 * scan results). Flags survive rescans; deleting a flag re-enables automatic
 * classification for that package.
 */
@Entity(tableName = "user_flags")
public class UserFlag {
    public static final String KIND_JUNK = "JUNK";
    public static final String KIND_DANGEROUS = "DANGEROUS";
    public static final String KIND_IGNORED = "IGNORED";

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "package_name")
    public String packageName = "";

    /** One of {@link #KIND_JUNK}, {@link #KIND_DANGEROUS}, {@link #KIND_IGNORED}. */
    @NonNull
    @ColumnInfo(name = "kind")
    public String kind = KIND_JUNK;

    @ColumnInfo(name = "note")
    public String note;

    @ColumnInfo(name = "flagged_at")
    public long flaggedAt;
}
