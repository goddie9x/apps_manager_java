package com.god.ApplicationManager.Entity;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;

public class AppInfo implements Parcelable {
    public Drawable appIcon;
    public String appName;
    public String packageName;
    public boolean isSystemApp = false;
    public boolean isRunning = false;
    public boolean isFrozen = false;
    private final String TAG = "AppInfo";
    public AppInfo(Drawable appIcon,String appName,String packageName){
        this.appIcon = appIcon;
        this.appName = appName;
        this.packageName = packageName;
    }
    public AppInfo(Drawable appIcon,String appName,String packageName,boolean isSystemApp){
        this.appIcon = appIcon;
        this.appName = appName;
        this.packageName = packageName;
        this.isSystemApp = isSystemApp;
    }
    public AppInfo(Drawable appIcon,
                   String appName,
                   String packageName,
                   boolean isSystemApp,
                   boolean isRunning){
        this.appIcon = appIcon;
        this.appName = appName;
        this.packageName = packageName;
        this.isSystemApp = isSystemApp;
        this.isRunning = isRunning;
    }
    public AppInfo(Drawable appIcon,
                   String appName,
                   String packageName,
                   boolean isSystemApp,
                   boolean isRunning,
                   boolean isFrozen){
        this.appIcon = appIcon;
        this.appName = appName;
        this.packageName = packageName;
        this.isSystemApp = isSystemApp;
        this.isRunning = isRunning;
        this.isFrozen = isFrozen;
    }
    public AppInfo(Parcel in){
        appName = in.readString();
        packageName = in.readString();
        isSystemApp = in.readByte() != 0;
        isRunning = in.readByte() != 0;
        isFrozen = in.readByte()!=0;
        try {
            Bitmap iconBitmap = (Bitmap) in.readParcelable(getClass().getClassLoader());
            appIcon = new BitmapDrawable(Resources.getSystem(), iconBitmap);

        } catch (ClassCastException e) {
            Log.e(TAG,e.getMessage());
        }
    }
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(appName);
        dest.writeString(packageName);
        if (appIcon != null) {
            if (appIcon instanceof BitmapDrawable) {
                Bitmap bitmap = ((BitmapDrawable) appIcon).getBitmap();
                dest.writeParcelable(bitmap, flags);
            } else if (appIcon instanceof AdaptiveIconDrawable) {
                Drawable foregroundDrawable = ((AdaptiveIconDrawable) appIcon).getForeground();
                Bitmap bitmap = ((BitmapDrawable) foregroundDrawable).getBitmap();
                dest.writeParcelable(bitmap, flags);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            dest.writeBoolean(isSystemApp);
            dest.writeBoolean(isRunning);
            dest.writeBoolean(isFrozen);
        }
        else {
            dest.writeInt(isSystemApp ? 1 : 0);
            dest.writeInt(isRunning ? 1 : 0);
            dest.writeInt(isFrozen ? 1 : 0);
        }
    }

    public static class Settings {
    }
    public static final Creator<AppInfo> CREATOR = new Creator<AppInfo>() {
        @Override
        public AppInfo createFromParcel(Parcel in) {
            return new AppInfo(in);
        }

        @Override
        public AppInfo[] newArray(int size) {
            return new AppInfo[size];
        }
    };
}
