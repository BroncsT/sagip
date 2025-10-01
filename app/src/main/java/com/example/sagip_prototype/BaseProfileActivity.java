package com.example.sagip_prototype;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public abstract class BaseProfileActivity extends AppCompatActivity {
    protected com.google.firebase.auth.FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = com.google.firebase.auth.FirebaseAuth.getInstance();
        
        // Apply saved language preference
        String savedLanguage = LanguageSelectionActivity.getSavedLanguage(this);
        LanguageSelectionActivity.setAppLanguage(this, savedLanguage);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Auth guard: if logged out, redirect to login and clear back stack
        if (mAuth != null && mAuth.getCurrentUser() == null) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finishAffinity();
            return;
        }
        // Update UI language when returning
        updateUILanguage();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Add language selection menu item
        menu.add(Menu.NONE, 1001, Menu.NONE, getString(R.string.select_language));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1001) {
            showLanguageSelectionDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    protected void showLanguageSelectionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.language_selection));
        
        String[] languages = {getString(R.string.english), getString(R.string.filipino)};
        String currentLanguage = LanguageSelectionActivity.getSavedLanguage(this);
        int currentIndex = currentLanguage.equals("tl") ? 1 : 0;
        
        builder.setSingleChoiceItems(languages, currentIndex, (dialog, which) -> {
            String selectedLanguage = (which == 0) ? "en" : "tl";
            LanguageSelectionActivity.setAppLanguage(this, selectedLanguage);
            LanguageSelectionActivity.saveLanguagePreference(this, selectedLanguage);
            
            // Update UI elements without recreating the activity
            updateUILanguage();
            
            dialog.dismiss();
        });
        
        builder.setNegativeButton(getString(R.string.cancel), (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    protected void updateUILanguage() {
        // Update language selection text if it exists
        TextView languageSelectionText = findViewById(R.id.languageSelectionText);
        if (languageSelectionText != null) {
            languageSelectionText.setText(getString(R.string.select_language));
        }

        // Update common profile elements
        updateCommonProfileElements();
        
        // Let child classes update their specific elements
        updateSpecificProfileElements();
    }

    protected void updateCommonProfileElements() {
        // Update common profile elements like titles, buttons, etc.
        // This can be overridden by child classes
    }

    protected void updateSpecificProfileElements() {
        // This method should be overridden by child classes to update their specific UI elements
    }

    protected void addLanguageSelectionToLayout() {
        // This method can be called by child classes to add language selection to their layout
        TextView languageSelectionText = findViewById(R.id.languageSelectionText);
        if (languageSelectionText != null) {
            languageSelectionText.setOnClickListener(v -> showLanguageSelectionDialog());
        }
    }
}
