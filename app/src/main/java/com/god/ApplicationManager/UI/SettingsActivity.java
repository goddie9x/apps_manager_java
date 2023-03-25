package com.god.ApplicationManager.UI;

import android.os.Bundle;
import android.widget.Switch;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.god.ApplicationManager.R;

public class SettingsActivity extends AppCompatActivity {

    Switch darkmodeSwitcher;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        initAtrr();
        initEvents();
    }

    private void initAtrr() {
        darkmodeSwitcher= findViewById(R.id.switch_dark_mode);
    }

    private void initEvents() {
        
        findViewById(R.id.back_to_main_sreen).setOnClickListener(v->{
            finish();
        });
        darkmodeSwitcher.setOnCheckedChangeListener((v,isChecked)->{
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });
    }
}