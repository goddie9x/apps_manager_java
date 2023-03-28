package com.god.ApplicationManager.Service;

import static android.provider.Settings.System.SCREEN_BRIGHTNESS;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import com.god.ApplicationManager.UI.FreezeShortcutActivity;

public class FreezeOnScreenOff {
    private static final String TAG="FreezeOnScreenOff";
    private static  BroadcastReceiver screenReceiver;

    public interface VoidCallback{
        void callback();
    }
    public static BroadcastReceiver freezeOnScreenOff_init(Context context, final Runnable screenLockerFunction) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        initReciver();
        context.registerReceiver(screenReceiver, filter);
        return screenReceiver;
    }

    private static void initReciver() {
        if(screenReceiver==null){
            screenReceiver = new BroadcastReceiver(){
                // TODO Do also use the screenLockerFunction in newer versions of Android
                private long lastTime = 0L;

                private int originalBrightness = -1;
                private int originalBrightnessMode = -1;
                private int originalTimeout = -1;

                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction() == Intent.ACTION_SCREEN_OFF) {
                        Log.i(TAG, "Screen off.");
                        resetScreenIfNecessary(context);

                        // If the power button is pressed during freezing, freezing should be aborted:
                        if (FreezeShortcutActivity.activity!=null
                                &&FreezeShortcutActivity.activity.isWorking) {
                            FreezeShortcutActivity.activity.finish();
                            FreezeService.stopAnyCurrentFreezing();
                            return;
                        }

                        FreezeService.stopAnyCurrentFreezing(); // If a freeze was already running, stop it

                        /*if (com.god.ApplicationManager.DB.Settings.getInstance().isFreezeWhileScreenOff
                                && context.prefUseAccessibilityService
                        ) {

                            if (getAppsPendingFreeze(context).isEmpty()) {
                                return;
                            }

                            // Throttle to once a minute:
                            if (lastTime + 60 * 1000 > System.currentTimeMillis()) {
                                lastTime = Math.min(System.currentTimeMillis(), lastTime);
                                return;
                            }

                            lastTime = System.currentTimeMillis();

                            enableScreenUntilFrozen(context);

                            FreezeShortcutActivity.freezeAppsPendingFreeze(context);
                        }*/
                    }
                }

                // TODO Actually use screenLockerFunction on newer versions of Android
                private void enableScreenUntilFrozen(Context context) {
                    Log.i(TAG, "turning screen on for freeze...");

                    PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
                    try {
                        ContentResolver contentResolver =  context.getContentResolver();
                        originalBrightness = Settings.System.getInt(contentResolver, SCREEN_BRIGHTNESS, 120);
                        Settings.System.putInt(contentResolver, SCREEN_BRIGHTNESS, 0);

                        originalBrightnessMode = Settings.System.getInt(contentResolver,
                                SCREEN_BRIGHTNESS_MODE, SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
                        Settings.System.putInt(contentResolver, SCREEN_BRIGHTNESS_MODE, SCREEN_BRIGHTNESS_MODE_MANUAL);
                    } catch (SecurityException e) {
                        Log.w(TAG, "Could not change screen brightness");
                    }

                    /*FreezeShortcutActivity.onFreezeFinishedListener = {
                            turnScreenOffAfterFreeze(wl, kl, this);
                    }*/
                }
/*
                private fun turnScreenOffAfterFreeze(
                        wl: PowerManager.WakeLock?,
                        @Suppress("DEPRECATION") kl: KeyguardManager.KeyguardLock,
                        context: Context
                ) {
                    Log.i(TAG, "turning screen off after freeze...")

                    try {
                        wl?.release()
                    } catch (e: RuntimeException) { // See https://stackoverflow.com/a/24057982
                        Log.w(TAG, "release failed: ${e.message}")
                    }

                    kl.reenableKeyguard()

                    // Turn screen off:
                    try {
                        originalTimeout = Settings.System.getInt(context.contentResolver, SCREEN_OFF_TIMEOUT, 1 * 60 * 1000)
                        Settings.System.putInt(context.contentResolver, SCREEN_OFF_TIMEOUT, 0)
                    } catch (e: SecurityException) {
                        Log.w(TAG, "Could not change screen timeout")
                    }

                    // We do not have to take care about calling resetScreenIfNecessary here;
                    // onReceive will call it when the screen goes off
                }
*/
                /**
                 * Resets the settings that were changed
                 */
                private void resetScreenIfNecessary(Context context) {
                    try {
                        ContentResolver contentResolver= context.getContentResolver();
                        if (originalBrightness >= 0) {
                            Log.i(TAG, "Reset brightness");
                            Settings.System.putInt(contentResolver,
                                    SCREEN_BRIGHTNESS, originalBrightness);
                            originalBrightness = -1;
                        }
/*
                        if (originalBrightnessMode != null) {
                            Log.i(TAG, "Reset brightness mode");
                            Settings.System.putInt(contentResolver,
                                    SCREEN_BRIGHTNESS_MODE, originalBrightnessMode.intValue());
                            originalBrightnessMode = null;
                        }

                        if (originalTimeout >= 0) {
                            Log.i(TAG, "Reset timeout");
                            Settings.System.putInt(contentResolver, SCREEN_OFF_TIMEOUT, originalTimeout);
                            originalTimeout = -1;
                        }
*/
                    } catch ( SecurityException e) {
                        Log.e(TAG, "Could not change screen brightness and timeout");
                    }
                }
            };
        }
    }

}
