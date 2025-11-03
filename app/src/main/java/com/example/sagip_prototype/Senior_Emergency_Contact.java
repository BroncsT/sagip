package com.example.sagip_prototype;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public class Senior_Emergency_Contact extends AppCompatActivity implements EmergencyContactAdapter.OnContactActionListener {

    private static final String PREF_NAME = "SagipAppPrefs";
    private static final String KEY_CACHED_EMERGENCY_CONTACTS = "cachedEmergencyContacts";
    
    // Store reference to active dialogs for language refresh
    private AlertDialog activeUpdateDialog;
    private AlertDialog activeDeleteDialog;
    private int activeUpdatePosition;
    private Emergency_Contacts activeUpdateContact;
    private int activeDeletePosition;
    private Emergency_Contacts activeDeleteContact;
    
    // Track current language for change detection
    private String currentLanguage;

    FirebaseFirestore db;
    FirebaseAuth mAuth;
    private SharedPreferences sharedPreferences;

    RecyclerView recyclerView;
    EmergencyContactAdapter adapter;
    List<Emergency_Contacts> emergencyContacts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        
        // Apply saved font size preference
        FontSizeHelper.applyFontSize(this);
        
        setContentView(R.layout.activity_senior_emergency_contact);

        recyclerView = findViewById(R.id.emergencyRecycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        emergencyContacts = new ArrayList<>();
        adapter = new EmergencyContactAdapter(emergencyContacts, this);
        adapter.setOnContactActionListener(this); // Set the listener
        recyclerView.setAdapter(adapter);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        
        // Initialize current language and check for changes
        String newLanguage = getResources().getConfiguration().locale.getLanguage();
        Log.d("Senior_Emergency_Contact", "Language in onCreate: " + newLanguage);
        
        // Check if language has changed from previous session
        String previousLanguage = sharedPreferences.getString("last_language", null);
        if (previousLanguage != null && !previousLanguage.equals(newLanguage)) {
            Log.d("Senior_Emergency_Contact", "Language changed from " + previousLanguage + " to " + newLanguage);
            Toast.makeText(this, String.format(getString(R.string.language_changed_to_format), newLanguage), Toast.LENGTH_SHORT).show();
        }
        
        // Store current language for next session
        sharedPreferences.edit().putString("last_language", newLanguage).apply();
        currentLanguage = newLanguage;

        // No longer need to find labelProfile since we removed the user name display
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavBar2);
        FloatingActionButton addEmergencyContact = findViewById(R.id.senior_add_btn);

        addEmergencyContact.setOnClickListener(v -> {
            Intent intent = new Intent(Senior_Emergency_Contact.this, Senior_add_Emergency_Contact.class);
            startActivity(intent);
        });

        // Bottom nav logic
        bottomNavigationView.setSelectedItemId(R.id.senior_location);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.senior_home) {
                startActivity(new Intent(getApplicationContext(), Senior_Dashboard.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (itemId == R.id.senior_profile) {
                startActivity(new Intent(getApplicationContext(), Senior_Profile.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (itemId == R.id.senior_location) {
                return true;
            }
            return false;
        });

        // Load initial contacts
        fetchEmergencyContacts();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        
        Log.d("Senior_Emergency_Contact", "onNewIntent called");
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Load cached contacts immediately for instant display, then fetch latest from Firestore
        loadCachedEmergencyContacts();
        fetchEmergencyContacts();
        
        // Check for language change as backup method
        checkForLanguageChange();
        
        // Force refresh UI elements to ensure language consistency
        refreshAllUIElements();
        
        // Check if we need to show any dialogs that were open before recreation
        checkForPendingDialogs();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        // Handle language change without recreating activity
        Log.d("Senior_Emergency_Contact", "=== CONFIGURATION CHANGED ===");
        Log.d("Senior_Emergency_Contact", "Configuration changed - language change detected");
        Log.d("Senior_Emergency_Contact", "Active update dialog: " + (activeUpdateDialog != null ? "exists" : "null"));
        Log.d("Senior_Emergency_Contact", "Active delete dialog: " + (activeDeleteDialog != null ? "exists" : "null"));
        
        // Show a toast to confirm the method is being called
        Toast.makeText(this, getString(R.string.toast_language_change_detected), Toast.LENGTH_SHORT).show();
        
        // Refresh active update dialog if it exists
        if (activeUpdateDialog != null && activeUpdateDialog.isShowing()) {
            Log.d("Senior_Emergency_Contact", "Refreshing active update dialog with new language");
            try {
                activeUpdateDialog.dismiss();
                // Small delay to ensure dialog is fully dismissed
                new android.os.Handler().postDelayed(() -> {
                    if (activeUpdateContact != null) {
                        showUpdateDialog(activeUpdatePosition, activeUpdateContact);
                    }
                }, 200);
            } catch (Exception e) {
                Log.e("Senior_Emergency_Contact", "Error refreshing update dialog", e);
            }
        }
        
        // Refresh active delete dialog if it exists
        if (activeDeleteDialog != null && activeDeleteDialog.isShowing()) {
            Log.d("Senior_Emergency_Contact", "Refreshing active delete dialog with new language");
            try {
                activeDeleteDialog.dismiss();
                // Small delay to ensure dialog is fully dismissed
                new android.os.Handler().postDelayed(() -> {
                    if (activeDeleteContact != null) {
                        onDeleteContact(activeDeletePosition, activeDeleteContact);
                    }
                }, 200);
            } catch (Exception e) {
                Log.e("Senior_Emergency_Contact", "Error refreshing delete dialog", e);
            }
        }
        
        // Log the current language for debugging
        String currentLang = getResources().getConfiguration().locale.getLanguage();
        Log.d("Senior_Emergency_Contact", "Current language: " + currentLang);
        
        // Force refresh all UI elements
        refreshAllUIElements();
    }

    // User profile loading methods removed since we no longer display user name

    private void fetchEmergencyContacts() {
        String uid = mAuth.getCurrentUser().getUid();
        String userType = "seniors";

        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Clear the list before adding new contacts to prevent duplication
                        emergencyContacts.clear();

                        List<Map<String, Object>> contactList = (List<Map<String, Object>>) documentSnapshot.get("emergencyContacts");

                        if (contactList != null) {
                            for (Map<String, Object> contactMap : contactList) {
                                String name = contactMap.get("name").toString();
                                String number = contactMap.get("number").toString();
                                String address = contactMap.get("address") != null ? contactMap.get("address").toString() : "";
                                String relationship = contactMap.get("relationship") != null ? contactMap.get("relationship").toString() : "";

                                Emergency_Contacts contact = new Emergency_Contacts(name, number, address, relationship);
                                emergencyContacts.add(contact);
                            }
                        }
                        
                        // Cache the contacts for future instant loading
                        cacheEmergencyContacts(emergencyContacts);
                        
                        adapter.notifyDataSetChanged();
                        Log.d("Senior_Emergency_Contact", "Loaded " + emergencyContacts.size() + " emergency contacts from Firestore");
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, String.format(getString(R.string.failed_to_load_contacts_format), e.getMessage()), Toast.LENGTH_SHORT).show();
                    Log.e("Senior_Emergency_Contact", "Failed to load emergency contacts", e);
                });
    }

    @Override
    public void onDeleteContact(int position, Emergency_Contacts contact) {
        // Store references for language refresh
        activeDeletePosition = position;
        activeDeleteContact = contact;
        
        // Show confirmation dialog
        String deleteTitle = getString(R.string.delete_contact_title);
        String deleteMessage = String.format(getString(R.string.delete_contact_message), contact.getName());
        String deleteButton = getString(R.string.delete_button);
        String cancelButton = getString(R.string.cancel_dialog_button);
        
        Log.d("Senior_Emergency_Contact", "Delete dialog - Title: " + deleteTitle + ", Message: " + deleteMessage);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(deleteTitle)
                .setMessage(deleteMessage)
                .setPositiveButton(deleteButton, (dialog, which) -> {
                    deleteContactFromFirestore(position, contact);
                })
                .setNegativeButton(cancelButton, null);
        
        // Store dialog reference and show
        activeDeleteDialog = builder.create();
        activeDeleteDialog.show();
    }

    @Override
    public void onUpdateContact(int position, Emergency_Contacts contact) {
        showUpdateDialog(position, contact);
    }

    private void deleteContactFromFirestore(int position, Emergency_Contacts contactToDelete) {
        String uid = mAuth.getCurrentUser().getUid();
        String userType = "seniors";

        // Create updated contact list without the deleted contact
        List<Map<String, Object>> updatedContactList = new ArrayList<>();
        for (Emergency_Contacts contact : emergencyContacts) {
            if (!contact.getName().equals(contactToDelete.getName()) ||
                    !contact.getNumber().equals(contactToDelete.getNumber())) {
                Map<String, Object> contactMap = new HashMap<>();
                contactMap.put("name", contact.getName());
                contactMap.put("number", contact.getNumber());
                updatedContactList.add(contactMap);
            }
        }

        // Update Firestore
        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .update("emergencyContacts", updatedContactList)
                .addOnSuccessListener(aVoid -> {
                    // Remove from local list and update adapter
                    adapter.removeItem(position);
                    
                    // Update cache after successful deletion
                    cacheEmergencyContacts(emergencyContacts);
                    
                    Toast.makeText(this, getString(R.string.contact_deleted_successfully), Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, String.format(getString(R.string.failed_to_delete_contact_format), e.getMessage()), Toast.LENGTH_SHORT).show();
                });
    }

    private void showUpdateDialog(int position, Emergency_Contacts contact) {
        // Store references for language refresh
        activeUpdatePosition = position;
        activeUpdateContact = contact;
        
        Log.d("Senior_Emergency_Contact", "Creating update dialog for contact: " + contact.getName());
        
        // Create dialog layout programmatically
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_update_contact, null);

        EditText nameEditText = dialogView.findViewById(R.id.editTextName);
        EditText numberEditText = dialogView.findViewById(R.id.editTextNumber);
        EditText addressEditText = dialogView.findViewById(R.id.editTextAddress);
        Spinner relationshipSpinner = dialogView.findViewById(R.id.spinnerRelationship);
        
        // Setup relationship spinner
        setupRelationshipSpinnerForDialog(relationshipSpinner, contact.getRelationship());

        // Add input filter to restrict phone number input to digits only
        numberEditText.setFilters(new InputFilter[]{new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                for (int i = start; i < end; i++) {
                    if (!Character.isDigit(source.charAt(i))) {
                        return "";
                    }
                }
                return null;
            }
        }});

        nameEditText.setText(contact.getName());
        numberEditText.setText(contact.getNumber());
        addressEditText.setText(contact.getAddress());

        builder.setView(dialogView)
                .setTitle(getString(R.string.update_contact_title))
                .setPositiveButton(getString(R.string.update_dialog_button), (dialog, which) -> {
                    String newName = nameEditText.getText().toString().trim();
                    String newNumber = numberEditText.getText().toString().trim();
                    String newAddress = addressEditText.getText().toString().trim();
                    String newRelationship = relationshipSpinner.getSelectedItem().toString();

                    if (!newName.isEmpty() && !newNumber.isEmpty() && !newAddress.isEmpty() && !newRelationship.equals(getString(R.string.select_relationship))) {
                        if (!isValidPhoneNumber(newNumber)) {
                            Toast.makeText(this, getString(R.string.valid_mobile_error), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        // Check for duplicate phone numbers before updating
                        checkForDuplicateAndUpdate(position, contact, newName, newNumber, newAddress, newRelationship);
                    } else {
                        Toast.makeText(this, getString(R.string.fill_all_fields_error), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(getString(R.string.cancel_dialog_button), null);
        
        // Store dialog reference and show
        activeUpdateDialog = builder.create();
        activeUpdateDialog.show();
    }

    private void setupRelationshipSpinnerForDialog(Spinner spinner, String currentRelationship) {
        // Create adapter for the spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.relationship_options,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        // Set the current relationship as selected
        if (currentRelationship != null && !currentRelationship.isEmpty()) {
            for (int i = 0; i < adapter.getCount(); i++) {
                if (adapter.getItem(i).toString().equals(currentRelationship)) {
                    spinner.setSelection(i);
                    break;
                }
            }
        }
    }

    private void updateContactInFirestore(int position, Emergency_Contacts oldContact, String newName, String newNumber, String newAddress, String newRelationship) {
        String uid = mAuth.getCurrentUser().getUid();
        String userType = "seniors";

        // Create updated contact list
        List<Map<String, Object>> updatedContactList = new ArrayList<>();
        for (int i = 0; i < emergencyContacts.size(); i++) {
            Emergency_Contacts contact = emergencyContacts.get(i);
            Map<String, Object> contactMap = new HashMap<>();

            if (i == position) {
                contactMap.put("name", newName);
                contactMap.put("number", newNumber);
                contactMap.put("address", newAddress);
                contactMap.put("relationship", newRelationship);
            } else {
                contactMap.put("name", contact.getName());
                contactMap.put("number", contact.getNumber());
                contactMap.put("address", contact.getAddress());
                contactMap.put("relationship", contact.getRelationship());
            }
            updatedContactList.add(contactMap);
        }

        // Update Firestore
        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .update("emergencyContacts", updatedContactList)
                .addOnSuccessListener(aVoid -> {
                    // Update local contact object
                    Emergency_Contacts updatedContact = new Emergency_Contacts(newName, newNumber, newAddress, newRelationship);
                    adapter.updateItem(position, updatedContact);
                    
                    // Update cache after successful update
                    cacheEmergencyContacts(emergencyContacts);
                    
                    Toast.makeText(this, getString(R.string.contact_updated_successfully), Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, String.format(getString(R.string.failed_to_update_contact_format), e.getMessage()), Toast.LENGTH_SHORT).show();
                });
    }

    private void checkForDuplicateAndUpdate(int position, Emergency_Contacts oldContact, String newName, String newNumber, String newAddress, String newRelationship) {
        String uid = mAuth.getCurrentUser().getUid();
        String userType = "seniors";

        // Check if the new number is different from the old number
        if (oldContact.getNumber().equals(newNumber)) {
            // Same number, just update the name, address and relationship
            updateContactInFirestore(position, oldContact, newName, newNumber, newAddress, newRelationship);
            return;
        }

        // Check for duplicate phone numbers in existing contacts
        boolean isDuplicate = false;
        for (int i = 0; i < emergencyContacts.size(); i++) {
            if (i != position) { // Skip the current contact being updated
                Emergency_Contacts existingContact = emergencyContacts.get(i);
                if (existingContact.getNumber().equals(newNumber)) {
                    isDuplicate = true;
                    break;
                }
            }
        }

        if (isDuplicate) {
            Toast.makeText(this, getString(R.string.phone_number_already_exists), Toast.LENGTH_SHORT).show();
        } else {
            updateContactInFirestore(position, oldContact, newName, newNumber, newAddress, newRelationship);
        }
    }

    private boolean isValidPhoneNumber(String number) {
        return !number.isEmpty() && number.matches("09\\d{9}");
    }

    private void loadCachedEmergencyContacts() {
        // Ensure SharedPreferences is initialized
        if (sharedPreferences == null) {
            sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        }
        
        String cachedContactsJson = sharedPreferences.getString(KEY_CACHED_EMERGENCY_CONTACTS, null);
        if (cachedContactsJson != null && !cachedContactsJson.isEmpty()) {
            try {
                emergencyContacts.clear();
                JSONArray jsonArray = new JSONArray(cachedContactsJson);
                
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject contactJson = jsonArray.getJSONObject(i);
                    String name = contactJson.getString("name");
                    String number = contactJson.getString("number");
                    String address = contactJson.optString("address", "");
                    String relationship = contactJson.optString("relationship", "");
                    
                    Emergency_Contacts contact = new Emergency_Contacts(name, number, address, relationship);
                    emergencyContacts.add(contact);
                }
                
                adapter.notifyDataSetChanged();
                Log.d("Senior_Emergency_Contact", "Loaded " + emergencyContacts.size() + " cached emergency contacts");
            } catch (Exception e) {
                Log.e("Senior_Emergency_Contact", "Error loading cached emergency contacts", e);
            }
        } else {
            Log.d("Senior_Emergency_Contact", "No cached emergency contacts found");
        }
    }

    private void cacheEmergencyContacts(List<Emergency_Contacts> contacts) {
        try {
            JSONArray jsonArray = new JSONArray();
            
            for (Emergency_Contacts contact : contacts) {
                JSONObject contactJson = new JSONObject();
                contactJson.put("name", contact.getName());
                contactJson.put("number", contact.getNumber());
                contactJson.put("address", contact.getAddress());
                contactJson.put("relationship", contact.getRelationship());
                jsonArray.put(contactJson);
            }
            
            String contactsJson = jsonArray.toString();
            sharedPreferences.edit()
                    .putString(KEY_CACHED_EMERGENCY_CONTACTS, contactsJson)
                    .apply();
            
            Log.d("Senior_Emergency_Contact", "Cached " + contacts.size() + " emergency contacts");
        } catch (Exception e) {
            Log.e("Senior_Emergency_Contact", "Error caching emergency contacts", e);
        }
    }

    private void refreshAllUIElements() {
        Log.d("Senior_Emergency_Contact", "Refreshing all UI elements for language change");
        
        // Refresh the RecyclerView to pick up new string resources without breaking data reference
        if (adapter != null && recyclerView != null) {
            adapter.setOnContactActionListener(this);
            adapter.notifyDataSetChanged();
            recyclerView.setAdapter(adapter);
            Log.d("Senior_Emergency_Contact", "Adapter refreshed with new language");
        }
        
        // Clear dialog references to prevent stale dialogs
        activeUpdateDialog = null;
        activeDeleteDialog = null;
        
        // Force refresh the relationship spinner if it exists
        refreshRelationshipSpinner();
        
        Log.d("Senior_Emergency_Contact", "UI elements refreshed");
    }

    private void refreshRelationshipSpinner() {
        // This method will be called to refresh any relationship spinners
        // The main spinner is refreshed in setupRelationshipSpinner()
        Log.d("Senior_Emergency_Contact", "Refreshing relationship spinner with new language");
    }

    private void checkForPendingDialogs() {
        // Check if there were any dialogs that should be shown after activity recreation
        // This is a placeholder for future implementation if needed
        Log.d("Senior_Emergency_Contact", "Checking for pending dialogs after activity recreation");
    }

    private void checkForLanguageChange() {
        String newLanguage = getResources().getConfiguration().locale.getLanguage();
        
        if (currentLanguage == null) {
            // First time, just store the current language
            currentLanguage = newLanguage;
            Log.d("Senior_Emergency_Contact", "Initial language set to: " + currentLanguage);
        } else if (!currentLanguage.equals(newLanguage)) {
            // Language has changed
            Log.d("Senior_Emergency_Contact", "Language changed from " + currentLanguage + " to " + newLanguage);
            currentLanguage = newLanguage;
            
            // Show toast to confirm language change detection
            Toast.makeText(this, String.format(getString(R.string.language_changed_to_format), newLanguage), Toast.LENGTH_SHORT).show();
            
            // Refresh all UI elements
            refreshAllUIElements();
            
            // Refresh active dialogs if they exist
            refreshActiveDialogs();
        }
    }

    private void refreshActiveDialogs() {
        Log.d("Senior_Emergency_Contact", "Refreshing active dialogs due to language change");
        
        // Refresh active update dialog if it exists
        if (activeUpdateDialog != null && activeUpdateDialog.isShowing()) {
            Log.d("Senior_Emergency_Contact", "Refreshing active update dialog with new language");
            try {
                activeUpdateDialog.dismiss();
                new android.os.Handler().postDelayed(() -> {
                    if (activeUpdateContact != null) {
                        showUpdateDialog(activeUpdatePosition, activeUpdateContact);
                    }
                }, 300);
            } catch (Exception e) {
                Log.e("Senior_Emergency_Contact", "Error refreshing update dialog", e);
            }
        }
        
        // Refresh active delete dialog if it exists
        if (activeDeleteDialog != null && activeDeleteDialog.isShowing()) {
            Log.d("Senior_Emergency_Contact", "Refreshing active delete dialog with new language");
            try {
                activeDeleteDialog.dismiss();
                new android.os.Handler().postDelayed(() -> {
                    if (activeDeleteContact != null) {
                        onDeleteContact(activeDeletePosition, activeDeleteContact);
                    }
                }, 300);
            } catch (Exception e) {
                Log.e("Senior_Emergency_Contact", "Error refreshing delete dialog", e);
            }
        }
    }
}