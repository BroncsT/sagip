package com.example.sagip_prototype;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages FCM token registration and updates
 * Ensures tokens are properly stored in the database for notifications
 */
public class FCMTokenManager {
    
    private static final String TAG = "FCMTokenManager";
    private static final String PREF_NAME = "SagipAppPrefs";
    private static final String KEY_FCM_TOKEN = "fcmToken";
    private static final String KEY_TOKEN_UPDATED = "tokenUpdated";
    
    /**
     * Registers FCM token for the current user
     */
    public static void registerFCMToken(Context context) {
        Log.d(TAG, "🔑 Registering FCM token for current user");
        
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.e(TAG, "❌ Failed to get FCM token", task.getException());
                        return;
                    }
                    
                    String token = task.getResult();
                    Log.d(TAG, "✅ FCM token obtained: " + token.substring(0, Math.min(20, token.length())) + "...");
                    
                    // Get current user info
                    SharedPreferences sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                    String userId = sharedPreferences.getString("userId", null);
                    String userType = sharedPreferences.getString("userType", null);
                    
                    if (userId != null && userType != null) {
                        // Update token in database
                        updateTokenInDatabase(userId, userType, token);
                        
                        // Store token locally
                        sharedPreferences.edit()
                                .putString(KEY_FCM_TOKEN, token)
                                .putLong(KEY_TOKEN_UPDATED, System.currentTimeMillis())
                                .apply();
                        
                        Log.d(TAG, "✅ FCM token registered for user: " + userId + ", type: " + userType);
                    } else {
                        Log.w(TAG, "⚠️ Cannot register FCM token - user not logged in");
                    }
                });
    }
    
    /**
     * Updates FCM token in the database
     */
    private static void updateTokenInDatabase(String userId, String userType, String token) {
        Log.d(TAG, "📝 Updating FCM token in database for user: " + userId);
        
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        Map<String, Object> tokenData = new HashMap<>();
        tokenData.put("fcmToken", token);
        tokenData.put("lastTokenUpdate", System.currentTimeMillis());
        tokenData.put("tokenStatus", "active");
        
        // Use update() instead of set() to avoid recreating deleted user documents
        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(userId)
                .update(tokenData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ FCM token updated successfully in database for user: " + userId);
                })
                .addOnFailureListener(e -> {
                    // NOT_FOUND means user document was deleted - don't try to recreate it
                    if (e.getMessage() != null && e.getMessage().contains("NOT_FOUND")) {
                        Log.w(TAG, "⚠️ User document not found (likely deleted) - skipping FCM token update");
                    } else {
                        Log.e(TAG, "❌ Failed to update FCM token in database for user: " + userId, e);
                    }
                });
    }
    
    /**
     * Gets the current FCM token from SharedPreferences
     */
    public static String getCurrentToken(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sharedPreferences.getString(KEY_FCM_TOKEN, null);
    }
    
    /**
     * Checks if FCM token needs to be refreshed
     */
    public static boolean shouldRefreshToken(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long lastUpdate = sharedPreferences.getLong(KEY_TOKEN_UPDATED, 0);
        long currentTime = System.currentTimeMillis();
        
        // Refresh token if it's older than 24 hours
        return (currentTime - lastUpdate) > (24 * 60 * 60 * 1000);
    }
    
    /**
     * Forces FCM token refresh
     */
    public static void forceTokenRefresh(Context context) {
        Log.d(TAG, "🔄 Forcing FCM token refresh");
        
        FirebaseMessaging.getInstance().deleteToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "✅ Old FCM token deleted");
                        // Register new token
                        registerFCMToken(context);
                    } else {
                        Log.e(TAG, "❌ Failed to delete old FCM token", task.getException());
                    }
                });
    }
    
    /**
     * Verifies FCM token is properly registered
     */
    public static void verifyTokenRegistration(Context context) {
        Log.d(TAG, "🔍 Verifying FCM token registration");
        
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String userId = sharedPreferences.getString("userId", null);
        String userType = sharedPreferences.getString("userType", null);
        String localToken = sharedPreferences.getString(KEY_FCM_TOKEN, null);
        
        if (userId == null || userType == null) {
            Log.w(TAG, "⚠️ Cannot verify token - user not logged in");
            return;
        }
        
        if (localToken == null) {
            Log.w(TAG, "⚠️ No local FCM token found, registering new token");
            registerFCMToken(context);
            return;
        }
        
        // Check if token is in database
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String dbToken = documentSnapshot.getString("fcmToken");
                        if (localToken.equals(dbToken)) {
                            Log.d(TAG, "✅ FCM token verified - matches database");
                        } else {
                            Log.w(TAG, "⚠️ FCM token mismatch - updating database");
                            updateTokenInDatabase(userId, userType, localToken);
                        }
                    } else {
                        Log.w(TAG, "⚠️ User document not found in database");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to verify FCM token", e);
                });
    }
}
