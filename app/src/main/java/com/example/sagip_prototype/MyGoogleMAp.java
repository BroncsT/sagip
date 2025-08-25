package com.example.sagip_prototype;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

/**
 * MyGoogleMAp - Placeholder Activity
 * 
 * This is the old Google Maps implementation that has been replaced by MyOpenStreetMap.
 * This activity now serves as a redirect to the new OpenStreetMap implementation.
 */
public class MyGoogleMAp extends AppCompatActivity {

    private static final String TAG = "MyGoogleMAp";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        
        // Get the intent data that was passed to this activity
        Intent intent = getIntent();
        
        // Create a new intent for MyOpenStreetMap with the same data
        Intent openStreetMapIntent = new Intent(this, MyOpenStreetMap.class);
        
        // Copy all the extras from the original intent
        if (intent != null) {
            openStreetMapIntent.putExtra("latitude", intent.getDoubleExtra("latitude", 0.0));
            openStreetMapIntent.putExtra("longitude", intent.getDoubleExtra("longitude", 0.0));
            openStreetMapIntent.putExtra("locationAddress", intent.getStringExtra("locationAddress"));
            openStreetMapIntent.putExtra("isEmergency", intent.getBooleanExtra("isEmergency", false));
            openStreetMapIntent.putExtra("isRescuerMode", intent.getBooleanExtra("isRescuerMode", false));
            openStreetMapIntent.putExtra("seniorName", intent.getStringExtra("seniorName"));
            openStreetMapIntent.putExtra("seniorPhone", intent.getStringExtra("seniorPhone"));
            openStreetMapIntent.putExtra("helpRequestId", intent.getStringExtra("helpRequestId"));
            openStreetMapIntent.putExtra("emergencyDescription", intent.getStringExtra("emergencyDescription"));
            openStreetMapIntent.putExtra("isSeniorTrackingMode", intent.getBooleanExtra("isSeniorTrackingMode", false));
            openStreetMapIntent.putExtra("helpRequestIdForTracking", intent.getStringExtra("helpRequestIdForTracking"));
        }
        
        // Show a brief message to the user
        Toast.makeText(this, getString(R.string.redirecting_to_openstreetmap), Toast.LENGTH_SHORT).show();
        
        // Start the OpenStreetMap activity
        startActivity(openStreetMapIntent);
        
        // Close this activity
        finish();
    }
}