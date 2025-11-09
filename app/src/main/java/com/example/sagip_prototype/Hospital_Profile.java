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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class Hospital_Profile extends BaseProfileActivity {

    private static final String PREF_NAME = "SagipAppPrefs";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USER_TYPE = "userType";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USER_EMAIL = "userEmail";

    FirebaseAuth mAuth;
    FirebaseFirestore db;
    FirebaseStorage storage;
    private SharedPreferences sharedPreferences;
    
    // Activity result launcher for font size changes
    private ActivityResultLauncher<Intent> fontSizeActivityLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        
        // Apply saved font size preference
        FontSizeHelper.applyFontSize(this);
        
        setContentView(R.layout.activity_hospital_profile);

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        
        // Initialize activity result launcher for font size
        fontSizeActivityLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == FontSizeActivity.RESULT_FONT_SIZE_CHANGED) {
                    // Font size changed - refresh smoothly
                    applyFontSizeImmediately();
                }
            }
        );

        // Add language selection functionality
        addLanguageSelectionToLayout();

        LinearLayout UpdateProfile = findViewById(R.id.gotoupdate);
        TextView hospitalProfile = findViewById(R.id.profileName);
        TextView hospitalEmail = findViewById(R.id.profileEmail);
        LinearLayout logout = findViewById(R.id.logoutLayout);
        LinearLayout deleteAccountLayout = findViewById(R.id.deleteAccountLayout);
        LinearLayout fontSizeLayout = findViewById(R.id.notificationSettingsLayout);

        setupBottomNavigation();

        // Edit Information opens blank screen for hospital users
        UpdateProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent gotoUpdate = new Intent(Hospital_Profile.this, BlankEditProfileActivity.class);
                startActivity(gotoUpdate);
            }
        });

        // Font Size Layout
        if (fontSizeLayout != null) {
            fontSizeLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openFontSizeSettings();
                }
            });
        }

        // Feedback Support Layout
        LinearLayout feedbackSupportLayout = findViewById(R.id.feedbackSupportLayout);
        feedbackSupportLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Hospital_Profile.this, FeedbackActivity.class);
                startActivity(intent);
            }
        });

        logout.setOnClickListener(new View.OnClickListener() {
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

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(Hospital_Profile.this, getString(R.string.user_not_authenticated), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        String uid = currentUser.getUid();
        String userType = "hospital";

        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("hospitalName");
                        String email = documentSnapshot.getString("email");

                        if (name != null && !name.isEmpty()) {
                            hospitalProfile.setText(name);
                        }
                        if (email != null && !email.isEmpty()) {
                            hospitalEmail.setText(email);
                        }
                    } else {
                        Log.w("Hospital_Profile", "Hospital document not found for uid: " + uid);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Hospital_Profile", "Failed to load hospital profile", e);
                });
    }

    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.logout))
                .setMessage(getString(R.string.logout_confirmation_message_alt))
                .setPositiveButton("Yes", (dialog, which) -> {
                    // User confirmed logout
                    Log.d("Hospital_Profile", "🚪 User logging out - stopping all background services");
                    
                    // Stop ALL background services to prevent notifications to wrong user
                    BackgroundServiceManager.stopAllBackgroundServices(this);
                    
                    // Clear stored credentials
                    clearStoredCredentials();

                    // Then sign out from Firebase
                    mAuth.signOut();

                    Intent intent = new Intent(Hospital_Profile.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("No", (dialog, which) -> {
                    // User cancelled logout
                    dialog.dismiss();
                })
                .show();
    }

    // Helper method to clear stored credentials
    private void clearStoredCredentials() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_USER_ID);
        editor.remove(KEY_USER_TYPE);
        editor.remove(KEY_IS_LOGGED_IN);
        editor.remove(KEY_USER_EMAIL);
        editor.apply();
    }
    
    /**
     * Stops the status notification service (called on logout)
     */
    private void stopStatusNotificationService() {
        Intent serviceIntent = new Intent(this, HospitalStatusNotificationService.class);
        serviceIntent.putExtra("action", "cancel_notification");
        startService(serviceIntent);
        
        Log.d("Hospital_Profile", "Stopped status notification service on logout");
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavBar2);
        bottomNavigationView.setSelectedItemId(R.id.hospital_profile);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.hospital_profile) {
                return true;
            } else if (itemId == R.id.hospital_list) {
                startActivity(new Intent(getApplicationContext(), Hospital_List.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (itemId == R.id.hospital_home) {
                startActivity(new Intent(getApplicationContext(), Hospital_Dashboard.class));
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
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

        TextView moreOptionsTitle = findViewById(R.id.moreOptionsTitle);
        if (moreOptionsTitle != null) {
            moreOptionsTitle.setText(getString(R.string.more_options));
        }


        TextView feedbackSupportText = findViewById(R.id.feedbackSupportText);
        if (feedbackSupportText != null) {
            feedbackSupportText.setText(getString(R.string.feedback_support));
        }

        TextView logoutText = findViewById(R.id.logoutText);
        if (logoutText != null) {
            logoutText.setText(getString(R.string.logout));
        }
        
        TextView notificationSettingsText = findViewById(R.id.notificationSettingsText);
        if (notificationSettingsText != null) {
            notificationSettingsText.setText(getString(R.string.notification_settings));
        }
    }
    
    private void openFontSizeSettings() {
        Log.d("Hospital_Profile", "🔤 Opening font size settings");
        Intent intent = new Intent(Hospital_Profile.this, FontSizeActivity.class);
        fontSizeActivityLauncher.launch(intent);
    }
    
    private void applyFontSizeImmediately() {
        Log.d("Hospital_Profile", "Font size changed - navigating to dashboard");
        
        // Navigate to Hospital Dashboard to show the font size change
        Intent intent = new Intent(Hospital_Profile.this, Hospital_Dashboard.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
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
        String userType = "hospital";

        // Show progress
        Toast.makeText(this, getString(R.string.delete_account_progress), Toast.LENGTH_SHORT).show();
        Log.d("Hospital_Profile", "🗑️ Starting account deletion process for hospital: " + uid);

        // Step 1: Archive user data first (backup)
        archiveUserData(uid, userType, () -> {
            // Step 2: DELETE USER DOCUMENT FIRST - Most critical step
            deleteUserDocument(uid, userType, () -> {
                // Step 3: Delete user notifications
                deleteUserNotifications(uid, userType, () -> {
                    // Step 4: Delete user images from Storage
                    deleteUserImages(uid);
                });
            });
        });
    }

    private void deleteUserDocument(String uid, String userType, Runnable onComplete) {
        Log.d("Hospital_Profile", "🗑️ Deleting hospital document from Firestore: " + uid);
        
        // Get reference to user document
        com.google.firebase.firestore.DocumentReference userDocRef = db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid);
        
        // First, verify document exists
        userDocRef.get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Log.d("Hospital_Profile", "📄 Hospital document exists, deleting now...");
                        // Delete the document
                        userDocRef.delete()
                                .addOnSuccessListener(aVoid -> {
                                    Log.d("Hospital_Profile", "✅ Hospital document deleted successfully from Firestore");
                                    
                                    // Verify deletion
                                    verifyDocumentDeletion(uid, userType, 0, () -> {
                                        onComplete.run();
                                    });
                                })
                                .addOnFailureListener(e -> {
                                    Log.e("Hospital_Profile", "❌ Failed to delete hospital document: " + e.getMessage());
                                    Log.e("Hospital_Profile", "❌ Error details: " + e.getClass().getSimpleName());
                                    
                                    // Retry deletion
                                    retryDeleteDocument(uid, userType, 1, () -> {
                                        onComplete.run();
                                    });
                                });
                    } else {
                        Log.d("Hospital_Profile", "⚠️ Hospital document does not exist, may have been already deleted");
                        onComplete.run();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Hospital_Profile", "⚠️ Failed to check if hospital document exists: " + e.getMessage());
                    // Try to delete anyway
                    userDocRef.delete()
                            .addOnSuccessListener(aVoid -> {
                                Log.d("Hospital_Profile", "✅ Hospital document deleted (existence check failed but delete succeeded)");
                                onComplete.run();
                            })
                            .addOnFailureListener(ex -> {
                                Log.e("Hospital_Profile", "❌ Failed to delete hospital document: " + ex.getMessage());
                                // Continue anyway - might not exist
                                onComplete.run();
                            });
                });
    }

    private void retryDeleteDocument(String uid, String userType, int attempt, Runnable onComplete) {
        if (attempt >= 3) {
            Log.e("Hospital_Profile", "❌ Failed to delete hospital document after 3 attempts");
            onComplete.run();
            return;
        }
        
        Log.d("Hospital_Profile", "🔄 Retrying document deletion (attempt " + (attempt + 1) + "/3)");
        
        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d("Hospital_Profile", "✅ Hospital document deleted on retry attempt " + (attempt + 1));
                    verifyDocumentDeletion(uid, userType, 0, () -> {
                        onComplete.run();
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e("Hospital_Profile", "❌ Retry " + (attempt + 1) + " failed: " + e.getMessage());
                    // Wait 1 second before next retry
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        retryDeleteDocument(uid, userType, attempt + 1, onComplete);
                    }, 1000);
                });
    }

    private void verifyDocumentDeletion(String uid, String userType, int attempt, Runnable onComplete) {
        if (attempt >= 3) {
            Log.w("Hospital_Profile", "⚠️ Could not verify document deletion after 3 attempts, assuming deleted");
            onComplete.run();
            return;
        }
        
        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        Log.d("Hospital_Profile", "✅ Verified: Hospital document successfully deleted");
                        onComplete.run();
                    } else {
                        Log.w("Hospital_Profile", "⚠️ Document still exists, attempting to delete again (verification attempt " + (attempt + 1) + ")");
                        // Try to delete again
                        db.collection("Sagip")
                                .document("users")
                                .collection(userType)
                                .document(uid)
                                .delete()
                                .addOnSuccessListener(aVoid -> {
                                    // Verify again after a delay
                                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                        verifyDocumentDeletion(uid, userType, attempt + 1, onComplete);
                                    }, 500);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e("Hospital_Profile", "❌ Failed to delete document on verification: " + e.getMessage());
                                    onComplete.run();
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w("Hospital_Profile", "⚠️ Could not verify deletion: " + e.getMessage());
                    onComplete.run();
                });
    }

    private void archiveUserData(String uid, String userType, Runnable onComplete) {
        Log.d("Hospital_Profile", "📦 Archiving hospital data for: " + uid);
        
        // Get user document
        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Create archive document with user data
                        Map<String, Object> archiveData = new HashMap<>();
                        archiveData.putAll(documentSnapshot.getData());
                        archiveData.put("archivedAt", System.currentTimeMillis());
                        archiveData.put("originalUserType", userType);
                        archiveData.put("originalUid", uid);
                        
                        // Save to archive collection
                        db.collection("Sagip")
                                .document("archivedUsers")
                                .collection("users")
                                .document(uid)
                                .set(archiveData)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d("Hospital_Profile", "✅ Hospital data archived successfully");
                                    onComplete.run();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e("Hospital_Profile", "⚠️ Failed to archive hospital data: " + e.getMessage());
                                    // Continue with deletion even if archiving fails
                                    onComplete.run();
                                });
                    } else {
                        Log.d("Hospital_Profile", "⚠️ Hospital document not found, skipping archive");
                        onComplete.run();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Hospital_Profile", "⚠️ Failed to fetch hospital data for archiving: " + e.getMessage());
                    // Continue with deletion even if archiving fails
                    onComplete.run();
                });
    }

    private void deleteUserNotifications(String uid, String userType, Runnable onComplete) {
        Log.d("Hospital_Profile", "🗑️ Deleting hospital notifications for: " + uid);
        
        // Delete all notifications in user's notification collection
        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .collection("notifications")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        Log.d("Hospital_Profile", "✅ No notifications to delete");
                        onComplete.run();
                        return;
                    }
                    
                    int totalNotifications = querySnapshot.size();
                    final int[] deletedCount = {0};
                    
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : querySnapshot) {
                        doc.getReference().delete()
                                .addOnSuccessListener(aVoid -> {
                                    deletedCount[0]++;
                                    if (deletedCount[0] == totalNotifications) {
                                        Log.d("Hospital_Profile", "✅ Deleted " + totalNotifications + " notifications");
                                        onComplete.run();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e("Hospital_Profile", "⚠️ Failed to delete notification: " + e.getMessage());
                                    deletedCount[0]++;
                                    if (deletedCount[0] == totalNotifications) {
                                        onComplete.run();
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Hospital_Profile", "⚠️ Failed to fetch notifications: " + e.getMessage());
                    // Continue even if notification deletion fails
                    onComplete.run();
                });
    }

    private void deleteUserImages(String uid) {
        Log.d("Hospital_Profile", "🗑️ Deleting hospital images for: " + uid);
        StorageReference userImagesRef = storage.getReference().child("users/" + uid);
        
        userImagesRef.listAll()
                .addOnSuccessListener(listResult -> {
                    if (listResult.getItems().isEmpty()) {
                        Log.d("Hospital_Profile", "✅ No images to delete");
                        // Proceed to delete Firebase Auth account
                        deleteFirebaseAuthAccount();
                        return;
                    }
                    
                    int totalImages = listResult.getItems().size();
                    final int[] deletedCount = {0};
                    
                    // Delete all images
                    for (StorageReference item : listResult.getItems()) {
                        item.delete()
                                .addOnSuccessListener(aVoid -> {
                                    deletedCount[0]++;
                                    if (deletedCount[0] == totalImages) {
                                        Log.d("Hospital_Profile", "✅ Deleted " + totalImages + " hospital images");
                                        // Proceed to delete Firebase Auth account
                                        deleteFirebaseAuthAccount();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e("Hospital_Profile", "⚠️ Failed to delete image: " + e.getMessage());
                                    deletedCount[0]++;
                                    if (deletedCount[0] == totalImages) {
                                        // Proceed even if some images failed to delete
                                        deleteFirebaseAuthAccount();
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Hospital_Profile", "⚠️ Failed to list images: " + e.getMessage());
                    // Even if image deletion fails, proceed with account deletion
                    deleteFirebaseAuthAccount();
                });
    }

    private void deleteFirebaseAuthAccount() {
        Log.d("Hospital_Profile", "🗑️ Deleting Firebase Auth account");
        
        // Stop all background services first
        BackgroundServiceManager.stopAllBackgroundServices(this);
        
        // Clear stored credentials
        clearStoredCredentials();
        
        // Delete the user from Firebase Auth
        if (mAuth.getCurrentUser() != null) {
            mAuth.getCurrentUser().delete()
                    .addOnSuccessListener(aVoid -> {
                        Log.d("Hospital_Profile", "✅ Firebase Auth account deleted successfully");
                        Toast.makeText(this, getString(R.string.delete_account_success), Toast.LENGTH_LONG).show();
                        
                        // Redirect to login page
                        Intent intent = new Intent(Hospital_Profile.this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Log.e("Hospital_Profile", "❌ Failed to delete Firebase Auth account: " + e.getMessage());
                        Toast.makeText(this, getString(R.string.delete_account_failed) + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        } else {
            Log.e("Hospital_Profile", "❌ No current user in Firebase Auth");
            Toast.makeText(this, getString(R.string.delete_account_failed), Toast.LENGTH_SHORT).show();
        }
    }
}