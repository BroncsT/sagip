package com.example.sagip_prototype;

import static android.content.ContentValues.TAG;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String PREF_NAME = "SagipAppPrefs";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USER_TYPE = "userType";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USER_PHONE = "userPhone";

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private final Long timeout = 60L;
    private SharedPreferences sharedPreferences;

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
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        
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
            // Set content view and show login screen
            setContentView(R.layout.activity_main);
            initializeUI();
            setupPhoneLogin();
            setupEmailLogin();
            return;
        }

        // Check if user is already logged in (only if we have stored credentials)
        if (isUserLoggedIn()) {
            Log.d(TAG, "User already logged in with stored credentials, redirecting to dashboard");
            redirectToStoredUserDashboard();
            return;
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

        // Set content view and initialize UI
        setContentView(R.layout.activity_main);
        initializeUI();
        setupPhoneLogin();
        setupEmailLogin();
    }

    private boolean isUserLoggedIn() {
        boolean isLoggedIn = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);
        String userId = sharedPreferences.getString(KEY_USER_ID, null);
        String userType = sharedPreferences.getString(KEY_USER_TYPE, null);

        Log.d(TAG, "🔍 Checking stored login state:");
        Log.d(TAG, "  - isLoggedIn: " + isLoggedIn);
        Log.d(TAG, "  - userId: " + userId);
        Log.d(TAG, "  - userType: " + userType);

        // Check if this is a fresh install by looking for a fresh install flag
        boolean isFreshInstall = sharedPreferences.getBoolean("FRESH_INSTALL_FLAG", true);
        Log.d(TAG, "  - isFreshInstall: " + isFreshInstall);

        // Additional Samsung-specific check: if we have stored data but no Firebase user,
        // it might be restored data from Samsung Cloud/Smart Switch
        FirebaseUser currentUser = auth.getCurrentUser();
        boolean hasStoredData = isLoggedIn && userId != null && userType != null;
        boolean hasFirebaseUser = currentUser != null;
        
        Log.d(TAG, "  - hasStoredData: " + hasStoredData);
        Log.d(TAG, "  - hasFirebaseUser: " + hasFirebaseUser);

        // If it's a fresh install OR we have stored data but no Firebase user (restored data),
        // don't auto-login and force login screen
        if (isFreshInstall || (hasStoredData && !hasFirebaseUser)) {
            Log.d(TAG, "  - Fresh install or restored data detected, forcing login screen");
            // Mark that we've checked this install
            sharedPreferences.edit().putBoolean("FRESH_INSTALL_FLAG", false).apply();
            return false;
        }

        boolean result = isLoggedIn && userId != null && userType != null;
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
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_USER_TYPE, userType);
        if (phoneNumber != null) {
            editor.putString(KEY_USER_PHONE, phoneNumber);
        }
        // Clear fresh install flag since user has now logged in
        editor.putBoolean("FRESH_INSTALL_FLAG", false);
        editor.apply();
        
        // Also save to user_prefs for notification services
        SharedPreferences userPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        SharedPreferences.Editor userEditor = userPrefs.edit();
        userEditor.putString("user_id", userId);
        userEditor.putString("user_type", userType);
        if (phoneNumber != null) {
            userEditor.putString("user_phone", phoneNumber);
        }
        // Clear logout flag since user is now logged in
        userEditor.putBoolean("user_logged_out", false);
        userEditor.apply();
        
        // Start notification services in background thread to prevent ANR
        new Thread(() -> {
            try {
                // Verify FCM token registration for notifications
                FCMTokenManager.verifyTokenRegistration(this);
                
                // Use BackgroundServiceManager to start appropriate services based on user type
                BackgroundServiceManager.startBackgroundServicesForUser(this, userType);
                
                // Also start WorkManager for reliable background notifications (FCM alternative)
                NotificationWorkManager.startNotificationMonitoring(this);
                
                // Start alternative notification manager (no FCM required)
                AlternativeNotificationManager.getInstance(this).startMonitoring();
                
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

    private void clearStoredCredentials() {
        Log.d(TAG, "Clearing stored credentials");
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_IS_LOGGED_IN);
        editor.remove(KEY_USER_ID);
        editor.remove(KEY_USER_TYPE);
        editor.remove(KEY_USER_PHONE);
        // Reset fresh install flag so next launch will be treated as fresh
        editor.putBoolean("FRESH_INSTALL_FLAG", true);
        editor.apply();
        
        // Set logout flag to prevent services from restarting
        SharedPreferences userPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        SharedPreferences.Editor userEditor = userPrefs.edit();
        userEditor.putBoolean("user_logged_out", true);
        userEditor.remove("user_id");
        userEditor.remove("user_type");
        userEditor.remove("user_phone");
        userEditor.apply();
        
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
                        Log.d(TAG, "Checking registration status for: +63" + number);
                        checkUserExistsByPhoneNumber("+63" + number);
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
                            Log.d(TAG, "Phone number detected in email field: " + email);
                            checkUserExistsByPhoneNumber("+63" + email);
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
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        showProgressBar(false);

                        if (task.isSuccessful()) {
                            FirebaseUser user = auth.getCurrentUser();
                            if (user != null) {
                                Log.d(TAG, "Email login successful for: " + email);
                                
                                // Check if user is verified - REQUIRE verification for admin-provided accounts
                                if (user.isEmailVerified()) {
                                    checkUserTypeAndRedirect(user.getUid(), false);
                                } else {
                                    // Email not verified - require verification before proceeding
                                    Log.d(TAG, "User email not verified, requiring verification for admin-provided account");
                                    showEmailVerificationRequiredDialog(user);
                                }
                            }
                        } else {
                            Log.e(TAG, "Email login failed", task.getException());
                            String errorMessage = getLoginErrorMessage(task.getException());
                            Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        }
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
        String[] userTypes = {"barangay", "rescuer", "hospital", "seniors"};
        checkPhoneNumberInCollections(formattedNumber, userTypes, 0);
    }

    private void checkPhoneNumberInCollections(String formattedNumber, String[] userTypes, int index) {
        if (index >= userTypes.length) {
            String plainNumber = formattedNumber.substring(3);
            Log.d(TAG, "User not found in any collection after checking all types, sending OTP for new user. Phone: " + formattedNumber);
            sendOtp(plainNumber, true);
            return;
        }

        // Try both with and without +63 prefix
        final String searchNumber = formattedNumber.startsWith("+63") ? formattedNumber.substring(3) : formattedNumber;
        final String finalFormattedNumber = formattedNumber;
        Log.d(TAG, "Searching for phone number: " + searchNumber + " in collection: " + userTypes[index] + " (original: " + finalFormattedNumber + ")");
        
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
                                                        Log.d(TAG, "User not found in collection: " + userTypes[index] + " with either format, checking next collection");
                                                        checkPhoneNumberInCollections(finalFormattedNumber, userTypes, index + 1);
                                                    }
                                                }
                                            });
                                } else {
                                    Log.d(TAG, "User not found in collection: " + userTypes[index] + ", checking next collection");
                                    checkPhoneNumberInCollections(formattedNumber, userTypes, index + 1);
                                }
                            }
                        } else {
                            Log.e(TAG, "Error checking user", task.getException());
                            Toast.makeText(MainActivity.this, getString(R.string.error_checking_registration_status), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
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
        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber("+63" + number)
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
                        Log.e(TAG, "OTP verification failed: " + e.getMessage());
                        Log.e(TAG, "Error class: " + e.getClass().getSimpleName());
                        Log.e(TAG, "Error cause: " + (e.getCause() != null ? e.getCause().getMessage() : "null"));
                        
                        String errorMessage = "Failed to send OTP";
                        String detailedError = e.getMessage();
                        
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
                        Log.d(TAG, "OTP code sent successfully");
                        Intent intent = new Intent(MainActivity.this, OTP_PAGE.class);
                        intent.putExtra("VERIFICATION_ID", verificationId);
                        intent.putExtra("MOBILE_NUMBER", "+63" + number);
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
        
        // Stop all background services when activity is destroyed
        try {
            BackgroundServiceManager.stopAllBackgroundServices(this);
        } catch (Exception e) {
            Log.e(TAG, "Error stopping background services in onDestroy: " + e.getMessage());
        }
    }

}