package com.example.sagip_prototype;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

/**
 * Utility class to handle battery optimization whitelist requests
 * This prevents the Android system from killing background services for emergency notifications
 */
public class BatteryOptimizationHelper {
    
    private static final String TAG = "BatteryOptimizationHelper";
    
    /**
     * Checks if the app is exempted from battery optimization
     * @param context Application context
     * @return true if exempted, false otherwise
     */
    public static boolean isIgnoringBatteryOptimizations(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                return powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
            }
        }
        return true; // Pre-Marshmallow devices don't have battery optimization
    }
    
    /**
     * Requests the user to whitelist the app from battery optimization
     * This should be called when user logs in as rescuer or barangay official
     * @param context Activity context
     */
    public static void requestBatteryOptimizationWhitelist(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!isIgnoringBatteryOptimizations(context)) {
                Log.d(TAG, "Requesting battery optimization whitelist for reliable notifications");
                
                try {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + context.getPackageName()));
                    context.startActivity(intent);
                    
                    Log.d(TAG, "Battery optimization whitelist request sent to user");
                } catch (Exception e) {
                    Log.e(TAG, "Error requesting battery optimization whitelist: " + e.getMessage(), e);
                    
                    // Fallback: open app settings page
                    try {
                        Intent settingsIntent = new Intent();
                        settingsIntent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        settingsIntent.setData(Uri.parse("package:" + context.getPackageName()));
                        context.startActivity(settingsIntent);
                        
                        Log.d(TAG, "Opened app settings as fallback for battery optimization");
                    } catch (Exception e2) {
                        Log.e(TAG, "Error opening app settings: " + e2.getMessage(), e2);
                    }
                }
            } else {
                Log.d(TAG, "App is already whitelisted from battery optimization");
            }
        } else {
            Log.d(TAG, "Battery optimization not applicable for Android < 6.0");
        }
    }
    
    /**
     * Shows a dialog explaining why battery optimization whitelist is needed
     * @param context Activity context
     * @param onPositiveClick Runnable to execute when user agrees
     */
    public static void showBatteryOptimizationDialog(Context context, Runnable onPositiveClick) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!isIgnoringBatteryOptimizations(context)) {
                androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(context);
                builder.setTitle("Emergency Notifications")
                       .setMessage("To ensure you receive emergency notifications even when the app is closed, please whitelist SAGIP from battery optimization. This allows the app to run background services reliably.")
                       .setPositiveButton("Enable", (dialog, which) -> {
                           if (onPositiveClick != null) {
                               onPositiveClick.run();
                           }
                           requestBatteryOptimizationWhitelist(context);
                       })
                       .setNegativeButton("Not Now", (dialog, which) -> {
                           Log.w(TAG, "User declined battery optimization whitelist - emergency notifications may be unreliable");
                       })
                       .setCancelable(false)
                       .show();
            }
        }
    }
    
    /**
     * Checks and logs battery optimization status for debugging
     * @param context Application context
     */
    public static void logBatteryOptimizationStatus(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean isIgnoring = isIgnoringBatteryOptimizations(context);
            Log.d(TAG, "Battery optimization status - Ignoring: " + isIgnoring);
            
            if (!isIgnoring) {
                Log.w(TAG, "⚠️ App is NOT whitelisted from battery optimization");
                Log.w(TAG, "⚠️ Emergency notifications may be unreliable when app is closed");
                Log.w(TAG, "⚠️ Call BatteryOptimizationHelper.requestBatteryOptimizationWhitelist() to fix");
            } else {
                Log.d(TAG, "✅ App is whitelisted from battery optimization - notifications should be reliable");
            }
        } else {
            Log.d(TAG, "Battery optimization check not needed for Android < 6.0");
        }
    }
    
    /**
     * Checks and shows battery optimization dialog if needed for emergency users
     * @param context Activity context
     * @param userType Type of user (rescuer, barangay, seniors, etc.)
     */
    public static void checkAndShowBatteryOptimization(Context context, String userType) {
        // Show for all users who need reliable notifications (rescuers, barangay, and seniors)
        if ("rescuer".equals(userType) || "barangay".equals(userType) || 
            "seniors".equals(userType) || "senior".equals(userType)) {
            Log.d(TAG, "🔋 User detected (" + userType + ") - checking battery optimization");
            logBatteryOptimizationStatus(context);
            
            // Only show the dialog if not already whitelisted
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isIgnoringBatteryOptimizations(context)) {
                // Show dialog after a short delay to ensure UI is ready
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    showBatteryOptimizationDialog(context, () -> {
                        // This runs when user clicks 'Enable' on the dialog
                        Log.d(TAG, "User agreed to enable battery optimization whitelist");
                        requestBatteryOptimizationWhitelist(context);
                    });
                }, 1000); // 1 second delay
            }
        }
    }
    
    /**
     * Shows battery optimization dialog specifically for seniors
     * @param context Activity context
     */
    public static void showBatteryOptimizationForSenior(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!isIgnoringBatteryOptimizations(context)) {
                androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(context);
                builder.setTitle("🛡️ Enable Emergency Notifications")
                       .setMessage("To receive notifications when a rescuer responds to your emergency, please allow SAGIP to run in the background. This ensures you won't miss important updates even when the app is closed.")
                       .setPositiveButton("Enable", (dialog, which) -> {
                           Log.d(TAG, "Senior agreed to enable battery optimization whitelist");
                           requestBatteryOptimizationWhitelist(context);
                       })
                       .setNegativeButton("Not Now", (dialog, which) -> {
                           Log.w(TAG, "Senior declined battery optimization whitelist - notifications may be unreliable");
                       })
                       .setCancelable(false)
                       .show();
            }
        }
    }
}
