package com.example.sagip_prototype;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

public class Senior_Profile extends BaseProfileActivity {

    private static final String PREF_NAME = "SagipAppPrefs";
    private static final String KEY_CACHED_FULL_NAME = "cachedFullName";
    private static final String KEY_CACHED_MOBILE_NUMBER = "cachedMobileNumber";
    private static final String TAG = "Senior_Profile";

    FirebaseAuth mAuth;
    FirebaseFirestore db;
    FirebaseStorage storage;
    private SharedPreferences sharedPreferences;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_senior_profile);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        
        
        // Add language selection functionality
        addLanguageSelectionToLayout();

        LinearLayout gotoupdate = findViewById(R.id.gotoupdate);
        TextView tvFullName = findViewById(R.id.seniorProfileName);
        TextView tvnumber = findViewById(R.id.seniorProfileNumber);
        LinearLayout logOut = findViewById(R.id.logoutLayout);
        LinearLayout deleteAccountLayout = findViewById(R.id.deleteAccountLayout);

        logOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLogoutConfirmationDialog();
            }
        });

        deleteAccountLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDeleteAccountConfirmationDialog();
            }
        });

        gotoupdate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Senior_Profile.this, Senior_Update_Profile.class);
                startActivity(intent);
            }
        });

        // Feedback Support Layout
        LinearLayout feedbackSupportLayout = findViewById(R.id.feedbackSupportLayout);
        feedbackSupportLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Senior_Profile.this, FeedbackActivity.class);
                startActivity(intent);
            }
        });

        // Load cached data immediately for instant display
        loadCachedData(tvFullName, tvnumber);

        String uid = mAuth.getCurrentUser().getUid();
        String userType = "seniors";

        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String firstName = documentSnapshot.getString("firstName");
                        String middleName = documentSnapshot.getString("middleName");
                        String lastName = documentSnapshot.getString("lastName");
                        String mobileNumber = documentSnapshot.getString("mobileNumber");

                        String fullName = firstName + " " + middleName + " " + lastName;

                        tvFullName.setText(fullName);
                        tvnumber.setText(PhoneNumberUtils.formatPhoneNumber(mobileNumber));
                        
                        
                        // Cache the data for future instant loading
                        cacheUserData(fullName, mobileNumber);
                    }
                });


        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavBar2);

        bottomNavigationView.setSelectedItemId(R.id.senior_profile);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.senior_home) {
                startActivity(new Intent(getApplicationContext(), Senior_Dashboard.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (itemId == R.id.senior_profile) {
                return true;
            } else if (itemId == R.id.senior_location) {
                startActivity(new Intent(getApplicationContext(), Senior_Emergency_Contact.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            return false;
        });

    }

    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    // Proceed with logout
                    Log.d(TAG, "🚪 User logging out - stopping all background services");
                    
                    // Stop ALL background services to prevent notifications to wrong user
                    BackgroundServiceManager.stopAllBackgroundServices(this);
                    
                    // Clear stored credentials first
                    clearStoredCredentials();
                    
                    // Then sign out from Firebase
                    mAuth.signOut();
                    
                    // Navigate to login screen
                    Intent intent = new Intent(Senior_Profile.this, MainActivity.class);
                    intent.putExtra("LOGOUT_ACTION", true);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    // Dismiss dialog, do nothing
                    dialog.dismiss();
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void showDeleteAccountConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_account_confirmation_title))
                .setMessage(getString(R.string.delete_account_confirmation_message))
                .setPositiveButton(getString(R.string.delete_account_confirm), (dialog, which) -> {
                    // Proceed with account deletion
                    deleteUserAccount();
                })
                .setNegativeButton(getString(R.string.delete_account_cancel), (dialog, which) -> {
                    // Dismiss dialog, do nothing
                    dialog.dismiss();
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void deleteUserAccount() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, getString(R.string.user_not_authenticated), Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = mAuth.getCurrentUser().getUid();
        String userType = "seniors";

        // Show progress
        Toast.makeText(this, getString(R.string.delete_account_progress), Toast.LENGTH_SHORT).show();

        // Delete user data from Firestore
        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    // Delete user images from Storage
                    deleteUserImages(uid);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, getString(R.string.delete_account_failed), Toast.LENGTH_SHORT).show();
                });
    }

    private void deleteUserImages(String uid) {
        StorageReference userImagesRef = storage.getReference().child("users/" + uid);
        
        userImagesRef.listAll()
                .addOnSuccessListener(listResult -> {
                    // Delete all images
                    for (StorageReference item : listResult.getItems()) {
                        item.delete();
                    }
                    
                    // Delete the user from Firebase Auth
                    mAuth.getCurrentUser().delete()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, getString(R.string.delete_account_success), Toast.LENGTH_LONG).show();
                                
                                // Redirect to login page
                                Intent intent = new Intent(Senior_Profile.this, MainActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, getString(R.string.delete_account_failed), Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    // Even if image deletion fails, proceed with account deletion
                    mAuth.getCurrentUser().delete()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, getString(R.string.delete_account_success), Toast.LENGTH_LONG).show();
                                
                                Intent intent = new Intent(Senior_Profile.this, MainActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            })
                            .addOnFailureListener(ex -> {
                                Toast.makeText(this, getString(R.string.delete_account_failed), Toast.LENGTH_SHORT).show();
                            });
                });
    }

    @Override
    protected void updateSpecificProfileElements() {
        // Update profile-specific UI elements
        TextView accountSettingsTitle = findViewById(R.id.accountSettingsTitle);
        if (accountSettingsTitle != null) {
            accountSettingsTitle.setText(getString(R.string.account_settings));
        }

        TextView changeInfoText = findViewById(R.id.changeInfoText);
        if (changeInfoText != null) {
            changeInfoText.setText(getString(R.string.change_Info));
        }

        TextView notificationSettingsText = findViewById(R.id.notificationSettingsText);
        if (notificationSettingsText != null) {
            notificationSettingsText.setText(getString(R.string.notification_settings));
        }

        TextView moreOptionsTitle = findViewById(R.id.moreOptionsTitle);
        if (moreOptionsTitle != null) {
            moreOptionsTitle.setText(getString(R.string.more_options));
        }

        TextView helpSupportText = findViewById(R.id.helpSupportText);
        if (helpSupportText != null) {
            helpSupportText.setText(getString(R.string.help_support));
        }

        TextView logoutText = findViewById(R.id.logoutText);
        if (logoutText != null) {
            logoutText.setText(getString(R.string.logout));
        }
    }

    private void loadCachedData(TextView tvFullName, TextView tvNumber) {
        String cachedName = sharedPreferences.getString(KEY_CACHED_FULL_NAME, null);
        String cachedNumber = sharedPreferences.getString(KEY_CACHED_MOBILE_NUMBER, null);
        
        if (cachedName != null && !cachedName.isEmpty()) {
            tvFullName.setText(cachedName);
        } else {
            tvFullName.setText("Loading...");
        }
        
        if (cachedNumber != null && !cachedNumber.isEmpty()) {
            tvNumber.setText(PhoneNumberUtils.formatPhoneNumber(cachedNumber));
        } else {
            tvNumber.setText("Loading...");
        }
    }

    private void cacheUserData(String fullName, String mobileNumber) {
        sharedPreferences.edit()
                .putString(KEY_CACHED_FULL_NAME, fullName)
                .putString(KEY_CACHED_MOBILE_NUMBER, mobileNumber)
                .apply();
    }
    
    // Helper method to clear stored credentials
    private void clearStoredCredentials() {
        Log.d(TAG, "Clearing stored credentials");
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_CACHED_FULL_NAME);
        editor.remove(KEY_CACHED_MOBILE_NUMBER);
        editor.remove("KEY_IS_LOGGED_IN");
        editor.remove("KEY_USER_ID");
        editor.remove("KEY_USER_TYPE");
        editor.remove("KEY_USER_PHONE");
        editor.remove("KEY_USER_EMAIL");
        editor.apply();
    }
    

    @Override
    protected void onResume() {
        super.onResume();
        
        // Load cached data immediately when returning to profile
        TextView tvFullName = findViewById(R.id.seniorProfileName);
        TextView tvNumber = findViewById(R.id.seniorProfileNumber);
        if (tvFullName != null && tvNumber != null) {
            loadCachedData(tvFullName, tvNumber);
        }
    }
}