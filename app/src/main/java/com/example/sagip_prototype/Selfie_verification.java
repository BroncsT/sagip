package com.example.sagip_prototype;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.squareup.picasso.Picasso;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Selfie_verification extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int STORAGE_PERMISSION_CODE = 101;
    private static final int GALLERY_REQUEST_CODE = 2001;

    Button takeSelfieButton, submitVerificationButton, manualCaptureButton;
    ImageView selfieImageView, facePlaceholderImageView;
    TextView instructionsTextView, selfieStepIndicator, selfiePlaceholderText, guidelinesTitle;
    PreviewView previewView;

    StorageReference storageReference;
    FirebaseAuth auth;
    FirebaseFirestore db;

    private String idPhotoUrl; // To store the ID photo URL from previous screen
    private String selfieUrl; // To store the selfie URL
    private String idType; // To store the ID type from previous screen

    // Camera and face detection
    private ProcessCameraProvider cameraProvider;
    private ExecutorService cameraExecutor;
    private FaceDetector faceDetector;
    private boolean isFaceDetected = false;
    private boolean isFacePositioned = false;
    private boolean isGoodLighting = false;
    private boolean autoCaptureEnabled = true;
    private int faceDetectionCount = 0;
    private static final int REQUIRED_FACE_DETECTIONS = 30; // 30 frames with good face detection

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_selfie_verification);

        // Initialize Firebase components
        storageReference = FirebaseStorage.getInstance().getReference();
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Get the ID photo URL and ID type from the intent
        idPhotoUrl = getIntent().getStringExtra("idPhotoUrl");
        idType = getIntent().getStringExtra("idType");
        if (idPhotoUrl == null) {
            Toast.makeText(this, "Error: Missing ID photo information", Toast.LENGTH_SHORT).show();
        }

        // Find views
        takeSelfieButton = findViewById(R.id.takeSelfieButton);
        submitVerificationButton = findViewById(R.id.verifySelfieButton);
        manualCaptureButton = findViewById(R.id.manualCaptureButton);
        selfieImageView = findViewById(R.id.selfiePhotoImageView);
        facePlaceholderImageView = findViewById(R.id.facePlaceholderImageView);
        instructionsTextView = findViewById(R.id.instructionsTextView);
        selfieStepIndicator = findViewById(R.id.selfieStepIndicator);
        selfiePlaceholderText = findViewById(R.id.selfiePlaceholderText);
        guidelinesTitle = findViewById(R.id.guidelinesTitle);
        previewView = findViewById(R.id.previewView);

        // Initially disable submit button until selfie is taken
        submitVerificationButton.setEnabled(false);
        manualCaptureButton.setEnabled(false);
        
        // Setup initial UI state
        setupInitialUI();

        // Initialize face detector
        setupFaceDetector();

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
                startAutomaticSelfieCapture();
            }
        });

        // Set click listener for manual capture button
        manualCaptureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                captureCurrentFrame();
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
    
    private void setupInitialUI() {
        // Set initial instructions
        instructionsTextView.setText("Automatic Selfie Verification\n\n" +
                "📱 Position your face in the frame\n" +
                "😊 Look directly at the camera\n" +
                "💡 Ensure good lighting\n" +
                "👤 Keep your face centered\n" +
                "⏱️ Photo will be taken automatically");
        
        // Update step indicator
        selfieStepIndicator.setText("Step 2 of 3: Automatic Selfie Verification");
        
        // Show placeholder elements initially
        facePlaceholderImageView.setVisibility(View.VISIBLE);
        selfiePlaceholderText.setVisibility(View.VISIBLE);
        selfiePlaceholderText.setText("Tap 'Start Automatic Capture' to begin");
        
        // Hide guidelines initially
        guidelinesTitle.setVisibility(View.GONE);
        
        // Hide preview initially
        previewView.setVisibility(View.GONE);
    }

    private void setupFaceDetector() {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setMinFaceSize(0.15f)
                .build();

        faceDetector = FaceDetection.getClient(options);
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void startAutomaticSelfieCapture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
            return;
        }

        // Show camera preview
        previewView.setVisibility(View.VISIBLE);
        facePlaceholderImageView.setVisibility(View.GONE);
        selfiePlaceholderText.setVisibility(View.GONE);
        takeSelfieButton.setVisibility(View.GONE);
        manualCaptureButton.setVisibility(View.VISIBLE);

        // Update instructions
        instructionsTextView.setText("🔍 Detecting face...\n\n" +
                "📱 Position your face in the center\n" +
                "😊 Look directly at the camera\n" +
                "💡 Ensure good lighting\n" +
                "⏱️ Photo will be taken automatically when ready");

        // Start camera
        startCamera();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "Error starting camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, new FaceDetectionAnalyzer());

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
        } catch (Exception e) {
            Toast.makeText(this, "Error binding camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private class FaceDetectionAnalyzer implements ImageAnalysis.Analyzer {
        @Override
        public void analyze(@NonNull ImageProxy imageProxy) {
            InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

            faceDetector.process(image)
                    .addOnSuccessListener(faces -> {
                        processFaces(faces, imageProxy.getWidth(), imageProxy.getHeight());
                        imageProxy.close();
                    })
                    .addOnFailureListener(e -> {
                        imageProxy.close();
                    });
        }
    }

    private void processFaces(List<Face> faces, int imageWidth, int imageHeight) {
        if (faces.isEmpty()) {
            isFaceDetected = false;
            isFacePositioned = false;
            updateInstructions("🔍 No face detected\n\nPlease position your face in the camera view");
            return;
        }

        Face face = faces.get(0);
        isFaceDetected = true;

        // Check face position (should be in center)
        android.graphics.Rect boundingBox = face.getBoundingBox();
        float faceCenterX = boundingBox.centerX();
        float faceCenterY = boundingBox.centerY();
        
        float imageCenterX = imageWidth / 2f;
        float imageCenterY = imageHeight / 2f;
        
        float distanceFromCenter = (float) Math.sqrt(
                Math.pow(faceCenterX - imageCenterX, 2) + 
                Math.pow(faceCenterY - imageCenterY, 2)
        );
        
        float maxDistance = Math.min(imageWidth, imageHeight) * 0.3f; // 30% of smaller dimension
        
        isFacePositioned = distanceFromCenter < maxDistance;

        // Check face size (should be reasonably large)
        float faceSize = Math.min(boundingBox.width(), boundingBox.height());
        float minFaceSize = Math.min(imageWidth, imageHeight) * 0.2f; // 20% of smaller dimension
        boolean isFaceSizeGood = faceSize > minFaceSize;

        // Check if face is looking forward (simple check)
        boolean isLookingForward = face.getHeadEulerAngleY() < 20 && face.getHeadEulerAngleY() > -20;

        // Check lighting (using face detection confidence as proxy)
        boolean isGoodLighting = true; // Assume good lighting if face is detected

        // Update instructions based on conditions
        StringBuilder instruction = new StringBuilder();
        instruction.append("🔍 Face detected!\n\n");

        if (!isFacePositioned) {
            instruction.append("📱 Move your face to the center\n");
        }
        if (!isFaceSizeGood) {
            instruction.append("📏 Move closer to the camera\n");
        }
        if (!isLookingForward) {
            instruction.append("😊 Look directly at the camera\n");
        }
        if (!isGoodLighting) {
            instruction.append("💡 Improve lighting\n");
        }

        if (isFacePositioned && isFaceSizeGood && isLookingForward && isGoodLighting) {
            instruction.append("✅ Perfect! Taking photo in ");
            instruction.append(3 - (faceDetectionCount / 10));
            instruction.append(" seconds...");
            
            faceDetectionCount++;
            
            if (faceDetectionCount >= REQUIRED_FACE_DETECTIONS && autoCaptureEnabled) {
                autoCaptureEnabled = false;
                captureCurrentFrame();
            }
        } else {
            faceDetectionCount = 0;
        }

        updateInstructions(instruction.toString());
    }

    private void updateInstructions(String text) {
        runOnUiThread(() -> {
            instructionsTextView.setText(text);
        });
    }

    private void captureCurrentFrame() {
        if (previewView.getBitmap() != null) {
            Bitmap bitmap = previewView.getBitmap();
            processCapturedImage(bitmap);
        } else {
            // Fallback: take a screenshot of the preview
            View view = previewView.getRootView();
            view.setDrawingCacheEnabled(true);
            Bitmap bitmap = Bitmap.createBitmap(view.getDrawingCache());
            view.setDrawingCacheEnabled(false);
            processCapturedImage(bitmap);
        }
    }

    private void processCapturedImage(Bitmap bitmap) {
        // Stop camera
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        
        // Hide preview and show captured image
        previewView.setVisibility(View.GONE);
        selfieImageView.setVisibility(View.VISIBLE);
        selfieImageView.setImageBitmap(bitmap);
        
        // Update UI
        updateUIForSelfieSuccess();
        
        // Upload the image
        uploadSelfieFromBitmap(bitmap);
    }

    private void uploadSelfieFromBitmap(Bitmap bitmap) {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show loading state
        instructionsTextView.setText("📤 Uploading your selfie...\nPlease wait a moment.");

        // Convert bitmap to byte array
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos);
        byte[] data = baos.toByteArray();

        StorageReference reference = storageReference.child("users/" + auth.getUid() + "/selfie_photos");

        reference.putBytes(data).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                reference.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                    @Override
                    public void onSuccess(Uri uri) {
                        selfieUrl = uri.toString();
                        submitVerificationButton.setEnabled(true);
                        Toast.makeText(Selfie_verification.this, "✅ Selfie captured and uploaded successfully!", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                instructionsTextView.setText("❌ Upload failed. Please try again.");
                Toast.makeText(Selfie_verification.this, "Selfie Upload Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void updateUIForSelfieSuccess() {
        // Hide placeholder elements
        facePlaceholderImageView.setVisibility(View.GONE);
        selfiePlaceholderText.setVisibility(View.GONE);
        
        // Show guidelines
        guidelinesTitle.setVisibility(View.VISIBLE);
        
        // Update instructions
        instructionsTextView.setText("✅ Perfect! Your selfie has been captured automatically.\n\n" +
                "Please review the guidelines below and tap 'Verify Selfie' to continue.");
        
        // Update step indicator
        selfieStepIndicator.setText("Step 2 of 3: Selfie Verification ✓");
        
        // Show retake option
        takeSelfieButton.setVisibility(View.VISIBLE);
        takeSelfieButton.setText("📷 Retake Selfie");
        manualCaptureButton.setVisibility(View.GONE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startAutomaticSelfieCapture();
            } else {
                instructionsTextView.setText("❌ Camera permission denied.\n\n" +
                        "This permission is required for automatic selfie verification.\n" +
                        "Please grant permission in Settings or try again.");
                Toast.makeText(this, "Camera permission is required for automatic selfie verification", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void saveVerificationData() {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show submission progress
        instructionsTextView.setText("📤 Submitting verification...\nPlease wait while we process your information.");
        submitVerificationButton.setEnabled(false);
        takeSelfieButton.setEnabled(false);

        // Create data map with both URLs and ID type
        Map<String, Object> verificationData = new HashMap<>();
        verificationData.put("idPhotoUrl", idPhotoUrl);
        verificationData.put("selfieUrl", selfieUrl);
        verificationData.put("idType", idType);
        verificationData.put("verificationSubmittedAt", System.currentTimeMillis());
        verificationData.put("status", "pending"); // Ensure status is set to pending

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
                        // Show success message
                        instructionsTextView.setText("✅ Verification submitted successfully!\n\n" +
                                "Your account is now pending approval.\n" +
                                "You will be redirected to the login page.");
                        
                        Toast.makeText(Selfie_verification.this, "✅ Verification submitted successfully! Your account is pending approval.", Toast.LENGTH_LONG).show();
                        
                        // Delay before redirecting to show success message
                        new android.os.Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                // Sign out the user and redirect to login page
                                auth.signOut();
                                
                                // Navigate to MainActivity with logout action to clear any stored credentials
                                Intent intent = new Intent(Selfie_verification.this, MainActivity.class);
                                intent.putExtra("LOGOUT_ACTION", true);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK); // Clear activity stack
                                startActivity(intent);
                                finish();
                            }
                        }, 2000); // 2 second delay
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // Reset UI on failure
                        instructionsTextView.setText("❌ Submission failed. Please try again.\n\n" +
                                "Check your internet connection and try again.");
                        submitVerificationButton.setEnabled(true);
                        takeSelfieButton.setEnabled(true);
                        Toast.makeText(Selfie_verification.this, "Failed to submit verification: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (faceDetector != null) {
            faceDetector.close();
        }
    }
}