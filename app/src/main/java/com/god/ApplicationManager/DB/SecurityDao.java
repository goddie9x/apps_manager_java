package com.god.ApplicationManager.DB;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface SecurityDao {
    // ---- Manual threat list ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertManual(ManualThreat threat);

    @Query("DELETE FROM manual_threats")
    void clearManual();

    @Query("SELECT * FROM manual_threats ORDER BY package_name ASC")
    List<ManualThreat> allManual();

    @Query("SELECT * FROM manual_threats WHERE package_name = :pkg LIMIT 1")
    ManualThreat findManual(String pkg);

    @Query("DELETE FROM manual_threats WHERE package_name = :pkg")
    void deleteManual(String pkg);

    // ---- User flags ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertFlag(UserFlag flag);

    @Query("SELECT * FROM user_flags ORDER BY flagged_at DESC")
    List<UserFlag> allFlags();

    @Query("SELECT * FROM user_flags WHERE package_name = :pkg LIMIT 1")
    UserFlag findFlag(String pkg);

    @Query("DELETE FROM user_flags WHERE package_name = :pkg")
    void deleteFlag(String pkg);

    @Query("DELETE FROM user_flags")
    void clearFlags();

    // ---- Remote feeds ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long upsertFeed(FeedInfo feed);

    @Update
    void updateFeed(FeedInfo feed);

    @Query("SELECT * FROM threat_feeds ORDER BY id ASC")
    List<FeedInfo> allFeeds();

    @Query("SELECT * FROM threat_feeds WHERE enabled = 1 ORDER BY id ASC")
    List<FeedInfo> enabledFeeds();

    @Query("SELECT * FROM threat_feeds WHERE url = :url LIMIT 1")
    FeedInfo findFeedByUrl(String url);

    @Query("DELETE FROM threat_feeds WHERE id = :id")
    void deleteFeed(long id);
}
