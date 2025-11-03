package com.example.sagip_prototype;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FeedbackActivity extends AppCompatActivity {

    private static final String TAG = "FeedbackActivity";
    private static final int STORAGE_PERMISSION_REQUEST = 1002;
    private static final int MAX_MESSAGE_LENGTH = 1000;
    private static final int MIN_MESSAGE_LENGTH = 10;
    private static final int MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private SharedPreferences sharedPreferences;

    // UI Components
    private Spinner feedbackTypeSpinner;
    private EditText subjectEditText;
    private EditText messageEditText;
    private CheckBox includeContactCheckBox;
    private EditText contactEmailEditText;
    private EditText contactPhoneEditText;
    private View attachmentImageView;
    private ImageView attachmentImage;
    private Button addAttachmentButton;
    private Button removeAttachmentButton;
    private Button submitButton;
    private ProgressBar progressBar;
    private TextView characterCountText;
    private LinearLayout contactInfoLayout;
    private FloatingActionButton myReportsFab;

    // Data
    private String selectedFeedbackType;
    private Uri attachmentUri;
    private String userType;
    private String userId;
    private String userName;
    private String userEmail;

    // Activity result launchers
    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<String> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_feedback);

        initializeFirebase();
        initializeViews();
        setupSpinners();
        setupTextWatchers();
        setupActivityResultLaunchers();
        loadUserInfo();
        setupClickListeners();
    }

    private void initializeFirebase() {
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        sharedPreferences = getSharedPreferences("SagipAppPrefs", MODE_PRIVATE);
    }

    private void initializeViews() {
        feedbackTypeSpinner = findViewById(R.id.feedbackTypeSpinner);
        subjectEditText = findViewById(R.id.subjectEditText);
        messageEditText = findViewById(R.id.messageEditText);
        includeContactCheckBox = findViewById(R.id.includeContactCheckBox);
        contactEmailEditText = findViewById(R.id.contactEmailEditText);
        contactPhoneEditText = findViewById(R.id.contactPhoneEditText);
        attachmentImageView = findViewById(R.id.attachmentImageView);
        addAttachmentButton = findViewById(R.id.addAttachmentButton);
        removeAttachmentButton = findViewById(R.id.removeAttachmentButton);
        submitButton = findViewById(R.id.submitButton);
        progressBar = findViewById(R.id.progressBar);
        characterCountText = findViewById(R.id.characterCountText);
        contactInfoLayout = findViewById(R.id.contactInfoLayout);
        myReportsFab = findViewById(R.id.myReportsFab);

        // Get the actual ImageView inside the MaterialCardView
        if (attachmentImageView != null) {
            attachmentImage = (ImageView) ((ViewGroup) attachmentImageView).getChildAt(0);
        }

        // Initially hide contact info and attachment
        contactInfoLayout.setVisibility(View.GONE);
        attachmentImageView.setVisibility(View.GONE);
        removeAttachmentButton.setVisibility(View.GONE);
    }

    private void setupSpinners() {
        // Feedback Type Spinner
        String[] feedbackTypes = {
                getString(R.string.feedback_type_bug),
                getString(R.string.feedback_type_feature),
                getString(R.string.feedback_type_general),
                getString(R.string.feedback_type_complaint),
                getString(R.string.feedback_type_other)
        };
        ArrayAdapter<String> feedbackTypeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, feedbackTypes);
        feedbackTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        feedbackTypeSpinner.setAdapter(feedbackTypeAdapter);

        // Set default selection
        feedbackTypeSpinner.setSelection(0);
    }

    private void setupTextWatchers() {
        messageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateCharacterCount(s.length());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupActivityResultLaunchers() {
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        attachmentUri = result.getData().getData();
                        displayAttachment();
                    }
                }
        );

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        showImageSourceDialog();
                    } else {
                        Toast.makeText(this, getString(R.string.feedback_storage_permission), Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void loadUserInfo() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            userId = currentUser.getUid();
            userType = sharedPreferences.getString("userType", "unknown");
            userName = sharedPreferences.getString("userName", "Unknown User");
            userEmail = currentUser.getEmail();
        }
    }

    private void setupClickListeners() {
        feedbackTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedFeedbackType = parent.getItemAtPosition(position).toString();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedFeedbackType = getString(R.string.feedback_type_general);
            }
        });

        includeContactCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            contactInfoLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        addAttachmentButton.setOnClickListener(v -> checkPermissionsAndShowDialog());
        removeAttachmentButton.setOnClickListener(v -> removeAttachment());
        submitButton.setOnClickListener(v -> validateAndSubmitFeedback());
        myReportsFab.setOnClickListener(v -> openMyReports());
    }

    private void checkPermissionsAndShowDialog() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
        } else {
            showImageSourceDialog();
        }
    }

    private void showImageSourceDialog() {
        // Directly open gallery since camera is removed
        openGallery();
    }



    private void openGallery() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(galleryIntent);
    }



    private void displayAttachment() {
        if (attachmentUri != null && attachmentImage != null) {
            attachmentImage.setImageURI(attachmentUri);
            attachmentImageView.setVisibility(View.VISIBLE);
            removeAttachmentButton.setVisibility(View.VISIBLE);
            Toast.makeText(this, getString(R.string.feedback_image_selected), Toast.LENGTH_SHORT).show();
        }
    }

    private void removeAttachment() {
        attachmentUri = null;
        attachmentImageView.setVisibility(View.GONE);
        removeAttachmentButton.setVisibility(View.GONE);
        addAttachmentButton.setText(getString(R.string.feedback_add_attachment));
        Toast.makeText(this, getString(R.string.feedback_image_removed), Toast.LENGTH_SHORT).show();
    }

    private void updateCharacterCount(int length) {
        characterCountText.setText(getString(R.string.feedback_character_count, length));
        
        if (length > MAX_MESSAGE_LENGTH) {
            characterCountText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        } else if (length < MIN_MESSAGE_LENGTH) {
            characterCountText.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
        } else {
            characterCountText.setTextColor(getResources().getColor(android.R.color.darker_gray));
        }
    }

    private void validateAndSubmitFeedback() {
        String subject = subjectEditText.getText().toString().trim();
        String message = messageEditText.getText().toString().trim();
        String contactEmail = contactEmailEditText.getText().toString().trim();
        String contactPhone = contactPhoneEditText.getText().toString().trim();

        // Validation
        if (subject.isEmpty()) {
            subjectEditText.setError(getString(R.string.feedback_required_fields));
            return;
        }

        if (message.length() < MIN_MESSAGE_LENGTH) {
            messageEditText.setError(getString(R.string.feedback_min_length));
            return;
        }

        if (message.length() > MAX_MESSAGE_LENGTH) {
            messageEditText.setError(getString(R.string.feedback_max_length));
            return;
        }

        if (includeContactCheckBox.isChecked()) {
            if (contactEmail.isEmpty() && contactPhone.isEmpty()) {
                Toast.makeText(this, getString(R.string.feedback_include_contact), Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Validate phone number if provided
            if (!contactPhone.isEmpty() && !isValidPhoneNumber(contactPhone)) {
                contactPhoneEditText.setError(getString(R.string.valid_mobile_error));
                return;
            }
        }

        submitFeedback(subject, message, contactEmail, contactPhone);
    }

    private void submitFeedback(String subject, String message, String contactEmail, String contactPhone) {
        setLoading(true);

        // Create feedback data
        Map<String, Object> feedbackData = new HashMap<>();
        feedbackData.put("feedbackType", selectedFeedbackType);
        feedbackData.put("subject", subject);
        feedbackData.put("message", message);
        feedbackData.put("includeContact", includeContactCheckBox.isChecked());
        feedbackData.put("contactEmail", contactEmail);
        feedbackData.put("contactPhone", contactPhone);
        feedbackData.put("status", getString(R.string.feedback_status_pending));
        feedbackData.put("timestamp", new Date());
        feedbackData.put("userType", userType);
        feedbackData.put("userId", userId);
        feedbackData.put("userName", userName);
        feedbackData.put("userEmail", userEmail);

        // Upload attachment if exists
        if (attachmentUri != null) {
            uploadAttachmentAndSubmitFeedback(feedbackData);
        } else {
            submitFeedbackToFirestore(feedbackData, null);
        }
    }

    private void uploadAttachmentAndSubmitFeedback(Map<String, Object> feedbackData) {
        String attachmentFileName = "feedback_" + UUID.randomUUID().toString() + ".jpg";
        StorageReference attachmentRef = storage.getReference().child("feedback_attachments").child(attachmentFileName);

        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), attachmentUri);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            byte[] data = baos.toByteArray();

            attachmentRef.putBytes(data)
                    .addOnSuccessListener(taskSnapshot -> {
                        attachmentRef.getDownloadUrl()
                                .addOnSuccessListener(uri -> {
                                    feedbackData.put("attachmentUrl", uri.toString());
                                    submitFeedbackToFirestore(feedbackData, null);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error getting download URL", e);
                                    submitFeedbackToFirestore(feedbackData, e.getMessage());
                                });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error uploading attachment", e);
                        submitFeedbackToFirestore(feedbackData, e.getMessage());
                    });
        } catch (IOException e) {
            Log.e(TAG, "Error processing image", e);
            submitFeedbackToFirestore(feedbackData, e.getMessage());
        }
    }

    private void submitFeedbackToFirestore(Map<String, Object> feedbackData, String error) {
        if (error != null) {
            feedbackData.put("uploadError", error);
        }

        db.collection("feedback")
                .add(feedbackData)
                .addOnSuccessListener(documentReference -> {
                    setLoading(false);
                    showSuccessDialog();
                    clearForm();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Log.e(TAG, "Error submitting feedback", e);
                    Toast.makeText(this, getString(R.string.feedback_error_message), Toast.LENGTH_LONG).show();
                });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        submitButton.setEnabled(!loading);
        submitButton.setText(loading ? getString(R.string.feedback_submitting) : getString(R.string.feedback_submit));
    }

    private void showSuccessDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.feedback_success))
                .setMessage(getString(R.string.feedback_success_message))
                .setPositiveButton(getString(R.string.ok), (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private void clearForm() {
        subjectEditText.setText("");
        messageEditText.setText("");
        feedbackTypeSpinner.setSelection(0);
        includeContactCheckBox.setChecked(false);
        contactEmailEditText.setText("");
        contactPhoneEditText.setText("");
        removeAttachment();
        updateCharacterCount(0);
    }

    private void openMyReports() {
        Intent intent = new Intent(this, MyReportsActivity.class);
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == STORAGE_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showImageSourceDialog();
            } else {
                Toast.makeText(this, getString(R.string.feedback_storage_permission), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean isValidPhoneNumber(String number) {
        return !number.isEmpty() && number.matches("09\\d{9}");
    }
}
