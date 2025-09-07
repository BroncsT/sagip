package com.example.sagip_prototype;

import android.content.Intent;
import android.os.Bundle;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
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
                mAuth.signOut();
                startActivity(new Intent(Barangay_Profile.this, MainActivity.class));
                finish();
            }
        });

        deleteAccountLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDeleteAccountConfirmationDialog();
            }
        });

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