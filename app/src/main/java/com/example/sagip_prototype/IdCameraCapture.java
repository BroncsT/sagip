package com.example.sagip_prototype;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
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
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IdCameraCapture extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 100;

    private PreviewView previewView;
    private ImageView frameOverlay;
    private Button captureButton, retakeButton, confirmButton;
    private TextView instructionsTextView;
    private ImageView capturedImageView;

    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;

    private FirebaseAuth auth;
    private FirebaseStorage storage;
    private StorageReference storageReference;

    private boolean isFrontSide;
    private String idType;
    private Bitmap capturedBitmap;
    private String uploadedImageUrl = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        
        // Apply saved language preference
        String savedLanguage = LanguageSelectionActivity.getSavedLanguage(this);
        LanguageSelectionActivity.setAppLanguage(this, savedLanguage);
        
        setContentView(R.layout.activity_id_camera_capture);

        // Get intent data
        isFrontSide = getIntent().getBooleanExtra("isFrontSide", true);
        idType = getIntent().getStringExtra("idType");

        // Initialize Firebase
        auth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();
        storageReference = storage.getReference();

        // Initialize views
        previewView = findViewById(R.id.previewView);
        frameOverlay = findViewById(R.id.frameOverlay);
        captureButton = findViewById(R.id.captureButton);
        retakeButton = findViewById(R.id.retakeButton);
        confirmButton = findViewById(R.id.confirmButton);
        instructionsTextView = findViewById(R.id.instructionsTextView);
        capturedImageView = findViewById(R.id.capturedImageView);

        // Set initial UI state
        setupInitialUI();

        // Set click listeners
        captureButton.setOnClickListener(v -> captureImage());
        retakeButton.setOnClickListener(v -> retakePhoto());
        confirmButton.setOnClickListener(v -> confirmAndReturn());

        // Start camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void setupInitialUI() {
        String sideText = isFrontSide ? getString(R.string.position_front_id) : getString(R.string.position_back_id);
        instructionsTextView.setText(sideText + "\n\n" + getString(R.string.make_sure_visible));
        
        // Initially hide retake and confirm buttons
        retakeButton.setVisibility(View.GONE);
        confirmButton.setVisibility(View.GONE);
        capturedImageView.setVisibility(View.GONE);
        
        // Show camera preview and frame overlay
        previewView.setVisibility(View.VISIBLE);
        frameOverlay.setVisibility(View.VISIBLE);
        captureButton.setVisibility(View.VISIBLE);
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
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .build();

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
        } catch (Exception e) {
            Toast.makeText(this, "Error binding camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void captureImage() {
        if (imageCapture == null) {
            Toast.makeText(this, "Camera not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show loading state
        captureButton.setEnabled(false);
        captureButton.setText(getString(R.string.capturing));

        // Create a temporary file for the image
        File photoFile = new File(getExternalCacheDir(), "id_capture_" + System.currentTimeMillis() + ".jpg");

        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                // Load the saved image as bitmap
                capturedBitmap = android.graphics.BitmapFactory.decodeFile(photoFile.getAbsolutePath());
                
                // Update UI to show captured image
                showCapturedImage();
                
                // Upload the image
                uploadImage();
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Toast.makeText(IdCameraCapture.this, getString(R.string.failed_capture) + ": " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                captureButton.setEnabled(true);
                captureButton.setText(getString(R.string.capture));
            }
        });
    }



    private void showCapturedImage() {
        // Hide camera preview and frame overlay
        previewView.setVisibility(View.GONE);
        frameOverlay.setVisibility(View.GONE);
        captureButton.setVisibility(View.GONE);

        // Show captured image and action buttons
        capturedImageView.setVisibility(View.VISIBLE);
        capturedImageView.setImageBitmap(capturedBitmap);
        retakeButton.setVisibility(View.VISIBLE);
        confirmButton.setVisibility(View.VISIBLE);

        // Update instructions
        instructionsTextView.setText(getString(R.string.review_captured_image) + "\n\n" + getString(R.string.tap_confirm_retake));
    }

    private void retakePhoto() {
        // Reset UI to camera mode
        previewView.setVisibility(View.VISIBLE);
        frameOverlay.setVisibility(View.VISIBLE);
        captureButton.setVisibility(View.VISIBLE);
        captureButton.setEnabled(true);
        captureButton.setText(getString(R.string.capture));

        capturedImageView.setVisibility(View.GONE);
        retakeButton.setVisibility(View.GONE);
        confirmButton.setVisibility(View.GONE);

        // Reset captured data
        capturedBitmap = null;
        uploadedImageUrl = "";

        // Update instructions
        String sideText = isFrontSide ? getString(R.string.position_front_id) : getString(R.string.position_back_id);
        instructionsTextView.setText(sideText + "\n\n" + getString(R.string.make_sure_visible));
    }

    private void uploadImage() {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, getString(R.string.user_not_authenticated), Toast.LENGTH_SHORT).show();
            return;
        }

        if (capturedBitmap == null) {
            Toast.makeText(this, getString(R.string.error), Toast.LENGTH_SHORT).show();
            return;
        }

        // Show uploading state
        confirmButton.setEnabled(false);
        confirmButton.setText(getString(R.string.uploading));

        // Resize bitmap to optimize storage
        Bitmap resizedBitmap = resizeBitmap(capturedBitmap, 1200, 800);

        // Convert bitmap to byte array
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos);
        byte[] data = baos.toByteArray();

        // Create storage reference
        String path = isFrontSide ? "id_photos_front" : "id_photos_back";
        StorageReference reference = storageReference.child("users/" + auth.getUid() + "/" + path);

        // Upload to Firebase Storage
        reference.putBytes(data).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                reference.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                    @Override
                    public void onSuccess(Uri uri) {
                        uploadedImageUrl = uri.toString();
                        confirmButton.setEnabled(true);
                        confirmButton.setText(getString(R.string.confirm));
                        Toast.makeText(IdCameraCapture.this, getString(R.string.image_uploaded_successfully), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(IdCameraCapture.this, getString(R.string.upload_failed) + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
                confirmButton.setEnabled(true);
                confirmButton.setText(getString(R.string.confirm));
            }
        });
    }

    private void confirmAndReturn() {
        if (uploadedImageUrl.isEmpty()) {
            Toast.makeText(this, getString(R.string.please_wait_upload), Toast.LENGTH_SHORT).show();
            return;
        }

        // Return the result to the calling activity
        Intent resultIntent = new Intent();
        resultIntent.putExtra("imageUrl", uploadedImageUrl);
        resultIntent.putExtra("isFrontSide", isFrontSide);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private Bitmap resizeBitmap(Bitmap bitmap, int maxWidth, int maxHeight) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        float scaleWidth = ((float) maxWidth) / width;
        float scaleHeight = ((float) maxHeight) / height;
        float scale = Math.min(scaleWidth, scaleHeight);
        
        if (scale < 1) {
            int newWidth = Math.round(width * scale);
            int newHeight = Math.round(height * scale);
            return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
        }
        
        return bitmap;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, getString(R.string.camera_permission_required_verification), Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}
