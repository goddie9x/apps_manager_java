package com.god.ApplicationManager.DB;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AppInfoDao {
    @Query("SELECT * FROM app_info")
    List<AppInfoDB> getAll();

    @Query("SELECT * FROM app_info WHERE package_name = :packageName")
    AppInfoDB findByPackage(String packageName);

    @Query("SELECT * FROM app_info WHERE is_have_to_be_freeze = 1")
    List<AppInfoDB> getFreezeList();

    @Query("SELECT * FROM app_info WHERE is_have_to_turn_off_notif = 1")
    List<AppInfoDB> getTurnOffNotifList();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(AppInfoDB appInfo);
}
