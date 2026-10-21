package com.god.ApplicationManager.Entity;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

import androidx.annotation.NonNull;

public class AppInfo implements Parcelable {
    public Drawable appIcon;
    public String appName;
    public String packageName;
    public boolean isSystemApp = false;
    public boolean isRunning = false;
    public boolean isFrozen = false;
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
    public AppInfo(Parcel in) {
        appName = in.readString();
        packageName = in.readString();
        isSystemApp = in.readByte() != 0;
        isRunning = in.readByte() != 0;
        isFrozen = in.readByte() != 0;
        boolean hasIcon = in.readByte() != 0;
        if (hasIcon) {
            Bitmap iconBitmap = (Bitmap) in.readParcelable(Bitmap.class.getClassLoader());
            appIcon = iconBitmap != null ? new BitmapDrawable(null, iconBitmap) : null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppInfo other)) return false;
        return Objects.equals(packageName, other.packageName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(packageName);
    }
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(appName);
        dest.writeString(packageName);
        dest.writeByte((byte) (isSystemApp ? 1 : 0));
        dest.writeByte((byte) (isRunning ? 1 : 0));
        dest.writeByte((byte) (isFrozen ? 1 : 0));
        Bitmap iconBitmap = toBitmap(appIcon);
        dest.writeByte((byte) (iconBitmap != null ? 1 : 0));
        if (iconBitmap != null) {
            dest.writeParcelable(iconBitmap, flags);
        }
    }

    private static Bitmap toBitmap(Drawable drawable) {
        if (drawable == null) return null;
        if (drawable instanceof BitmapDrawable bitmapDrawable) {
            return bitmapDrawable.getBitmap();
        }
        int width = Math.max(1, drawable.getIntrinsicWidth());
        int height = Math.max(1, drawable.getIntrinsicHeight());
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
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
