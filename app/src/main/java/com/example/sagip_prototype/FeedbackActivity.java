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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
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
    private static final int MAX_DAILY_REPORTS = 3;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private SharedPreferences sharedPreferences;

    // UI Components
    private Spinner feedbackTypeSpinner;
    private EditText messageEditText;
    private View attachmentImageView;
    private ImageView attachmentImage;
    private Button addAttachmentButton;
    private Button removeAttachmentButton;
    private Button submitButton;
    private ProgressBar progressBar;
    private AlertDialog loadingDialog;

    // Data
    private String selectedFeedbackType;
    private Uri attachmentUri;
    private String userType;
    private String userId;
    private String userName;
    private String userEmail;
    private String userPhone;

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
        messageEditText = findViewById(R.id.messageEditText);
        attachmentImageView = findViewById(R.id.attachmentImageView);
        addAttachmentButton = findViewById(R.id.addAttachmentButton);
        removeAttachmentButton = findViewById(R.id.removeAttachmentButton);
        submitButton = findViewById(R.id.submitButton);
        progressBar = findViewById(R.id.progressBar);

        // Get the actual ImageView inside the MaterialCardView
        if (attachmentImageView != null) {
            attachmentImage = (ImageView) ((ViewGroup) attachmentImageView).getChildAt(0);
        }

        // Initially hide attachment
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
        ArrayAdapter<String> feedbackTypeAdapter = new ArrayAdapter<>(this, R.layout.spinner_item_feedback, feedbackTypes);
        feedbackTypeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_feedback);
        feedbackTypeSpinner.setAdapter(feedbackTypeAdapter);

        // Set default selection
        feedbackTypeSpinner.setSelection(0);
    }

    private void setupTextWatchers() {
        // Character counter is now handled by TextInputLayout in the XML
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
            userPhone = sharedPreferences.getString("userPhone", "");
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

        addAttachmentButton.setOnClickListener(v -> checkPermissionsAndShowDialog());
        removeAttachmentButton.setOnClickListener(v -> removeAttachment());
        submitButton.setOnClickListener(v -> validateAndSubmitFeedback());
    }

    private void checkPermissionsAndShowDialog() {
        // For Android 13+ (API 33+), use READ_MEDIA_IMAGES instead of READ_EXTERNAL_STORAGE
        String permission;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permission = Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }
        
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(permission);
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


    private void validateAndSubmitFeedback() {
        String message = messageEditText.getText().toString().trim();

        // Validation
        if (message.length() < MIN_MESSAGE_LENGTH) {
            messageEditText.setError(getString(R.string.feedback_min_length));
            return;
        }

        if (message.length() > MAX_MESSAGE_LENGTH) {
            messageEditText.setError(getString(R.string.feedback_max_length));
            return;
        }

        // Require attachment
        if (attachmentUri == null) {
            Toast.makeText(this, getString(R.string.please_add_attachment), Toast.LENGTH_SHORT).show();
            return;
        }

        // Check daily report limit before submitting
        checkDailyReportLimitAndSubmit(message);
    }

    private void checkDailyReportLimitAndSubmit(String message) {
        if (userId == null) {
            Toast.makeText(this, getString(R.string.user_not_authenticated_feedback), Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        // Get start and end of today
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date startOfDay = calendar.getTime();

        calendar.add(Calendar.DAY_OF_MONTH, 1);
        Date endOfDay = calendar.getTime();

        // Query today's reports for this user - only filter by userId first
        // Then filter by date client-side to avoid composite index requirement
        db.collection("feedback")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    // Count only today's reports client-side
                    int todayReportCount = 0;
                    for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots) {
                        Object timestampObj = doc.get("timestamp");
                        if (timestampObj != null) {
                            Date docDate = null;
                            if (timestampObj instanceof com.google.firebase.Timestamp) {
                                docDate = ((com.google.firebase.Timestamp) timestampObj).toDate();
                            } else if (timestampObj instanceof Date) {
                                docDate = (Date) timestampObj;
                            }
                            if (docDate != null && !docDate.before(startOfDay) && docDate.before(endOfDay)) {
                                todayReportCount++;
                            }
                        }
                    }
                    Log.d(TAG, "Today's feedback count for user: " + todayReportCount);
                    if (todayReportCount >= MAX_DAILY_REPORTS) {
                        setLoading(false);
                        showDailyLimitReachedDialog();
                    } else {
                        // Proceed with submission
                        submitFeedbackAfterCheck(message);
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Log.e(TAG, "Error checking daily report limit", e);
                    Toast.makeText(this, getString(R.string.failed_verify_report_limit), Toast.LENGTH_SHORT).show();
                });
    }

    private void showDailyLimitReachedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Daily Limit Reached")
                .setMessage("You have reached the maximum of " + MAX_DAILY_REPORTS + " reports per day. Please try again tomorrow.")
                .setPositiveButton(getString(R.string.ok), (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .show();
    }

    private void submitFeedbackAfterCheck(String message) {
        // Create feedback data with user contact info automatically included
        Map<String, Object> feedbackData = new HashMap<>();
        feedbackData.put("feedbackType", selectedFeedbackType);
        feedbackData.put("message", message);
        feedbackData.put("contactEmail", userEmail != null ? userEmail : "");
        feedbackData.put("contactPhone", userPhone != null ? userPhone : "");
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
        if (loading) {
            // Show loading dialog
            if (loadingDialog == null) {
                View dialogView = getLayoutInflater().inflate(R.layout.dialog_loading, null);
                TextView loadingText = dialogView.findViewById(R.id.loadingText);
                if (loadingText != null) {
                    loadingText.setText("Sending feedback...");
                }
                loadingDialog = new AlertDialog.Builder(this)
                        .setView(dialogView)
                        .setCancelable(false)
                        .create();
                if (loadingDialog.getWindow() != null) {
                    loadingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                }
            }
            loadingDialog.show();
        } else {
            // Dismiss loading dialog
            if (loadingDialog != null && loadingDialog.isShowing()) {
                loadingDialog.dismiss();
            }
        }
        submitButton.setEnabled(!loading);
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
        messageEditText.setText("");
        feedbackTypeSpinner.setSelection(0);
        removeAttachment();
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

}
