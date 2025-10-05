package com.example.sagip_prototype;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class LanguageSelectionActivity extends AppCompatActivity {

    private Button englishButton, filipinoButton, continueButton;
    private TextView titleText, subtitleText;
    private String selectedLanguage = "en"; // Default to English

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_language_selection);

        // Initialize views
        titleText = findViewById(R.id.titleText);
        subtitleText = findViewById(R.id.subtitleText);
        englishButton = findViewById(R.id.englishButton);
        filipinoButton = findViewById(R.id.filipinoButton);
        continueButton = findViewById(R.id.continueButton);

        // Set initial text
        updateTexts();

        // Set English as default selection
        englishButton.setSelected(true);
        continueButton.setEnabled(true);

        // Set click listeners
        englishButton.setOnClickListener(v -> selectLanguage("en"));
        filipinoButton.setOnClickListener(v -> selectLanguage("tl"));
        continueButton.setOnClickListener(v -> continueToApp());
    }

    private void selectLanguage(String languageCode) {
        selectedLanguage = languageCode;
        
        // Update button states
        englishButton.setSelected(languageCode.equals("en"));
        filipinoButton.setSelected(languageCode.equals("tl"));
        
        // Enable continue button
        continueButton.setEnabled(true);
        
        // Update texts based on selected language
        updateTexts();
    }

    private void updateTexts() {
        if (selectedLanguage.equals("tl")) {
            // Filipino
            titleText.setText(R.string.language_selection);
            subtitleText.setText(R.string.select_language);
            englishButton.setText(R.string.english);
            filipinoButton.setText(R.string.filipino);
            continueButton.setText(R.string.continue_button);
        } else {
            // English (default)
            titleText.setText(R.string.language_selection);
            subtitleText.setText(R.string.select_language);
            englishButton.setText(R.string.english);
            filipinoButton.setText(R.string.filipino);
            continueButton.setText(R.string.continue_button);
        }
    }

    private void continueToApp() {
        if (!selectedLanguage.isEmpty()) {
            // Save selected language
            saveLanguagePreference(selectedLanguage);
            
            // Set the language for the app
            setAppLanguage(selectedLanguage);
            
            // Navigate to main activity
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        }
    }

    private void saveLanguagePreference(String languageCode) {
        SharedPreferences prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("language", languageCode);
        editor.apply();
        Log.d("LanguageSelection", "🌐 Language preference saved: " + languageCode);
    }

    private void setAppLanguage(String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);
        
        Resources resources = getResources();
        Configuration config = resources.getConfiguration();
        config.setLocale(locale);
        
        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }

    public static void setAppLanguage(android.content.Context context, String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);
        
        Resources resources = context.getResources();
        Configuration config = resources.getConfiguration();
        config.setLocale(locale);
        
        resources.updateConfiguration(config, resources.getDisplayMetrics());
        Log.d("LanguageSelection", "🌐 Language set to: " + languageCode + " (Locale: " + locale + ")");
    }

    public static String getSavedLanguage(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE);
        String language = prefs.getString("language", "en"); // Default to English
        Log.d("LanguageSelection", "🌐 Retrieved saved language: " + language);
        return language;
    }

    public static void saveLanguagePreference(android.content.Context context, String languageCode) {
        SharedPreferences prefs = context.getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("language", languageCode);
        editor.apply();
    }
}
