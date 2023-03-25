package com.god.ApplicationManager.UI;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.god.ApplicationManager.R;
import com.god.ApplicationManager.Service.FreezeService;

import java.util.ListIterator;

public class FreezeShortcutActivity extends AppCompatActivity {
    private boolean isBeingNewlyCreated = true;
    private ListIterator<String> appsToBeFrozenIter= null;
    private boolean isWorking = false;
    private boolean screenOff = false;
    private AppCompatActivity activity;
    private final String TAG="God freeze app shortcut";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        activity = this;
        isBeingNewlyCreated = true;
        Intent intent = getIntent();
        screenOff = (intent.getStringExtra("extraID") == "dyn_screenOff");

        if (Intent.ACTION_CREATE_SHORTCUT == intent.getAction()) {
            setResult(RESULT_OK, createShortcutResultIntent(this);
            finish();
        } else {
            FreezeService.stopAnyCurrentFreezing(); // Might be that there still was a previous (failed) freeze process, in this case stop it
            if (KeyguardManager(getSystemService(Context.KEYGUARD_SERVICE)).isKeyguardLocked &&
                    getPrefs(this).getBoolean("freeze_on_screen_off", false)
			) {
                onFreezeFinishedListener?.invoke(this)
                onFreezeFinishedListener = null
                freezeOnScreenOffFailedDialog()
                Log.e(TAG, "Screen not unlocked.")
                return
            }
            Log.i(TAG, "Performing Freeze.")
            isWorking = true
            FreezerService.doOnAppCouldNotBeFrozen = ::onAppCouldNotBeFrozen
            performFreeze()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // If activity != this, another instance of FreezeShortcutActivity might already have been started
        // and we must not clean the "static" things up here!
        if (activity == this) {
            Log.i(TAG, "Destroying, cleaning up")
            activity = null
            FreezerService.doOnAppCouldNotBeFrozen = null
            FreezerService.finishedFreezing()
        } else {
            Log.i(TAG, "Destroying, but not cleaning up because activity != this")
        }
        isWorking = false
    }

    private fun performFreeze() {

        if (isRootAvailable) {
            freezeAppsUsingRoot(getAppsPendingFreeze(this), this, screenOff)
            finish()
            return
        }

        val appsPendingFreeze = getAppsPendingFreeze(applicationContext)
        if (appsPendingFreeze.isEmpty()) {
            toast(getString(R.string.NothingToFreeze), Toast.LENGTH_SHORT)
            finish()
            return
        }

        if (FreezerService.isEnabled) {
            toast(getString(R.string.power_button_hint), Toast.LENGTH_LONG)
        }

        // The actual freezing work will be done in onResume(). Here we just create this iterator.
        appsToBeFrozenIter = appsPendingFreeze.listIterator()
    }


    override fun onResume() {
        super.onResume()

        Companion.onResume(this)

        doNextFreezingStep()
    }

    private fun doNextFreezingStep() {
        if (!FreezerService.isEnabled) {
            // Sometimes the accessibility service is disabled for some reason.
            // In this case, tell the user to re-enable it:
            if (prefUseAccessibilityService) {
                promptForAccessibility()
                return
            }

            if (prefShowExplainingDialog) {
                AlertDialog.Builder(this)
                        .setTitle(R.string.freeze_manually)
                        .setMessage(R.string.Press_forcestop_ok_back)
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                        prefShowExplainingDialog = false
                    doNextFreezingStep()
                }
					.setNegativeButton(R.string.freeze_manually_no) { _, _ ->
                        prefUseAccessibilityService =
                                true // apparently the user wants to use the accessibility service
                    recreate()
                }
					.show()
                return
            }
        }

        if (appsToBeFrozenIter != null) {
            if (appsToBeFrozenIter!!.hasNext()) {
                val settingsScreenLaunched = freezeApp(appsToBeFrozenIter!!.next(), this)
                if (!settingsScreenLaunched) doNextFreezingStep() // Freezing already failed or succeeded, just go on in either case
            } else {
                onFreezeFinishedListener?.invoke(this)
                onFreezeFinishedListener = null
                finish()
                Log.i(TAG, "Finished freezing")
            }
        }
    }

