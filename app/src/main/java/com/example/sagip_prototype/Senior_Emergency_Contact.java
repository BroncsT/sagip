package com.example.sagip_prototype;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Senior_Emergency_Contact extends AppCompatActivity {

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
        recyclerView.setAdapter(adapter);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        labelProfile = findViewById(R.id.labelProfile);
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavBar);
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
}