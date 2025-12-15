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

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class Rescuer_Profile extends BaseRescuerActivity {

    private static final String TAG = "Rescuer_Profile";
    private static final String PREF_NAME = "SagipAppPrefs";
    private static final String KEY_CACHED_RESCUER_NAME = "cachedRescuerName";
    private static final String KEY_CACHED_RESCUER_EMAIL = "cachedRescuerEmail";
    
    FirebaseFirestore db;
    FirebaseStorage storage;
    private SharedPreferences sharedPreferences;
    
    // Emergency notification system variables
    private ListenerRegistration emergencyListener;
    private String userId;
    private String userType;
    
    // Activity result launcher for font size changes
    private ActivityResultLauncher<Intent> fontSizeActivityLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        
        // Apply saved font size preference
        FontSizeHelper.applyFontSize(this);
        
        setContentView(R.layout.activity_rescuer_profile);

        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        
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

        LinearLayout UpdateProfile = findViewById(R.id.gotoupdate1);
        TextView rescueProfile = findViewById(R.id.profileName);
        TextView rescueEmail = findViewById(R.id.profileEmail);
        LinearLayout logout = findViewById(R.id.logoutLayout);
        LinearLayout deleteAccountLayout = findViewById(R.id.deleteAccountLayout);
        LinearLayout fontSizeLayout = findViewById(R.id.notificationSettingsLayout);

        setupBottomNavigation();

        // Edit Information opens blank screen for rescuer users
        UpdateProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent gotoUpdate = new Intent(Rescuer_Profile.this, BlankEditProfileActivity.class);
                startActivity(gotoUpdate);
            }
        });

        // Font Size Layout
        fontSizeLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFontSizeSettings();
            }
        });

        // Feedback Support Layout
        LinearLayout feedbackSupportLayout = findViewById(R.id.feedbackSupportLayout);
        feedbackSupportLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Rescuer_Profile.this, FeedbackActivity.class);
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

        // Show cached values immediately for instant UI
        loadCachedRescuerProfile(rescueProfile, rescueEmail);

        String uid = mAuth.getCurrentUser().getUid();
        String userType = "rescuer";

        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String firstName = documentSnapshot.getString("rescuegroup");
                        String middleName = documentSnapshot.getString("email");


                        rescueProfile.setText(firstName);
                        rescueEmail.setText(middleName);

                        // Cache fetched values for future instant loading
                        cacheRescuerProfile(firstName, middleName);
                    }
                });
    }

    private void loadCachedRescuerProfile(TextView rescueProfile, TextView rescueEmail) {
        String cachedName = sharedPreferences.getString(KEY_CACHED_RESCUER_NAME, null);
        String cachedEmail = sharedPreferences.getString(KEY_CACHED_RESCUER_EMAIL, null);

        if (cachedName != null && !cachedName.isEmpty()) {
            rescueProfile.setText(cachedName);
        } else {
            rescueProfile.setText(getString(R.string.loading));
        }

        if (cachedEmail != null && !cachedEmail.isEmpty()) {
            rescueEmail.setText(cachedEmail);
        } else {
            rescueEmail.setText("");
        }
    }

    private void cacheRescuerProfile(String name, String email) {
        sharedPreferences.edit()
                .putString(KEY_CACHED_RESCUER_NAME, name != null ? name : "")
                .putString(KEY_CACHED_RESCUER_EMAIL, email != null ? email : "")
                .apply();
    }

    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.logout))
                .setMessage(getString(R.string.logout_confirmation_message_alt))
                .setPositiveButton("Yes", (dialog, which) -> {
                    // User confirmed logout - use the proper logout method
                    handleLogout();
                })
                .setNegativeButton("No", (dialog, which) -> {
                    // User cancelled logout
                    dialog.dismiss();
                })
                .show();
    }

    // Method to handle logout and clear emergency state
    private void handleLogout() {
        Log.d(TAG, "🚪 User logging out - stopping all background services");
        
        // Stop ALL background services to prevent notifications to wrong user
        BackgroundServiceManager.stopAllBackgroundServices(this);
        
        // Clear stored credentials first
        clearStoredCredentials();
        
        // Sign out from Firebase
        mAuth.signOut();
        
        // Navigate to login screen with a cleared back stack
        Intent intent = new Intent(Rescuer_Profile.this, MainActivity.class);
        intent.putExtra("LOGOUT_ACTION", true); // Signal that this is a logout action
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finishAffinity();
    }
    
    // Helper method to clear stored credentials
    private void clearStoredCredentials() {
        Log.d(TAG, "Clearing stored credentials");
        android.content.SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        editor.remove("user_id");
        editor.remove("user_type");
        editor.remove("is_logged_in");
        editor.remove("user_phone");
        editor.remove("user_email");
        editor.commit(); // Use commit() for synchronous clearing before redirect
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavBar2);
        bottomNavigationView.setSelectedItemId(R.id.rescuer_profile);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.rescuer_profile) {
                return true;
            } else if (itemId == R.id.rescuer_hospital) {
                startActivity(new Intent(getApplicationContext(), Rescuer_List.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (itemId == R.id.rescuer_dashboard) {
                startActivity(new Intent(getApplicationContext(), Rescuer_Dashboard.class));
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
    }

    @Override
    protected void updateSpecificProfileElements() {
        // Update profile-specific UI elements
        TextView labelProfile = findViewById(R.id.labelProfile);
        if (labelProfile != null) {
            labelProfile.setText(getString(R.string.rescuer_profile));
        }
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


        TextView feedbackSupportText = findViewById(R.id.feedbackSupportText);
        if (feedbackSupportText != null) {
            feedbackSupportText.setText(getString(R.string.feedback_support));
        }

        TextView logoutText = findViewById(R.id.logoutText);
        if (logoutText != null) {
            logoutText.setText(getString(R.string.logout));
        }
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
        String userType = "rescuer";

        // Show progress
        Toast.makeText(this, getString(R.string.delete_account_progress), Toast.LENGTH_SHORT).show();
        Log.d(TAG, "🗑️ Starting account deletion process for rescuer: " + uid);

        // Step 1: Archive user data first (backup)
        archiveUserData(uid, userType, () -> {
            // Step 2: DELETE USER DOCUMENT FIRST - Most critical step
            deleteUserDocument(uid, userType, () -> {
                // Step 3: Delete user notifications
                deleteUserNotifications(uid, userType, () -> {
                    // Step 4: Delete emergency notifications (rescuer-specific)
                    deleteEmergencyNotifications(uid, () -> {
                        // Step 5: Handle emergency requests assigned to this rescuer
                        archiveRescuerEmergencyRequests(uid, () -> {
                            // Step 6: Delete user images from Storage
                            deleteUserImages(uid);
                        });
                    });
                });
            });
        });
    }

    private void deleteUserDocument(String uid, String userType, Runnable onComplete) {
        Log.d(TAG, "🗑️ Deleting rescuer document from Firestore: " + uid);
        Log.d(TAG, "🗑️ Full path: Sagip/users/" + userType + "/" + uid);
        
        // Get reference to user document
        com.google.firebase.firestore.DocumentReference userDocRef = db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid);
        
        // Delete immediately without checking existence first
        // This is more aggressive and ensures deletion happens
        userDocRef.delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Rescuer document delete() called successfully");
                    
                    // Wait a moment for Firestore to process, then verify
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        verifyAndForceDeleteDocument(uid, userType, 0, onComplete);
                    }, 1000);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to delete rescuer document: " + e.getMessage());
                    Log.e(TAG, "❌ Error type: " + e.getClass().getSimpleName());
                    if (e instanceof com.google.firebase.firestore.FirebaseFirestoreException) {
                        com.google.firebase.firestore.FirebaseFirestoreException firestoreEx = 
                            (com.google.firebase.firestore.FirebaseFirestoreException) e;
                        Log.e(TAG, "❌ Firestore error code: " + firestoreEx.getCode());
                    }
                    
                    // Retry deletion immediately
                    retryDeleteDocument(uid, userType, 1, onComplete);
                });
    }
    
    private void verifyAndForceDeleteDocument(String uid, String userType, int attempt, Runnable onComplete) {
        if (attempt >= 5) {
            Log.e(TAG, "❌ Document still exists after 5 verification attempts - forcing final deletion");
            // Final aggressive attempt
            forceDeleteDocument(uid, userType, onComplete);
            return;
        }
        
        Log.d(TAG, "🔍 Verifying document deletion (attempt " + (attempt + 1) + "/5)");
        
        com.google.firebase.firestore.DocumentReference userDocRef = db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid);
        
        userDocRef.get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        Log.d(TAG, "✅ VERIFIED: Rescuer document successfully deleted from Firestore");
                        onComplete.run();
                    } else {
                        Log.w(TAG, "⚠️ Document STILL EXISTS! Attempting forced deletion (attempt " + (attempt + 1) + ")");
                        // Document still exists - delete it again
                        userDocRef.delete()
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "✅ Forced delete() called again");
                                    // Wait and verify again
                                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                        verifyAndForceDeleteDocument(uid, userType, attempt + 1, onComplete);
                                    }, 1500);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "❌ Forced deletion failed: " + e.getMessage());
                                    // Try again after delay
                                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                        verifyAndForceDeleteDocument(uid, userType, attempt + 1, onComplete);
                                    }, 2000);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "⚠️ Could not verify deletion: " + e.getMessage());
                    // Assume deleted if we can't verify
                    if (attempt >= 3) {
                        Log.w(TAG, "⚠️ Assuming document deleted after 3 failed verification attempts");
                        onComplete.run();
                    } else {
                        verifyAndForceDeleteDocument(uid, userType, attempt + 1, onComplete);
                    }
                });
    }
    
    private void forceDeleteDocument(String uid, String userType, Runnable onComplete) {
        Log.e(TAG, "🔥 FORCE DELETING document - final attempt");
        
        com.google.firebase.firestore.DocumentReference userDocRef = db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid);
        
        // Try multiple times in quick succession
        final int[] attempts = {0};
        final int maxAttempts = 3;
        
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        
        Runnable deleteAttempt = new Runnable() {
            @Override
            public void run() {
                attempts[0]++;
                Log.d(TAG, "🔥 Force delete attempt " + attempts[0] + "/" + maxAttempts);
                
                userDocRef.delete()
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "✅ Force delete attempt " + attempts[0] + " succeeded");
                            // Wait and verify one more time
                            handler.postDelayed(() -> {
                                userDocRef.get()
                                        .addOnSuccessListener(doc -> {
                                            if (!doc.exists()) {
                                                Log.d(TAG, "✅ FORCE DELETE VERIFIED: Document removed");
                                                onComplete.run();
                                            } else {
                                                Log.e(TAG, "❌ Document STILL EXISTS after force delete!");
                                                onComplete.run(); // Continue anyway
                                            }
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.w(TAG, "⚠️ Could not verify force delete: " + e.getMessage());
                                            onComplete.run();
                                        });
                            }, 2000);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "❌ Force delete attempt " + attempts[0] + " failed: " + e.getMessage());
                            if (attempts[0] < maxAttempts) {
                                handler.postDelayed(this, 1000);
                            } else {
                                Log.e(TAG, "❌ All force delete attempts failed");
                                onComplete.run();
                            }
                        });
            }
        };
        
        deleteAttempt.run();
    }

    private void retryDeleteDocument(String uid, String userType, int attempt, Runnable onComplete) {
        if (attempt >= 5) {
            Log.e(TAG, "❌ Failed to delete rescuer document after 5 retry attempts - using force delete");
            forceDeleteDocument(uid, userType, onComplete);
            return;
        }
        
        Log.d(TAG, "🔄 Retrying document deletion (attempt " + (attempt + 1) + "/5)");
        
        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Rescuer document deleted on retry attempt " + (attempt + 1));
                    // Wait and verify
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        verifyAndForceDeleteDocument(uid, userType, 0, onComplete);
                    }, 1000);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Retry " + (attempt + 1) + " failed: " + e.getMessage());
                    // Wait before next retry
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        retryDeleteDocument(uid, userType, attempt + 1, onComplete);
                    }, 1500);
                });
    }

    private void archiveUserData(String uid, String userType, Runnable onComplete) {
        Log.d(TAG, "📦 Archiving rescuer data for: " + uid);
        
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
                                    Log.d(TAG, "✅ Rescuer data archived successfully");
                                    onComplete.run();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "⚠️ Failed to archive rescuer data: " + e.getMessage());
                                    // Continue with deletion even if archiving fails
                                    onComplete.run();
                                });
                    } else {
                        Log.d(TAG, "⚠️ Rescuer document not found, skipping archive");
                        onComplete.run();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "⚠️ Failed to fetch rescuer data for archiving: " + e.getMessage());
                    // Continue with deletion even if archiving fails
                    onComplete.run();
                });
    }

    private void deleteUserNotifications(String uid, String userType, Runnable onComplete) {
        Log.d(TAG, "🗑️ Deleting rescuer notifications for: " + uid);
        
        // Delete all notifications in user's notification collection
        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .collection("notifications")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        Log.d(TAG, "✅ No notifications to delete");
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
                                        Log.d(TAG, "✅ Deleted " + totalNotifications + " notifications");
                                        onComplete.run();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "⚠️ Failed to delete notification: " + e.getMessage());
                                    deletedCount[0]++;
                                    if (deletedCount[0] == totalNotifications) {
                                        onComplete.run();
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "⚠️ Failed to fetch notifications: " + e.getMessage());
                    // Continue even if notification deletion fails
                    onComplete.run();
                });
    }

    private void deleteEmergencyNotifications(String uid, Runnable onComplete) {
        Log.d(TAG, "🗑️ Deleting emergency notifications for rescuer: " + uid);
        
        // Delete all emergency notifications for this rescuer
        db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .document(uid)
                .collection("emergencyNotifications")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        Log.d(TAG, "✅ No emergency notifications to delete");
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
                                        Log.d(TAG, "✅ Deleted " + totalNotifications + " emergency notifications");
                                        onComplete.run();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "⚠️ Failed to delete emergency notification: " + e.getMessage());
                                    deletedCount[0]++;
                                    if (deletedCount[0] == totalNotifications) {
                                        onComplete.run();
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "⚠️ Failed to fetch emergency notifications: " + e.getMessage());
                    // Continue even if emergency notification deletion fails
                    onComplete.run();
                });
    }

    private void archiveRescuerEmergencyRequests(String uid, Runnable onComplete) {
        Log.d(TAG, "📦 Archiving emergency requests assigned to rescuer: " + uid);
        
        // Find all emergency requests assigned to this rescuer
        db.collection("Sagip")
                .document("emergencyRequests")
                .collection("activeRequests")
                .whereEqualTo("assignedRescuerId", uid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        Log.d(TAG, "✅ No emergency requests to archive");
                        onComplete.run();
                        return;
                    }
                    
                    int totalRequests = querySnapshot.size();
                    final int[] archivedCount = {0};
                    
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : querySnapshot) {
                        // Archive the emergency request
                        Map<String, Object> archiveData = new HashMap<>();
                        archiveData.putAll(doc.getData());
                        archiveData.put("archivedAt", System.currentTimeMillis());
                        archiveData.put("archivedReason", "rescuer_account_deleted");
                        
                        db.collection("Sagip")
                                .document("archivedUsers")
                                .collection("emergencyRequests")
                                .document(doc.getId())
                                .set(archiveData)
                                .addOnSuccessListener(aVoid -> {
                                    // Remove rescuer assignment from active request
                                    Map<String, Object> updateData = new HashMap<>();
                                    updateData.put("assignedRescuerId", null);
                                    updateData.put("status", "pending");
                                    
                                    doc.getReference().update(updateData)
                                            .addOnSuccessListener(aVoid2 -> {
                                                archivedCount[0]++;
                                                if (archivedCount[0] == totalRequests) {
                                                    Log.d(TAG, "✅ Archived " + totalRequests + " emergency requests");
                                                    onComplete.run();
                                                }
                                            })
                                            .addOnFailureListener(e -> {
                                                Log.e(TAG, "⚠️ Failed to update emergency request: " + e.getMessage());
                                                archivedCount[0]++;
                                                if (archivedCount[0] == totalRequests) {
                                                    onComplete.run();
                                                }
                                            });
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "⚠️ Failed to archive emergency request: " + e.getMessage());
                                    archivedCount[0]++;
                                    if (archivedCount[0] == totalRequests) {
                                        onComplete.run();
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "⚠️ Failed to fetch emergency requests: " + e.getMessage());
                    // Continue even if emergency request handling fails
                    onComplete.run();
                });
    }

    private void deleteUserImages(String uid) {
        Log.d(TAG, "🗑️ Deleting rescuer images for: " + uid);
        StorageReference userImagesRef = storage.getReference().child("users/" + uid);
        
        userImagesRef.listAll()
                .addOnSuccessListener(listResult -> {
                    if (listResult.getItems().isEmpty()) {
                        Log.d(TAG, "✅ No images to delete");
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
                                        Log.d(TAG, "✅ Deleted " + totalImages + " rescuer images");
                                        // Proceed to delete Firebase Auth account
                                        deleteFirebaseAuthAccount();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "⚠️ Failed to delete image: " + e.getMessage());
                                    deletedCount[0]++;
                                    if (deletedCount[0] == totalImages) {
                                        // Proceed even if some images failed to delete
                                        deleteFirebaseAuthAccount();
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "⚠️ Failed to list images: " + e.getMessage());
                    // Even if image deletion fails, proceed with account deletion
                    deleteFirebaseAuthAccount();
                });
    }

    private void deleteFirebaseAuthAccount() {
        Log.d(TAG, "🗑️ Deleting Firebase Auth account");

        // Delete the user from Firebase Auth
        if (mAuth.getCurrentUser() != null) {
            mAuth.getCurrentUser().delete()
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "✅ Firebase Auth account deleted successfully");
                        
                        // Stop services and clear credentials after successful deletion
                        BackgroundServiceManager.stopAllBackgroundServices(this);
                        clearStoredCredentials();
                        
                        Toast.makeText(this, getString(R.string.delete_account_success), Toast.LENGTH_LONG).show();
                        
                        // Redirect to login page
                        Intent intent = new Intent(Rescuer_Profile.this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ Failed to delete Firebase Auth account: " + e.getMessage());
                        
                        // Firestore data is already deleted, so cleanup and redirect anyway
                        BackgroundServiceManager.stopAllBackgroundServices(this);
                        clearStoredCredentials();
                        mAuth.signOut();
                        
                        Toast.makeText(this, getString(R.string.delete_account_success), Toast.LENGTH_LONG).show();
                        
                        // Redirect to login page
                        Intent intent = new Intent(Rescuer_Profile.this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    });
        } else {
            Log.e(TAG, "❌ No current user in Firebase Auth - cleaning up and redirecting");
            
            // Firestore data is already deleted, so cleanup and redirect
            BackgroundServiceManager.stopAllBackgroundServices(this);
            clearStoredCredentials();
            
            Toast.makeText(this, getString(R.string.delete_account_success), Toast.LENGTH_LONG).show();
            
            // Redirect to login page
            Intent intent = new Intent(Rescuer_Profile.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "🚨 Rescuer_Profile onResume - starting emergency listener");
        
        // Initialize emergency notification system
        initializeEmergencyNotificationSystem();
        
        // Start emergency listener when activity resumes
        if (emergencyListener == null) {
            startEmergencyListener();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "🚨 Rescuer_Profile onPause - stopping emergency listener");
        
        // Stop emergency listener when activity pauses - EmergencyNotificationService will handle background
        if (emergencyListener != null) {
            emergencyListener.remove();
            emergencyListener = null;
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "🚨 Rescuer_Profile onDestroy - cleaning up emergency listener");
        
        // Remove emergency listener
        if (emergencyListener != null) {
            emergencyListener.remove();
            emergencyListener = null;
        }
    }
    
    // =============== EMERGENCY NOTIFICATION SYSTEM ===============
    
    /**
     * Initialize emergency notification system
     */
    private void initializeEmergencyNotificationSystem() {
        Log.d(TAG, "🚨 Initializing emergency notification system in Rescuer_Profile");
        
        // Get user info from preferences
        android.content.SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        userId = prefs.getString("user_id", null);
        userType = prefs.getString("user_type", null);
        
        Log.d(TAG, "🚨 User ID: " + userId + ", User Type: " + userType);
    }
    
    /**
     * Start emergency listener
     */
    private void startEmergencyListener() {
        Log.d(TAG, "🚨 Starting emergency listener in Rescuer_Profile...");
        
        // Check if user is a rescuer
        if (userId == null || userType == null || !userType.equals("rescuer")) {
            Log.w(TAG, "⚠️ User is not a rescuer, skipping emergency listener");
            return;
        }
        
        // Prevent duplicate listeners
        if (emergencyListener != null) {
            Log.w(TAG, "Emergency listener already exists, removing old one first");
            emergencyListener.remove();
            emergencyListener = null;
        }
        
        // Listen for new emergency notifications
        emergencyListener = db.collection("Sagip")
                .document("emergencyNotifications")
                .collection("activeEmergencies")
                .whereEqualTo("isActive", true)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "🚨 Emergency listener failed.", e);
                        return;
                    }
                    
                    Log.d(TAG, "🚨 Emergency listener triggered in Rescuer_Profile - snapshots: " + (snapshots != null ? snapshots.size() : "null"));
                    
                    if (snapshots != null && !snapshots.isEmpty()) {
                        for (DocumentChange dc : snapshots.getDocumentChanges()) {
                            Log.d(TAG, "🚨 Document change type: " + dc.getType() + " for document: " + dc.getDocument().getId());
                            
                            if (dc.getType() == DocumentChange.Type.ADDED) {
                                // New emergency detected!
                                DocumentSnapshot emergency = dc.getDocument();
                                Log.d(TAG, "🚨 NEW EMERGENCY DETECTED IN RESCUER_PROFILE: " + emergency.getId());
                                handleNewEmergency(emergency);
                            }
                        }
                    } else {
                        Log.d(TAG, "🚨 No active emergencies found in Rescuer_Profile");
                    }
                });
        
        Log.d(TAG, "🚨 Emergency listener started successfully in Rescuer_Profile");
    }
    
    /**
     * Handle new emergency notification
     */
    private void handleNewEmergency(DocumentSnapshot emergency) {
        String title = emergency.getString("title");
        String message = emergency.getString("message");
        String seniorName = emergency.getString("seniorName");
        String seniorPhone = emergency.getString("seniorPhone");
        String locationAddress = emergency.getString("locationAddress");
        String helpRequestId = emergency.getString("helpRequestId");
        
        Log.d(TAG, "🚨🚨🚨 NEW EMERGENCY RECEIVED IN RESCUER_PROFILE 🚨🚨🚨");
        Log.d(TAG, "🚨 Senior: " + seniorName);
        Log.d(TAG, "🚨 Location: " + locationAddress);
        Log.d(TAG, "🚨 Help Request ID: " + helpRequestId);
        
        // Check if this rescuer has already responded to this emergency
        String respondedBy = emergency.getString("respondedBy");
        if (respondedBy != null && respondedBy.equals(userId)) {
            Log.d(TAG, "Current rescuer already responded to this emergency, skipping notification for: " + helpRequestId);
            return;
        }
        
        // Show emergency alert dialog
        showEmergencyAlert(title, message, seniorName, seniorPhone, locationAddress, helpRequestId);
    }
    
    /**
     * Show emergency alert dialog
     */
    private void showEmergencyAlert(String title, String message, String seniorName, 
                                  String seniorPhone, String locationAddress, String helpRequestId) {
        
        String fullMessage = "🚨 EMERGENCY ALERT 🚨\n\n" +
                "👤 Senior: " + seniorName + "\n" +
                "📍 Location: " + locationAddress + "\n" +
                "📞 Phone: " + (seniorPhone != null ? seniorPhone : "Not provided") + "\n\n" +
                "Please respond immediately!";
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title != null ? title : "🚨 EMERGENCY HELP REQUEST")
                .setMessage(fullMessage)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("🚑 RESPOND NOW", (dialog, which) -> {
                    // Navigate to dashboard to handle emergency
                    Intent intent = new Intent(this, Rescuer_Dashboard.class);
                    intent.putExtra("emergency_notification", true);
                    intent.putExtra("helpRequestId", helpRequestId);
                    intent.putExtra("senior_name", seniorName);
                    intent.putExtra("location", locationAddress);
                    startActivity(intent);
                    dialog.dismiss();
                })
                .setNegativeButton("Close", (dialog, which) -> dialog.dismiss())
                .setCancelable(false); // Prevent dismissing by tapping outside
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        Log.d(TAG, "✅ Emergency alert shown in Rescuer_Profile for: " + seniorName);
    }
    
    private void openFontSizeSettings() {
        Log.d(TAG, "🔤 Opening font size settings");
        Intent intent = new Intent(Rescuer_Profile.this, FontSizeActivity.class);
        fontSizeActivityLauncher.launch(intent);
    }
    
    private void applyFontSizeImmediately() {
        Log.d(TAG, "Font size changed - navigating to dashboard");
        
        // Navigate to Rescuer Dashboard to show the font size change
        Intent intent = new Intent(Rescuer_Profile.this, Rescuer_Dashboard.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
}