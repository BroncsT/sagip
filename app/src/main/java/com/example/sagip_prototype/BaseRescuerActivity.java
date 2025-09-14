package com.example.sagip_prototype;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public abstract class BaseRescuerActivity extends AppCompatActivity {
    
    private static final String TAG = "BaseRescuerActivity";
    protected FirebaseAuth mAuth;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        
        // Apply saved language preference
        String savedLanguage = LanguageSelectionActivity.getSavedLanguage(this);
        LanguageSelectionActivity.setAppLanguage(this, savedLanguage);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Handle notifications when activity resumes
        handleNotificationClick();
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // Handle notifications when activity receives new intent
        handleNotificationClick();
    }
    
    /**
     * Handles notification clicks for hospital status updates
     */
    protected void handleNotificationClick() {
        Intent intent = getIntent();
        if (intent != null) {
            String notificationType = intent.getStringExtra("notification_type");
            Log.d(TAG, "Activity opened from notification - Type: " + notificationType);
            
            if ("hospital_status_update".equals(notificationType)) {
                // Handle hospital status update notification
                String hospitalName = intent.getStringExtra("hospital_name");
                String hospitalStatus = intent.getStringExtra("hospital_status");
                int availableBeds = intent.getIntExtra("available_beds", 0);
                int availableDoctors = intent.getIntExtra("available_doctors", 0);
                
                Log.d(TAG, "Hospital status update notification - Hospital: " + hospitalName + 
                    ", Status: " + hospitalStatus + ", Beds: " + availableBeds + ", Doctors: " + availableDoctors);
                
                if (hospitalName != null && hospitalStatus != null) {
                    // Show hospital status update info
                    showHospitalStatusUpdateDialog(hospitalName, hospitalStatus, availableBeds, availableDoctors);
                    
                    // Clear the intent extras to prevent repeated handling
                    intent.removeExtra("notification_type");
                    intent.removeExtra("hospital_name");
                    intent.removeExtra("hospital_status");
                    intent.removeExtra("available_beds");
                    intent.removeExtra("available_doctors");
                }
            }
        }
    }
    
    /**
     * Shows hospital status update dialog
     */
    private void showHospitalStatusUpdateDialog(String hospitalName, String hospitalStatus, int availableBeds, int availableDoctors) {
        String statusEmoji = getStatusEmoji(hospitalStatus);
        String message = "🏥 " + hospitalName + "\n\n" +
                        "Status: " + statusEmoji + " " + hospitalStatus.toUpperCase() + "\n" +
                        "Available Beds: " + availableBeds + "\n" +
                        "Available Doctors: " + availableDoctors + "\n\n" +
                        "This information will help with emergency response planning.";
        
        new AlertDialog.Builder(this)
                .setTitle("🏥 Hospital Status Update")
                .setMessage(message)
                .setPositiveButton("View Hospital List", (dialog, which) -> {
                    // Navigate to hospital list with highlighting
                    Intent intent = new Intent(this, Rescuer_List.class);
                    intent.putExtra("highlight_hospital", hospitalName);
                    intent.putExtra("notification_type", "hospital_status_update");
                    intent.putExtra("hospital_status", hospitalStatus);
                    intent.putExtra("available_beds", availableBeds);
                    intent.putExtra("available_doctors", availableDoctors);
                    startActivity(intent);
                })
                .setNeutralButton("View Dashboard", (dialog, which) -> {
                    // Navigate to dashboard
                    Intent intent = new Intent(this, Rescuer_Dashboard.class);
                    startActivity(intent);
                })
                .setNegativeButton("Dismiss", (dialog, which) -> {
                    // Just dismiss the dialog
                    dialog.dismiss();
                })
                .setCancelable(true)
                .setIcon(android.R.drawable.ic_dialog_info)
                .show();
    }
    
    /**
     * Gets status emoji for hospital status
     */
    private String getStatusEmoji(String status) {
        if (status == null) return "❓";
        
        switch (status.toLowerCase()) {
            case "operational":
                return "🟢";
            case "busy":
                return "🟡";
            case "overcrowded":
                return "🟠";
            case "closed":
                return "🔴";
            case "emergency_only":
                return "🚨";
            default:
                return "❓";
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Add language selection menu item
        menu.add(Menu.NONE, 1001, Menu.NONE, getString(R.string.select_language));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1001) {
            showLanguageSelectionDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    protected void showLanguageSelectionDialog() {
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

    protected void updateUILanguage() {
        // Update language selection text if it exists
        TextView languageSelectionText = findViewById(R.id.languageSelectionText);
        if (languageSelectionText != null) {
            languageSelectionText.setText(getString(R.string.select_language));
        }

        // Update common profile elements
        updateCommonProfileElements();
        
        // Let child classes update their specific elements
        updateSpecificProfileElements();
    }

    protected void updateCommonProfileElements() {
        // Update common profile elements like titles, buttons, etc.
        // This can be overridden by child classes
    }

    protected void updateSpecificProfileElements() {
        // This method should be overridden by child classes to update their specific UI elements
    }

    protected void addLanguageSelectionToLayout() {
        // This method can be called by child classes to add language selection to their layout
        TextView languageSelectionText = findViewById(R.id.languageSelectionText);
        if (languageSelectionText != null) {
            languageSelectionText.setOnClickListener(v -> showLanguageSelectionDialog());
        }
    }
}
