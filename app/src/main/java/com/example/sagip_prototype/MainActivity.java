package com.example.sagip_prototype;

import static android.content.ContentValues.TAG;

import static androidx.core.content.ContextCompat.startForegroundService;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Typeface;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.MultiFactorAssertion;
import com.google.firebase.auth.MultiFactorResolver;
import com.google.firebase.auth.MultiFactorSession;
import com.google.firebase.auth.MultiFactorInfo;
import com.google.firebase.auth.PhoneMultiFactorGenerator;
import com.google.firebase.auth.PhoneMultiFactorInfo;
import com.google.firebase.auth.FirebaseAuthMultiFactorException;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;
import com.google.firebase.messaging.FirebaseMessaging;

import com.google.firebase.FirebaseTooManyRequestsException;

import java.util.HashMap;
import java.util.Map;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String PREF_NAME = "SagipAppPrefs";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USER_TYPE = "userType";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USER_PHONE = "userPhone";

    private static final int MAX_OTP_REQUESTS_PER_WINDOW = 3;
    private static final long OTP_REQUEST_WINDOW_MS = TimeUnit.MINUTES.toMillis(10);
    private static final long OTP_LOCKOUT_MS = TimeUnit.MINUTES.toMillis(15);
    private static final String KEY_OTP_WINDOW_START_PREFIX = "otpWindowStart_";
    private static final String KEY_OTP_REQUEST_COUNT_PREFIX = "otpRequestCount_";
    private static final String KEY_OTP_LOCKOUT_UNTIL_PREFIX = "otpLockoutUntil_";

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseFunctions functions;
    private final Long timeout = 60L;
    private SharedPreferences sharedPreferences;
    private FirebaseAuth.AuthStateListener sessionRestoreListener;
    private Handler sessionRestoreHandler;
    private Runnable sessionRestoreTimeoutRunnable;
    private boolean waitingForSessionRestore = false;
    
    // MFA verification
    private String mfaVerificationId;
    private PhoneAuthProvider.ForceResendingToken mfaResendToken;
    private MultiFactorResolver multiFactorResolver;
    private MultiFactorSession cachedMfaSession;

    // UI Components for Phone Login
    private View phoneLoginLayout;
    private EditText phoneNumberInput;
    private Button phoneLoginButton;
    private TextView phoneErrorTextView;

    // UI Components for Email Login
    private View emailLoginLayout;
    private EditText emailInput;
    private EditText passwordInput;
    private Button emailLoginButton;

    private boolean isPhoneLoginMode = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        // Initialize native notification system
        NativeNotificationSender.createNotificationChannel(this);
        
        // Initialize FCM token manager
        new Thread(() -> {
            try {
                FCMTokenManager.registerFCMToken(this);
            } catch (Exception e) {
                Log.e(TAG, "Error initializing FCM token: " + e.getMessage());
            }
        }).start();

        // Apply saved language preference
        String savedLanguage = LanguageSelectionActivity.getSavedLanguage(this);
        LanguageSelectionActivity.setAppLanguage(this, savedLanguage);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        functions = FirebaseFunctions.getInstance();
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        
        // Handle fresh install - must be done before any other auth checks
        handleFreshInstall();
        
        // Initialize Firebase App Check
        try {
            FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
            
            // Use debug provider for debug builds, production provider for release builds
            if (BuildConfig.DEBUG) {
                firebaseAppCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                );
                Log.d(TAG, "Firebase App Check initialized with DEBUG provider");
            } else {
                // For release builds, use Play Integrity
                // Note: This requires:
                // 1. SHA-256 fingerprint added to Firebase Console
                // 2. App Check API enabled in Google Cloud Console
                // 3. App registered in Firebase App Check
                try {
                    firebaseAppCheck.installAppCheckProviderFactory(
                        PlayIntegrityAppCheckProviderFactory.getInstance()
                    );
                    Log.d(TAG, "Firebase App Check initialized with Play Integrity provider");
                } catch (Exception playIntegrityError) {
                    Log.e(TAG, "Failed to initialize Play Integrity provider: " + playIntegrityError.getMessage());
                    Log.w(TAG, "This usually means SHA-256 fingerprint is missing or App Check API is not enabled");
                    Log.w(TAG, "See FIREBASE_APP_CHECK_SETUP.md for instructions");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Firebase App Check: " + e.getMessage());
            Log.e(TAG, "This may cause OTP verification to fail. Please check:");
            Log.e(TAG, "1. Firebase App Check API is enabled in Google Cloud Console");
            Log.e(TAG, "2. SHA-256 fingerprint is added to Firebase Console");
            Log.e(TAG, "3. See FIREBASE_APP_CHECK_SETUP.md for complete setup guide");
        }

        // CRITICAL: Check if this activity was opened from an FCM notification click
        // When app is closed and user clicks notification, Android opens the launcher activity (MainActivity)
        // We need to forward to the appropriate dashboard with the notification data
        if (handleFCMNotificationClick()) {
            return; // Activity will finish and forward to the correct dashboard
        }

        // Check if this is a logout action
        Bundle extras = getIntent().getExtras();
        boolean isLogoutAction = false;
        if (extras != null) {
            isLogoutAction = extras.getBoolean("LOGOUT_ACTION", false);
        }

        // If it's a logout action, clear stored credentials and force logout
        if (isLogoutAction) {
            Log.d(TAG, "Logout action detected, clearing credentials and signing out");
            auth.signOut();
            clearStoredCredentials();
            showLoginScreen();
            return;
        }

        // First, check if this is a fresh install
        boolean isFreshInstall = sharedPreferences.getBoolean("FRESH_INSTALL_FLAG", true);
        if (isFreshInstall) {
            Log.d(TAG, "Fresh install detected in onCreate, forcing clean state");
            auth.signOut();
            clearStoredCredentials();
            sharedPreferences.edit().putBoolean("FRESH_INSTALL_FLAG", false).commit();
            showLoginScreen();
            return;
        }

        // Check for unverified email users (skip for seniors - they use phone login only)
        FirebaseUser storedUser = auth.getCurrentUser();
        String storedUserType = sharedPreferences.getString(KEY_USER_TYPE, null);
        boolean isSeniorUser = "seniors".equals(storedUserType) || "senior".equals(storedUserType);
        
        if (storedUser != null && storedUser.getEmail() != null && !storedUser.isEmailVerified() && !isSeniorUser) {
            Log.d(TAG, "Stored Firebase email user not verified, forcing logout and clearing credentials");
            auth.signOut();
            clearStoredCredentials();
            showLoginScreen();
            return;
        }

        // Only proceed with auto-login if we have valid stored credentials
        if (hasStoredCredentials()) {
            if (storedUser == null) {
                Log.d(TAG, "Stored credentials found but no Firebase user, waiting for restore");
                showLoginScreen();
                waitForFirebaseSessionRestore();
                return;
            } else if (isUserLoggedIn()) {
                Log.d(TAG, "User already logged in with stored credentials, redirecting to dashboard");
                redirectToStoredUserDashboard();
                return;
            }
        }

        // For fresh installs, always show login screen first
        // Don't auto-redirect based on Firebase Auth alone - let user choose to login
        Log.d(TAG, "Fresh install or no stored credentials - showing login screen");
        
        // Debug: Check Firebase Auth state for logging purposes
        FirebaseUser currentUser = auth.getCurrentUser();
        Log.d(TAG, "🔍 Firebase Auth state check:");
        Log.d(TAG, "  - Firebase user: " + (currentUser != null ? currentUser.getUid() : "null"));
        if (currentUser != null) {
            Log.d(TAG, "  - Phone: " + currentUser.getPhoneNumber());
            Log.d(TAG, "  - Email: " + currentUser.getEmail());
        }
        
        // Samsung-specific logging
        String manufacturer = android.os.Build.MANUFACTURER;
        String model = android.os.Build.MODEL;
        Log.d(TAG, "🔍 Device info:");
        Log.d(TAG, "  - Manufacturer: " + manufacturer);
        Log.d(TAG, "  - Model: " + model);
        Log.d(TAG, "  - Is Samsung: " + manufacturer.toLowerCase().contains("samsung"));

        showLoginScreen();
    }

    private void showLoginScreen() {
        setContentView(R.layout.activity_main);
        initializeUI();
        setupPhoneLogin();
        setupEmailLogin();
    }

    private void handleFreshInstall() {
        boolean isFreshInstall = sharedPreferences.getBoolean("FRESH_INSTALL_FLAG", true);
        if (isFreshInstall) {
            Log.d(TAG, "Fresh install detected: resetting auth state before showing login screen");
            if (auth != null) {
                auth.signOut();
            }
            clearStoredCredentials();
            sharedPreferences.edit().putBoolean("FRESH_INSTALL_FLAG", false).commit();
        }
    }

    private boolean hasStoredCredentials() {
        boolean isLoggedIn = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);
        String userId = sharedPreferences.getString(KEY_USER_ID, null);
        String userType = sharedPreferences.getString(KEY_USER_TYPE, null);
        return isLoggedIn && userId != null && userType != null;
    }

    private boolean isUserLoggedIn() {
        // Always check for fresh install first
        boolean isFreshInstall = sharedPreferences.getBoolean("FRESH_INSTALL_FLAG", true);
        if (isFreshInstall) {
            Log.d(TAG, "Fresh install detected in isUserLoggedIn(), forcing login screen");
            // Clear any potential cached credentials
            if (auth != null) {
                auth.signOut();
            }
            clearStoredCredentials();
            sharedPreferences.edit().putBoolean("FRESH_INSTALL_FLAG", false).commit();
            return false;
        }

        boolean isLoggedIn = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);
        String userId = sharedPreferences.getString(KEY_USER_ID, null);
        String userType = sharedPreferences.getString(KEY_USER_TYPE, null);

        Log.d(TAG, "🔍 Checking stored login state:");
        Log.d(TAG, "  - isLoggedIn: " + isLoggedIn);
        Log.d(TAG, "  - userId: " + userId);
        Log.d(TAG, "  - userType: " + userType);

        FirebaseUser currentUser = auth.getCurrentUser();
        boolean hasStoredData = isLoggedIn && userId != null && userType != null;
        boolean hasFirebaseUser = currentUser != null;
        
        Log.d(TAG, "  - hasStoredData: " + hasStoredData);
        Log.d(TAG, "  - hasFirebaseUser: " + hasFirebaseUser);
        Log.d(TAG, "  - Current Firebase user: " + (currentUser != null ? currentUser.getUid() : "null"));

        // If no Firebase user is authenticated, don't immediately clear credentials
        // Firebase Auth session restoration can take time when app reopens
        // Let the session restore mechanism handle this instead of clearing immediately
        if (currentUser == null) {
            Log.d(TAG, "No authenticated Firebase user yet, returning false but NOT clearing credentials");
            Log.d(TAG, "Session restore will be attempted separately");
            return false;
        }

        boolean result = hasStoredData && hasFirebaseUser;
        Log.d(TAG, "  - Result: " + result);
        
        return result;
    }

    private void redirectToStoredUserDashboard() {
        String userType = sharedPreferences.getString(KEY_USER_TYPE, null);
        if (userType != null) {
            redirectToUserDashboard(userType);
        } else {
            // Clear invalid stored data
            clearStoredCredentials();
        }
    }

    private void saveUserCredentials(String userId, String userType, String phoneNumber) {
        Log.d(TAG, "Saving user credentials: " + userId + ", " + userType);
        
        // CRITICAL: Save login timestamp for filtering old notifications
        long loginTimestamp = System.currentTimeMillis();
        Log.d(TAG, "📌 Saving loginTimestamp: " + loginTimestamp);
        
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_USER_TYPE, userType);
        editor.putLong("loginTimestamp", loginTimestamp); // Save login time
        if (phoneNumber != null) {
            editor.putString(KEY_USER_PHONE, phoneNumber);
        }
        // Clear fresh install flag since user has now logged in
        editor.putBoolean("FRESH_INSTALL_FLAG", false);
        // Use commit() instead of apply() for immediate, synchronous persistence
        // This is critical for seniors to prevent session loss when app is closed
        editor.commit();
        
        // Also save to user_prefs for notification services
        SharedPreferences userPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        SharedPreferences.Editor userEditor = userPrefs.edit();
        userEditor.putString("user_id", userId);
        userEditor.putString("user_type", userType);
        userEditor.putLong("loginTimestamp", loginTimestamp); // Save login time here too
        if (phoneNumber != null) {
            userEditor.putString("user_phone", phoneNumber);
        }
        // Clear logout flag since user is now logged in
        userEditor.putBoolean("user_logged_out", false);
        // Use commit() for immediate persistence
        userEditor.commit();
        
        // Start notification services in background thread to prevent ANR
        new Thread(() -> {
            try {
                // Use BackgroundServiceManager to start appropriate services based on user type
                BackgroundServiceManager.startBackgroundServicesForUser(this, userType);
                
                // CRITICAL FIX: Request battery optimization whitelist for all users who need notifications
                // This ensures background services aren't killed by Android battery optimization
                if ("rescuer".equals(userType) || "barangay".equals(userType) || 
                    "seniors".equals(userType) || "senior".equals(userType)) {
                    Log.d(TAG, "🔋 User detected (" + userType + ") - requesting battery optimization whitelist");
                    BatteryOptimizationHelper.logBatteryOptimizationStatus(this);
                    runOnUiThread(() -> {
                        if ("seniors".equals(userType) || "senior".equals(userType)) {
                            BatteryOptimizationHelper.showBatteryOptimizationForSenior(this);
                        } else {
                            BatteryOptimizationHelper.showBatteryOptimizationDialog(this, null);
                        }
                    });
                }
                
                // Also start WorkManager for reliable background notifications (FCM alternative)
                NotificationWorkManager.startNotificationMonitoring(this);
            } catch (Exception e) {
                Log.e(TAG, "Error starting notification services: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * Starts the rescuer foreground service for reliable notifications when app is closed
     */
    private void startRescuerForegroundService() {
        Log.d(TAG, "Starting rescuer foreground service for reliable notifications when app is closed");
        Intent serviceIntent = new Intent(this, RescuerForegroundService.class);
        startForegroundService(serviceIntent);
    }
    
    /**
     * Starts the hospital status notification service for rescuers
     */
    private void startHospitalStatusNotificationService() {
        Log.d(TAG, "Starting hospital status notification service for immediate hospital updates");
        Intent serviceIntent = new Intent(this, HospitalStatusNotificationService.class);
        serviceIntent.putExtra("action", "start_monitoring");
        startForegroundService(serviceIntent);
    }
    
    /**
     * Starts the emergency notification service for real-time SOS alerts
     */
    private void startEmergencyNotificationService() {
        Log.d(TAG, "🚨 Starting emergency notification service for real-time SOS alerts");
        Intent serviceIntent = new Intent(this, EmergencyNotificationService.class);
        startForegroundService(serviceIntent);
    }
    
    /**
     * Starts the hospital status reminder service for hospital users
     */
    private void startHospitalStatusReminderService() {
        Log.d(TAG, "Starting hospital status reminder service for status update notifications");
        Intent serviceIntent = new Intent(this, HospitalStatusReminderService.class);
        serviceIntent.putExtra("action", "start_monitoring");
        startForegroundService(serviceIntent);
    }
    
    /**
     * Starts the background notification service for other user types
     */
    private void startBackgroundNotificationService() {
        Log.d(TAG, "Starting background notification service for user");
        Intent serviceIntent = new Intent(this, BackgroundNotificationService.class);
        startForegroundService(serviceIntent);
    }
    
    /**
     * Handles FCM notification clicks when app was closed
     * When app is killed and user clicks an FCM notification, Android opens the launcher activity (MainActivity)
     * This method checks for notification data and forwards to the appropriate dashboard
     * 
     * @return true if notification was handled and activity should finish, false otherwise
     */
    private boolean handleFCMNotificationClick() {
        Intent intent = getIntent();
        if (intent == null || intent.getExtras() == null) {
            return false;
        }
        
        Bundle extras = intent.getExtras();
        String notificationType = extras.getString("type");
        
        // Check for emergency SOS notification (for rescuers)
        if ("emergency_sos".equals(notificationType) || 
            "true".equals(extras.getString("emergency_sos_clicked")) ||
            "true".equals(extras.getString("from_emergency_notification"))) {
            
            Log.d(TAG, "🚨 FCM notification click detected - Emergency SOS for rescuer");
            Log.d(TAG, "📋 Notification extras: " + extras);
            
            // Forward to Rescuer_Dashboard with all the notification data
            Intent rescuerIntent = new Intent(this, Rescuer_Dashboard.class);
            rescuerIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            
            // Copy all extras to the new intent
            rescuerIntent.putExtra("emergency_sos_clicked", true);
            rescuerIntent.putExtra("from_emergency_notification", true);
            rescuerIntent.putExtra("senior_name", extras.getString("senior_name"));
            rescuerIntent.putExtra("senior_phone", extras.getString("senior_phone"));
            rescuerIntent.putExtra("location_address", extras.getString("location_address"));
            rescuerIntent.putExtra("emergency_type", extras.getString("emergency_type"));
            rescuerIntent.putExtra("request_id", extras.getString("request_id"));
            
            // Parse GPS coordinates
            String seniorLat = extras.getString("senior_lat");
            String seniorLng = extras.getString("senior_lng");
            if (seniorLat != null && !seniorLat.isEmpty() && !"0".equals(seniorLat)) {
                try {
                    rescuerIntent.putExtra("senior_lat", Double.parseDouble(seniorLat));
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Invalid senior_lat: " + seniorLat);
                }
            }
            if (seniorLng != null && !seniorLng.isEmpty() && !"0".equals(seniorLng)) {
                try {
                    rescuerIntent.putExtra("senior_lng", Double.parseDouble(seniorLng));
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Invalid senior_lng: " + seniorLng);
                }
            }
            
            Log.d(TAG, "✅ Forwarding to Rescuer_Dashboard with emergency data");
            startActivity(rescuerIntent);
            finish();
            return true;
        }
        
        // Check for rescuer response notification (for seniors)
        if ("RESCUER_RESPONSE".equals(notificationType)) {
            Log.d(TAG, "🚑 FCM notification click detected - Rescuer response for senior");
            
            // Forward to Senior_Dashboard with notification data
            Intent seniorIntent = new Intent(this, Senior_Dashboard.class);
            seniorIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            
            seniorIntent.putExtra("notification_id", extras.getString("notification_id", "fcm_" + System.currentTimeMillis()));
            seniorIntent.putExtra("rescuer_name", extras.getString("rescuerName", extras.getString("rescuer_name")));
            seniorIntent.putExtra("rescuer_phone", extras.getString("rescuerPhone", extras.getString("rescuer_phone")));
            seniorIntent.putExtra("rescuer_team", extras.getString("rescuerTeam", extras.getString("rescuer_team")));
            seniorIntent.putExtra("request_id", extras.getString("requestId", extras.getString("request_id")));
            
            Log.d(TAG, "✅ Forwarding to Senior_Dashboard with rescuer response data");
            startActivity(seniorIntent);
            finish();
            return true;
        }
        
        // Check for barangay emergency alert
        if ("EMERGENCY_ALERT".equals(notificationType)) {
            Log.d(TAG, "🏢 FCM notification click detected - Emergency alert for barangay");
            
            // Forward to Barangay_Dashboard with notification data
            Intent barangayIntent = new Intent(this, Barangay_Dashboard.class);
            barangayIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            
            barangayIntent.putExtra("emergency_alert_clicked", true);
            barangayIntent.putExtra("notification_id", "fcm_mainactivity_" + System.currentTimeMillis());
            barangayIntent.putExtra("senior_name", extras.getString("seniorName", extras.getString("senior_name")));
            barangayIntent.putExtra("senior_phone", extras.getString("seniorPhone", extras.getString("senior_phone")));
            barangayIntent.putExtra("location_address", extras.getString("locationAddress", extras.getString("location_address")));
            barangayIntent.putExtra("barangay", extras.getString("barangay"));
            barangayIntent.putExtra("emergency_type", extras.getString("emergencyType", extras.getString("emergency_type")));
            barangayIntent.putExtra("request_id", extras.getString("requestId", extras.getString("request_id")));
            
            Log.d(TAG, "✅ Forwarding to Barangay_Dashboard with emergency alert data");
            startActivity(barangayIntent);
            finish();
            return true;
        }
        
        return false;
    }

    private void clearStoredCredentials() {
        Log.d(TAG, "Clearing stored credentials");
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_IS_LOGGED_IN);
        editor.remove(KEY_USER_ID);
        editor.remove(KEY_USER_TYPE);
        editor.remove(KEY_USER_PHONE);
        // NOTE: Do NOT reset FRESH_INSTALL_FLAG here - it should only be true on actual fresh install
        // Setting it to true here caused a bug where session restore timeout would trigger
        // endless logout loop on next app launch
        // Use commit() for immediate synchronous persistence
        editor.commit();
        
        // Set logout flag to prevent services from restarting
        SharedPreferences userPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        SharedPreferences.Editor userEditor = userPrefs.edit();
        userEditor.putBoolean("user_logged_out", true);
        userEditor.remove("user_id");
        userEditor.remove("user_type");
        userEditor.remove("user_phone");
        // Use commit() for immediate synchronous persistence
        userEditor.commit();
        
        // Stop all background services and clear notifications
        BackgroundServiceManager.stopAllBackgroundServices(this);
        
        // Clear FCM token to prevent notifications from being sent to old user
        try {
            FirebaseMessaging.getInstance().deleteToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "FCM token deleted successfully");
                    } else {
                        Log.w(TAG, "Failed to delete FCM token: " + task.getException());
                    }
                });
        } catch (Exception e) {
            Log.w(TAG, "Error deleting FCM token: " + e.getMessage());
        }
        
        Log.d(TAG, "Logout flag set to prevent service restarts and FCM token cleared");
    }

    private void initializeUI() {
        // Custom Tab Buttons
        TextView phoneTabButton = findViewById(R.id.phoneTabButton);
        TextView emailTabButton = findViewById(R.id.emailTabButton);

        // Phone Login Components
        phoneLoginLayout = findViewById(R.id.phoneInputCard);
        phoneNumberInput = findViewById(R.id.user_number);
        phoneLoginButton = findViewById(R.id.login_btn);
        phoneErrorTextView = findViewById(R.id.errorTextView);

        // Email Login Components
        emailLoginLayout = findViewById(R.id.emailInputSection);
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        emailLoginButton = findViewById(R.id.login_btn); // Same button for both modes

        // Setup language selection (now available in both modes)
        TextView languageSelectionText = findViewById(R.id.languageSelectionText);
        if (languageSelectionText != null) {
            languageSelectionText.setOnClickListener(v -> {
                showLanguageSelectionDialog();
            });
        }

        // Set up tab click listeners
        phoneTabButton.setOnClickListener(v -> {
            showPhoneLogin();
            updateTabAppearance(phoneTabButton, emailTabButton);
        });

        emailTabButton.setOnClickListener(v -> {
            showEmailLogin();
            updateTabAppearance(emailTabButton, phoneTabButton);
        });

        // Initially show phone login
        showPhoneLogin();
        updateTabAppearance(phoneTabButton, emailTabButton);
    }

    private void updateTabAppearance(TextView selectedTab, TextView unselectedTab) {
        // Update selected tab
        selectedTab.setTextColor(getResources().getColor(android.R.color.white, null));
        selectedTab.setBackgroundResource(R.drawable.tab_selected);
        selectedTab.setTypeface(null, Typeface.BOLD);

        // Update unselected tab
        unselectedTab.setTextColor(getResources().getColor(android.R.color.darker_gray, null));
        unselectedTab.setBackgroundResource(R.drawable.tab_unselected);
        unselectedTab.setTypeface(null, Typeface.NORMAL);
    }

    private void showPhoneLogin() {
        isPhoneLoginMode = true;
        phoneLoginLayout.setVisibility(View.VISIBLE);
        emailLoginLayout.setVisibility(View.GONE);
        phoneErrorTextView.setVisibility(View.GONE);

        // Update button text and prompt
        phoneLoginButton.setText(getString(R.string.continue_button_text));
        TextView loginPrompt = findViewById(R.id.loginPromptText);
        loginPrompt.setText(getString(R.string.enter_mobile_continue));
    }

    private void showEmailLogin() {
        isPhoneLoginMode = false;
        phoneLoginLayout.setVisibility(View.GONE);
        emailLoginLayout.setVisibility(View.VISIBLE);

        // Update button text and prompt
        phoneLoginButton.setText(getString(R.string.login_with_email));
        TextView loginPrompt = findViewById(R.id.loginPromptText);
        loginPrompt.setText(getString(R.string.enter_email_password));
    }

    private void setupPhoneLogin() {
        phoneLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isPhoneLoginMode) {
                    // Handle phone login
                    String number = phoneNumberInput.getText().toString().trim();

                    if (isValidPhoneNumber(number)) {
                        phoneErrorTextView.setVisibility(View.GONE);
                        // Remove leading "0" for correct international format (+639XXXXXXXXX)
                        String formattedNumber = number.startsWith("0") ? number.substring(1) : number;
                        Log.d(TAG, "Checking registration status for: +63" + formattedNumber);
                        checkUserExistsByPhoneNumber("+63" + formattedNumber);
                    } else {
                        phoneErrorTextView.setVisibility(View.VISIBLE);
                        phoneErrorTextView.setText(getString(R.string.valid_mobile_error));
                        Log.e(TAG, "Invalid phone number entered: " + number);
                    }
                } else {
                    // Handle email/phone login
                    String email = emailInput.getText().toString().trim();
                    String password = passwordInput.getText().toString().trim();

                    if (!email.isEmpty()) {
                        // Check if it's a phone number
                        if (isValidPhoneNumber(email)) {
                            // Phone number login - skip password requirement
                            // Remove leading "0" for correct international format (+639XXXXXXXXX)
                            String formattedEmail = email.startsWith("0") ? email.substring(1) : email;
                            Log.d(TAG, "Phone number detected in email field: +63" + formattedEmail);
                            checkUserExistsByPhoneNumber("+63" + formattedEmail);
                        } else {
                            // Email login - always require password for admin emails
                            if (password.isEmpty()) {
                                Toast.makeText(MainActivity.this, 
                                    "Please enter your password to login with this email.", 
                                    Toast.LENGTH_SHORT).show();
                            } else {
                                // Password provided, proceed with email login
                                loginWithEmail(email, password);
                            }
                        }
                    } else {
                        Toast.makeText(MainActivity.this, getString(R.string.please_enter_email_password), Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }

    private void setupEmailLogin() {
        View passwordToggle = findViewById(R.id.passwordToggle);
        if (passwordToggle != null) {
            passwordToggle.setOnClickListener(v -> {
                if (passwordInput.getInputType() == (android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
                    passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
                } else {
                    passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                }
                passwordInput.setSelection(passwordInput.getText().length());
            });
        }

        // Handle forgot password
        TextView forgotPassword = findViewById(R.id.forgotPasswordText);
        if (forgotPassword != null) {
            forgotPassword.setOnClickListener(v -> {
                showForgotPasswordDialog();
            });
        }

        // Add text change listener to email input to show/hide password field
        emailInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                String input = s.toString().trim();
                View passwordCard = findViewById(R.id.passwordInputCard);
                View forgotPasswordLayout = findViewById(R.id.forgotPasswordLayout);
                
                if (isValidPhoneNumber(input)) {
                    // Hide password field for phone numbers
                    if (passwordCard != null) passwordCard.setVisibility(View.GONE);
                    if (forgotPasswordLayout != null) forgotPasswordLayout.setVisibility(View.GONE);
                } else {
                    // Show password field for email addresses and other inputs
                    if (passwordCard != null) passwordCard.setVisibility(View.VISIBLE);
                    if (forgotPasswordLayout != null) forgotPasswordLayout.setVisibility(View.VISIBLE);
                }
            }
        });
    }



    private void showForgotPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.reset_password));
        builder.setMessage(getString(R.string.enter_email_for_reset));

        // Create an EditText for email input
        final EditText emailEditText = new EditText(this);
        emailEditText.setHint(getString(R.string.email_hint));
        emailEditText.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        // Add some padding to the EditText
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        emailEditText.setPadding(padding, padding, padding, padding);

        builder.setView(emailEditText);

        builder.setPositiveButton(getString(R.string.send_reset_link), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String email = emailEditText.getText().toString().trim();
                if (isValidEmail(email)) {
                    sendPasswordResetEmail(email);
                } else {
                    Toast.makeText(MainActivity.this, getString(R.string.please_enter_valid_email), Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();

        // Pre-fill with current email if available
        String currentEmail = emailInput.getText().toString().trim();
        if (!currentEmail.isEmpty() && isValidEmail(currentEmail)) {
            emailEditText.setText(currentEmail);
            emailEditText.setSelection(currentEmail.length());
        }
    }

    private void sendPasswordResetEmail(String email) {
        // Show progress
        showProgressBar(true);

        auth.sendPasswordResetEmail(email)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        showProgressBar(false);

                        if (task.isSuccessful()) {
                            Log.d(TAG, "Password reset email sent successfully to: " + email);
                            showPasswordResetSuccessDialog(email);
                        } else {
                            Log.e(TAG, "Failed to send password reset email", task.getException());
                            String errorMessage = getPasswordResetErrorMessage(task.getException());
                            showPasswordResetErrorDialog(errorMessage);
                        }
                    }
                });
    }

    private void showPasswordResetSuccessDialog(String email) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.password_reset_email_sent));
        builder.setMessage(getString(R.string.password_reset_sent_message, email));
        builder.setIcon(android.R.drawable.ic_dialog_info);

        builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                // Optionally switch to email login tab
                if (isPhoneLoginMode) {
                    TextView emailTabButton = findViewById(R.id.emailTabButton);
                    TextView phoneTabButton = findViewById(R.id.phoneTabButton);
                    showEmailLogin();
                    updateTabAppearance(emailTabButton, phoneTabButton);
                }
            }
        });

        builder.setCancelable(false);
        builder.show();
    }

    private void showPasswordResetErrorDialog(String errorMessage) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.password_reset_failed));
        builder.setMessage(errorMessage);
        builder.setIcon(android.R.drawable.ic_dialog_alert);

        builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        builder.setNegativeButton(getString(R.string.try_again), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                showForgotPasswordDialog();
            }
        });

        builder.show();
    }

    private String getPasswordResetErrorMessage(Exception exception) {
        if (exception == null) {
            return getString(R.string.unknown_error_occurred);
        }

        String errorMessage = exception.getMessage();
        if (errorMessage == null) {
            return getString(R.string.unknown_error_occurred);
        }

        // Handle common Firebase Auth error codes
        if (errorMessage.contains("There is no user record")) {
            return getString(R.string.no_account_found_email);
        } else if (errorMessage.contains("The email address is badly formatted")) {
            return getString(R.string.please_enter_valid_email);
        } else if (errorMessage.contains("too-many-requests")) {
            return getString(R.string.too_many_requests);
        } else if (errorMessage.contains("network-request-failed")) {
            return getString(R.string.network_error_check_connection);
        } else {
            return getString(R.string.failed_send_password_reset);
        }
    }

    private boolean isValidEmail(String email) {
        return !TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    // Email Login Methods
    private void loginWithEmail(String email, String password) {
        showProgressBar(true);

        // First, try to find the email in Firestore to check if it's an admin-provided email
        checkAdminProvidedEmail(email, password);
    }



    private void checkAdminProvidedEmail(String email, String password) {
        String[] userTypes = {"rescuer", "hospital", "barangay", "seniors"};
        checkEmailInCollections(email, password, userTypes, 0);
    }

    private void checkEmailInCollections(String email, String password, String[] userTypes, int index) {
        if (index >= userTypes.length) {
            // Email not found in any collection, try regular Firebase Auth
            Log.d(TAG, "Email not found in Firestore, trying Firebase Auth");
            tryFirebaseEmailAuth(email, password);
            return;
        }

        db.collection("Sagip")
                .document("users")
                .collection(userTypes[index])
                .whereEqualTo("email", email)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            if (!task.getResult().isEmpty()) {
                                // Admin-provided email found in Firestore
                                for (QueryDocumentSnapshot document : task.getResult()) {
                                    String status = document.getString("status");
                                    String uid = document.getId();
                                    
                                    Log.d(TAG, "Admin-provided email found: " + email + " with status: " + status);
                                    
                                    // User has Firebase Auth account, try login
                                    if (password.isEmpty()) {
                                        // No password provided, show message to enter password
                                        Toast.makeText(MainActivity.this, 
                                            "Please enter your password to login with this email.", 
                                            Toast.LENGTH_LONG).show();
                                        showProgressBar(false);
                                    } else {
                                        tryFirebaseEmailAuth(email, password);
                                    }
                                    return;
                                }
                            } else {
                                // Email not found in this collection, check next
                                checkEmailInCollections(email, password, userTypes, index + 1);
                            }
                        } else {
                            Log.e(TAG, "Error checking email in collection", task.getException());
                            // Try next collection
                            checkEmailInCollections(email, password, userTypes, index + 1);
                        }
                    }
                });
    }




    private void tryFirebaseEmailAuth(String email, String password) {
        if (password.isEmpty()) {
            // No password provided, show message
            Toast.makeText(MainActivity.this, 
                "Please enter your password to login with this email.", 
                Toast.LENGTH_LONG).show();
            showProgressBar(false);
            return;
        }

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            Log.d(TAG, "Email login successful for: " + email);
                            
                            // Check if user has MFA enrolled
                            if (user.getMultiFactor().getEnrolledFactors().isEmpty()) {
                                // No MFA enrolled, check if email is verified first
                                Log.d(TAG, "No MFA enrolled, checking email verification");
                                if (user.isEmailVerified()) {
                                    Log.d(TAG, "Email verified, prompting MFA enrollment");
                                    showMfaEnrollmentDialog(user);
                                } else {
                                    Log.d(TAG, "Email not verified, sending verification email");
                                    showEmailVerificationDialog(user, true); // true = send email on first show
                                }
                            } else {
                                // MFA already enrolled but login succeeded (shouldn't happen normally)
                                // This means verification was already done
                                showProgressBar(false);
                                checkUserTypeAndRedirect(user.getUid(), false);
                            }
                        }
                    } else {
                        // Check if this is an MFA challenge
                        if (task.getException() instanceof FirebaseAuthMultiFactorException) {
                            FirebaseAuthMultiFactorException mfaException = 
                                (FirebaseAuthMultiFactorException) task.getException();
                            multiFactorResolver = mfaException.getResolver();
                            
                            Log.d(TAG, "MFA required, showing verification dialog");
                            
                            // Get the first phone factor hint
                            for (MultiFactorInfo info : multiFactorResolver.getHints()) {
                                if (info instanceof PhoneMultiFactorInfo) {
                                    PhoneMultiFactorInfo phoneInfo = (PhoneMultiFactorInfo) info;
                                    startMfaVerification(phoneInfo);
                                    return;
                                }
                            }
                            
                            showProgressBar(false);
                            Toast.makeText(MainActivity.this, 
                                getString(R.string.mfa_no_phone_factor), 
                                Toast.LENGTH_LONG).show();
                        } else {
                            showProgressBar(false);
                            Log.e(TAG, "Email login failed", task.getException());
                            String errorMessage = getLoginErrorMessage(task.getException());
                            Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    // Show email verification dialog before MFA enrollment
    private void showEmailVerificationDialog(FirebaseUser user, boolean sendEmail) {
        showProgressBar(false);
        
        // Only send verification email if requested (first time showing dialog)
        if (sendEmail) {
            user.sendEmailVerification()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Verification email sent to: " + user.getEmail());
                        Toast.makeText(MainActivity.this, 
                            getString(R.string.email_verification_sent), 
                            Toast.LENGTH_SHORT).show();
                    } else {
                        Log.e(TAG, "Failed to send verification email", task.getException());
                        // Handle rate limiting
                        if (task.getException() instanceof com.google.firebase.FirebaseTooManyRequestsException) {
                            Toast.makeText(MainActivity.this, 
                                getString(R.string.too_many_requests_try_later), 
                                Toast.LENGTH_LONG).show();
                        }
                    }
                });
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.email_verification_required_title));
        builder.setMessage(getString(R.string.email_verification_required_message));
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        
        builder.setPositiveButton(getString(R.string.email_verification_check), (dialog, which) -> {
            // Refresh user to check verification status
            showProgressBar(true);
            user.reload().addOnCompleteListener(reloadTask -> {
                showProgressBar(false);
                if (reloadTask.isSuccessful()) {
                    FirebaseUser refreshedUser = auth.getCurrentUser();
                    if (refreshedUser != null && refreshedUser.isEmailVerified()) {
                        Log.d(TAG, "Email now verified, proceeding to MFA enrollment");
                        Toast.makeText(MainActivity.this, 
                            getString(R.string.email_verified_success), 
                            Toast.LENGTH_SHORT).show();
                        showMfaEnrollmentDialog(refreshedUser);
                    } else {
                        Toast.makeText(MainActivity.this, 
                            getString(R.string.email_not_verified_yet), 
                            Toast.LENGTH_LONG).show();
                        showEmailVerificationDialog(refreshedUser != null ? refreshedUser : user, false); // Don't resend
                    }
                } else {
                    Log.e(TAG, "Failed to reload user", reloadTask.getException());
                    auth.signOut();
                }
            });
        });
        
        builder.setNeutralButton(getString(R.string.email_verification_resend), (dialog, which) -> {
            user.sendEmailVerification()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(MainActivity.this, 
                            getString(R.string.email_verification_sent), 
                            Toast.LENGTH_SHORT).show();
                    } else {
                        // Handle rate limiting error
                        if (task.getException() instanceof com.google.firebase.FirebaseTooManyRequestsException) {
                            Toast.makeText(MainActivity.this, 
                                getString(R.string.too_many_requests_try_later), 
                                Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(MainActivity.this, 
                                getString(R.string.email_verification_send_failed), 
                                Toast.LENGTH_SHORT).show();
                        }
                    }
                    showEmailVerificationDialog(user, false); // Don't auto-send again
                });
        });
        
        builder.setNegativeButton(getString(R.string.cancel), (dialog, which) -> {
            auth.signOut();
            Toast.makeText(MainActivity.this, 
                getString(R.string.email_verification_required_login), 
                Toast.LENGTH_LONG).show();
        });
        
        builder.setCancelable(false);
        builder.show();
    }

    // Show MFA enrollment dialog for first-time setup
    private void showMfaEnrollmentDialog(FirebaseUser user) {
        showProgressBar(false);
        
        // Check if user's first factor is phone-based - SMS MFA is not compatible with phone auth
        boolean hasPhoneProvider = false;
        for (com.google.firebase.auth.UserInfo providerData : user.getProviderData()) {
            if ("phone".equals(providerData.getProviderId())) {
                hasPhoneProvider = true;
                break;
            }
        }
        
        if (hasPhoneProvider) {
            // User signed in with phone - skip SMS MFA enrollment (phone auth already provides 2FA-like security)
            Log.d(TAG, "User has phone provider, skipping SMS MFA enrollment (not compatible)");
            Toast.makeText(MainActivity.this, 
                getString(R.string.phone_auth_no_mfa_needed), 
                Toast.LENGTH_SHORT).show();
            checkUserTypeAndRedirect(user.getUid(), false);
            return;
        }
        
        // Pre-fetch MFA session in background while user enters phone number
        cachedMfaSession = null;
        user.getMultiFactor().getSession()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    cachedMfaSession = task.getResult();
                    Log.d(TAG, "MFA session pre-fetched successfully");
                } else {
                    Log.w(TAG, "Failed to pre-fetch MFA session", task.getException());
                }
            });
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.mfa_enrollment_title));
        builder.setMessage(getString(R.string.mfa_enrollment_message));
        builder.setIcon(android.R.drawable.ic_dialog_info);

        // Create phone number input
        final EditText phoneInput = new EditText(this);
        phoneInput.setHint(getString(R.string.mfa_enter_phone_hint));
        phoneInput.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        phoneInput.setPadding(padding, padding, padding, padding);
        builder.setView(phoneInput);

        builder.setPositiveButton(getString(R.string.mfa_enroll_button), (dialog, which) -> {
            String phoneNumber = phoneInput.getText().toString().trim();
            if (!phoneNumber.isEmpty()) {
                // Format phone number if needed
                if (!phoneNumber.startsWith("+")) {
                    phoneNumber = "+63" + phoneNumber.replaceFirst("^0+", "");
                }
                startMfaEnrollment(user, phoneNumber);
            } else {
                Toast.makeText(MainActivity.this, 
                    getString(R.string.mfa_phone_required), 
                    Toast.LENGTH_SHORT).show();
                // Sign out since they cancelled enrollment
                auth.signOut();
            }
        });

        builder.setNegativeButton(getString(R.string.cancel), (dialog, which) -> {
            // Sign out since MFA is required
            auth.signOut();
            Toast.makeText(MainActivity.this, 
                getString(R.string.mfa_required_message), 
                Toast.LENGTH_LONG).show();
        });

        builder.setCancelable(false);
        builder.show();
    }

    // Start MFA enrollment process
    private void startMfaEnrollment(FirebaseUser user, String phoneNumber) {
        showProgressBar(true);
        Log.d(TAG, "Starting MFA enrollment for phone: " + phoneNumber);
        
        // Use pre-fetched session if available, otherwise fetch now
        if (cachedMfaSession != null) {
            Log.d(TAG, "Using pre-fetched MFA session");
            sendMfaVerificationCode(user, phoneNumber, cachedMfaSession);
        } else {
            Log.d(TAG, "No cached session, fetching MFA session now");
            user.getMultiFactor().getSession()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        sendMfaVerificationCode(user, phoneNumber, task.getResult());
                    } else {
                        showProgressBar(false);
                        Log.e(TAG, "Failed to get MFA session", task.getException());
                        Toast.makeText(MainActivity.this, 
                            getString(R.string.mfa_session_failed), 
                            Toast.LENGTH_LONG).show();
                        auth.signOut();
                    }
                });
        }
    }
    
    // Send MFA verification code using the provided session
    private void sendMfaVerificationCode(FirebaseUser user, String phoneNumber, MultiFactorSession session) {
        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(timeout, TimeUnit.SECONDS)
            .setActivity(this)
            .setMultiFactorSession(session)
            .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                @Override
                public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                    Log.d(TAG, "MFA enrollment auto-verified");
                    completeMfaEnrollment(credential);
                }

                @Override
                public void onVerificationFailed(@NonNull com.google.firebase.FirebaseException e) {
                    showProgressBar(false);
                    Log.e(TAG, "MFA enrollment verification failed", e);
                    
                    // Check if phone is already used for MFA or if first factor is phone
                    String errMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                    
                    // Check for "phone cannot be first factor" error - skip MFA for phone-authenticated users
                    if (errMsg.contains("first factor") || errMsg.contains("sms based mfa") ||
                        errMsg.contains("phone number cannot be set")) {
                        Log.d(TAG, "SMS MFA not compatible with phone auth, proceeding without MFA");
                        Toast.makeText(MainActivity.this, 
                            getString(R.string.phone_auth_no_mfa_needed), 
                            Toast.LENGTH_SHORT).show();
                        checkUserTypeAndRedirect(user.getUid(), false);
                        return;
                    }
                    
                    if (errMsg.contains("already") || errMsg.contains("in use") || 
                        errMsg.contains("second factor") || errMsg.contains("credential")) {
                        Toast.makeText(MainActivity.this, 
                            getString(R.string.mfa_phone_already_used), 
                            Toast.LENGTH_LONG).show();
                        // Let user try a different phone number
                        showMfaEnrollmentDialog(user);
                        return;
                    }
                    
                    Toast.makeText(MainActivity.this, 
                        getString(R.string.mfa_verification_failed) + ": " + e.getMessage(), 
                        Toast.LENGTH_LONG).show();
                    auth.signOut();
                }

                @Override
                public void onCodeSent(@NonNull String verificationId, 
                        @NonNull PhoneAuthProvider.ForceResendingToken token) {
                    showProgressBar(false);
                    Log.d(TAG, "MFA enrollment code sent");
                    mfaVerificationId = verificationId;
                    mfaResendToken = token;
                    showMfaEnrollmentCodeDialog(phoneNumber);
                }
            })
            .build();
        
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    // Show dialog to enter enrollment verification code
    private void showMfaEnrollmentCodeDialog(String phoneNumber) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.mfa_enter_code_title));
        builder.setMessage(String.format(getString(R.string.mfa_code_sent_message), phoneNumber));
        builder.setIcon(android.R.drawable.ic_dialog_info);

        final EditText codeInput = new EditText(this);
        codeInput.setHint(getString(R.string.mfa_code_hint));
        codeInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        codeInput.setPadding(padding, padding, padding, padding);
        builder.setView(codeInput);

        builder.setPositiveButton(getString(R.string.verify), (dialog, which) -> {
            String code = codeInput.getText().toString().trim();
            if (code.length() == 6) {
                PhoneAuthCredential credential = PhoneAuthProvider.getCredential(mfaVerificationId, code);
                completeMfaEnrollment(credential);
            } else {
                Toast.makeText(MainActivity.this, 
                    getString(R.string.mfa_invalid_code), 
                    Toast.LENGTH_SHORT).show();
                auth.signOut();
            }
        });

        builder.setNegativeButton(getString(R.string.cancel), (dialog, which) -> {
            auth.signOut();
        });

        builder.setCancelable(false);
        builder.show();
    }

    // Complete MFA enrollment
    private void completeMfaEnrollment(PhoneAuthCredential credential) {
        showProgressBar(true);
        
        MultiFactorAssertion assertion = PhoneMultiFactorGenerator.getAssertion(credential);
        FirebaseUser user = auth.getCurrentUser();
        
        if (user != null) {
            user.getMultiFactor().enroll(assertion, "Phone Number")
                .addOnCompleteListener(task -> {
                    showProgressBar(false);
                    if (task.isSuccessful()) {
                        Log.d(TAG, "MFA enrollment successful");
                        Toast.makeText(MainActivity.this, 
                            getString(R.string.mfa_enrollment_success), 
                            Toast.LENGTH_SHORT).show();
                        checkUserTypeAndRedirect(user.getUid(), false);
                    } else {
                        Log.e(TAG, "MFA enrollment failed", task.getException());
                        String errorMessage = getString(R.string.mfa_enrollment_failed);
                        
                        // Check if phone is already used for MFA on another account
                        Exception exception = task.getException();
                        if (exception != null && exception.getMessage() != null) {
                            String msg = exception.getMessage().toLowerCase();
                            if (msg.contains("already") || msg.contains("in use") || 
                                msg.contains("second factor") || msg.contains("credential")) {
                                errorMessage = getString(R.string.mfa_phone_already_used);
                                // Let user try a different phone number instead of signing out
                                Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                                showMfaEnrollmentDialog(user);
                                return;
                            }
                        }
                        
                        Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        auth.signOut();
                    }
                });
        }
    }

    // Start MFA verification for login
    private void startMfaVerification(PhoneMultiFactorInfo phoneInfo) {
        Log.d(TAG, "Starting MFA verification for: " + phoneInfo.getPhoneNumber());
        
        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(auth)
            .setMultiFactorHint(phoneInfo)
            .setTimeout(timeout, TimeUnit.SECONDS)
            .setActivity(this)
            .setMultiFactorSession(multiFactorResolver.getSession())
            .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                @Override
                public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                    Log.d(TAG, "MFA verification auto-completed");
                    completeMfaSignIn(credential);
                }

                @Override
                public void onVerificationFailed(@NonNull com.google.firebase.FirebaseException e) {
                    showProgressBar(false);
                    Log.e(TAG, "MFA verification failed", e);
                    Toast.makeText(MainActivity.this, 
                        getString(R.string.mfa_verification_failed) + ": " + e.getMessage(), 
                        Toast.LENGTH_LONG).show();
                }

                @Override
                public void onCodeSent(@NonNull String verificationId, 
                        @NonNull PhoneAuthProvider.ForceResendingToken token) {
                    showProgressBar(false);
                    Log.d(TAG, "MFA verification code sent");
                    mfaVerificationId = verificationId;
                    mfaResendToken = token;
                    showMfaVerificationDialog(phoneInfo.getPhoneNumber());
                }
            })
            .build();
        
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    // Show MFA verification dialog
    private void showMfaVerificationDialog(String phoneNumber) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.mfa_verify_login_title));
        builder.setMessage(String.format(getString(R.string.mfa_verify_login_message), phoneNumber));
        builder.setIcon(android.R.drawable.ic_dialog_info);

        final EditText codeInput = new EditText(this);
        codeInput.setHint(getString(R.string.mfa_code_hint));
        codeInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        codeInput.setPadding(padding, padding, padding, padding);
        builder.setView(codeInput);

        builder.setPositiveButton(getString(R.string.verify), (dialog, which) -> {
            String code = codeInput.getText().toString().trim();
            if (code.length() == 6) {
                PhoneAuthCredential credential = PhoneAuthProvider.getCredential(mfaVerificationId, code);
                completeMfaSignIn(credential);
            } else {
                Toast.makeText(MainActivity.this, 
                    getString(R.string.mfa_invalid_code), 
                    Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton(getString(R.string.cancel), (dialog, which) -> {
            dialog.dismiss();
        });

        builder.setCancelable(false);
        builder.show();
    }

    // Complete MFA sign-in
    private void completeMfaSignIn(PhoneAuthCredential credential) {
        showProgressBar(true);
        
        MultiFactorAssertion assertion = PhoneMultiFactorGenerator.getAssertion(credential);
        
        multiFactorResolver.resolveSignIn(assertion)
            .addOnCompleteListener(task -> {
                showProgressBar(false);
                if (task.isSuccessful()) {
                    Log.d(TAG, "MFA sign-in successful");
                    FirebaseUser user = task.getResult().getUser();
                    Toast.makeText(MainActivity.this, 
                        getString(R.string.verification_successful), 
                        Toast.LENGTH_SHORT).show();
                    checkUserTypeAndRedirect(user.getUid(), false);
                } else {
                    Log.e(TAG, "MFA sign-in failed", task.getException());
                    Toast.makeText(MainActivity.this, 
                        getString(R.string.mfa_signin_failed), 
                        Toast.LENGTH_LONG).show();
                }
            });
    }

    private void showEmailVerificationRequiredDialog(FirebaseUser user) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.email_verification_required_title));
        builder.setMessage(String.format(getString(R.string.email_verification_required_message), user.getEmail()));
        builder.setIcon(android.R.drawable.ic_dialog_info);

        builder.setPositiveButton("Send Verification Email", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                sendEmailVerification(user);
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Sign out the user since they can't proceed without verification
                auth.signOut();
                dialog.dismiss();
            }
        });

        builder.setCancelable(false);
        builder.show();
    }

    private void sendEmailVerification(FirebaseUser user) {
        showProgressBar(true);
        
        user.sendEmailVerification()
                .addOnCompleteListener(this, new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        showProgressBar(false);
                        
                        if (task.isSuccessful()) {
                            Log.d(TAG, "Verification email sent to: " + user.getEmail());
                            Toast.makeText(MainActivity.this, 
                                "Verification email sent! Please check your email and click the verification link.", 
                                Toast.LENGTH_LONG).show();
                            
                            // Sign out user until they verify
                            auth.signOut();
                        } else {
                            Log.e(TAG, "Failed to send verification email", task.getException());
                            Toast.makeText(MainActivity.this, 
                                "Failed to send verification email. Please try again.", 
                                Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private String getLoginErrorMessage(Exception exception) {
        if (exception == null) {
            return "Authentication failed. Please try again.";
        }

        String errorMessage = exception.getMessage();
        if (errorMessage == null) {
            return "Authentication failed. Please try again.";
        }

        // Handle common Firebase Auth error codes
        if (errorMessage.contains("There is no user record")) {
            return "No account found with this email. Please check your email or register.";
        } else if (errorMessage.contains("The password is invalid")) {
            return "Incorrect password. Please try again or use 'Forgot Password'.";
        } else if (errorMessage.contains("The email address is badly formatted")) {
            return "Please enter a valid email address.";
        } else if (errorMessage.contains("too-many-requests")) {
            return "Too many failed attempts. Please try again later.";
        } else if (errorMessage.contains("user-disabled")) {
            return "This account has been disabled. Please contact support.";
        } else {
            return "Authentication failed. Please check your credentials and try again.";
        }
    }

    // Phone Login Methods
    private void checkUserTypeAndRedirect(String identifier, boolean isPhoneNumber) {
        if (isPhoneNumber) {
            // Check all possible user type collections for phone number
            // Reorder to check non-senior users first to avoid senior approval popup conflicts
            String[] userTypes = {"barangay", "rescuer", "hospital", "seniors"};
            checkAuthenticatedUserTypeByPhone(identifier, userTypes, 0);
        } else {
            // Check all possible user type collections for UID (email users)
            String[] userTypes = {"barangay", "rescuer", "hospital", "seniors"};
            checkAuthenticatedUserTypeByUID(identifier, userTypes, 0);
        }
    }

    private void checkAuthenticatedUserTypeByPhone(String phoneNumber, String[] userTypes, int index) {
        if (index >= userTypes.length) {
            Log.e(TAG, "User is authenticated but not found in any collection: " + phoneNumber);
            // Try alternative search methods before giving up
            tryAlternativeUserSearch(phoneNumber);
            return;
        }

        // Try both with and without +63 prefix
        String searchNumber = phoneNumber;
        if (phoneNumber.startsWith("+63")) {
            searchNumber = phoneNumber.substring(3); // Remove +63 prefix
        }
        
        Log.d(TAG, "🔍 Checking user type: " + userTypes[index] + " for phone: " + phoneNumber + " (searching with: " + searchNumber + ")");
        db.collection("Sagip")
                .document("users")
                .collection(userTypes[index])
                .whereEqualTo("mobileNumber", searchNumber)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            if (!task.getResult().isEmpty()) {
                                Log.d(TAG, "✅ Found user in collection: " + userTypes[index] + " with status: " + (task.getResult().getDocuments().get(0).getString("status")));
                                for (QueryDocumentSnapshot document : task.getResult()) {
                                    String status = document.getString("status");
                                    // Handle different user types
                                    if (userTypes[index].equals("seniors")) {
                                        if (status != null && status.equals("approved")) {
                                            // Save user credentials before redirecting
                                            FirebaseUser currentUser = auth.getCurrentUser();
                                            if (currentUser != null) {
                                                saveUserCredentials(currentUser.getUid(), userTypes[index], phoneNumber);
                                            }
                                            redirectToUserDashboard(userTypes[index]);
                                        } else if (status != null && status.equals("pending")) {
                                            // User is pending approval - BLOCK ACCESS to dashboard
                                            FirebaseUser currentUser = auth.getCurrentUser();
                                            if (currentUser != null) {
                                                auth.signOut();
                                            }
                                            showPendingApprovalMessage();
                                            clearStoredCredentials();
                                        } else {
                                            showPendingApprovalMessage();
                                            auth.signOut();
                                            clearStoredCredentials();
                                        }
                                    } else {
                                        // For non-senior users (rescuer, barangay, hospital):
                                        Log.d(TAG, "🎯 Redirecting non-senior user to dashboard: " + userTypes[index]);
                                        // If status is "new", route to the existing registration page first.
                                        if ("new".equals(status)) {
                                            Class<?> registrationClass;
                                            switch (userTypes[index]) {
                                                case "hospital":
                                                    registrationClass = Hospital_Registration.class;
                                                    break;
                                                case "barangay":
                                                    registrationClass = Barangay_Registration.class;
                                                    break;
                                                case "rescuer":
                                                    registrationClass = Rescuer_Registration.class;
                                                    break;
                                                default:
                                                    registrationClass = Senior_Registration.class;
                                                    break;
                                            }
                                            Intent i = new Intent(MainActivity.this, registrationClass);
                                            startActivity(i);
                                            finish();
                                            return;
                                        }
                                        // User is registered, proceed directly
                                        FirebaseUser currentUser = auth.getCurrentUser();
                                        if (currentUser != null) {
                                            saveUserCredentials(currentUser.getUid(), userTypes[index], phoneNumber);
                                        }
                                        redirectToUserDashboard(userTypes[index]);
                                    }
                                    return;
                                }
                            } else {
                                Log.d(TAG, "❌ No user found in collection: " + userTypes[index]);
                                checkAuthenticatedUserTypeByPhone(phoneNumber, userTypes, index + 1);
                            }
                        } else {
                            Log.e(TAG, "Error checking user", task.getException());
                            Toast.makeText(MainActivity.this, getString(R.string.error_checking_user_status), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void checkAuthenticatedUserTypeByUID(String uid, String[] userTypes, int index) {
        if (index >= userTypes.length) {
            Log.e(TAG, "User is authenticated but not found in any collection: " + uid);
            // Try alternative search methods before giving up
            tryAlternativeUserSearchByUID(uid);
            return;
        }

        db.collection("Sagip")
                .document("users")
                .collection(userTypes[index])
                .document(uid)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String status = document.getString("status");
                        // Handle different user types
                        if (userTypes[index].equals("seniors")) {
                            if (status != null && status.equals("approved")) {
                                // Save user credentials before redirecting
                                FirebaseUser currentUser = auth.getCurrentUser();
                                if (currentUser != null) {
                                    saveUserCredentials(uid, userTypes[index], currentUser.getEmail());
                                }
                                redirectToUserDashboard(userTypes[index]);
                            } else if (status != null && status.equals("pending")) {
                                // User is pending approval - BLOCK ACCESS to dashboard
                                FirebaseUser currentUser = auth.getCurrentUser();
                                if (currentUser != null) {
                                    auth.signOut();
                                }
                                showPendingApprovalMessage();
                                clearStoredCredentials();
                            } else {
                                showPendingApprovalMessage();
                                auth.signOut();
                                clearStoredCredentials();
                            }
                        } else {
                            // For email-based users (hospital, barangay, rescuer):
                            // If status is "new", route to the existing registration page first.
                            if ("new".equals(status)) {
                                Class<?> registrationClass;
                                switch (userTypes[index]) {
                                    case "hospital":
                                        registrationClass = Hospital_Registration.class;
                                        break;
                                    case "barangay":
                                        registrationClass = Barangay_Registration.class;
                                        break;
                                    case "rescuer":
                                        registrationClass = Rescuer_Registration.class;
                                        break;
                                    default:
                                        registrationClass = Senior_Registration.class;
                                        break;
                                }
                                Intent i = new Intent(MainActivity.this, registrationClass);
                                startActivity(i);
                                finish();
                                return;
                            }
                            // User is registered, proceed directly
                            FirebaseUser currentUser = auth.getCurrentUser();
                            if (currentUser != null) {
                                saveUserCredentials(uid, userTypes[index], currentUser.getEmail());
                            }
                            redirectToUserDashboard(userTypes[index]);
                        }
                    } else {
                        checkAuthenticatedUserTypeByUID(uid, userTypes, index + 1);
                    }
                })
                .addOnFailureListener(e -> {
                    checkAuthenticatedUserTypeByUID(uid, userTypes, index + 1);
                });
    }

    private void redirectToUserDashboard(String userType) {
        Intent dashboardIntent;

        switch (userType) {
            case "seniors":
            case "senior":
                dashboardIntent = new Intent(MainActivity.this, Senior_Dashboard.class);
                break;
            case "rescuer":
                dashboardIntent = new Intent(MainActivity.this, Rescuer_Dashboard.class);
                break;
            case "hospital":
                dashboardIntent = new Intent(MainActivity.this, Hospital_Dashboard.class);
                break;
            case "barangay":
                dashboardIntent = new Intent(MainActivity.this, Barangay_Dashboard.class);
                break;
            default:
                dashboardIntent = new Intent(MainActivity.this, Senior_Dashboard.class);
                break;
        }

        startActivity(dashboardIntent);
        finish();
    }

    private void checkUserExistsByPhoneNumber(String formattedNumber) {
        // Only seniors use phone number authentication
        String[] userTypes = {"seniors"};
        checkPhoneNumberInCollections(formattedNumber, userTypes, 0);
    }

    private void checkPhoneNumberInCollections(String formattedNumber, String[] userTypes, int index) {
        if (index >= userTypes.length) {
            String plainNumber = formattedNumber.substring(3);
            Log.d(TAG, "User not found in any collection after checking all types, sending OTP for new user. Phone: " + formattedNumber);
            sendOtp(plainNumber, true);
            return;
        }

        // Try multiple formats for backward compatibility
        // New correct format: +639XXXXXXXXX (e.g., +639123456789)
        // Old wrong format: +6309XXXXXXXXX (e.g., +6309123456789) or 09XXXXXXXXX
        final String searchNumber = formattedNumber.startsWith("+63") ? formattedNumber.substring(3) : formattedNumber;
        final String finalFormattedNumber = formattedNumber;
        // Generate legacy formats for backward compatibility
        final String legacyLocalFormat = "0" + searchNumber; // e.g., "09123456789"
        final String legacyInternationalFormat = "+630" + searchNumber; // e.g., "+6309123456789"
        Log.d(TAG, "Searching for phone number: " + searchNumber + " in collection: " + userTypes[index] + 
            " (original: " + finalFormattedNumber + ", legacyLocal: " + legacyLocalFormat + 
            ", legacyIntl: " + legacyInternationalFormat + ")");
        
        // Try searching with the number without +63 prefix first
        db.collection("Sagip")
                .document("users")
                .collection(userTypes[index])
                .whereEqualTo("mobileNumber", searchNumber)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            if (!task.getResult().isEmpty()) {
                                for (QueryDocumentSnapshot document : task.getResult()) {
                                    String status = document.getString("status");
                                    String documentId = document.getId();
                                    Log.d(TAG, "Found user in " + userTypes[index] + " collection. Document ID: " + documentId + ", Status: " + status);
                                    
                                    // Handle different user types
                                    if (userTypes[index].equals("seniors")) {
                                        if (status != null && status.equals("approved")) {
                                            Log.d(TAG, "Senior user found with approved status, sending OTP for existing user");
                                            String plainNumber = formattedNumber.substring(3);
                                            sendOtp(plainNumber, false);
                                        } else if (status != null && status.equals("pending")) {
                                            Log.d(TAG, "Senior user found but status is pending - BLOCKING ACCESS");
                                            showPendingApprovalMessage();
                                        } else {
                                            Log.d(TAG, "Senior user found but status not approved/pending: " + status);
                                            showPendingApprovalMessage();
                                        }
                                    } else {
                                        // For non-senior users, allow login regardless of status
                                        Log.d(TAG, "Non-senior user found, sending OTP for existing user");
                                        String plainNumber = formattedNumber.substring(3);
                                        sendOtp(plainNumber, false);
                                    }
                                    return;
                                }
                            } else {
                                Log.d(TAG, "User not found in collection: " + userTypes[index] + " with format " + searchNumber + ", trying with full format");
                                // Try with the full number format (including +63 prefix)
                                if (!finalFormattedNumber.equals(searchNumber)) {
                                    db.collection("Sagip")
                                            .document("users")
                                            .collection(userTypes[index])
                                            .whereEqualTo("mobileNumber", finalFormattedNumber)
                                            .get()
                                            .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                                                @Override
                                                public void onComplete(@NonNull Task<QuerySnapshot> task2) {
                                                    if (task2.isSuccessful() && !task2.getResult().isEmpty()) {
                                                        Log.d(TAG, "User found with full format: " + finalFormattedNumber);
                                                        // Process the found user with the same logic
                                                        for (QueryDocumentSnapshot document : task2.getResult()) {
                                                            String status = document.getString("status");
                                                            String documentId = document.getId();
                                                            Log.d(TAG, "Found user in " + userTypes[index] + " collection. Document ID: " + documentId + ", Status: " + status);
                                                            
                                                            // Handle different user types
                                                            if (userTypes[index].equals("seniors")) {
                                                                if (status != null && status.equals("approved")) {
                                                                    Log.d(TAG, "Senior user found with approved status, sending OTP for existing user");
                                                                    String plainNumber = finalFormattedNumber.substring(3);
                                                                    sendOtp(plainNumber, false);
                                                                } else if (status != null && status.equals("pending")) {
                                                                    Log.d(TAG, "Senior user found but status is pending - BLOCKING ACCESS");
                                                                    showPendingApprovalMessage();
                                                                } else {
                                                                    Log.d(TAG, "Senior user found but status not approved/pending: " + status);
                                                                    showPendingApprovalMessage();
                                                                }
                                                            } else {
                                                                // For non-senior users, allow login regardless of status
                                                                Log.d(TAG, "Non-senior user found, sending OTP for existing user");
                                                                String plainNumber = finalFormattedNumber.substring(3);
                                                                sendOtp(plainNumber, false);
                                                            }
                                                            return;
                                                        }
                                                    } else {
                                                        // Try legacy format for backward compatibility with old wrong format
                                                        Log.d(TAG, "User not found in collection: " + userTypes[index] + " with new formats, trying legacy format: " + legacyInternationalFormat);
                                                        tryLegacyFormatSearch(legacyInternationalFormat, legacyLocalFormat, userTypes, index, finalFormattedNumber);
                                                    }
                                                }
                                            });
                                } else {
                                    // Try legacy format for backward compatibility
                                    Log.d(TAG, "User not found in collection: " + userTypes[index] + ", trying legacy format: " + legacyInternationalFormat);
                                    tryLegacyFormatSearch(legacyInternationalFormat, legacyLocalFormat, userTypes, index, formattedNumber);
                                }
                            }
                        } else {
                            Log.e(TAG, "Error checking user", task.getException());
                            Toast.makeText(MainActivity.this, getString(R.string.error_checking_registration_status), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    /**
     * Try searching with legacy phone number formats for backward compatibility.
     * This handles cases where existing database entries have the old wrong format (+6309XXXXXXXXX or 09XXXXXXXXX).
     */
    private void tryLegacyFormatSearch(String legacyInternationalFormat, String legacyLocalFormat, 
                                       String[] userTypes, int index, String originalFormattedNumber) {
        // First try the legacy international format (+6309XXXXXXXXX)
        db.collection("Sagip")
                .document("users")
                .collection(userTypes[index])
                .whereEqualTo("mobileNumber", legacyInternationalFormat)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        Log.d(TAG, "User found with legacy international format: " + legacyInternationalFormat);
                        handleFoundUser(task.getResult(), userTypes[index], originalFormattedNumber);
                    } else {
                        // Try the legacy local format (09XXXXXXXXX)
                        db.collection("Sagip")
                                .document("users")
                                .collection(userTypes[index])
                                .whereEqualTo("mobileNumber", legacyLocalFormat)
                                .get()
                                .addOnCompleteListener(task2 -> {
                                    if (task2.isSuccessful() && !task2.getResult().isEmpty()) {
                                        Log.d(TAG, "User found with legacy local format: " + legacyLocalFormat);
                                        handleFoundUser(task2.getResult(), userTypes[index], originalFormattedNumber);
                                    } else {
                                        Log.d(TAG, "User not found with any format in collection: " + userTypes[index] + ", checking next collection");
                                        checkPhoneNumberInCollections(originalFormattedNumber, userTypes, index + 1);
                                    }
                                });
                    }
                });
    }

    /**
     * Handle a found user from any of the search formats.
     */
    private void handleFoundUser(QuerySnapshot querySnapshot, String userType, String formattedNumber) {
        for (QueryDocumentSnapshot document : querySnapshot) {
            String status = document.getString("status");
            String documentId = document.getId();
            Log.d(TAG, "Found user in " + userType + " collection. Document ID: " + documentId + ", Status: " + status);
            
            if (userType.equals("seniors")) {
                if (status != null && status.equals("approved")) {
                    Log.d(TAG, "Senior user found with approved status, sending OTP for existing user");
                    String plainNumber = formattedNumber.substring(3);
                    sendOtp(plainNumber, false);
                } else if (status != null && status.equals("pending")) {
                    Log.d(TAG, "Senior user found but status is pending - BLOCKING ACCESS");
                    showPendingApprovalMessage();
                } else {
                    Log.d(TAG, "Senior user found but status not approved/pending: " + status);
                    showPendingApprovalMessage();
                }
            } else {
                // For non-senior users, allow login regardless of status
                Log.d(TAG, "Non-senior user found, sending OTP for existing user");
                String plainNumber = formattedNumber.substring(3);
                sendOtp(plainNumber, false);
            }
            return;
        }
    }

    private void showPendingApprovalMessage() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.senior_account_pending_approval_title))
                .setMessage(getString(R.string.senior_account_pending_approval_message))
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        // Ensure user is signed out and redirected to login
                        auth.signOut();
                        clearStoredCredentials();
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void sendOtp(String number, boolean isNewUser) {
        // Show loading indicator while waiting for OTP
        showProgressBar(true);
        Toast.makeText(this, getString(R.string.sending_otp), Toast.LENGTH_SHORT).show();

        // Remove leading "0" from Philippine mobile numbers for correct international format
        // e.g., "09123456789" becomes "9123456789", then "+639123456789"
        String formattedForInternational = number.startsWith("0") ? number.substring(1) : number;

        String fullPhoneNumber = "+63" + formattedForInternational;
        String otpKey = fullPhoneNumber.trim();
        long now = System.currentTimeMillis();

        long lockoutUntil = sharedPreferences.getLong(KEY_OTP_LOCKOUT_UNTIL_PREFIX + otpKey, 0L);
        if (lockoutUntil > now) {
            showProgressBar(false);
            long remainingMs = lockoutUntil - now;
            long remainingMinutes = Math.max(1L, TimeUnit.MILLISECONDS.toMinutes(remainingMs));
            Toast.makeText(this,
                    "Too many OTP requests. Try again in " + remainingMinutes + " minute(s).",
                    Toast.LENGTH_LONG).show();
            return;
        }

        long windowStart = sharedPreferences.getLong(KEY_OTP_WINDOW_START_PREFIX + otpKey, 0L);
        int requestCount = sharedPreferences.getInt(KEY_OTP_REQUEST_COUNT_PREFIX + otpKey, 0);
        if (windowStart <= 0L || now - windowStart > OTP_REQUEST_WINDOW_MS) {
            windowStart = now;
            requestCount = 0;
        }

        if (requestCount >= MAX_OTP_REQUESTS_PER_WINDOW) {
            sharedPreferences.edit()
                    .putLong(KEY_OTP_LOCKOUT_UNTIL_PREFIX + otpKey, now + OTP_LOCKOUT_MS)
                    .apply();
            showProgressBar(false);
            Toast.makeText(this,
                    "Too many OTP requests. Please wait 15 minutes and try again.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        sharedPreferences.edit()
                .putLong(KEY_OTP_WINDOW_START_PREFIX + otpKey, windowStart)
                .putInt(KEY_OTP_REQUEST_COUNT_PREFIX + otpKey, requestCount + 1)
                .apply();

        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(fullPhoneNumber)
                .setTimeout(timeout, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(com.google.firebase.auth.PhoneAuthCredential credential) {
                        Log.d(TAG, "Auto-verification completed");
                        // Don't do anything here - let the OTP page handle the verification
                        // This prevents MainActivity from interfering with the OTP flow
                    }

                    @Override
                    public void onVerificationFailed(com.google.firebase.FirebaseException e) {
                        // Hide loading indicator on failure
                        showProgressBar(false);

                        Log.e(TAG, "OTP verification failed: " + e.getMessage());
                        Log.e(TAG, "Error class: " + e.getClass().getSimpleName());
                        Log.e(TAG, "Error cause: " + (e.getCause() != null ? e.getCause().getMessage() : "null"));

                        String errorMessage = "Failed to send OTP";
                        String detailedError = e.getMessage();

                        if (e instanceof FirebaseTooManyRequestsException) {
                            sharedPreferences.edit()
                                    .putLong(KEY_OTP_LOCKOUT_UNTIL_PREFIX + otpKey, System.currentTimeMillis() + OTP_LOCKOUT_MS)
                                    .apply();
                            errorMessage = "Too many OTP requests. Please wait 15 minutes and try again.";
                        }

                        // Check for specific error types
                        if (detailedError != null) {
                            if (detailedError.contains("missing a valid app identifier") ||
                                detailedError.contains("Play Integrity") ||
                                detailedError.contains("reCAPTCHA")) {
                                errorMessage = "App verification failed. This may be due to:\n" +
                                              "1. Missing SHA-256 fingerprint in Firebase Console\n" +
                                              "2. Google Play Services issues\n" +
                                              "3. Device compatibility\n\n" +
                                              "Please contact support or try:\n" +
                                              "- Update Google Play Services\n" +
                                              "- Clear app data and try again";
                                Log.e(TAG, "Play Integrity/App Check verification failed - device may need SHA-256 fingerprint added to Firebase Console");
                            } else if (detailedError.contains("invalid phone number")) {
                                errorMessage = "Invalid phone number format. Please enter a valid Philippine mobile number (09XXXXXXXXX)";
                            } else if (detailedError.contains("network")) {
                                errorMessage = "Network error. Please check your internet connection and try again";
                            } else if (detailedError.contains("quota")) {
                                errorMessage = "Too many requests. Please wait a few minutes and try again";
                            }
                        }

                        // Show user-friendly error dialog instead of just Toast
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("OTP Verification Failed")
                                .setMessage(errorMessage + "\n\nTechnical details: " + detailedError)
                                .setPositiveButton("OK", null)
                                .setNeutralButton("Copy Error", (dialog, which) -> {
                                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                                            getSystemService(Context.CLIPBOARD_SERVICE);
                                    android.content.ClipData clip = android.content.ClipData.newPlainText("Error", detailedError);
                                    clipboard.setPrimaryClip(clip);
                                    Toast.makeText(MainActivity.this, getString(R.string.error_copied_to_clipboard), Toast.LENGTH_SHORT).show();
                                })
                                .show();
                    }

                    @Override
                    public void onCodeSent(String verificationId, PhoneAuthProvider.ForceResendingToken token) {
                        // Hide loading indicator on success
                        showProgressBar(false);

                        Log.d(TAG, "OTP code sent successfully");
                        Intent intent = new Intent(MainActivity.this, OTP_PAGE.class);
                        intent.putExtra("VERIFICATION_ID", verificationId);
                        intent.putExtra("MOBILE_NUMBER", fullPhoneNumber);
                        intent.putExtra("IS_NEW_USER", isNewUser);
                        startActivity(intent);
                        // Finish MainActivity to prevent it from interfering with OTP flow
                        finish();
                    }
                })
                .build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private boolean isValidPhoneNumber(String number) {
        return !TextUtils.isEmpty(number) && number.matches("09\\d{9}");
    }

    private void waitForFirebaseSessionRestore() {
        waitingForSessionRestore = true;
        Toast.makeText(this, getString(R.string.restoring_session), Toast.LENGTH_SHORT).show();
        showProgressBar(true);
        setLoginButtonsEnabled(false);

        if (sessionRestoreListener != null) {
            auth.removeAuthStateListener(sessionRestoreListener);
        }

        sessionRestoreListener = firebaseAuth -> {
            FirebaseUser restoredUser = firebaseAuth.getCurrentUser();
            if (restoredUser != null && waitingForSessionRestore) {
                Log.d(TAG, "Firebase session restored, redirecting to stored dashboard");
                stopSessionRestoreWait();
                redirectToStoredUserDashboard();
            }
        };
        auth.addAuthStateListener(sessionRestoreListener);

        if (sessionRestoreHandler == null) {
            sessionRestoreHandler = new Handler(Looper.getMainLooper());
        }

        sessionRestoreTimeoutRunnable = () -> {
            if (waitingForSessionRestore) {
                Log.w(TAG, "Firebase session restore timed out, trying Firestore verification as fallback");
                stopSessionRestoreWait();
                // Instead of immediately clearing credentials, try to verify via Firestore
                // This allows session to persist even if Firebase Auth has issues
                verifyStoredCredentialsViaFirestore();
            }
        };
        // Increase timeout to 8 seconds to give Firebase more time to restore session
        // This is especially important for seniors on slower devices/networks
        sessionRestoreHandler.postDelayed(sessionRestoreTimeoutRunnable, 8000);
    }

    private void stopSessionRestoreWait() {
        waitingForSessionRestore = false;
        if (sessionRestoreListener != null) {
            auth.removeAuthStateListener(sessionRestoreListener);
            sessionRestoreListener = null;
        }
        if (sessionRestoreHandler != null && sessionRestoreTimeoutRunnable != null) {
            sessionRestoreHandler.removeCallbacks(sessionRestoreTimeoutRunnable);
            sessionRestoreTimeoutRunnable = null;
        }
    }

    /**
     * Verify stored credentials via Firestore when Firebase Auth session restore fails.
     * This allows seniors to stay logged in even if Firebase Auth has issues.
     */
    private void verifyStoredCredentialsViaFirestore() {
        String storedUserId = sharedPreferences.getString(KEY_USER_ID, null);
        String storedUserType = sharedPreferences.getString(KEY_USER_TYPE, null);
        
        if (storedUserId == null || storedUserType == null) {
            Log.d(TAG, "No stored credentials to verify, showing login screen");
            showProgressBar(false);
            setLoginButtonsEnabled(true);
            return;
        }
        
        Log.d(TAG, "Verifying stored credentials via Firestore for user: " + storedUserId + ", type: " + storedUserType);
        
        db.collection("Sagip")
                .document("users")
                .collection(storedUserType)
                .document(storedUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    showProgressBar(false);
                    setLoginButtonsEnabled(true);
                    
                    if (documentSnapshot.exists()) {
                        // User exists in Firestore - trust the stored credentials
                        Log.d(TAG, "User verified in Firestore, redirecting to dashboard");
                        Toast.makeText(this, getString(R.string.session_restored), Toast.LENGTH_SHORT).show();
                        redirectToStoredUserDashboard();
                    } else {
                        // User doesn't exist in Firestore - credentials are invalid
                        Log.w(TAG, "User not found in Firestore, clearing stored credentials");
                        clearStoredCredentials();
                        Toast.makeText(this, getString(R.string.session_restore_failed), Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    showProgressBar(false);
                    setLoginButtonsEnabled(true);
                    
                    // Network error - don't immediately logout, give benefit of doubt
                    Log.e(TAG, "Error verifying credentials via Firestore: " + e.getMessage());
                    // If we have stored credentials and just can't verify, proceed cautiously
                    // This prevents logout due to temporary network issues
                    Log.d(TAG, "Network error during verification, proceeding with stored credentials");
                    Toast.makeText(this, getString(R.string.session_restored), Toast.LENGTH_SHORT).show();
                    redirectToStoredUserDashboard();
                });
    }

    private void setLoginButtonsEnabled(boolean enabled) {
        if (phoneLoginButton != null) {
            phoneLoginButton.setEnabled(enabled);
        }
        if (emailLoginButton != null) {
            emailLoginButton.setEnabled(enabled);
        }
    }

    private void showProgressBar(boolean show) {
        ProgressBar progressBar = findViewById(R.id.progressBar);
        if (progressBar != null) {
            if (show) {
                progressBar.setVisibility(View.VISIBLE);
                phoneLoginButton.setEnabled(false);
            } else {
                progressBar.setVisibility(View.GONE);
                phoneLoginButton.setEnabled(true);
            }
        }
    }

    private void showLanguageSelectionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.language_selection));
        
        String[] languages = {getString(R.string.english), getString(R.string.filipino)};
        String currentLanguage = LanguageSelectionActivity.getSavedLanguage(this);
        int currentIndex = currentLanguage.equals("tl") ? 1 : 0;
        
        builder.setSingleChoiceItems(languages, currentIndex, (dialog, which) -> {
            String selectedLanguage = (which == 0) ? "en" : "tl";
            LanguageSelectionActivity.setAppLanguage(this, selectedLanguage);
            LanguageSelectionActivity.saveLanguagePreference(this, selectedLanguage);
            
            // Update UI elements without recreating the activity
            updateUILanguage();
            
            dialog.dismiss();
        });
        
        builder.setNegativeButton(getString(R.string.cancel), (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void updateUILanguage() {
        // Update all text elements with new language
        TextView loginPromptText = findViewById(R.id.loginPromptText);
        if (loginPromptText != null) {
            if (isPhoneLoginMode) {
                loginPromptText.setText(getString(R.string.enter_mobile_continue));
            } else {
                loginPromptText.setText(getString(R.string.enter_email_password));
            }
        }

        // Update tab buttons
        TextView phoneTabButton = findViewById(R.id.phoneTabButton);
        TextView emailTabButton = findViewById(R.id.emailTabButton);
        if (phoneTabButton != null) {
            phoneTabButton.setText(getString(R.string.phone));
        }
        if (emailTabButton != null) {
            emailTabButton.setText(getString(R.string.email));
        }

        // Update language selection text
        TextView languageSelectionText = findViewById(R.id.languageSelectionText);
        if (languageSelectionText != null) {
            languageSelectionText.setText(getString(R.string.select_language));
        }

        // Update forgot password text
        TextView forgotPasswordText = findViewById(R.id.forgotPasswordText);
        if (forgotPasswordText != null) {
            forgotPasswordText.setText(getString(R.string.forgot_password));
        }

        // Update login button text
        if (phoneLoginButton != null) {
            if (isPhoneLoginMode) {
                phoneLoginButton.setText(getString(R.string.continue_button_text));
            } else {
                phoneLoginButton.setText(getString(R.string.login_with_email));
            }
        }

        // Update input hints
        EditText userNumber = findViewById(R.id.user_number);
        if (userNumber != null) {
            userNumber.setHint(getString(R.string.mobile_hint));
        }

        EditText emailInput = findViewById(R.id.emailInput);
        if (emailInput != null) {
            emailInput.setHint(getString(R.string.email_address));
        }

        EditText passwordInput = findViewById(R.id.passwordInput);
        if (passwordInput != null) {
            passwordInput.setHint(getString(R.string.password));
        }

        // Update error text
        TextView errorTextView = findViewById(R.id.errorTextView);
        if (errorTextView != null && errorTextView.getVisibility() == View.VISIBLE) {
            errorTextView.setText(getString(R.string.valid_mobile_error));
        }

        // Update registration cards
        updateRegistrationCards();
    }

    private void updateRegistrationCards() {
        // Update Senior Citizen card
        TextView seniorTitle = findViewById(R.id.senior_title);
        if (seniorTitle != null) {
            seniorTitle.setText(getString(R.string.senior_title));
        }
        TextView seniorDescription = findViewById(R.id.senior_description);
        if (seniorDescription != null) {
            seniorDescription.setText(getString(R.string.senior_description));
        }

        // Update Barangay card
        TextView barangayTitle = findViewById(R.id.barangay_title);
        if (barangayTitle != null) {
            barangayTitle.setText(getString(R.string.barangay_title));
        }
        TextView barangayDescription = findViewById(R.id.barangay_description);
        if (barangayDescription != null) {
            barangayDescription.setText(getString(R.string.barangay_description));
        }

        // Update Rescuer card
        TextView rescueTitle = findViewById(R.id.rescue_title);
        if (rescueTitle != null) {
            rescueTitle.setText(getString(R.string.rescue_title));
        }
        TextView rescueDescription = findViewById(R.id.rescue_description);
        if (rescueDescription != null) {
            rescueDescription.setText(getString(R.string.rescue_description));
        }

        // Update Hospital card
        TextView hospitalTitle = findViewById(R.id.hospital_title);
        if (hospitalTitle != null) {
            hospitalTitle.setText(getString(R.string.hospital_title));
        }
        TextView hospitalDescription = findViewById(R.id.hospital_description);
        if (hospitalDescription != null) {
            hospitalDescription.setText(getString(R.string.hospital_description));
        }
    }

    private void saveLanguagePreference(String languageCode) {
        SharedPreferences prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("language", languageCode);
        editor.apply();
    }

    private void tryAlternativeUserSearch(String phoneNumber) {
        Log.d(TAG, "Trying alternative search methods for phone: " + phoneNumber);
        
        // Try searching with different phone number formats
        String[] searchFormats = {
            phoneNumber, // Original format
            phoneNumber.startsWith("+63") ? phoneNumber.substring(3) : "+63" + phoneNumber, // Toggle +63
            phoneNumber.startsWith("+63") ? "0" + phoneNumber.substring(3) : phoneNumber, // Add 0 prefix
        };
        
        String[] userTypes = {"barangay", "rescuer", "hospital", "seniors"};
        
        for (String searchFormat : searchFormats) {
            for (String userType : userTypes) {
                Log.d(TAG, "Trying format: " + searchFormat + " in collection: " + userType);
                
                db.collection("Sagip")
                        .document("users")
                        .collection(userType)
                        .whereEqualTo("mobileNumber", searchFormat)
                        .get()
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful() && !task.getResult().isEmpty()) {
                                Log.d(TAG, "Found user with alternative search: " + searchFormat + " in " + userType);
                                for (QueryDocumentSnapshot document : task.getResult()) {
                                    String status = document.getString("status");
                                    if (userType.equals("seniors")) {
                                        if (status != null && status.equals("approved")) {
                                            FirebaseUser currentUser = auth.getCurrentUser();
                                            if (currentUser != null) {
                                                saveUserCredentials(currentUser.getUid(), userType, phoneNumber);
                                            }
                                            redirectToUserDashboard(userType);
                                            return;
                                        } else {
                                            showPendingApprovalMessage();
                                            auth.signOut();
                                            clearStoredCredentials();
                                            return;
                                        }
                                    } else {
                                        FirebaseUser currentUser = auth.getCurrentUser();
                                        if (currentUser != null) {
                                            saveUserCredentials(currentUser.getUid(), userType, phoneNumber);
                                        }
                                        redirectToUserDashboard(userType);
                                        return;
                                    }
                                }
                            }
                        });
            }
        }
        
        // If all alternative searches fail, show error
        Toast.makeText(MainActivity.this, getString(R.string.error_finding_user_profile_login_again), Toast.LENGTH_SHORT).show();
        auth.signOut();
        clearStoredCredentials();
    }

    private void tryAlternativeUserSearchByUID(String uid) {
        Log.d(TAG, "Trying alternative UID search for: " + uid);
        
        String[] userTypes = {"barangay", "rescuer", "hospital", "seniors"};
        
        for (String userType : userTypes) {
            Log.d(TAG, "Trying UID search in collection: " + userType);
            
            db.collection("Sagip")
                    .document("users")
                    .collection(userType)
                    .document(uid)
                    .get()
                    .addOnSuccessListener(document -> {
                        if (document.exists()) {
                            Log.d(TAG, "Found user with UID in collection: " + userType);
                            String status = document.getString("status");
                            if (userType.equals("seniors")) {
                                if (status != null && status.equals("approved")) {
                                    FirebaseUser currentUser = auth.getCurrentUser();
                                    if (currentUser != null) {
                                        saveUserCredentials(currentUser.getUid(), userType, currentUser.getPhoneNumber());
                                    }
                                    redirectToUserDashboard(userType);
                                    return;
                                } else {
                                    showPendingApprovalMessage();
                                    auth.signOut();
                                    clearStoredCredentials();
                                    return;
                                }
                            } else {
                                FirebaseUser currentUser = auth.getCurrentUser();
                                if (currentUser != null) {
                                    saveUserCredentials(currentUser.getUid(), userType, currentUser.getPhoneNumber());
                                }
                                redirectToUserDashboard(userType);
                                return;
                            }
                        }
                    });
        }
        
        // If all UID searches fail, show error
        Toast.makeText(MainActivity.this, getString(R.string.error_finding_user_profile_login_again), Toast.LENGTH_SHORT).show();
        auth.signOut();
        clearStoredCredentials();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MainActivity destroyed - cleaning up resources");
        stopSessionRestoreWait();
        
        // NOTE: Do NOT stop background services here!
        // MainActivity is destroyed when navigating to dashboard activities (Senior_Dashboard, etc.)
        // If we stop services here, it kills services that the dashboard just started,
        // causing "Context.startForegroundService() did not then call Service.startForeground()" crash.
        // Services should only be stopped on explicit logout, not on activity destruction.
    }

}