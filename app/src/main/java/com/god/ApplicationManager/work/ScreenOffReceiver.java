package com.god.ApplicationManager.work;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Screen-off trigger for auto-freeze (replaces the old JobService polling loop).
 * ACTION_SCREEN_OFF is a protected system broadcast, so a manifest receiver is
 * allowed and reliable here; the work itself runs through WorkManager.
 */
public class ScreenOffReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
            FreezeWorkScheduler.enqueueFreezeNow(context, FreezeWorker.TRIGGER_SCREEN_OFF);
        }
    }
}
