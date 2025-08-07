package com.example.sagip_prototype;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
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

public class Senior_Emergency_Contact extends AppCompatActivity implements EmergencyContactAdapter.OnContactActionListener {

    FirebaseFirestore db;
    FirebaseAuth mAuth;

    RecyclerView recyclerView;
    EmergencyContactAdapter adapter;
    List<Emergency_Contacts> emergencyContacts;
    TextView labelProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_senior_emergency_contact);

        recyclerView = findViewById(R.id.emergencyRecycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        emergencyContacts = new ArrayList<>();
        adapter = new EmergencyContactAdapter(emergencyContacts, this);
        adapter.setOnContactActionListener(this); // Set the listener
        recyclerView.setAdapter(adapter);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        labelProfile = findViewById(R.id.labelProfile);
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

        // Load user profile and initial contacts
        loadUserProfile();
        fetchEmergencyContacts();
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchEmergencyContacts();
    }

    private void loadUserProfile() {
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

                        String fullName = firstName + " " + middleName + " " + lastName + "\n\nEmergency contact list";
                        labelProfile.setText(fullName);
                    }
                });
    }

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
                        emergencyContacts.clear();

                        List<Map<String, Object>> contactList = (List<Map<String, Object>>) documentSnapshot.get("emergencyContacts");

                        if (contactList != null) {
                            for (Map<String, Object> contactMap : contactList) {
                                String name = contactMap.get("name").toString();
                                String number = contactMap.get("number").toString();

                                Emergency_Contacts contact = new Emergency_Contacts(name, number);
                                emergencyContacts.add(contact);
                            }
                        }
                        adapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load contacts: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onDeleteContact(int position, Emergency_Contacts contact) {
        // Show confirmation dialog
        new AlertDialog.Builder(this)
                .setTitle("Delete Contact")
                .setMessage("Are you sure you want to delete " + contact.getName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteContactFromFirestore(position, contact);
                })
                .setNegativeButton("Cancel", null)
                .show();
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
                    Toast.makeText(this, "Contact deleted successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to delete contact: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void showUpdateDialog(int position, Emergency_Contacts contact) {
        // Create dialog layout programmatically
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_update_contact, null);

        EditText nameEditText = dialogView.findViewById(R.id.editTextName);
        EditText numberEditText = dialogView.findViewById(R.id.editTextNumber);

        // Pre-fill with current values
        nameEditText.setText(contact.getName());
        numberEditText.setText(contact.getNumber());

        builder.setView(dialogView)
                .setTitle("Update Contact")
                .setPositiveButton("Update", (dialog, which) -> {
                    String newName = nameEditText.getText().toString().trim();
                    String newNumber = numberEditText.getText().toString().trim();

                    if (!newName.isEmpty() && !newNumber.isEmpty()) {
                        updateContactInFirestore(position, contact, newName, newNumber);
                    } else {
                        Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateContactInFirestore(int position, Emergency_Contacts oldContact, String newName, String newNumber) {
        String uid = mAuth.getCurrentUser().getUid();
        String userType = "seniors";

        // Create updated contact list
        List<Map<String, Object>> updatedContactList = new ArrayList<>();
        for (int i = 0; i < emergencyContacts.size(); i++) {
            Emergency_Contacts contact = emergencyContacts.get(i);
            Map<String, Object> contactMap = new HashMap<>();

            if (i == position) {
                // Update this contact
                contactMap.put("name", newName);
                contactMap.put("number", newNumber);
            } else {
                // Keep existing contact data
                contactMap.put("name", contact.getName());
                contactMap.put("number", contact.getNumber());
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
                    Emergency_Contacts updatedContact = new Emergency_Contacts(newName, newNumber);
                    adapter.updateItem(position, updatedContact);
                    Toast.makeText(this, "Contact updated successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update contact: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}