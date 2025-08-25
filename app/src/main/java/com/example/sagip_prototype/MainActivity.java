package com.example.sagip_prototype;

import static android.content.ContentValues.TAG;

import android.app.AlertDialog;
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
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

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

        // Apply saved language preference
        String savedLanguage = LanguageSelectionActivity.getSavedLanguage(this);
        LanguageSelectionActivity.setAppLanguage(this, savedLanguage);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

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

        // Check if user is already logged in
        if (isUserLoggedIn()) {
            Log.d(TAG, "User already logged in, redirecting to dashboard");
            redirectToStoredUserDashboard();
            return;
        }

        // Check authentication status before setting content view
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            String phoneNumber = currentUser.getPhoneNumber();
            String email = currentUser.getEmail();

            if (phoneNumber != null) {
                Log.d(TAG, "User already logged in with phone: " + phoneNumber);
                checkUserTypeAndRedirect(phoneNumber, true);
                return;
            } else if (email != null) {
                Log.d(TAG, "User already logged in with email: " + email);
                checkUserTypeAndRedirect(currentUser.getUid(), false);
                return;
            } else {
                Log.d(TAG, "User logged in but no phone number or email found");
                auth.signOut();
            }
        }

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

        return isLoggedIn && userId != null && userType != null;
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
        editor.apply();
    }

    private void clearStoredCredentials() {
        Log.d(TAG, "Clearing stored credentials");
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_IS_LOGGED_IN);
        editor.remove(KEY_USER_ID);
        editor.remove(KEY_USER_TYPE);
        editor.remove(KEY_USER_PHONE);
        editor.apply();
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
                    // Handle email login
                    String email = emailInput.getText().toString().trim();
                    String password = passwordInput.getText().toString().trim();

                    if (!email.isEmpty() && !password.isEmpty()) {
                        loginWithEmail(email, password);
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

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        showProgressBar(false);

                        if (task.isSuccessful()) {
                            FirebaseUser user = auth.getCurrentUser();
                            if (user != null) {
                                Log.d(TAG, "Email login successful for: " + email);
                                checkUserTypeAndRedirect(user.getUid(), false);
                            }
                        } else {
                            Log.e(TAG, "Email login failed", task.getException());
                            String errorMessage = getLoginErrorMessage(task.getException());
                            Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
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
            String[] userTypes = {"seniors", "user", "rescuer", "barangay"};
            checkAuthenticatedUserTypeByPhone(identifier, userTypes, 0);
        } else {
            // Check all possible user type collections for UID (email users)
            String[] userTypes = {"rescuer", "hospital", "seniors", "barangay"};
            checkAuthenticatedUserTypeByUID(identifier, userTypes, 0);
        }
    }

    private void checkAuthenticatedUserTypeByPhone(String phoneNumber, String[] userTypes, int index) {
        if (index >= userTypes.length) {
            Log.e(TAG, "User is authenticated but not found in any collection: " + phoneNumber);
            Toast.makeText(MainActivity.this, "Error finding user profile. Please login again.", Toast.LENGTH_SHORT).show();
            auth.signOut();
            clearStoredCredentials();
            return;
        }

        db.collection("Sagip")
                .document("users")
                .collection(userTypes[index])
                .whereEqualTo("mobileNumber", phoneNumber)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            if (!task.getResult().isEmpty()) {
                                for (QueryDocumentSnapshot document : task.getResult()) {
                                    String status = document.getString("status");
                                    // Only require approval for senior citizens
                                    if (userTypes[index].equals("seniors")) {
                                        if (status != null && status.equals("approved")) {
                                            // Save user credentials before redirecting
                                            FirebaseUser currentUser = auth.getCurrentUser();
                                            if (currentUser != null) {
                                                saveUserCredentials(currentUser.getUid(), userTypes[index], phoneNumber);
                                            }
                                            redirectToUserDashboard(userTypes[index]);
                                        } else {
                                            showPendingApprovalMessage();
                                            auth.signOut();
                                            clearStoredCredentials();
                                        }
                                    } else {
                                        // For non-senior users, allow login regardless of status
                                        FirebaseUser currentUser = auth.getCurrentUser();
                                        if (currentUser != null) {
                                            saveUserCredentials(currentUser.getUid(), userTypes[index], phoneNumber);
                                        }
                                        redirectToUserDashboard(userTypes[index]);
                                    }
                                    return;
                                }
                            } else {
                                checkAuthenticatedUserTypeByPhone(phoneNumber, userTypes, index + 1);
                            }
                        } else {
                            Log.e(TAG, "Error checking user", task.getException());
                            Toast.makeText(MainActivity.this, "Error checking user status", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void checkAuthenticatedUserTypeByUID(String uid, String[] userTypes, int index) {
        if (index >= userTypes.length) {
            Log.e(TAG, "User is authenticated but not found in any collection: " + uid);
            Toast.makeText(MainActivity.this, "Error finding user profile. Please login again.", Toast.LENGTH_SHORT).show();
            auth.signOut();
            clearStoredCredentials();
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
                        // Only require approval for senior citizens
                        if (userTypes[index].equals("seniors")) {
                            if (status != null && status.equals("approved")) {
                                // Save user credentials before redirecting
                                FirebaseUser currentUser = auth.getCurrentUser();
                                if (currentUser != null) {
                                    saveUserCredentials(uid, userTypes[index], currentUser.getEmail());
                                }
                                redirectToUserDashboard(userTypes[index]);
                            } else {
                                showPendingApprovalMessage();
                                auth.signOut();
                                clearStoredCredentials();
                            }
                        } else {
                            // For non-senior users, allow login regardless of status
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
        String[] userTypes = {"seniors", "user", "rescuer", "barangay"};
        checkPhoneNumberInCollections(formattedNumber, userTypes, 0);
    }

    private void checkPhoneNumberInCollections(String formattedNumber, String[] userTypes, int index) {
        if (index >= userTypes.length) {
            String plainNumber = formattedNumber.substring(3);
            sendOtp(plainNumber, true);
            return;
        }

        db.collection("Sagip")
                .document("users")
                .collection(userTypes[index])
                .whereEqualTo("mobileNumber", formattedNumber)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            if (!task.getResult().isEmpty()) {
                                for (QueryDocumentSnapshot document : task.getResult()) {
                                    String status = document.getString("status");
                                    // Only require approval for senior citizens
                                    if (userTypes[index].equals("seniors")) {
                                        if (status != null && status.equals("approved")) {
                                            String plainNumber = formattedNumber.substring(3);
                                            sendOtp(plainNumber, false);
                                        } else {
                                            showPendingApprovalMessage();
                                        }
                                    } else {
                                        // For non-senior users, allow login regardless of status
                                        String plainNumber = formattedNumber.substring(3);
                                        sendOtp(plainNumber, false);
                                    }
                                    return;
                                }
                            } else {
                                checkPhoneNumberInCollections(formattedNumber, userTypes, index + 1);
                            }
                        } else {
                            Log.e(TAG, "Error checking user", task.getException());
                            Toast.makeText(MainActivity.this, "Error checking registration status", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void showPendingApprovalMessage() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Senior Citizen Account Pending Approval")
                .setMessage("Your Senior Citizen account is registered but pending administrator approval. Please try again later.")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
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
                    }

                    @Override
                    public void onVerificationFailed(com.google.firebase.FirebaseException e) {
                        Log.e(TAG, "OTP verification failed: " + e.getMessage());
                        Toast.makeText(MainActivity.this, "Failed to send OTP: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onCodeSent(String verificationId, PhoneAuthProvider.ForceResendingToken token) {
                        Log.d(TAG, "OTP code sent successfully");
                        Intent intent = new Intent(MainActivity.this, OTP_PAGE.class);
                        intent.putExtra("VERIFICATION_ID", verificationId);
                        intent.putExtra("MOBILE_NUMBER", "+63" + number);
                        intent.putExtra("IS_NEW_USER", isNewUser);
                        startActivity(intent);
                    }
                })
                .build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private boolean isValidPhoneNumber(String number) {
        return !TextUtils.isEmpty(number) && number.matches("\\d{10}");
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
}