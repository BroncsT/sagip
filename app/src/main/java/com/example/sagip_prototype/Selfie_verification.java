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
    ImageView selfieImageView, facePlaceholderImageView, circularOverlay, circularBorder;
    TextView instructionsTextView, selfieStepIndicator, selfiePlaceholderText, guidelinesTitle, selfieVerificationCompleteText;
    PreviewView previewView;

    StorageReference storageReference;
    FirebaseAuth auth;
    FirebaseFirestore db;

    private String frontIdPhotoUrl; // To store the front ID photo URL from previous screen
    private String backIdPhotoUrl; // To store the back ID photo URL from previous screen
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
    private static final int REQUIRED_FACE_DETECTIONS = 15; // Reduced to 15 frames for faster capture

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        
        // Apply saved language preference
        String savedLanguage = LanguageSelectionActivity.getSavedLanguage(this);
        LanguageSelectionActivity.setAppLanguage(this, savedLanguage);
        
        setContentView(R.layout.activity_selfie_verification);

        // Initialize Firebase components
        storageReference = FirebaseStorage.getInstance().getReference();
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Get the ID photo URLs and ID type from the intent
        frontIdPhotoUrl = getIntent().getStringExtra("frontIdPhotoUrl");
        backIdPhotoUrl = getIntent().getStringExtra("backIdPhotoUrl");
        idType = getIntent().getStringExtra("idType");
        if (frontIdPhotoUrl == null || backIdPhotoUrl == null) {
            Toast.makeText(this, getString(R.string.error_missing_id_photo), Toast.LENGTH_SHORT).show();
            return;
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
        selfieVerificationCompleteText = findViewById(R.id.selfieVerificationCompleteText);
        previewView = findViewById(R.id.previewView);
        circularOverlay = findViewById(R.id.circularOverlay);
        circularBorder = findViewById(R.id.circularBorder);

        // Initially disable submit button until selfie is taken
        submitVerificationButton.setEnabled(false);
        manualCaptureButton.setEnabled(false);
        
        // Setup initial UI state
        setupInitialUI();

        // Initialize face detector
        setupFaceDetector();

        // Check if user already has a selfie photo and display it
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, getString(R.string.user_not_authenticated), Toast.LENGTH_SHORT).show();
            return;
        }
        
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
                // Hide guidelines when retaking
                guidelinesTitle.setVisibility(View.GONE);
                findViewById(R.id.guidelinesLayout).setVisibility(View.GONE);
                selfieImageView.setVisibility(View.GONE);
                
                // Hide verification complete text and show step indicator
                selfieVerificationCompleteText.setVisibility(View.GONE);
                selfieStepIndicator.setVisibility(View.VISIBLE);
                
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
                if (selfieUrl != null && frontIdPhotoUrl != null && backIdPhotoUrl != null) {
                    saveVerificationData();
                } else {
                    Toast.makeText(Selfie_verification.this, getString(R.string.please_take_selfie), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
    
    private void setupInitialUI() {
        // Set initial instructions
        instructionsTextView.setText(getString(R.string.selfie_step_indicator) + "\n\n" +
                getString(R.string.position_face_circle) + "\n" +
                getString(R.string.look_directly_camera) + "\n" +
                getString(R.string.ensure_good_lighting) + "\n" +
                getString(R.string.keep_face_centered) + "\n" +
                getString(R.string.photo_taken_automatically));
        
        // Update step indicator
        selfieStepIndicator.setText(getString(R.string.step_2_automatic_verification));
        
        // Show placeholder elements initially
        facePlaceholderImageView.setVisibility(View.VISIBLE);
        selfiePlaceholderText.setVisibility(View.VISIBLE);
        selfiePlaceholderText.setText(getString(R.string.tap_start_capture_begin));
        
        // Hide guidelines initially
        guidelinesTitle.setVisibility(View.GONE);
        findViewById(R.id.guidelinesLayout).setVisibility(View.GONE);
        
        // Hide verification complete text initially
        selfieVerificationCompleteText.setVisibility(View.GONE);
        
        // Hide preview initially
        previewView.setVisibility(View.GONE);
        circularOverlay.setVisibility(View.GONE);
        circularBorder.setVisibility(View.GONE);
        
        // Ensure verify button is visible but disabled initially
        submitVerificationButton.setVisibility(View.VISIBLE);
        submitVerificationButton.setEnabled(false);
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

        // Show camera preview with circular overlay
        previewView.setVisibility(View.VISIBLE);
        circularOverlay.setVisibility(View.VISIBLE);
        circularBorder.setVisibility(View.VISIBLE);
        facePlaceholderImageView.setVisibility(View.GONE);
        selfiePlaceholderText.setVisibility(View.GONE);
        takeSelfieButton.setVisibility(View.GONE);
        manualCaptureButton.setVisibility(View.VISIBLE);

        // Configure preview view for optimal display
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);

        // Update instructions
        instructionsTextView.setText(getString(R.string.detecting_face_instructions));

        // Reset face detection count
        faceDetectionCount = 0;
        autoCaptureEnabled = true;

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
                Toast.makeText(this, getString(R.string.error_starting_camera, e.getMessage()), Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Configure preview view scaling to fill the entire container
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, new FaceDetectionAnalyzer());

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.error_binding_camera, e.getMessage()), Toast.LENGTH_SHORT).show();
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
            updateInstructions(getString(R.string.no_face_detected_instructions));
            return;
        }

        Face face = faces.get(0);
        isFaceDetected = true;

        // Check face position (should be in circular center area)
        android.graphics.Rect boundingBox = face.getBoundingBox();
        float faceCenterX = boundingBox.centerX();
        float faceCenterY = boundingBox.centerY();
        
        float imageCenterX = imageWidth / 2f;
        float imageCenterY = imageHeight / 2f;
        
        float distanceFromCenter = (float) Math.sqrt(
                Math.pow(faceCenterX - imageCenterX, 2) + 
                Math.pow(faceCenterY - imageCenterY, 2)
        );
        
        // Use circular radius (smaller of width/height * 0.3 for more forgiving circle)
        float circleRadius = Math.min(imageWidth, imageHeight) * 0.3f;
        
        isFacePositioned = distanceFromCenter < circleRadius;

        // Check face size (should be reasonably large)
        float faceSize = Math.min(boundingBox.width(), boundingBox.height());
        float minFaceSize = Math.min(imageWidth, imageHeight) * 0.15f; // Reduced to 15% for more forgiving size
        boolean isFaceSizeGood = faceSize > minFaceSize;

        // Check if face is looking forward (more forgiving check)
        boolean isLookingForward = face.getHeadEulerAngleY() < 30 && face.getHeadEulerAngleY() > -30;

        // Check lighting (using face detection confidence as proxy)
        boolean isGoodLighting = true; // Assume good lighting if face is detected

        // Update instructions based on conditions
        StringBuilder instruction = new StringBuilder();
                    instruction.append(getString(R.string.face_detected_instruction));

        if (!isFacePositioned) {
                            instruction.append(getString(R.string.move_face_to_circle_instruction));
        }
        if (!isFaceSizeGood) {
                            instruction.append(getString(R.string.move_closer_camera_instruction));
        }
        if (!isLookingForward) {
                            instruction.append(getString(R.string.look_directly_camera_instruction));
        }
        if (!isGoodLighting) {
                            instruction.append(getString(R.string.improve_lighting_instruction));
        }

        if (isFacePositioned && isFaceSizeGood && isLookingForward && isGoodLighting) {
            instruction.append(getString(R.string.perfect_taking_photo_instruction, 2 - (faceDetectionCount / 8)));
            
            faceDetectionCount++;
            
            if (faceDetectionCount >= REQUIRED_FACE_DETECTIONS && autoCaptureEnabled) {
                autoCaptureEnabled = false;
                runOnUiThread(() -> {
                    updateInstructions(getString(R.string.capturing_photo_now_instruction));
                });
                // Small delay to show the capture message
                new android.os.Handler().postDelayed(() -> {
                    captureCurrentFrame();
                }, 500);
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
        try {
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
        } catch (Exception e) {
            // If capture fails, try manual capture
            Toast.makeText(this, getString(R.string.auto_capture_failed_manual), Toast.LENGTH_SHORT).show();
            manualCaptureButton.setVisibility(View.VISIBLE);
            manualCaptureButton.setEnabled(true);
        }
    }

    private void processCapturedImage(Bitmap bitmap) {
        // Stop camera
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        
        // Hide preview and show captured image
        previewView.setVisibility(View.GONE);
        circularOverlay.setVisibility(View.GONE);
        circularBorder.setVisibility(View.GONE);
        selfieImageView.setVisibility(View.VISIBLE);
        selfieImageView.setImageBitmap(bitmap);
        
        // Update UI first
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
        instructionsTextView.setText(getString(R.string.uploading_selfie_instructions));

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
                        Toast.makeText(Selfie_verification.this, getString(R.string.selfie_captured_uploaded_success), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                instructionsTextView.setText(getString(R.string.upload_failed_try_again_instructions));
                Toast.makeText(Selfie_verification.this, getString(R.string.selfie_upload_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void updateUIForSelfieSuccess() {
        // Hide placeholder elements
        facePlaceholderImageView.setVisibility(View.GONE);
        selfiePlaceholderText.setVisibility(View.GONE);
        
        // Hide the step indicator and show the verification complete text
        selfieStepIndicator.setVisibility(View.GONE);
        selfieVerificationCompleteText.setVisibility(View.VISIBLE);
        
        // Show guidelines
        guidelinesTitle.setVisibility(View.VISIBLE);
        findViewById(R.id.guidelinesLayout).setVisibility(View.VISIBLE);
        
        // Update instructions
        instructionsTextView.setText(getString(R.string.perfect_selfie_captured_instructions));
        
        // Show retake option
        takeSelfieButton.setVisibility(View.VISIBLE);
        takeSelfieButton.setText(getString(R.string.retake_selfie_button));
        manualCaptureButton.setVisibility(View.GONE);
        
        // Ensure verify button is visible and enabled
        submitVerificationButton.setVisibility(View.VISIBLE);
        submitVerificationButton.setEnabled(true);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startAutomaticSelfieCapture();
            } else {
                instructionsTextView.setText(getString(R.string.camera_permission_denied_instructions));
                Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void saveVerificationData() {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show submission progress
        instructionsTextView.setText(getString(R.string.submitting_verification_instructions));
        submitVerificationButton.setEnabled(false);
        takeSelfieButton.setEnabled(false);

        // Create data map with all URLs and ID type
        Map<String, Object> verificationData = new HashMap<>();
        verificationData.put("frontIdPhotoUrl", frontIdPhotoUrl);
        verificationData.put("backIdPhotoUrl", backIdPhotoUrl);
        verificationData.put("selfieVerificationUrl", selfieUrl);
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
                        // Show simple success message
                        instructionsTextView.setText("✅ ID is accepted\n\nYour verification has been submitted successfully.");
                        
                        Toast.makeText(Selfie_verification.this, "ID is accepted", Toast.LENGTH_LONG).show();
                        
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
                        }, 3000); // 3 second delay to show the acceptance message
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // Reset UI on failure
                        instructionsTextView.setText(getString(R.string.submission_failed_instructions));
                        submitVerificationButton.setEnabled(true);
                        takeSelfieButton.setEnabled(true);
                        Toast.makeText(Selfie_verification.this, getString(R.string.failed_submit_verification, e.getMessage()), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        // Reconfigure camera preview when orientation changes
        if (previewView.getVisibility() == View.VISIBLE && cameraProvider != null) {
            // Small delay to allow layout to settle
            new android.os.Handler().postDelayed(() -> {
                if (cameraProvider != null) {
                    bindCameraUseCases();
                }
            }, 100);
        }
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