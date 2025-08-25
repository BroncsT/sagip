package com.example.sagip_prototype;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

public class Barangay_Profile extends BaseProfileActivity {
        FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        mAuth = FirebaseAuth.getInstance();
        setContentView(R.layout.activity_barangay_profile);
        
        // Add language selection functionality
        addLanguageSelectionToLayout();
        
        TextView labelProfile = findViewById(R.id.profileName);
        TextView email = findViewById(R.id.profileEmail);

        LinearLayout gotoUpdate = findViewById(R.id.gotoupdate);
        LinearLayout gotoLogut = findViewById(R.id.logoutLayout);

        gotoUpdate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Barangay_Profile.this, Barangay_Registration.class);
                startActivity(intent);
            }
        });

        gotoLogut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAuth.signOut();
                startActivity(new Intent(Barangay_Profile.this, MainActivity.class));
                finish();
            }
        });

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavBar2);
        bottomNavigationView.setSelectedItemId(R.id.barangay_profile);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.barangay_profile) {
                return true;
            } else if (itemId == R.id.barangay_seniorList) {
                startActivity(new Intent(getApplicationContext(), Barangay_List.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (itemId == R.id.barangay_dashboard) {
                startActivity(new Intent(getApplicationContext(), Barangay_Dashboard.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            return false;
        });
    }

    @Override
    protected void updateSpecificProfileElements() {
        // Update profile-specific UI elements
        TextView accountSettingsTitle = findViewById(R.id.accountSettingsTitle);
        if (accountSettingsTitle != null) {
            accountSettingsTitle.setText(getString(R.string.account_settings));
        }

        TextView changeInfoText = findViewById(R.id.changeInfoText);
        if (changeInfoText != null) {
            changeInfoText.setText(getString(R.string.change_Info));
        }

        TextView notificationSettingsText = findViewById(R.id.notificationSettingsText);
        if (notificationSettingsText != null) {
            notificationSettingsText.setText(getString(R.string.notification_settings));
        }

        TextView moreOptionsTitle = findViewById(R.id.moreOptionsTitle);
        if (moreOptionsTitle != null) {
            moreOptionsTitle.setText(getString(R.string.more_options));
        }

        TextView helpSupportText = findViewById(R.id.helpSupportText);
        if (helpSupportText != null) {
            helpSupportText.setText(getString(R.string.help_support));
        }

        TextView logoutText = findViewById(R.id.logoutText);
        if (logoutText != null) {
            logoutText.setText(getString(R.string.logout));
        }
    }
}