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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.os.Handler;
import android.os.Looper;

public class FaceVerification extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int BLINK_DETECTION_INTERVAL = 100; // Check every 100ms
    private static final int BLINK_THRESHOLD = 30; // Threshold for blink detection
    private static final int FACE_DETECTION_TIMEOUT = 30000; // 30 seconds timeout

    private PreviewView previewView;
    private ImageView circularFrameOverlay;
    private Button captureButton, retakeButton, confirmButton;
    private TextView mainInstruction, tipsTitle, blinkInstruction;
    private ImageView capturedImageView;
    private LinearLayout actionButtonsLayout, instructionsContainer;

    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
    private ExecutorService cameraExecutor;
    private ScheduledExecutorService blinkDetectionExecutor;
    private ProcessCameraProvider cameraProvider;

    private FirebaseAuth auth;
    private FirebaseStorage storage;
    private StorageReference storageReference;
    private FirebaseFirestore db;

    private Bitmap capturedBitmap;
    private String uploadedImageUrl = "";
    
    // Data from previous screen
    private String frontIdPhotoUrl;
    private String backIdPhotoUrl;
    private String idType;
    
    // Blink detection variables
    private boolean isDetectingBlink = false;
    private boolean hasDetectedFace = false;
    private long lastBlinkTime = 0;
    private int consecutiveBlinkFrames = 0;
    private Handler mainHandler;
    private long faceDetectionStartTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        
        // Force portrait orientation
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        
        // Apply saved language preference
        String savedLanguage = LanguageSelectionActivity.getSavedLanguage(this);
        LanguageSelectionActivity.setAppLanguage(this, savedLanguage);
        
        setContentView(R.layout.activity_face_verification);

        // Get data from previous screen
        frontIdPhotoUrl = getIntent().getStringExtra("frontIdPhotoUrl");
        backIdPhotoUrl = getIntent().getStringExtra("backIdPhotoUrl");
        idType = getIntent().getStringExtra("idType");

        // Initialize Firebase
        auth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();
        storageReference = storage.getReference();
        db = FirebaseFirestore.getInstance();

        // Initialize views
        previewView = findViewById(R.id.previewView);
        circularFrameOverlay = findViewById(R.id.circularFrameOverlay);
        captureButton = findViewById(R.id.captureButton);
        retakeButton = findViewById(R.id.retakeButton);
        confirmButton = findViewById(R.id.confirmButton);
        mainInstruction = findViewById(R.id.mainInstruction);
        tipsTitle = findViewById(R.id.tipsTitle);
        blinkInstruction = findViewById(R.id.blinkInstruction);
        capturedImageView = findViewById(R.id.capturedImageView);
        actionButtonsLayout = findViewById(R.id.actionButtonsLayout);
        instructionsContainer = findViewById(R.id.instructionsContainer);

        // Initialize handlers and executors before UI setup
        cameraExecutor = Executors.newSingleThreadExecutor();
        blinkDetectionExecutor = Executors.newScheduledThreadPool(1);
        mainHandler = new Handler(Looper.getMainLooper());

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
    }

    private void setupInitialUI() {
        // Set instructions text
        mainInstruction.setText(getString(R.string.get_ready_to_scan_face));
        tipsTitle.setText(getString(R.string.scan_face_tips));
        blinkInstruction.setText(getString(R.string.blink));
        
        // Initially hide retake and confirm buttons
        actionButtonsLayout.setVisibility(View.GONE);
        capturedImageView.setVisibility(View.GONE);
        
        // Show camera preview and frame overlay
        previewView.setVisibility(View.VISIBLE);
        circularFrameOverlay.setVisibility(View.VISIBLE);
        captureButton.setVisibility(View.GONE); // Hide manual capture button
        instructionsContainer.setVisibility(View.VISIBLE);
        
        // Start automatic blink detection
        startBlinkDetection();
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

        imageCapture = new ImageCapture.Builder()
                .build();

        // Set up ImageAnalysis for blink detection
        imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, new BlinkAnalyzer());

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.error_binding_camera, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void startBlinkDetection() {
        isDetectingBlink = true;
        faceDetectionStartTime = System.currentTimeMillis();
        
        // Update UI to show blink instruction
        mainHandler.post(() -> {
            blinkInstruction.setText(getString(R.string.blink));
            blinkInstruction.setVisibility(View.VISIBLE);
            mainInstruction.setText(getString(R.string.look_at_camera_and_blink));
        });
        
        // Set timeout for face detection
        mainHandler.postDelayed(() -> {
            if (isDetectingBlink && !hasDetectedFace) {
                onFaceDetectionTimeout();
            }
        }, FACE_DETECTION_TIMEOUT);
    }

    // ImageAnalyzer for blink detection
    private class BlinkAnalyzer implements ImageAnalysis.Analyzer {
        @Override
        public void analyze(@NonNull ImageProxy image) {
            if (!isDetectingBlink) {
                image.close();
                return;
            }

            try {
                // Convert ImageProxy to Bitmap
                Bitmap frame = imageProxyToBitmap(image);
                if (frame != null) {
                    // Check if we have a face in the frame
                    if (!hasDetectedFace && hasFaceInFrame(frame)) {
                        hasDetectedFace = true;
                        mainHandler.post(() -> {
                            blinkInstruction.setText(getString(R.string.face_detected_blink_now));
                        });
                    }

                    // Detect blink
                    if (hasDetectedFace) {
                        boolean blinked = detectBlinkInFrame(frame);
                        
                        if (blinked) {
                            consecutiveBlinkFrames++;
                            lastBlinkTime = System.currentTimeMillis();
                            
                            // If we detect enough consecutive blink frames, capture the image
                            if (consecutiveBlinkFrames >= 3) {
                                mainHandler.post(() -> {
                                    blinkInstruction.setText(getString(R.string.blink_detected));
                                    isDetectingBlink = false;
                                    captureImage();
                                });
                            }
                        } else {
                            consecutiveBlinkFrames = 0;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in blink analysis: " + e.getMessage());
            } finally {
                image.close();
            }
        }
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        try {
            android.media.Image mediaImage = image.getImage();
            if (mediaImage == null) return null;

            android.media.Image.Plane[] planes = mediaImage.getPlanes();
            android.media.Image.Plane plane = planes[0];
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int rowPadding = rowStride - pixelStride * image.getWidth();

            Bitmap bitmap = Bitmap.createBitmap(
                image.getWidth() + rowPadding / pixelStride,
                image.getHeight(),
                Bitmap.Config.ARGB_8888
            );
            bitmap.copyPixelsFromBuffer(plane.getBuffer());
            
            if (rowPadding == 0) {
                return bitmap;
            } else {
                return Bitmap.createBitmap(bitmap, 0, 0, image.getWidth(), image.getHeight());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error converting ImageProxy to Bitmap: " + e.getMessage());
            return null;
        }
    }


    private boolean detectBlinkInFrame(Bitmap frame) {
        if (frame == null) return false;
        
        // Simple blink detection based on eye area brightness changes
        // This is a basic implementation - in production, you'd use ML Kit or similar
        
        // Convert to grayscale
        Bitmap grayFrame = convertToGrayscale(frame);
        
        // Focus on the center area where eyes would be
        int width = grayFrame.getWidth();
        int height = grayFrame.getHeight();
        int centerX = width / 2;
        int centerY = height / 2;
        
        // Sample eye area (approximate)
        int eyeAreaSize = Math.min(width, height) / 4;
        int leftEyeX = centerX - eyeAreaSize / 2;
        int rightEyeX = centerX + eyeAreaSize / 2;
        int eyeY = centerY - eyeAreaSize / 4;
        
        // Calculate average brightness in eye areas
        double leftEyeBrightness = calculateAverageBrightness(grayFrame, 
            leftEyeX, eyeY, eyeAreaSize / 2, eyeAreaSize / 4);
        double rightEyeBrightness = calculateAverageBrightness(grayFrame, 
            rightEyeX, eyeY, eyeAreaSize / 2, eyeAreaSize / 4);
        
        // If both eyes are significantly darker (closed), consider it a blink
        double threshold = 50; // Adjust based on testing
        return leftEyeBrightness < threshold && rightEyeBrightness < threshold;
    }

    private boolean hasFaceInFrame(Bitmap frame) {
        if (frame == null) return false;
        
        // Simple face detection based on skin tone detection
        // This is a basic implementation - in production, use ML Kit Face Detection
        
        int width = frame.getWidth();
        int height = frame.getHeight();
        int skinPixels = 0;
        int totalPixels = 0;
        
        // Sample the center area
        int centerX = width / 2;
        int centerY = height / 2;
        int sampleSize = Math.min(width, height) / 2;
        
        for (int y = centerY - sampleSize / 2; y < centerY + sampleSize / 2; y += 5) {
            for (int x = centerX - sampleSize / 2; x < centerX + sampleSize / 2; x += 5) {
                if (x >= 0 && x < width && y >= 0 && y < height) {
                    int pixel = frame.getPixel(x, y);
                    int r = Color.red(pixel);
                    int g = Color.green(pixel);
                    int b = Color.blue(pixel);
                    
                    // Simple skin tone detection
                    if (isSkinTone(r, g, b)) {
                        skinPixels++;
                    }
                    totalPixels++;
                }
            }
        }
        
        // If more than 30% of pixels are skin tone, consider it a face
        return totalPixels > 0 && (double) skinPixels / totalPixels > 0.3;
    }

    private boolean isSkinTone(int r, int g, int b) {
        // Simple skin tone detection based on RGB ratios
        return r > 95 && g > 40 && b > 20 && 
               Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) > 15 &&
               Math.abs(r - g) > 15 && r > g && r > b;
    }

    private Bitmap convertToGrayscale(Bitmap original) {
        int width = original.getWidth();
        int height = original.getHeight();
        
        Bitmap grayBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(grayBitmap);
        
        Paint paint = new Paint();
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0);
        paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
        canvas.drawBitmap(original, 0, 0, paint);
        
        return grayBitmap;
    }

    private double calculateAverageBrightness(Bitmap bitmap, int x, int y, int width, int height) {
        int totalBrightness = 0;
        int pixelCount = 0;
        
        for (int i = y; i < y + height && i < bitmap.getHeight(); i++) {
            for (int j = x; j < x + width && j < bitmap.getWidth(); j++) {
                int pixel = bitmap.getPixel(j, i);
                int gray = Color.red(pixel); // Already grayscale
                totalBrightness += gray;
                pixelCount++;
            }
        }
        
        return pixelCount > 0 ? (double) totalBrightness / pixelCount : 0;
    }

    private void onFaceDetectionTimeout() {
        if (isDetectingBlink) {
            isDetectingBlink = false;
            mainHandler.post(() -> {
                Toast.makeText(this, getString(R.string.face_detection_timeout), Toast.LENGTH_LONG).show();
                // Show manual capture option
                captureButton.setVisibility(View.VISIBLE);
                blinkInstruction.setText(getString(R.string.manual_capture_available));
            });
        }
    }

    private void captureImage() {
        if (imageCapture == null) {
            Toast.makeText(this, getString(R.string.camera_not_ready), Toast.LENGTH_SHORT).show();
            return;
        }

        // Show loading state
        captureButton.setEnabled(false);

        // Create a temporary file for the image
        File photoFile = new File(getExternalCacheDir(), "face_capture_" + System.currentTimeMillis() + ".jpg");

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
                
                // Crop the image to the circular frame area
                capturedBitmap = cropToCircularFrame(capturedBitmap);
                
                // Show captured image
                showCapturedImage();
                uploadImage();
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Toast.makeText(FaceVerification.this, getString(R.string.failed_capture_image, exception.getMessage()), Toast.LENGTH_SHORT).show();
                captureButton.setEnabled(true);
            }
        });
    }

    private void showCapturedImage() {
        // Stop blink detection
        isDetectingBlink = false;
        
        // Hide camera preview and frame overlay
        previewView.setVisibility(View.GONE);
        circularFrameOverlay.setVisibility(View.GONE);
        captureButton.setVisibility(View.GONE);
        instructionsContainer.setVisibility(View.GONE);
        blinkInstruction.setVisibility(View.GONE);

        // Show captured image and action buttons
        capturedImageView.setVisibility(View.VISIBLE);
        capturedImageView.setImageBitmap(capturedBitmap);
        actionButtonsLayout.setVisibility(View.VISIBLE);

        // Update instructions
        mainInstruction.setText(getString(R.string.review_captured_image));
        mainInstruction.setVisibility(View.VISIBLE);
    }

    private void retakePhoto() {
        // Reset UI to camera mode
        previewView.setVisibility(View.VISIBLE);
        circularFrameOverlay.setVisibility(View.VISIBLE);
        captureButton.setVisibility(View.GONE); // Keep manual capture hidden
        captureButton.setEnabled(true);
        instructionsContainer.setVisibility(View.VISIBLE);
        blinkInstruction.setVisibility(View.VISIBLE);

        capturedImageView.setVisibility(View.GONE);
        actionButtonsLayout.setVisibility(View.GONE);

        // Reset captured data
        capturedBitmap = null;
        uploadedImageUrl = "";
        hasDetectedFace = false;
        consecutiveBlinkFrames = 0;

        // Restart blink detection
        startBlinkDetection();
    }

    private Bitmap cropToCircularFrame(Bitmap originalBitmap) {
        if (originalBitmap == null) return null;
        
        // Get the screen dimensions and calculate the circular frame area
        // The circular frame is 300dp x 300dp centered
        float density = getResources().getDisplayMetrics().density;
        int frameSize = (int) (300 * density);
        
        // Get the preview view dimensions
        int previewWidth = previewView.getWidth();
        int previewHeight = previewView.getHeight();
        
        if (previewWidth == 0 || previewHeight == 0) {
            // Fallback to screen dimensions if preview view not ready
            previewWidth = getResources().getDisplayMetrics().widthPixels;
            previewHeight = getResources().getDisplayMetrics().heightPixels;
        }
        
        // Calculate the circular frame center and radius
        int centerX = previewWidth / 2;
        int centerY = previewHeight / 2;
        int radius = frameSize / 2;
        
        // Calculate the crop area (square that contains the circle)
        int cropLeft = centerX - radius;
        int cropTop = centerY - radius;
        int cropSize = frameSize;
        
        // Scale the crop coordinates to match the actual image dimensions
        float scaleX = (float) originalBitmap.getWidth() / previewWidth;
        float scaleY = (float) originalBitmap.getHeight() / previewHeight;
        
        int cropX = (int) (cropLeft * scaleX);
        int cropY = (int) (cropTop * scaleY);
        int cropW = (int) (cropSize * scaleX);
        int cropH = (int) (cropSize * scaleY);
        
        // Ensure crop area is within image bounds
        cropX = Math.max(0, cropX);
        cropY = Math.max(0, cropY);
        cropW = Math.min(cropW, originalBitmap.getWidth() - cropX);
        cropH = Math.min(cropH, originalBitmap.getHeight() - cropY);
        
        // Crop the bitmap
        if (cropW > 0 && cropH > 0) {
            return Bitmap.createBitmap(originalBitmap, cropX, cropY, cropW, cropH);
        }
        
        return originalBitmap; // Return original if cropping fails
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
        Bitmap resizedBitmap = resizeBitmap(capturedBitmap, 800, 800);

        // Convert bitmap to byte array
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos);
        byte[] data = baos.toByteArray();

        // Create storage reference
        StorageReference reference = storageReference.child("users/" + auth.getUid() + "/face_verification");

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
                        Toast.makeText(FaceVerification.this, getString(R.string.image_uploaded_successfully), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(FaceVerification.this, getString(R.string.upload_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
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

        // Submit verification data to Firestore
        submitVerificationData();
    }

    private void submitVerificationData() {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, getString(R.string.user_not_authenticated), Toast.LENGTH_SHORT).show();
            return;
        }

        // Show submission progress
        confirmButton.setEnabled(false);
        confirmButton.setText(getString(R.string.uploading));

        // Create data map with all URLs and ID type
        Map<String, Object> verificationData = new HashMap<>();
        verificationData.put("frontIdPhotoUrl", frontIdPhotoUrl);
        verificationData.put("backIdPhotoUrl", backIdPhotoUrl);
        verificationData.put("faceVerificationUrl", uploadedImageUrl);
        verificationData.put("idType", idType);
        verificationData.put("verificationSubmittedAt", System.currentTimeMillis());
        verificationData.put("status", "pending");

        String userType = "seniors"; // replace with your actual user type variable
        String uid = auth.getUid();

        // Update Firestore with all verification data
        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .update(verificationData)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        // Show success message
                        Toast.makeText(FaceVerification.this, getString(R.string.verification_submitted_success_toast), Toast.LENGTH_LONG).show();
                        
                        // Navigate back to main activity
                        Intent intent = new Intent(FaceVerification.this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // Reset UI on failure
                        confirmButton.setEnabled(true);
                        confirmButton.setText(getString(R.string.confirm));
                        Toast.makeText(FaceVerification.this, getString(R.string.failed_submit_verification, e.getMessage()), Toast.LENGTH_SHORT).show();
                    }
                });
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
                Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isDetectingBlink = false;
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (blinkDetectionExecutor != null) {
            blinkDetectionExecutor.shutdown();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ensure portrait orientation is maintained
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }
}
