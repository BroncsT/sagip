package com.example.sagip_prototype;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class FontSizeActivity extends AppCompatActivity {

    private static final String PREF_NAME = "SagipAppPrefs";
    private static final String KEY_FONT_SIZE = "fontSizeMultiplier";
    public static final int RESULT_FONT_SIZE_CHANGED = 100;
    
    private RadioGroup fontSizeGroup;
    private TextView previewText;
    private SharedPreferences sharedPreferences;
    private boolean fontSizeChanged = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Apply current font size to this activity
        FontSizeHelper.applyFontSize(this);
        
        setContentView(R.layout.activity_font_size);

        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        
        fontSizeGroup = findViewById(R.id.fontSizeGroup);
        previewText = findViewById(R.id.previewText);
        
        // Load current font size preference
        float currentFontSize = sharedPreferences.getFloat(KEY_FONT_SIZE, 1.0f);
        
        // Set the appropriate radio button
        if (currentFontSize == 0.85f) {
            fontSizeGroup.check(R.id.radioSmall);
        } else if (currentFontSize == 1.0f) {
            fontSizeGroup.check(R.id.radioMedium);
        } else if (currentFontSize == 1.15f) {
            fontSizeGroup.check(R.id.radioLarge);
        } else if (currentFontSize == 1.3f) {
            fontSizeGroup.check(R.id.radioExtraLarge);
        }
        
        // Update preview text size
        updatePreviewText(currentFontSize);
        
        // Handle radio button selection
        fontSizeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            float multiplier = 1.0f;
            
            if (checkedId == R.id.radioSmall) {
                multiplier = 0.85f;
            } else if (checkedId == R.id.radioMedium) {
                multiplier = 1.0f;
            } else if (checkedId == R.id.radioLarge) {
                multiplier = 1.15f;
            } else if (checkedId == R.id.radioExtraLarge) {
                multiplier = 1.3f;
            }
            
            // Save preference
            FontSizeHelper.saveFontSize(this, multiplier);
            
            // Update preview
            updatePreviewText(multiplier);
            
            // Show confirmation
            Toast.makeText(this, getString(R.string.font_size_updated), Toast.LENGTH_SHORT).show();
            
            // Mark that font size was changed
            fontSizeChanged = true;
            setResult(RESULT_FONT_SIZE_CHANGED);
        });
        
        // Back button
        findViewById(R.id.backButton).setOnClickListener(v -> {
            finish();
        });
    }
    
    private void updatePreviewText(float multiplier) {
        float baseSize = 18f; // Base preview text size
        previewText.setTextSize(baseSize * multiplier);
    }
}

