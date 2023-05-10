package com.god.ApplicationManager.Interface;

import android.widget.LinearLayout;

import com.god.ApplicationManager.Entity.AppInfo;

public interface ITouchItemEvent {
    void onTouch(LinearLayout view, AppInfo appInfo);
}
