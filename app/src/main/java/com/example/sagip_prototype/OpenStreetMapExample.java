package com.example.sagip_prototype;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class OpenStreetMapExample extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_openstreet_map_example);

        Button btnRescuerMode = findViewById(R.id.btnRescuerMode);
        Button btnSeniorMode = findViewById(R.id.btnSeniorMode);
        Button btnEmergencyMode = findViewById(R.id.btnEmergencyMode);

        btnRescuerMode.setOnClickListener(v -> {
            // Launch OpenStreetMap in rescuer mode
            Intent intent = new Intent(this, MyOpenStreetMap.class);
            intent.putExtra("isRescuerMode", true);
            intent.putExtra("latitude", 14.5995); // Example coordinates (Manila)
            intent.putExtra("longitude", 120.9842);
            intent.putExtra("locationAddress", "Example Emergency Location, Manila");
            intent.putExtra("seniorName", "John Doe");
            intent.putExtra("seniorPhone", "+639123456789");
            intent.putExtra("emergencyDescription", "Senior needs immediate assistance");
            startActivity(intent);
        });

        btnSeniorMode.setOnClickListener(v -> {
            // Launch OpenStreetMap in senior tracking mode
            Intent intent = new Intent(this, MyOpenStreetMap.class);
            intent.putExtra("isSeniorTrackingMode", true);
            intent.putExtra("latitude", 14.5995);
            intent.putExtra("longitude", 120.9842);
            intent.putExtra("locationAddress", "Your Emergency Location, Manila");
            intent.putExtra("seniorName", "Your Name");
            intent.putExtra("helpRequestIdForTracking", "example_help_request_id");
            startActivity(intent);
        });

        btnEmergencyMode.setOnClickListener(v -> {
            // Launch OpenStreetMap in emergency mode
            Intent intent = new Intent(this, MyOpenStreetMap.class);
            intent.putExtra("isEmergency", true);
            intent.putExtra("latitude", 14.5995);
            intent.putExtra("longitude", 120.9842);
            intent.putExtra("locationAddress", "Emergency Location, Manila");
            startActivity(intent);
        });
    }
}
