package com.example.sagip_prototype;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Canvas;
import android.graphics.Color;
import android.app.AlertDialog;

public class IdCameraCapture extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 100;

    private PreviewView previewView;
    private ImageView frameOverlay;
    private Button captureButton, retakeButton, confirmButton;
    private TextView instructionsTextView, additionalInstructionsTextView;
    private ImageView capturedImageView;
    private LinearLayout actionButtonsLayout;

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
        
        // Force landscape orientation for ID cards (horizontal display)
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        
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
        previewView.setScaleType(androidx.camera.view.PreviewView.ScaleType.FILL_CENTER);
        previewView.setImplementationMode(androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE);
        frameOverlay = findViewById(R.id.frameOverlay);
        captureButton = findViewById(R.id.captureButton);
        retakeButton = findViewById(R.id.retakeButton);
        confirmButton = findViewById(R.id.confirmButton);
        instructionsTextView = findViewById(R.id.instructionsTextView);
        additionalInstructionsTextView = findViewById(R.id.additionalInstructionsTextView);
        capturedImageView = findViewById(R.id.capturedImageView);
        actionButtonsLayout = findViewById(R.id.actionButtonsLayout);
        
        // Set header title based on ID side
        TextView headerTitle = findViewById(R.id.headerTitle);
        String sideText = isFrontSide ? getString(R.string.front_of_id) : getString(R.string.back_of_id);
        headerTitle.setText(sideText);

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
        // Set instructions text
        instructionsTextView.setText(getString(R.string.id_capture_instructions));
        additionalInstructionsTextView.setText(getString(R.string.id_capture_additional_instructions));
        
        // Initially hide retake and confirm buttons
        actionButtonsLayout.setVisibility(View.GONE);
        capturedImageView.setVisibility(View.GONE);
        
        // Show camera preview (no frame overlay)
        previewView.setVisibility(View.VISIBLE);
        frameOverlay.setVisibility(View.GONE);
        captureButton.setVisibility(View.VISIBLE);
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
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        // Set target rotation for landscape
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        
        Preview preview = new Preview.Builder()
                .setTargetRotation(rotation)
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setTargetRotation(rotation)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build();

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.error_binding_camera, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void captureImage() {
        if (imageCapture == null) {
            Toast.makeText(this, getString(R.string.camera_not_ready), Toast.LENGTH_SHORT).show();
            return;
        }

        // Show loading state
        captureButton.setEnabled(false);
        // Note: Circular button doesn't show text, so we'll just disable it

        // Create a temporary file for the image
        File photoFile = new File(getExternalCacheDir(), "id_capture_" + System.currentTimeMillis() + ".jpg");

        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                // Load the saved image as bitmap and correct orientation
                capturedBitmap = android.graphics.BitmapFactory.decodeFile(photoFile.getAbsolutePath());
                try {
                    androidx.exifinterface.media.ExifInterface exif = new androidx.exifinterface.media.ExifInterface(photoFile.getAbsolutePath());
                    int orientation = exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL);
                    if (orientation == androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 ||
                            orientation == androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 ||
                            orientation == androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270) {
                        android.graphics.Matrix matrix = new android.graphics.Matrix();
                        switch (orientation) {
                            case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90:
                                matrix.postRotate(90);
                                break;
                            case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180:
                                matrix.postRotate(180);
                                break;
                            case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270:
                                matrix.postRotate(270);
                                break;
                        }
                        capturedBitmap = android.graphics.Bitmap.createBitmap(capturedBitmap, 0, 0, capturedBitmap.getWidth(), capturedBitmap.getHeight(), matrix, true);
                    }
                } catch (Exception ignore) {}
                
                // Capture full image without cropping
                
                // Show captured image immediately for faster user experience
                showCapturedImage();
                
                // Image captured successfully - no quality validation needed
                
                // Upload image
                uploadImage();
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Toast.makeText(IdCameraCapture.this, getString(R.string.failed_capture_image, exception.getMessage()), Toast.LENGTH_SHORT).show();
                captureButton.setEnabled(true);
            }
        });
    }



    private void showCapturedImage() {
        // Hide camera preview (overlay already hidden)
        previewView.setVisibility(View.GONE);
        captureButton.setVisibility(View.GONE);

        // Show captured image and action buttons
        capturedImageView.setVisibility(View.VISIBLE);
        capturedImageView.setImageBitmap(capturedBitmap);
        actionButtonsLayout.setVisibility(View.VISIBLE);

        // Update instructions
        instructionsTextView.setText(getString(R.string.review_captured_image));
        additionalInstructionsTextView.setText(getString(R.string.review_instructions));
    }

    private void retakePhoto() {
        // Reset UI to camera mode
        previewView.setVisibility(View.VISIBLE);
        frameOverlay.setVisibility(View.GONE);
        captureButton.setVisibility(View.VISIBLE);
        captureButton.setEnabled(true);

        capturedImageView.setVisibility(View.GONE);
        actionButtonsLayout.setVisibility(View.GONE);

        // Reset captured data
        capturedBitmap = null;
        uploadedImageUrl = "";

        // Update instructions
        instructionsTextView.setText(getString(R.string.id_capture_instructions));
        additionalInstructionsTextView.setText(getString(R.string.id_capture_additional_instructions));
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

        // Use original image without resizing
        Bitmap resizedBitmap = capturedBitmap;

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
                Toast.makeText(IdCameraCapture.this, getString(R.string.upload_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                confirmButton.setEnabled(true);
                confirmButton.setText(getString(R.string.confirm));
            }
        });
    }

    private void confirmAndReturn() {
        if (uploadedImageUrl.isEmpty()) {
            Toast.makeText(this, getString(R.string.wait_upload_complete), Toast.LENGTH_SHORT).show();
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

    /**
     * Validates the captured image for quality and ID presence
     * @param bitmap The captured image bitmap
     * @return true if image is valid, false if retake is needed
     */
    private boolean validateImageQuality(Bitmap bitmap) {
        if (bitmap == null) return false;
        
        // Check for blur using Laplacian variance
        double blurScore = calculateBlurScore(bitmap);
        Log.d(TAG, "Blur score: " + blurScore);
        
        // Check for ID-like content (text detection approximation)
        boolean hasTextContent = detectTextContent(bitmap);
        Log.d(TAG, "Has text content: " + hasTextContent);
        
        // Check image brightness and contrast
        boolean hasGoodContrast = checkImageContrast(bitmap);
        Log.d(TAG, "Has good contrast: " + hasGoodContrast);
        
        // Validation criteria
        boolean isNotBlurry = blurScore > 100; // Threshold for sharpness
        boolean hasContent = hasTextContent || hasGoodContrast;
        
        Log.d(TAG, "Image validation - Blurry: " + !isNotBlurry + ", Has content: " + hasContent);
        
        return isNotBlurry && hasContent;
    }

    /**
     * Calculates blur score using Laplacian variance
     * Higher values indicate sharper images
     */
    private double calculateBlurScore(Bitmap bitmap) {
        // Convert to grayscale for analysis
        Bitmap grayBitmap = convertToGrayscale(bitmap);
        
        int width = grayBitmap.getWidth();
        int height = grayBitmap.getHeight();
        
        // Sample a much smaller area for faster performance
        int sampleWidth = Math.min(width, 100);
        int sampleHeight = Math.min(height, 100);
        
        // Calculate Laplacian variance
        double variance = 0;
        double mean = 0;
        
        // First pass: calculate mean
        for (int y = 1; y < sampleHeight - 1; y++) {
            for (int x = 1; x < sampleWidth - 1; x++) {
                int pixel = grayBitmap.getPixel(x, y);
                int gray = Color.red(pixel); // Already grayscale
                mean += gray;
            }
        }
        mean /= ((sampleHeight - 2) * (sampleWidth - 2));
        
        // Second pass: calculate variance
        for (int y = 1; y < sampleHeight - 1; y++) {
            for (int x = 1; x < sampleWidth - 1; x++) {
                int pixel = grayBitmap.getPixel(x, y);
                int gray = Color.red(pixel);
                
                // Laplacian kernel: [[0,-1,0],[-1,4,-1],[0,-1,0]]
                double laplacian = 4 * gray - 
                    Color.red(grayBitmap.getPixel(x, y-1)) -
                    Color.red(grayBitmap.getPixel(x, y+1)) -
                    Color.red(grayBitmap.getPixel(x-1, y)) -
                    Color.red(grayBitmap.getPixel(x+1, y));
                
                variance += Math.pow(laplacian - mean, 2);
            }
        }
        variance /= ((sampleHeight - 2) * (sampleWidth - 2));
        
        return variance;
    }

    /**
     * Converts bitmap to grayscale
     */
    private Bitmap convertToGrayscale(Bitmap original) {
        // Use a smaller bitmap for faster processing
        int width = Math.min(original.getWidth(), 200);
        int height = Math.min(original.getHeight(), 200);
        Bitmap resized = Bitmap.createScaledBitmap(original, width, height, true);
        
        Bitmap grayBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(grayBitmap);
        
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0);
        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
        
        Paint paint = new Paint();
        paint.setColorFilter(filter);
        canvas.drawBitmap(resized, 0, 0, paint);
        
        return grayBitmap;
    }

    /**
     * Detects if image contains text-like content (approximation)
     */
    private boolean detectTextContent(Bitmap bitmap) {
        // Convert to grayscale
        Bitmap grayBitmap = convertToGrayscale(bitmap);
        
        int width = grayBitmap.getWidth();
        int height = grayBitmap.getHeight();
        
        // Sample the center area where ID text would be
        int centerX = width / 2;
        int centerY = height / 2;
        int sampleWidth = width / 3;
        int sampleHeight = height / 3;
        
        int startX = Math.max(0, centerX - sampleWidth / 2);
        int startY = Math.max(0, centerY - sampleHeight / 2);
        int endX = Math.min(width, startX + sampleWidth);
        int endY = Math.min(height, startY + sampleHeight);
        
        // Calculate edge density (text creates many edges)
        int edgeCount = 0;
        int totalPixels = 0;
        
        for (int y = startY + 1; y < endY - 1; y++) {
            for (int x = startX + 1; x < endX - 1; x++) {
                int pixel = grayBitmap.getPixel(x, y);
                int gray = Color.red(pixel);
                
                // Simple edge detection
                int leftPixel = Color.red(grayBitmap.getPixel(x-1, y));
                int rightPixel = Color.red(grayBitmap.getPixel(x+1, y));
                int topPixel = Color.red(grayBitmap.getPixel(x, y-1));
                int bottomPixel = Color.red(grayBitmap.getPixel(x, y+1));
                
                int horizontalDiff = Math.abs(gray - leftPixel) + Math.abs(gray - rightPixel);
                int verticalDiff = Math.abs(gray - topPixel) + Math.abs(gray - bottomPixel);
                
                if (horizontalDiff > 30 || verticalDiff > 30) {
                    edgeCount++;
                }
                totalPixels++;
            }
        }
        
        double edgeDensity = (double) edgeCount / totalPixels;
        Log.d(TAG, "Edge density: " + edgeDensity);
        
        return edgeDensity > 0.1; // Threshold for text content
    }

    /**
     * Checks if image has good contrast (not too dark or too bright)
     */
    private boolean checkImageContrast(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        // Sample the image
        int sampleSize = 10;
        int totalPixels = 0;
        int totalBrightness = 0;
        
        for (int y = 0; y < height; y += sampleSize) {
            for (int x = 0; x < width; x += sampleSize) {
                int pixel = bitmap.getPixel(x, y);
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);
                
                // Calculate brightness
                int brightness = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                totalBrightness += brightness;
                totalPixels++;
            }
        }
        
        double averageBrightness = (double) totalBrightness / totalPixels;
        Log.d(TAG, "Average brightness: " + averageBrightness);
        
        // Good contrast range: not too dark (below 50) or too bright (above 200)
        return averageBrightness > 50 && averageBrightness < 200;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_LONG).show();
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

    @Override
    protected void onResume() {
        super.onResume();
        // Ensure landscape orientation is maintained
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }
}
