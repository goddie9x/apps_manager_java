package com.god.ApplicationManager.DB;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface FeatureDao {
    // Activity log
    @Insert
    void insertLog(ActivityLogItem item);

    @Query("SELECT * FROM activity_log ORDER BY at DESC LIMIT :limit")
    List<ActivityLogItem> recentLog(int limit);

    @Query("DELETE FROM activity_log")
    void clearLog();

    // Blocked notification archive (capped by trimming in the writer)
    @Insert
    void insertBlocked(BlockedNotificationItem item);

    @Query("SELECT * FROM blocked_notifications ORDER BY at DESC LIMIT :limit")
    List<BlockedNotificationItem> recentBlocked(int limit);

    @Query("DELETE FROM blocked_notifications WHERE id NOT IN (SELECT id FROM blocked_notifications ORDER BY at DESC LIMIT :keep)")
    void trimBlocked(int keep);

    @Query("DELETE FROM blocked_notifications")
    void clearBlocked();

    // Freeze profiles
    @Insert
    void insertProfile(FreezeProfileItem item);

    @Query("SELECT * FROM freeze_profiles ORDER BY name ASC")
    List<FreezeProfileItem> allProfiles();

    @Query("DELETE FROM freeze_profiles WHERE id = :id")
    void deleteProfile(long id);
}
