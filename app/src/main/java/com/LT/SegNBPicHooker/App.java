package com.LT.SegNBPicHooker;

import android.app.Application;
import android.os.Build;

import com.google.android.material.color.DynamicColors;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ 跟随系统取色（Material 3 动态配色）
            DynamicColors.applyToActivitiesIfAvailable(this);
        }
    }
}
