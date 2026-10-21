package com.god.ApplicationManager.DB;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface FreezeQueueDao {
    @Query("SELECT * FROM freeze_queue ORDER BY enqueued_at ASC")
    List<FreezeQueueItem> getAll();

    @Insert
    void insert(FreezeQueueItem item);

    @Query("DELETE FROM freeze_queue WHERE id = :id")
    void deleteById(long id);

    @Query("UPDATE freeze_queue SET attempts = attempts + 1 WHERE id = :id")
    void incrementAttempts(long id);

    @Query("SELECT COUNT(*) FROM freeze_queue")
    int count();
}