    private fun promptForAccessibility() {
        showAccessibilityDialog(this, Companion) {
            // The accessibility service is now in exactly the state where the user wants it to be.
            // So, set prefUseAccessibilityService to whether the freezer (=accessibility) service is currently enabled.
            prefUseAccessibilityService = FreezerService.isEnabled
            recreate()
        }
    }

    @Suppress("unused")
    fun getRandomNumber(): Int {
        return 5
        // chosen by fair dice roll,
        // guaranteed to be random.

        // Greetings to anyone reviewing this code!
    }

    private fun freezeOnScreenOffFailedDialog() {
        AlertDialog.Builder(this)
                .setTitle("'Freeze when the screen turns off' failed")
                .setMessage("You have to disable 'Power button instantly locks' in the system settings for this to work.")
                .setPositiveButton("Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
            addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
            )
        })
            finish()
        }
			.setNegativeButton("Do not use 'Freeze when the screen turns off'") { _, _ ->
                prefShowFreezeWarning = false
            getPrefs(this).edit().putBoolean("freeze_on_screen_off", false).apply()
            finish()
        }
			.setNeutralButton(android.R.string.cancel) { _, _ ->
                finish()
        }
			.setIcon(R.mipmap.ic_launcher)
                .setCancelable(false)
                .show()
    }

        fun createShortcutResultIntent(activity: Activity): Intent {
			/*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				// There is a nice new api for shortcuts from Android O on, which we use here:
				val shortcutManager = activity.getSystemService(ShortcutManager::class.java)
				return shortcutManager.createShortcutResultIntent(
						ShortcutInfo.Builder(activity.applicationContext, "FreezeShortcut").build()
				)
			}*/

            // ...but for older versions we need to do everything manually :-(,
            // so actually using the new api does not have any benefits:
            val shortcutIntent = createShortcutIntent(activity)

            val intent = Intent()
            intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
            intent.putExtra(
                    Intent.EXTRA_SHORTCUT_NAME,
                    activity.getString(R.string.freeze_shortcut_label)
            )
            val iconResource = Intent.ShortcutIconResource.fromContext(
                    activity, R.drawable.ic_freeze
            )
            intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource)
            return intent
        }

        private fun createShortcutIntent(context: Context): Intent {
            val shortcutIntent =
                    Intent(context.applicationContext, FreezeShortcutActivity::class.java)
            shortcutIntent.addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            return shortcutIntent
        }

        fun freezeAppsPendingFreeze(context: Context) {
            if (isRootAvailable)
                freezeAppsUsingRoot(getAppsPendingFreeze(context), context)
            else
                context.startActivity(createShortcutIntent(context))
        }

        /**
         * Freeze a package.
         * @param packageName The name of the package to freeze
         * @return true if the settings intent ths been launched and you have to wait with freezing the next app.
         */
        @Contract(pure = true)
        fun freezeApp(packageName: String, context: Context): Boolean {
            if (isRootAvailable) {
                freezeAppsUsingRoot(listOf(packageName), context)
                return false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && FreezerService.isEnabled) {
                // clickFreezeButtons will wait for the Force stop button to appear and then click Force stop, Ok, Back.
                try {
                    FreezerService.clickFreezeButtons(context)
                } catch (e: IllegalStateException) {
                    Log.e(TAG, e.toString())
                    e.printStackTrace()
                    return false
                }
            }

            val intent = Intent()
            intent.action = "android.settings.APPLICATION_DETAILS_SETTINGS"
            intent.data = Uri.fromParts("package", packageName, null)
            return try {
                context.startActivity(intent)
                true
            } catch (e: SecurityException) {
                context.toast(context.getString(R.string.cant_freeze), Toast.LENGTH_SHORT)
                false
            }
        }


        var activity: FreezeShortcutActivity? = null
        private set

        var onFreezeFinishedListener: (Context.() -> Unit)? = null

        /**
         * Called after one app could not be frozen
         */
        private fun onAppCouldNotBeFrozen(context: Context) {
            Log.w(TAG, "AppCouldNotBeFrozen, restarting FreezeShortcutActivity")
            context.startActivity(createShortcutIntent(context))
        }
}
