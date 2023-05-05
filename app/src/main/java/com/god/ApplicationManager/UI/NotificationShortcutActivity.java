package com.god.ApplicationManager.UI;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.god.ApplicationManager.Facade.AppManagerFacade;
import com.god.ApplicationManager.Service.NotificationService;

public class NotificationShortcutActivity extends AppCompatActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();

        if (!Intent.ACTION_CREATE_SHORTCUT.equals(intent.getAction())) {
            AppManagerFacade.setActivity(this);
            AppManagerFacade.toggleStateAutoTurnOffNotification(this, ()->{
                NotificationService.getDisableNotificationStatus();
                this.finish();
            });
        }
    }
}
