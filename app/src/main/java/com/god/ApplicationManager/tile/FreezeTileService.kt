package com.god.ApplicationManager.tile

import android.service.quicksettings.TileService
import com.god.ApplicationManager.DB.AppDatabase
import com.god.ApplicationManager.data.FreezeQueueRepository
import com.god.ApplicationManager.work.FreezeWorkScheduler
import com.god.ApplicationManager.work.FreezeWorker

/** QS Tile (F27): one tap enqueues the whole freeze list into the durable queue. */
class FreezeTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val ctx = applicationContext
        AppDatabase.dbIo.execute {
            val packages = AppDatabase.getInstance(ctx).appInfoDao().freezeList.map { it.packageName }
            FreezeQueueRepository.enqueue(ctx, packages)
            FreezeWorkScheduler.enqueueFreezeNow(ctx, FreezeWorker.TRIGGER_MANUAL)
        }
    }
}
