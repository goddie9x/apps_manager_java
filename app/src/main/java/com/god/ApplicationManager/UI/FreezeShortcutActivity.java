package com.god.ApplicationManager.UI;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.god.ApplicationManager.DB.AppInfoDB;
import com.god.ApplicationManager.DB.SettingsDB;
import com.god.ApplicationManager.Facade.AppManagerFacade;
import com.god.ApplicationManager.Permission.PermissionHandler;
import com.god.ApplicationManager.R;
import com.god.ApplicationManager.Service.FreezeService;
import com.god.ApplicationManager.Util.DialogUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class FreezeShortcutActivity extends AppCompatActivity {
    private boolean isBeingNewlyCreated = true;
    private ListIterator<String> appsToBeFrozenIter= null;
    public boolean isWorking = false;
    private boolean screenOff = false;
    public static FreezeShortcutActivity activity;
    private KeyguardManager keyguardManager;
    private final String TAG="God freeze app shortcut";

    private interface OnFreezeFinishedListener{
        void callback(Context context);
    }
    private static OnFreezeFinishedListener onFreezeFinishedListener;
    public static void setOnFreezeFinishedListener(OnFreezeFinishedListener handler){
        onFreezeFinishedListener = handler;
    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        activity = this;
        isBeingNewlyCreated = true;
        Intent intent = getIntent();
        screenOff = (intent.getStringExtra("extraID") == "dyn_screenOff");
        keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);

        if (Intent.ACTION_CREATE_SHORTCUT == intent.getAction()) {
            setResult(RESULT_OK, createShortcutResultIntent(this));
            finish();
        } else {
            FreezeService.stopAnyCurrentFreezing(); // Might be that there still was a previous (failed) freeze process, in this case stop it
            if (keyguardManager.isKeyguardLocked() &&
                    SettingsDB.getInstance().isFreezeWhileScreenOff
			) {
                if(onFreezeFinishedListener!=null){
                    onFreezeFinishedListener.callback(this);
                    onFreezeFinishedListener = null;
                    freezeOnScreenOffFailedDialog();
                    Log.e(TAG, "Screen not unlocked.");
                    return;
                }
            }
            Log.i(TAG, "Performing Freeze.");
            isWorking = true;
            FreezeService.setOnAppCouldNotBeFrozen(this::handleOnAppCouldNotBeFrozen);
            performFreeze();
        }
    }
    @Override
    public void onDestroy() {
        super.onDestroy();

        // If activity != this, another instance of FreezeShortcutActivity might already have been started
        // and we must not clean the "static" things up here!
        if (activity == this) {
            Log.i(TAG, "Destroying, cleaning up");
            activity = null;
            FreezeService.setOnAppCouldNotBeFrozen(null);
            FreezeService.finishedFreezing();
        } else {
            Log.i(TAG, "Destroying, but not cleaning up because activity != this");
        }
        isWorking = false;
    }

    private void performFreeze() {
        List<AppInfoDB> listApp = AppManagerFacade
                .getListAppFromDB();
        List<String>listPackageNameToBeFreeze = new ArrayList<>();
        for (AppInfoDB app:
             listApp) {
            if(app.isHaveToBeFreeze){
                listPackageNameToBeFreeze.add(app.packageName);
            }
        }

        if (AppManagerFacade.hasRootPermission) {
            AppManagerFacade.freezeAppsUsingRoot(listPackageNameToBeFreeze, this, screenOff);
            finish();
            return;
        }

        if (listPackageNameToBeFreeze.isEmpty()) {
            Toast.makeText(this,R.string.nothingToFreeze,Toast.LENGTH_SHORT).show();
            return;
        }

        if (FreezeService.isEnabled) {
            Toast.makeText(this,(R.string.power_button_hint), Toast.LENGTH_LONG).show();
        }

        // The actual freezing work will be done in onResume(). Here we just create this iterator.
        appsToBeFrozenIter = listPackageNameToBeFreeze.listIterator();
    }


    @Override
    public void onResume() {
        super.onResume();

        //Companion.onResume(this)

        doNextFreezingStep();
    }

    private void doNextFreezingStep() {
        if (!FreezeService.isEnabled) {
            // Sometimes the accessibility service is disabled for some reason.
            // In this case, tell the user to re-enable it:
            if (!AppManagerFacade.hasUseAccessibilityServicePermission(this)) {
                PermissionHandler.getUseAccessibilityService(this);
                return;
            }
        }

        if (appsToBeFrozenIter != null) {
            if(appsToBeFrozenIter.hasNext()){
                if(freezeApp(appsToBeFrozenIter.next(), this));
            }
            else{
                if(onFreezeFinishedListener!=null){
                    onFreezeFinishedListener.callback(this);
                }
                onFreezeFinishedListener = null;
                finish();
                Log.i(TAG, "Finished freezing");
            }
        }
    }
    private void  freezeOnScreenOffFailedDialog() {
        DialogUtils.showAlertDialog(
                this,
                getString(R.string.freeze_screen_off_failed),
                getString(R.string.disable_power_btn_instantly_locks_warning),
                (dialog,whitch)->{
                    Intent intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                },
                (dialog,whitch)->{
                    SettingsDB.getInstance().doNotShowFreezeWarning = false;
                    SettingsDB.getInstance().isFreezeWhileScreenOff = false;
                    SettingsDB.saveSettings();
                    finish();
                }
        );
    }
    private Intent createShortcutResultIntent( AppCompatActivity activity){
        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // There is a nice new api for shortcuts from Android O on, which we use here:
            val shortcutManager = activity.getSystemService(ShortcutManager::class.java)
            return shortcutManager.createShortcutResultIntent(
                    ShortcutInfo.Builder(activity.applicationContext, "FreezeShortcut").build()
            )
        }*/

        // ...but for older versions we need to do everything manually :-(,
        // so actually using the new api does not have any benefits:
        Intent shortcutIntent = createShortcutIntent(activity);

        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        intent.putExtra(
                Intent.EXTRA_SHORTCUT_NAME,
                activity.getString(R.string.freeze_all_app)
        );
        Intent.ShortcutIconResource iconResource = Intent.ShortcutIconResource.fromContext(
                activity, R.drawable.ic_free_breakfast
        );
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);
        return intent;
    }

    private Intent createShortcutIntent(Context context) {
        Intent shortcutIntent =
                new Intent(context.getApplicationContext(), FreezeShortcutActivity.class);
        shortcutIntent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TASK
                |Intent.FLAG_ACTIVITY_NEW_TASK
                |Intent.FLAG_ACTIVITY_NO_ANIMATION
        );
        return shortcutIntent;
    }

    public void freezeAppsPendingFreeze(Context context) {
        if (AppManagerFacade.hasRootPermission){
            List<AppInfoDB> listApp = AppManagerFacade
                    .getListAppFromDB();
            List<String>listPackageNameToBeFreeze = new ArrayList<>();
            for (AppInfoDB app:
                    listApp) {
                if(app.isHaveToBeFreeze){
                    listPackageNameToBeFreeze.add(app.packageName);
                }
            }
            AppManagerFacade.freezeAppsUsingRoot(
                    listPackageNameToBeFreeze
                    , context,false);
        }
        else
            context.startActivity(createShortcutIntent(context));
    }

    /**
     * Freeze a package.
     * @param packageName The name of the package to freeze
     * @return true if the settings intent ths been launched and you have to wait with freezing the next app.
     */
    public boolean freezeApp(String packageName,Context context){
        if (AppManagerFacade.hasRootPermission) {
            AppManagerFacade.freezeAppUsingRoot(packageName, context,false);
            return false;
        }

        if (FreezeService.isEnabled) {
            // clickFreezeButtons will wait for the Force stop button to appear and then click Force stop, Ok, Back.
            try {
                FreezeService.clickFreezeButtons(context);
            } catch (IllegalStateException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                return false;
            }
        }

        Intent intent = new Intent();
        intent.setAction("android.settings.APPLICATION_DETAILS_SETTINGS");
        intent.setData(Uri.fromParts("package", packageName, null));
        try {
            context.startActivity(intent);
            return true;
        } catch (SecurityException e) {
            Toast.makeText(this,getString(R.string.cant_freeze,packageName),Toast.LENGTH_LONG).show();
            return false;
        }
    }


    /**
     * Called after one app could not be frozen
     */
    public void handleOnAppCouldNotBeFrozen(Context context) {
        Log.w(TAG, "AppCouldNotBeFrozen, restarting FreezeShortcutActivity");
        context.startActivity(createShortcutIntent(context));
    }
}
