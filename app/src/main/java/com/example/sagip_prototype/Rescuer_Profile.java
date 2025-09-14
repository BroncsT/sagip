package com.example.sagip_prototype;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

public class Rescuer_Profile extends BaseRescuerActivity {

    private static final String TAG = "Rescuer_Profile";
    
    FirebaseFirestore db;
    FirebaseStorage storage;
    
    // Emergency notification system variables
    private ListenerRegistration emergencyListener;
    private String userId;
    private String userType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_rescuer_profile);

        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        // Add language selection functionality
        addLanguageSelectionToLayout();

        LinearLayout UpdateProfile = findViewById(R.id.gotoupdate1);
        TextView rescueProfile = findViewById(R.id.profileName);
        TextView rescueEmail = findViewById(R.id.profileEmail);
        LinearLayout logout = findViewById(R.id.logoutLayout);
        LinearLayout deleteAccountLayout = findViewById(R.id.deleteAccountLayout);

        setupBottomNavigation();

        UpdateProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent gotoUpdate = new Intent(Rescuer_Profile.this, Rescuer_Registration.class);
                startActivity(gotoUpdate);

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
                    }
                });
    }

    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
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
        // Clear stored credentials first
        clearStoredCredentials();
        
        // Sign out from Firebase
        mAuth.signOut();
        
        // Navigate to login screen
        Intent intent = new Intent(Rescuer_Profile.this, MainActivity.class);
        intent.putExtra("LOGOUT_ACTION", true); // Signal that this is a logout action
        startActivity(intent);
        finish();
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
        editor.apply();
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
                                Intent intent = new Intent(Rescuer_Profile.this, MainActivity.class);
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
                                
                                Intent intent = new Intent(Rescuer_Profile.this, MainActivity.class);
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
}