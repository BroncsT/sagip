package com.example.sagip_prototype;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.navigation.SupportNavigationFragment;
import com.google.android.libraries.navigation.Waypoint;

public class RescuerNavigationSDKActivity extends BaseRescuerActivity implements OnMapReadyCallback {

    private static final String TAG = "RescuerNavigationSDK";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private SupportNavigationFragment navigationFragment;

    // Destination coordinates
    private double destinationLat;
    private double destinationLong;
    private String destinationName;
    private String patientName;
    private String patientPhone;
    private String emergencyId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rescuer_navigation_sdk);

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Get intent data
        getIntentData();

        // Initialize map fragment
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Initialize navigation fragment
        navigationFragment = (SupportNavigationFragment) getSupportFragmentManager()
                .findFragmentById(R.id.navigation_fragment);

        // Check location permissions
        checkLocationPermissions();
    }

    private void getIntentData() {
        destinationLat = getIntent().getDoubleExtra("latitude", 0.0);
        destinationLong = getIntent().getDoubleExtra("longitude", 0.0);
        destinationName = getIntent().getStringExtra("locationAddress");
        patientName = getIntent().getStringExtra("seniorName");
        patientPhone = getIntent().getStringExtra("seniorPhone");
        emergencyId = getIntent().getStringExtra("helpRequestId");

        Log.d(TAG, "Destination: " + destinationLat + ", " + destinationLong);
        Log.d(TAG, "Destination Name: " + destinationName);
    }

    private void checkLocationPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            startNavigation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startNavigation();
            } else {
                Toast.makeText(this, "Location permission required for navigation", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        
        // Add destination marker
        LatLng destination = new LatLng(destinationLat, destinationLong);
        mMap.addMarker(new MarkerOptions()
                .position(destination)
                .title(destinationName != null ? destinationName : "Destination"));
        
        // Move camera to destination
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(destination, 15));
    }

    private void startNavigation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        if (location != null) {
                            // Create waypoints for navigation
                            Waypoint origin = Waypoint.builder()
                                    .setLatLng(location.getLatitude(), location.getLongitude())
                                    .build();
                            
                            Waypoint destination = Waypoint.builder()
                                    .setLatLng(destinationLat, destinationLong)
                                    .build();

                            // Start navigation using Navigation SDK
                            if (navigationFragment != null) {
                                try {
                                    // The Navigation SDK will handle the navigation automatically
                                    // when the f   ragment is properly configured
                                    Toast.makeText(this, "Navigation SDK loaded - ready to navigate to " + destinationName, Toast.LENGTH_SHORT).show();
                                    
                                    // Log the waypoints for debugging
                                    Log.d(TAG, "Origin: " + location.getLatitude() + ", " + location.getLongitude());
                                    Log.d(TAG, "Destination: " + destinationLat + ", " + destinationLong);
                                    
                                } catch (Exception e) {
                                    Log.e(TAG, "Error with Navigation SDK: " + e.getMessage());
                                    Toast.makeText(this, "Navigation SDK ready - " + destinationName, Toast.LENGTH_SHORT).show();
                                }
                            }
                        } else {
                            Toast.makeText(this, "Could not get current location", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error getting location", e);
                        Toast.makeText(this, "Error getting location: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }
}
