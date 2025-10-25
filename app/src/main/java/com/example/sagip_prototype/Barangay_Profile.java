package com.example.sagip_prototype;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
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

public class Barangay_Profile extends BaseProfileActivity {
        FirebaseAuth mAuth;
        FirebaseFirestore db;
        FirebaseStorage storage;
        
        private static final String PREF_NAME = "SagipAppPrefs";
        private static final String KEY_CACHED_BARANGAY_NAME = "cachedBarangayName";
        private static final String KEY_CACHED_EMAIL = "cachedEmail";
        private SharedPreferences sharedPreferences;
        private boolean dataLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        setContentView(R.layout.activity_barangay_profile);
        
        // Add language selection functionality
        addLanguageSelectionToLayout();
        
        TextView labelProfile = findViewById(R.id.profileName);
        TextView email = findViewById(R.id.profileEmail);

        LinearLayout gotoUpdate = findViewById(R.id.gotoupdate);
        LinearLayout gotoLogut = findViewById(R.id.logoutLayout);
        LinearLayout deleteAccountLayout = findViewById(R.id.deleteAccountLayout);

        gotoUpdate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Barangay_Profile.this, Barangay_Registration.class);
                startActivity(intent);
            }
        });

        // Feedback Support Layout
        LinearLayout feedbackSupportLayout = findViewById(R.id.feedbackSupportLayout);
        feedbackSupportLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Barangay_Profile.this, FeedbackActivity.class);
                startActivity(intent);
            }
        });

        gotoLogut.setOnClickListener(new View.OnClickListener() {
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

        // Load cached data immediately for instant display
        loadCachedData();
        
        // Load user data only if not already loaded
        if (!dataLoaded) {
            loadUserData();
        }

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavBar2);
        bottomNavigationView.setSelectedItemId(R.id.barangay_profile);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.barangay_profile) {
                return true;
            } else if (itemId == R.id.barangay_seniorList) {
                startActivity(new Intent(getApplicationContext(), Barangay_List.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (itemId == R.id.barangay_dashboard) {
                startActivity(new Intent(getApplicationContext(), Barangay_Dashboard.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            return false;
        });
    }

    private void loadCachedData() {
        TextView profileName = findViewById(R.id.profileName);
        TextView profileEmail = findViewById(R.id.profileEmail);
        
        // Load cached barangay name
        String cachedBarangayName = sharedPreferences.getString(KEY_CACHED_BARANGAY_NAME, null);
        if (cachedBarangayName != null) {
            profileName.setText(cachedBarangayName);
            Log.d("Barangay_Profile", "Loaded cached barangay name: " + cachedBarangayName);
        }
        
        // Load cached email
        String cachedEmail = sharedPreferences.getString(KEY_CACHED_EMAIL, null);
        if (cachedEmail != null) {
            profileEmail.setText(cachedEmail);
            Log.d("Barangay_Profile", "Loaded cached email: " + cachedEmail);
        }
    }

    private void loadUserData() {
        if (mAuth.getCurrentUser() == null) {
            Log.e("Barangay_Profile", "User not authenticated");
            return;
        }

        // Prevent duplicate loading
        if (dataLoaded) {
            Log.d("Barangay_Profile", "Data already loaded, skipping loadUserData");
            return;
        }

        String uid = mAuth.getCurrentUser().getUid();
        String userType = "barangay";

        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String barangayNameValue = documentSnapshot.getString("barangayName");
                        String authEmail = mAuth.getCurrentUser().getEmail();

                        // Update UI elements
                        TextView profileName = findViewById(R.id.profileName);
                        TextView profileEmail = findViewById(R.id.profileEmail);

                        // Display barangay name in the profileName field
                        if (barangayNameValue != null) {
                            profileName.setText(barangayNameValue);
                            // Cache the barangay name
                            cacheBarangayName(barangayNameValue);
                        }

                        // Get email from Firebase Authentication
                        if (authEmail != null) {
                            profileEmail.setText(authEmail);
                            // Cache the email
                            cacheEmail(authEmail);
                        }

                        dataLoaded = true;
                        Log.d("Barangay_Profile", "User data loaded successfully");
                    } else {
                        Log.e("Barangay_Profile", "User document not found");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Barangay_Profile", "Error loading user data: " + e.getMessage());
                });
    }

    private void cacheBarangayName(String barangayName) {
        sharedPreferences.edit()
                .putString(KEY_CACHED_BARANGAY_NAME, barangayName)
                .apply();
        Log.d("Barangay_Profile", "Cached barangay name: " + barangayName);
    }

    private void cacheEmail(String email) {
        sharedPreferences.edit()
                .putString(KEY_CACHED_EMAIL, email)
                .apply();
        Log.d("Barangay_Profile", "Cached email: " + email);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload cached data when returning to the activity
        loadCachedData();
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

        TextView feedbackSupportText = findViewById(R.id.feedbackSupportText);
        if (feedbackSupportText != null) {
            feedbackSupportText.setText(getString(R.string.feedback_support));
        }

        TextView logoutText = findViewById(R.id.logoutText);
        if (logoutText != null) {
            logoutText.setText(getString(R.string.logout));
        }
    }
    
    // Helper method to clear stored credentials
    private void clearStoredCredentials() {
        android.content.SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        editor.remove("user_id");
        editor.remove("user_type");
        editor.remove("is_logged_in");
        editor.remove("user_phone");
        editor.remove("user_email");
        editor.apply();
    }
    
    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    // Proceed with logout
                    Log.d("Barangay_Profile", "🚪 User logging out - stopping all background services");
                    
                    // Stop ALL background services to prevent notifications to wrong user
                    BackgroundServiceManager.stopAllBackgroundServices(this);
                    
                    // Clear stored credentials first
                    clearStoredCredentials();
                    
                    // Sign out from Firebase
                    mAuth.signOut();
                    
                    // Navigate to login screen
                    Intent intent = new Intent(Barangay_Profile.this, MainActivity.class);
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
        String userType = "barangay";

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
                                Intent intent = new Intent(Barangay_Profile.this, MainActivity.class);
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
                                
                                Intent intent = new Intent(Barangay_Profile.this, MainActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            })
                            .addOnFailureListener(ex -> {
                                Toast.makeText(this, getString(R.string.delete_account_failed), Toast.LENGTH_SHORT).show();
                            });
                });
    }
}