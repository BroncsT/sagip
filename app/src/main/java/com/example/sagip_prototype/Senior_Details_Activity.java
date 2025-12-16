package com.example.sagip_prototype;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.squareup.picasso.Picasso;

public class Senior_Details_Activity extends AppCompatActivity {

    private static final String TAG = "Senior_Details_Activity";
    
    private ImageView seniorImageView;
    private TextView nameText;
    private TextView ageText;
    private TextView barangayText;
    private TextView phoneText;
    private TextView addressText;
    private TextView statusText;
    private Button callButton;
    private Button backButton;
    
    private String seniorPhone;
    private String seniorName;
    private String seniorProfileImage;
    private String seniorSelfieImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        
        // Apply saved language preference
        String savedLanguage = LanguageSelectionActivity.getSavedLanguage(this);
        LanguageSelectionActivity.setAppLanguage(this, savedLanguage);
        
        setContentView(R.layout.activity_senior_details);

        // Initialize views
        initializeViews();
        
        // Load senior details from intent
        loadSeniorDetails();
        
        // Setup click listeners
        setupClickListeners();
    }

    private void initializeViews() {
        seniorImageView = findViewById(R.id.seniorImageView);
        nameText = findViewById(R.id.nameText);
        ageText = findViewById(R.id.ageText);
        barangayText = findViewById(R.id.barangayText);
        phoneText = findViewById(R.id.phoneText);
        addressText = findViewById(R.id.addressText);
        statusText = findViewById(R.id.statusText);
        callButton = findViewById(R.id.callButton);
        backButton = findViewById(R.id.backButton);
    }

    private void loadSeniorDetails() {
        Intent intent = getIntent();
        
        if (intent != null) {
            seniorName = intent.getStringExtra("senior_name");
            String seniorBarangay = intent.getStringExtra("senior_barangay");
            seniorPhone = intent.getStringExtra("senior_phone");
            String seniorAddress = intent.getStringExtra("senior_address");
            int seniorAge = intent.getIntExtra("senior_age", 0);
            String seniorStatus = intent.getStringExtra("senior_status");
            seniorProfileImage = intent.getStringExtra("senior_profile_image");
            seniorSelfieImage = intent.getStringExtra("senior_selfie_image");
            
            // Set the data to views
            if (seniorName != null) {
                nameText.setText(seniorName);
            }
            
            if (seniorAge > 0) {
                ageText.setText(seniorAge + " years old");
            } else {
                ageText.setText("Age: N/A");
            }
            
            if (seniorBarangay != null) {
                barangayText.setText(seniorBarangay);
            } else {
                barangayText.setText(getString(R.string.barangay_not_available));
            }
            
            // Format and display phone number
            if (seniorPhone != null && !seniorPhone.isEmpty()) {
                String formattedPhone = PhoneNumberUtils.formatPhoneNumber(seniorPhone);
                phoneText.setText(formattedPhone);
                callButton.setVisibility(PhoneNumberUtils.isValidPhoneNumber(seniorPhone) ? View.VISIBLE : View.GONE);
            } else {
                phoneText.setText("Phone: N/A");
                callButton.setVisibility(View.GONE);
            }
            
            if (seniorAddress != null && !seniorAddress.isEmpty()) {
                addressText.setText(seniorAddress);
            } else {
                addressText.setText("Address: N/A");
            }
            
            if (seniorStatus != null) {
                statusText.setText("Status: " + seniorStatus);
            } else {
                statusText.setText("Status: N/A");
            }
            
            // Load profile image if available
            loadProfileImage();
        }
    }

    private void loadProfileImage() {
        // Set default image immediately for instant display
        seniorImageView.setImageResource(R.drawable.ic_senior_person);
        
        // Prioritize selfie verification image over profile image
        String imageUrl = seniorSelfieImage;
        String imageSource = "selfie verification";
        
        // If no selfie verification image, fall back to profile image
        if (imageUrl == null || imageUrl.isEmpty()) {
            imageUrl = seniorProfileImage;
            imageSource = "profile";
        }
        
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Log.d(TAG, "Loading " + imageSource + " image: " + imageUrl);
            
            // Use Picasso to load the image with circular transformation
            try {
                Picasso.get()
                        .load(imageUrl)
                        .placeholder(R.drawable.ic_senior_person)
                        .error(R.drawable.ic_senior_person)
                        .resize(200, 200) // Resize to ensure consistent size
                        .centerCrop() // Center crop before applying circle transform
                        .transform(new CircleTransform()) // Apply circular transformation
                        .noFade() // Disable fade animation for immediate display
                        .priority(com.squareup.picasso.Picasso.Priority.HIGH) // High priority loading
                        .into(seniorImageView);
            } catch (Exception e) {
                Log.e(TAG, "Error loading image: " + e.getMessage());
                seniorImageView.setImageResource(R.drawable.ic_senior_person);
            }
        } else {
            Log.d(TAG, "No image available, using default");
            // Default image already set above
        }
    }

    private void setupClickListeners() {
        callButton.setOnClickListener(v -> {
            if (seniorPhone != null && !seniorPhone.isEmpty()) {
                makePhoneCall(seniorPhone);
            } else {
                Toast.makeText(this, getString(R.string.phone_number_not_available_short), Toast.LENGTH_SHORT).show();
            }
        });
        
        backButton.setOnClickListener(v -> onBackPressed());
    }


    private void makePhoneCall(String phoneNumber) {
        try {
            String callableNumber = PhoneNumberUtils.getCallablePhoneNumber(phoneNumber);
            if (callableNumber == null) {
                Toast.makeText(this, getString(R.string.invalid_phone_format), Toast.LENGTH_SHORT).show();
                return;
            }
            
            Intent callIntent = new Intent(Intent.ACTION_DIAL);
            callIntent.setData(Uri.parse("tel:" + callableNumber));
            startActivity(callIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error making phone call: " + e.getMessage());
            Toast.makeText(this, getString(R.string.error_making_call), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}
