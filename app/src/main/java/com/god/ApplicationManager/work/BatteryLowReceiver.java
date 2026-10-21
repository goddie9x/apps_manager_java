package com.god.ApplicationManager.work;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Low-battery trigger (F28): when the battery runs low, freeze the freeze-list
 * to squeeze out background drain. Goes through the same durable queue/worker
 * as every other freeze trigger.
 */
public class BatteryLowReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BATTERY_LOW.equals(intent.getAction())) {
            FreezeWorkScheduler.enqueueFreezeNow(context, FreezeWorker.TRIGGER_BATTERY_LOW);
        }
    }
}
