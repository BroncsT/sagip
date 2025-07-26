package com.example.sagip_prototype;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Senior_Dashboard extends AppCompatActivity {

    private static final String TAG = "SeniorDashboard";
    FirebaseAuth mAuth;
    FirebaseFirestore db;

    TextView tvFullName, tvCurrentLocation;
    Button btnFindHospital, btnHelp;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private boolean locationUpdatesActive = false;
    private double currentLat = 0.0;
    private double currentLong = 0.0;
    private String currentLocationAddress = "";

    private ActivityResultLauncher<String[]> locationPermissionRequest;
    private ActivityResultLauncher<String> smsPermissionRequest;

    // SMS tracking variables
    private BroadcastReceiver smsSentReceiver;
    private BroadcastReceiver smsDeliveredReceiver;
    private boolean receiversRegistered = false;
    private int totalSMSToSend = 0;
    private int smsSentCount = 0;
    private int smsFailedCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_senior_dashboard);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (mAuth.getCurrentUser() == null) {
            startActivity(new Intent(Senior_Dashboard.this, MainActivity.class));
            finish();
            return;
        }

        initializeViews();
        initializeLocationServices();
        registerLocationPermissionLauncher();
        registerSMSPermissionLauncher();
        initializeSMSReceivers();
        loadUserData();
        setupBottomNavigation();
        requestLocationPermissions();
        requestSMSPermission();
    }

    private void initializeViews() {
        tvFullName = findViewById(R.id.seniorName);
        tvCurrentLocation = findViewById(R.id.tvCurrentLocation);
        btnFindHospital = findViewById(R.id.findhospital);
        btnHelp = findViewById(R.id.sosButton);

        btnFindHospital.setOnClickListener(v -> navigateToNearestHospital());
        btnHelp.setOnClickListener(v -> sendEmergencySMS());
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavBar);
        bottomNavigationView.setSelectedItemId(R.id.senior_home);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.senior_home) {
                return true;
            } else if (itemId == R.id.senior_profile) {
                startActivity(new Intent(getApplicationContext(), Senior_Profile.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (itemId == R.id.senior_location) {
                startActivity(new Intent(getApplicationContext(), Senior_Emergency_Contact.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            return false;
        });
    }

    // Initialize SMS broadcast receivers
    private void initializeSMSReceivers() {
        smsSentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String result = "";
                switch (getResultCode()) {
                    case Activity.RESULT_OK:
                        result = "SMS sent successfully";
                        smsSentCount++;
                        Log.d(TAG, result + " (" + smsSentCount + "/" + totalSMSToSend + ")");
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                        result = "Generic failure - Check network/carrier settings";
                        smsFailedCount++;
                        Log.e(TAG, result);
                        break;
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                        result = "No service - Check cellular signal";
                        smsFailedCount++;
                        Log.e(TAG, result);
                        break;
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                        result = "Null PDU - Message format error";
                        smsFailedCount++;
                        Log.e(TAG, result);
                        break;
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                        result = "Radio off - Check airplane mode";
                        smsFailedCount++;
                        Log.e(TAG, result);
                        break;
                    default:
                        result = "Unknown error: " + getResultCode();
                        smsFailedCount++;
                        Log.e(TAG, result);
                        break;
                }

                // Show status update
                if (smsSentCount + smsFailedCount == totalSMSToSend) {
                    String finalResult = "SMS Results: " + smsSentCount + " sent, " + smsFailedCount + " failed";
                    Toast.makeText(context, finalResult, Toast.LENGTH_LONG).show();
                    Log.d(TAG, "Final SMS results: " + finalResult);
                }
            }
        };

        smsDeliveredReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (getResultCode()) {
                    case Activity.RESULT_OK:
                        Log.d(TAG, "SMS delivered");
                        break;
                    case Activity.RESULT_CANCELED:
                        Log.w(TAG, "SMS not delivered");
                        break;
                }
            }
        };
    }

    private void registerSMSReceivers() {
        if (!receiversRegistered) {
            registerReceiver(smsSentReceiver, new IntentFilter("SMS_SENT"));
            registerReceiver(smsDeliveredReceiver, new IntentFilter("SMS_DELIVERED"));
            receiversRegistered = true;
            Log.d(TAG, "SMS receivers registered");
        }
    }

    private void unregisterSMSReceivers() {
        if (receiversRegistered) {
            try {
                unregisterReceiver(smsSentReceiver);
                unregisterReceiver(smsDeliveredReceiver);
                receiversRegistered = false;
                Log.d(TAG, "SMS receivers unregistered");
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering SMS receivers", e);
            }
        }
    }

    private void registerSMSPermissionLauncher() {
        smsPermissionRequest = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), isGranted -> {
                    if (isGranted) {
                        Log.d(TAG, "SMS permission granted");
                        Toast.makeText(this, "SMS permission granted. You can now send emergency messages.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "SMS permission denied. Emergency messages cannot be sent.", Toast.LENGTH_LONG).show();
                        Log.w(TAG, "SMS permission denied");
                    }
                }
        );
    }

    private void requestSMSPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            smsPermissionRequest.launch(Manifest.permission.SEND_SMS);
        }
    }

    // Check if device can send SMS
    private boolean canSendSMS() {
        // Check if device has telephony
        PackageManager pm = getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            Log.e(TAG, "Device does not support telephony");
            return false;
        }

        // Check if SMS permission is granted
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "SEND_SMS permission not granted");
            return false;
        }

        // Check telephony manager
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (tm == null || tm.getPhoneType() == TelephonyManager.PHONE_TYPE_NONE) {
            Log.e(TAG, "No telephony service available");
            return false;
        }

        // Check SIM state
        int simState = tm.getSimState();
        if (simState != TelephonyManager.SIM_STATE_READY) {
            Log.e(TAG, "SIM not ready. State: " + getSimStateString(simState));
            return false;
        }

        Log.d(TAG, "SMS capability check passed");
        return true;
    }

    private String getSimStateString(int simState) {
        switch (simState) {
            case TelephonyManager.SIM_STATE_ABSENT: return "ABSENT";
            case TelephonyManager.SIM_STATE_CARD_IO_ERROR: return "CARD_IO_ERROR";
            case TelephonyManager.SIM_STATE_CARD_RESTRICTED: return "CARD_RESTRICTED";
            case TelephonyManager.SIM_STATE_NETWORK_LOCKED: return "NETWORK_LOCKED";
            case TelephonyManager.SIM_STATE_NOT_READY: return "NOT_READY";
            case TelephonyManager.SIM_STATE_PERM_DISABLED: return "PERM_DISABLED";
            case TelephonyManager.SIM_STATE_PIN_REQUIRED: return "PIN_REQUIRED";
            case TelephonyManager.SIM_STATE_PUK_REQUIRED: return "PUK_REQUIRED";
            case TelephonyManager.SIM_STATE_READY: return "READY";
            case TelephonyManager.SIM_STATE_UNKNOWN: return "UNKNOWN";
            default: return "UNDEFINED: " + simState;
        }
    }

    private void sendEmergencySMS() {
        Log.d(TAG, "Emergency button pressed");

        // First check if we can send SMS
        if (!canSendSMS()) {
            Toast.makeText(this, "Cannot send SMS. Please check permissions and network.", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, "Sending emergency SMS...", Toast.LENGTH_SHORT).show();

        // Get current user info first
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
                        String lastName = documentSnapshot.getString("lastName");
                        String seniorName = (firstName != null && lastName != null) ?
                                firstName + " " + lastName : "Senior User";

                        fetchEmergencyContactsAndSendSMS(seniorName);
                    } else {
                        fetchEmergencyContactsAndSendSMS("Senior User");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting user info", e);
                    fetchEmergencyContactsAndSendSMS("Senior User");
                });
    }

    private void fetchEmergencyContactsAndSendSMS(String seniorName) {
        String uid = mAuth.getCurrentUser().getUid();
        String userType = "seniors";

        Log.d(TAG, "Fetching emergency contacts for user: " + uid);

        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        List<String> phoneNumbers = new ArrayList<>();

                        List<HashMap<String, Object>> emergencyContacts =
                                (List<HashMap<String, Object>>) documentSnapshot.get("emergencyContacts");

                        Log.d(TAG, "Emergency contacts from database: " + emergencyContacts);

                        if (emergencyContacts != null && !emergencyContacts.isEmpty()) {
                            for (HashMap<String, Object> contact : emergencyContacts) {
                                String phoneNumber = (String) contact.get("number");
                                String contactName = (String) contact.get("name");
                                Log.d(TAG, "Processing contact: " + contactName + " - " + phoneNumber);

                                if (phoneNumber != null && !phoneNumber.trim().isEmpty()) {
                                    phoneNumbers.add(phoneNumber.trim());
                                }
                            }
                        }

                        Log.d(TAG, "Phone numbers to send SMS: " + phoneNumbers);

                        if (phoneNumbers.isEmpty()) {
                            Toast.makeText(this, "No emergency contacts with phone numbers found", Toast.LENGTH_LONG).show();
                            return;
                        }

                        // Create emergency message
                        String emergencyMessage = createEmergencyMessage(seniorName);

                        // Register SMS receivers before sending
                        registerSMSReceivers();

                        // Initialize counters
                        totalSMSToSend = phoneNumbers.size();
                        smsSentCount = 0;
                        smsFailedCount = 0;

                        // Send SMS to all contacts
                        sendSMSToContacts(phoneNumbers, emergencyMessage);

                    } else {
                        Toast.makeText(this, "User document not found", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "User document does not exist");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching emergency contacts", e);
                    Toast.makeText(this, "Failed to fetch emergency contacts: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private String createEmergencyMessage(String seniorName) {
        StringBuilder message = new StringBuilder();
        message.append("🚨 EMERGENCY ALERT 🚨\n\n");
        message.append(seniorName).append(" needs immediate help!\n\n");

        if (!currentLocationAddress.isEmpty()) {
            message.append("📍 Location: ").append(currentLocationAddress).append("\n");
        }

        message.append("⏰ Time: ").append(java.text.DateFormat.getDateTimeInstance().format(new java.util.Date())).append("\n\n");
        message.append("Please check on them immediately or contact emergency services if needed.");

        String finalMessage = message.toString();
        Log.d(TAG, "Emergency message created (length: " + finalMessage.length() + "): " + finalMessage);

        return finalMessage;
    }

    private void sendSMSToContacts(List<String> phoneNumbers, String message) {
        SmsManager smsManager = SmsManager.getDefault();

        for (int i = 0; i < phoneNumbers.size(); i++) {
            String phoneNumber = phoneNumbers.get(i);

            try {
                // Clean phone number (remove spaces, dashes, etc.)
                String cleanPhoneNumber = phoneNumber.replaceAll("[^+\\d]", "");

                Log.d(TAG, "Sending SMS to: " + cleanPhoneNumber + " (original: " + phoneNumber + ")");

                // Create pending intents with unique request codes
                PendingIntent sentPI = PendingIntent.getBroadcast(
                        this, i,
                        new Intent("SMS_SENT"),
                        PendingIntent.FLAG_IMMUTABLE
                );

                PendingIntent deliveredPI = PendingIntent.getBroadcast(
                        this, i + 1000,
                        new Intent("SMS_DELIVERED"),
                        PendingIntent.FLAG_IMMUTABLE
                );

                // Check if message needs to be split
                ArrayList<String> messageParts = smsManager.divideMessage(message);

                if (messageParts.size() == 1) {
                    smsManager.sendTextMessage(cleanPhoneNumber, null, message, sentPI, deliveredPI);
                    Log.d(TAG, "Single SMS sent to: " + cleanPhoneNumber);
                } else {
                    ArrayList<PendingIntent> sentPIs = new ArrayList<>();
                    ArrayList<PendingIntent> deliveredPIs = new ArrayList<>();

                    for (int j = 0; j < messageParts.size(); j++) {
                        sentPIs.add(sentPI);
                        deliveredPIs.add(deliveredPI);
                    }

                    smsManager.sendMultipartTextMessage(cleanPhoneNumber, null, messageParts, sentPIs, deliveredPIs);
                    Log.d(TAG, "Multipart SMS (" + messageParts.size() + " parts) sent to: " + cleanPhoneNumber);
                }

            } catch (Exception e) {
                Log.e(TAG, "Failed to send SMS to " + phoneNumber, e);
                smsFailedCount++;
            }
        }

        // Log the emergency event
        logEmergencyEvent(phoneNumbers.size());
    }

    private void logEmergencyEvent(int contactCount) {
        String uid = mAuth.getCurrentUser().getUid();

        Map<String, Object> emergencyLog = new HashMap<>();
        emergencyLog.put("timestamp", System.currentTimeMillis());
        emergencyLog.put("latitude", currentLat);
        emergencyLog.put("longitude", currentLong);
        emergencyLog.put("location", currentLocationAddress);
        emergencyLog.put("contactsNotified", contactCount);
        emergencyLog.put("type", "help_button_pressed");

        db.collection("Sagip")
                .document("users")
                .collection("seniors")
                .document(uid)
                .collection("emergencyLogs")
                .add(emergencyLog)
                .addOnSuccessListener(documentReference ->
                        Log.d(TAG, "Emergency event logged: " + documentReference.getId()))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Error logging emergency event", e));
    }

    private void navigateToNearestHospital() {
        if (currentLat == 0.0 && currentLong == 0.0) {
            Toast.makeText(this, "Current location not available. Please wait or check permissions.", Toast.LENGTH_SHORT).show();
            return;
        }

        String source = currentLat + "," + currentLong;
        String destination = "hospital";

        Uri uri = Uri.parse("https://www.google.com/maps/dir/" + source + "/" + destination);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setPackage("com.google.android.apps.maps");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        }
    }

    // ... [Location and other methods remain the same as in your original code] ...
    // I'll include just the essential parts for brevity

    private void registerLocationPermissionLauncher() {
        locationPermissionRequest = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    Boolean fineLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                    Boolean coarseLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);

                    if (fineLocationGranted != null && fineLocationGranted) {
                        startLocationUpdates();
                    } else if (coarseLocationGranted != null && coarseLocationGranted) {
                        startLocationUpdates();
                    } else {
                        Toast.makeText(this, "Location permission needed for emergency location sharing", Toast.LENGTH_SHORT).show();
                        tvCurrentLocation.setText("Location permission denied");
                    }
                }
        );
    }

    private void initializeLocationServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    currentLat = location.getLatitude();
                    currentLong = location.getLongitude();
                    updateLocationUI(location);
                    saveLocationToDatabase(location);
                }
            }
        };
    }

    private void requestLocationPermissions() {
        locationPermissionRequest.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(10000)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMinUpdateIntervalMillis(5000)
                .build();

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        locationUpdatesActive = true;
        tvCurrentLocation.setText("Fetching current location...");
    }

    private void stopLocationUpdates() {
        if (locationUpdatesActive) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            locationUpdatesActive = false;
        }
    }

    private void updateLocationUI(Location location) {
        if (location != null) {
            getAddressFromLocation(location);
        }
    }

    private void getAddressFromLocation(Location location) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());

        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);

            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                StringBuilder addressText = new StringBuilder();

                if (address.getThoroughfare() != null) {
                    addressText.append(address.getThoroughfare());
                    if (address.getSubThoroughfare() != null) {
                        addressText.append(" ").append(address.getSubThoroughfare());
                    }
                    addressText.append(", ");
                }

                if (address.getLocality() != null) {
                    addressText.append(address.getLocality()).append(", ");
                }

                if (address.getAdminArea() != null) {
                    addressText.append(address.getAdminArea());
                }

                currentLocationAddress = addressText.toString();
                tvCurrentLocation.setText(currentLocationAddress);
                Log.d(TAG, "Current location: " + currentLocationAddress);
            } else {
                currentLocationAddress = "Location found but address unknown";
                tvCurrentLocation.setText(currentLocationAddress);
            }
        } catch (IOException e) {
            currentLocationAddress = "Unable to get address from location";
            tvCurrentLocation.setText(currentLocationAddress);
            Log.e(TAG, "Error getting address from location", e);
        }
    }

    private void saveLocationToDatabase(Location location) {
        if (mAuth.getCurrentUser() == null) return;

        String uid = mAuth.getCurrentUser().getUid();
        String userType = "seniors";

        Map<String, Object> locationData = new HashMap<>();
        locationData.put("latitude", location.getLatitude());
        locationData.put("longitude", location.getLongitude());
        locationData.put("accuracy", location.getAccuracy());
        locationData.put("timestamp", System.currentTimeMillis());

        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                locationData.put("currentLocation", addresses.get(0).getAddressLine(0));
            }
        } catch (IOException e) {
            Log.e(TAG, "Error getting address for database", e);
        }

        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .update(locationData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Location saved to database"))
                .addOnFailureListener(e -> Log.e(TAG, "Error saving location to database", e));
    }

    private void loadUserData() {
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
                        String currentLocation = documentSnapshot.getString("currentLocation");

                        if (documentSnapshot.getDouble("latitude") != null && documentSnapshot.getDouble("longitude") != null) {
                            currentLat = documentSnapshot.getDouble("latitude");
                            currentLong = documentSnapshot.getDouble("longitude");
                        }

                        if (firstName != null && middleName != null && lastName != null) {
                            String fullName = firstName + " " + middleName + " " + lastName;
                            tvFullName.setText(fullName);
                        } else {
                            tvFullName.setText("Full Name Not Available");
                        }

                        if (currentLocation != null && !currentLocation.isEmpty()) {
                            currentLocationAddress = currentLocation;
                            tvCurrentLocation.setText(currentLocation);
                        } else {
                            tvCurrentLocation.setText("Waiting for location update...");
                        }
                    } else {
                        tvFullName.setText("User data not found.");
                        Log.d(TAG, "Document doesn't exist");
                    }
                })
                .addOnFailureListener(e -> {
                    tvFullName.setText("Failed to load data.");
                    Log.e(TAG, "Error fetching user data", e);
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!locationUpdatesActive) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterSMSReceivers();
    }
}