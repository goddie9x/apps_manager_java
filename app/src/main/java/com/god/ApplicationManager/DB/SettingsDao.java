package com.god.ApplicationManager.DB;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    SettingsDB getSettings();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(SettingsDB settings);
}
