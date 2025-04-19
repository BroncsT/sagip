package com.example.sagip_prototype;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.squareup.picasso.Picasso;

import java.util.HashMap;
import java.util.Map;

public class Selfie_verification extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int STORAGE_PERMISSION_CODE = 101;
    private static final int CAMERA_REQUEST_CODE = 2000;
    private static final int GALLERY_REQUEST_CODE = 2001;

    Button takeSelfieButton, submitVerificationButton;
    ImageView selfieImageView;

    StorageReference storageReference;
    FirebaseAuth auth;
    FirebaseFirestore db;

    private String idPhotoUrl; // To store the ID photo URL from previous screen
    private String selfieUrl; // To store the selfie URL

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_selfie_verification);

        // Initialize Firebase components
        storageReference = FirebaseStorage.getInstance().getReference();
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Get the ID photo URL from the intent
        idPhotoUrl = getIntent().getStringExtra("idPhotoUrl");
        if (idPhotoUrl == null) {
            Toast.makeText(this, "Error: Missing ID photo information", Toast.LENGTH_SHORT).show();
            // You might want to redirect back to the previous screen if this is critical
        }

        // Find views
        takeSelfieButton = findViewById(R.id.takeSelfieButton);
        submitVerificationButton = findViewById(R.id.verifySelfieButton);
        selfieImageView = findViewById(R.id.selfiePhotoImageView);

        // Initially disable submit button until selfie is taken
        submitVerificationButton.setEnabled(false);

        // Check if user already has a selfie photo and display it
        if (auth.getCurrentUser() != null) {
            StorageReference selfieReference = storageReference.child("users/" + auth.getUid() + "/selfie_photos");
            selfieReference.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                @Override
                public void onSuccess(Uri uri) {
                    Picasso.get().load(uri).into(selfieImageView);
                    selfieUrl = uri.toString();
                    submitVerificationButton.setEnabled(true);
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    // No existing selfie, which is fine
                }
            });
        }

        // Set click listener for take selfie button
        takeSelfieButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // First check storage permission as we'll need it regardless of camera or gallery
                checkStoragePermission();
            }
        });

        // Set click listener for submit verification button
        submitVerificationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selfieUrl != null && idPhotoUrl != null) {
                    saveVerificationData();
                } else {
                    Toast.makeText(Selfie_verification.this, "Please take a selfie first", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            // Storage permission not granted, request it
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    },
                    STORAGE_PERMISSION_CODE);
        } else {
            // Storage permission already granted, proceed with camera permission check
            checkCameraPermission();
        }
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // Camera permission not granted, request it
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_CODE);
        } else {
            // Camera permission already granted, proceed with camera
            openCamera();
        }
    }

    private void openCamera() {
        try {
            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (cameraIntent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
            } else {
                // If camera is not available, try gallery as fallback
                Toast.makeText(this, "Camera not available, opening gallery instead", Toast.LENGTH_SHORT).show();
                openGallery();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error accessing camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            openGallery(); // Fallback to gallery
        }
    }

    private void openGallery() {
        try {
            Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(galleryIntent, GALLERY_REQUEST_CODE);
        } catch (Exception e) {
            Toast.makeText(this, "Error opening gallery: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Storage permission granted, proceed with camera permission check
                checkCameraPermission();
            } else {
                // Storage permission denied
                Toast.makeText(this, "Storage permission is required to save photos", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Camera permission granted, proceed with camera
                openCamera();
            } else {
                // Camera permission denied, try gallery instead
                Toast.makeText(this, "Camera permission denied, opening gallery instead", Toast.LENGTH_SHORT).show();
                openGallery();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null) {
            Uri imageUri = null;

            if (requestCode == CAMERA_REQUEST_CODE) {
                // Handle camera photo result
                try {
                    Bundle extras = data.getExtras();
                    if (extras != null && extras.containsKey("data")) {
                        // This is just a thumbnail, in a real app you'd want to save the full image
                        imageUri = Uri.parse(MediaStore.Images.Media.insertImage(
                                getContentResolver(),
                                (android.graphics.Bitmap) extras.get("data"),
                                "Selfie",
                                "Verification Selfie"));
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "Error processing camera image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else if (requestCode == GALLERY_REQUEST_CODE) {
                // Handle gallery selection result
                imageUri = data.getData();
            }

            if (imageUri != null) {
                uploadSelfie(imageUri);
            } else {
                Toast.makeText(this, "Failed to get image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void uploadSelfie(Uri selfieImage) {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        StorageReference reference = storageReference.child("users/" + auth.getUid() + "/selfie_photos");

        reference.putFile(selfieImage).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                // Get the download URL after upload completes
                reference.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                    @Override
                    public void onSuccess(Uri uri) {
                        // Display the image
                        Picasso.get().load(uri).into(selfieImageView);

                        // Save the selfie URL
                        selfieUrl = uri.toString();

                        // Enable submit button
                        submitVerificationButton.setEnabled(true);

                        Toast.makeText(Selfie_verification.this, "Selfie uploaded successfully", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(Selfie_verification.this, "Selfie Upload Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveVerificationData() {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create data map with both URLs
        Map<String, Object> verificationData = new HashMap<>();
        verificationData.put("idPhotoUrl", idPhotoUrl);
        verificationData.put("selfieUrl", selfieUrl);
        verificationData.put("verificationSubmittedAt", System.currentTimeMillis());

        String userType = "seniors"; // replace with your actual user type variable
        String uid = auth.getUid();

        // Update Firestore with both image URLs
        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .update(verificationData)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Toast.makeText(Selfie_verification.this, "Verification submitted successfully", Toast.LENGTH_SHORT).show();
                        // Navigate to next screen or home screen
                        Intent intent = new Intent(Selfie_verification.this, MainActivity.class); // Replace with your actual home activity
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK); // Clear activity stack
                        startActivity(intent);
                        finish();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(Selfie_verification.this, "Failed to submit verification: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }
}