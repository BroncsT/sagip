package com.example.sagip_prototype;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Utility class for managing app permissions
 */
public class PermissionManager {
    private static final String TAG = "PermissionManager";
    public static final int SMS_PERMISSION_REQUEST_CODE = 1001;

    /**
     * Check if SMS permission is granted
     */
    public static boolean hasSMSPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) 
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request SMS permission if not already granted
     */
    public static void requestSMSPermission(Activity activity) {
        if (!hasSMSPermission(activity)) {
            Log.d(TAG, "📱 Requesting SMS permission");
            ActivityCompat.requestPermissions(activity, 
                    new String[]{Manifest.permission.SEND_SMS}, 
                    SMS_PERMISSION_REQUEST_CODE);
        } else {
            Log.d(TAG, "✅ SMS permission already granted");
        }
    }

    /**
     * Check if location permission is granted
     */
    public static boolean hasLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request location permission if not already granted
     */
    public static void requestLocationPermission(Activity activity) {
        if (!hasLocationPermission(activity)) {
            Log.d(TAG, "📍 Requesting location permission");
            ActivityCompat.requestPermissions(activity, 
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 
                    1002);
        } else {
            Log.d(TAG, "✅ Location permission already granted");
        }
    }

    /**
     * Check if camera permission is granted
     */
    public static boolean hasCameraPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) 
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request camera permission if not already granted
     */
    public static void requestCameraPermission(Activity activity) {
        if (!hasCameraPermission(activity)) {
            Log.d(TAG, "📷 Requesting camera permission");
            ActivityCompat.requestPermissions(activity, 
                    new String[]{Manifest.permission.CAMERA}, 
                    1003);
        } else {
            Log.d(TAG, "✅ Camera permission already granted");
        }
    }

    /**
     * Check if call permission is granted
     */
    public static boolean hasCallPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) 
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request call permission if not already granted
     */
    public static void requestCallPermission(Activity activity) {
        if (!hasCallPermission(activity)) {
            Log.d(TAG, "📞 Requesting call permission");
            ActivityCompat.requestPermissions(activity, 
                    new String[]{Manifest.permission.CALL_PHONE}, 
                    1004);
        } else {
            Log.d(TAG, "✅ Call permission already granted");
        }
    }

    /**
     * Check if all essential permissions are granted
     */
    public static boolean hasAllEssentialPermissions(Context context) {
        return hasSMSPermission(context) && 
               hasLocationPermission(context) && 
               hasCallPermission(context);
    }

    /**
     * Request all essential permissions
     */
    public static void requestAllEssentialPermissions(Activity activity) {
        if (!hasAllEssentialPermissions(activity)) {
            Log.d(TAG, "🔐 Requesting all essential permissions");
            ActivityCompat.requestPermissions(activity, 
                    new String[]{
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.CALL_PHONE
                    }, 
                    1005);
        } else {
            Log.d(TAG, "✅ All essential permissions already granted");
        }
    }
}
