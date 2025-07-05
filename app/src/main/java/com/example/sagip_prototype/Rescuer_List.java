package com.example.sagip_prototype;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class Rescuer_List extends AppCompatActivity {

    RecyclerView recyclerView;
    HospitalAdapter hospitalAdapter;
    List<HospitalLIst> hospitalList;

    FirebaseFirestore  db;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_rescuer_list);
        setupBottomNavigation();
        SetupRecyclerView();
    }

    private void SetupRecyclerView() {

        recyclerView = findViewById(R.id.recyclerViewHospitals);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        hospitalList = new ArrayList<>();
        hospitalAdapter = new HospitalAdapter(hospitalList, this);
        recyclerView.setAdapter(hospitalAdapter);

        db = FirebaseFirestore.getInstance();
        fetchHospitalData();

    }

    private void fetchHospitalData() {

        db.collection("Sagip")
                .document("users")
                .collection("hospital") // This should match the userType you use when saving hospital data
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot value, @Nullable FirebaseFirestoreException error) {
                        if (error != null) {
                            // Log error for debugging
                            System.out.println("Error fetching hospital data: " + error.getMessage());
                            return;
                        }

                        if (value != null) {
                            hospitalList.clear();
                            for (com.google.firebase.firestore.DocumentSnapshot document : value.getDocuments()) {
                                HospitalLIst hospital = document.toObject(HospitalLIst.class);
                                if (hospital != null) {
                                    hospitalList.add(hospital);
                                }
                            }
                            hospitalAdapter.notifyDataSetChanged();
                        }
                    }
                });
    }


    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavBar);
        bottomNavigationView.setSelectedItemId(R.id.rescuer_hospital);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.rescuer_hospital) {
                return true;
            } else if (itemId == R.id.rescuer_profile) {
                startActivity(new Intent(getApplicationContext(), Rescuer_Profile.class));
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
}