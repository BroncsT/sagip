package com.example.sagip_prototype;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

public class FontSizeHelper {
    
    private static final String PREF_NAME = "SagipAppPrefs";
    private static final String KEY_FONT_SIZE = "fontSizeMultiplier";
    
    /**
     * Apply the saved font size preference to the activity
     * Call this in onCreate() before setContentView()
     */
    public static void applyFontSize(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        float fontScale = prefs.getFloat(KEY_FONT_SIZE, 1.0f);
        
        Configuration configuration = activity.getResources().getConfiguration();
        configuration.fontScale = fontScale;
        
        activity.getResources().updateConfiguration(configuration, 
                activity.getResources().getDisplayMetrics());
    }
    
    /**
     * Get the current font size multiplier
     */
    public static float getFontSizeMultiplier(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getFloat(KEY_FONT_SIZE, 1.0f);
    }
    
    /**
     * Save font size preference
     */
    public static void saveFontSize(Context context, float multiplier) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putFloat(KEY_FONT_SIZE, multiplier).apply();
    }
}

