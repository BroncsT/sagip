package com.example.sagip_prototype;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class Senior_Emergency_Contact extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_senior_emergency_contact);


        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavBar);


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

    }
}