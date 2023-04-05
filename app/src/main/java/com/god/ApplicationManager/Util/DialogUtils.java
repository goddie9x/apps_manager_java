package com.god.ApplicationManager.Util;

import android.content.Context;
import android.content.DialogInterface;

import androidx.appcompat.app.AlertDialog;

import com.god.ApplicationManager.DB.SettingsDB;
import com.god.ApplicationManager.R;

public class DialogUtils {
    public interface SetMoreOptions{
        void callback(AlertDialog.Builder builder);
    }
    public static void showAlertDialog(Context context, String title, String message,
                                       DialogInterface.OnClickListener positiveListener,
                                       DialogInterface.OnClickListener negativeListener) {

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(message);

        if (negativeListener != null) {
            builder.setNegativeButton(android.R.string.cancel, negativeListener);
        }
        if (positiveListener != null) {
            builder.setPositiveButton(android.R.string.ok, positiveListener);
        }
        builder.show();
    }
    public static void showAlertDialog(Context context, String title, String message,
                                       DialogInterface.OnClickListener positiveListener) {

        AlertDialog.Builder builder = new AlertDialog.Builder(context, SettingsDB.getInstance().isEnableDarkMode ?
                R.style.Theme_ApplicationManager_DarkMode_PopupMenuStyle
                : R.style.Theme_ApplicationManager_PopupMenuStyle);
        builder.setTitle(title);
        builder.setMessage(message);

        if (positiveListener != null) {
            builder.setPositiveButton(android.R.string.ok, positiveListener);
        }
        builder.show();
    }
    public static void showAlertDialog(Context context, String title, String message,
                                       DialogInterface.OnClickListener positiveListener,
                                       SetMoreOptions setMoreOptions) {

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(message);

        if (positiveListener != null) {
            builder.setPositiveButton(android.R.string.ok, positiveListener);
        }
        if(setMoreOptions!=null){
            setMoreOptions.callback(builder);
        }
        builder.show();
    }
}

