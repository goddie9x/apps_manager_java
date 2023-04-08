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
import com.god.ApplicationManager.Enum.FreezeServiceNextAction;
import com.god.ApplicationManager.Facade.AppManagerFacade;
import com.god.ApplicationManager.R;
import com.god.ApplicationManager.Service.FreezeService;
import com.god.ApplicationManager.Util.DialogUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

public class FreezeShortcutActivity extends AppCompatActivity {
    private ListIterator<String> appsToBeFrozenIterator= null;
    public boolean isWorking = false;
    private boolean screenOff = false;
    public static FreezeShortcutActivity activity;
    private static final String TAG="God freeze app shortcut";

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
        Intent intent = getIntent();
        screenOff = (Objects.equals(intent.getStringExtra("extraID"), "dyn_screenOff"));
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);

        if (!Intent.ACTION_CREATE_SHORTCUT.equals(intent.getAction())) {
            FreezeService.stopAnyCurrentFreezing(); // Might be that there still was a previous (failed) freeze process, in this case stop it
            if (keyguardManager.isKeyguardLocked()) {
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

        if (listPackageNameToBeFreeze.isEmpty()) {
            Toast.makeText(this,R.string.nothingToFreeze,Toast.LENGTH_SHORT).show();
            return;
        }
        if (FreezeService.isEnabled) {
            Toast.makeText(this,(R.string.power_button_hint), Toast.LENGTH_LONG).show();
        }

        // The actual freezing work will be done in onResume(). Here we just create this iterator.
        appsToBeFrozenIterator = listPackageNameToBeFreeze.listIterator();
    }


    @Override
    public void onResume() {
        super.onResume();

        doNextFreezingStep();
    }

    private void doNextFreezingStep() {
        if (!FreezeService.isEnabled) {
            // Sometimes the accessibility service is disabled for some reason.
            // In this case, tell the user to re-enable it:
            return;
        }

        if (appsToBeFrozenIterator != null) {
            if(appsToBeFrozenIterator.hasNext()){
                if(freezeApp(appsToBeFrozenIterator.next(), this));
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
                (dialog,which)->{
                    Intent intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                },
                (dialog,which)->{
                    SettingsDB.getInstance().doNotShowFreezeWarning = false;
                    SettingsDB.saveSettings();
                    finish();
                }
        );
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
            AppManagerFacade.freezeListAppUsingRoot(
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
    public static boolean freezeApp(String packageName,Context context){
        if (AppManagerFacade.hasRootPermission) {
            return AppManagerFacade.freezeAppUsingRoot(packageName, context,false);
        }

        if (FreezeService.isEnabled) {
            // clickFreezeButtons will wait for the Force stop button to appear and then click Force stop, Ok, Back.
            try {
                FreezeService.setNextAction(FreezeServiceNextAction.PRESS_FORCE_STOP);
                Intent intent = new Intent();
                intent.setAction("android.settings.APPLICATION_DETAILS_SETTINGS");
                intent.setData(Uri.fromParts("package", packageName, null));
                context.startActivity(intent);
            } catch (IllegalStateException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }


    /**
     * Called after one app could not be frozen
     */
    public void handleOnAppCouldNotBeFrozen(Context context) {
        Log.w(TAG, "AppCouldNotBeFrozen, restarting FreezeShortcutActivity");
        context.startActivity(createShortcutIntent(context));
    }
}
