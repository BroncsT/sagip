package com.example.sagip_prototype;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class Verification_Page extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int GALLERY_REQUEST_CODE_FRONT = 1000;
    private static final int CAMERA_REQUEST_CODE_FRONT = 1001;
    private static final int GALLERY_REQUEST_CODE_BACK = 1002;
    private static final int CAMERA_REQUEST_CODE_BACK = 1003;
    private static final int ID_CAMERA_CAPTURE_REQUEST = 2000;
    private static final int ID_CROP_REQUEST = 3000;

    Button uploadIdPhotoButton, captureIdPhotoButton, nextButton;
    TextView frontIdPhotoImageView, backIdPhotoImageView;
    TextView frontIdPlaceholderText, backIdPlaceholderText;
    AutoCompleteTextView idTypeDropdown;

    StorageReference storageReference;

    FirebaseAuth auth;
    FirebaseFirestore db;
    
    private String selectedIdType = "";
    private String frontImageUrl = "";
    private String backImageUrl = "";
    private String frontCroppedImagePath = "";
    private String backCroppedImagePath = "";
    private boolean isFrontImageSelected = false;
    private boolean isBackImageSelected = false;
    private boolean pendingIsFront = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        
        // Apply saved language preference
        String savedLanguage = LanguageSelectionActivity.getSavedLanguage(this);
        LanguageSelectionActivity.setAppLanguage(this, savedLanguage);
        
        setContentView(R.layout.activity_verification_page);

        // Initialize Firebase components
        storageReference = FirebaseStorage.getInstance().getReference();
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        nextButton = findViewById(R.id.nextButton);
        frontIdPhotoImageView = findViewById(R.id.frontIdPhotoImageView);
        backIdPhotoImageView = findViewById(R.id.backIdPhotoImageView);
        frontIdPlaceholderText = findViewById(R.id.frontIdPlaceholderText);
        backIdPlaceholderText = findViewById(R.id.backIdPlaceholderText);
        idTypeDropdown = findViewById(R.id.idTypeDropdown);
        
        // Setup ID type dropdown
        setupIdTypeDropdown();

        // Check if user already has ID photos and display them
        if (auth.getCurrentUser() != null) {
            loadExistingPhotos();
        }

        // Set click listeners for front ID photo
        frontIdPhotoImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pendingIsFront = true;
                if (ContextCompat.checkSelfPermission(Verification_Page.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(Verification_Page.this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
                    return;
                }
                openCamera(true);
            }
        });

        // Set click listeners for back ID photo
        backIdPhotoImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pendingIsFront = false;
                if (ContextCompat.checkSelfPermission(Verification_Page.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(Verification_Page.this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
                    return;
                }
                openCamera(false);
            }
        });

        // Hide legacy buttons (upload from gallery / capture with camera)
        if (uploadIdPhotoButton != null) uploadIdPhotoButton.setVisibility(View.GONE);
        if (captureIdPhotoButton != null) captureIdPhotoButton.setVisibility(View.GONE);
        
        // Set click listener for next button
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                        if (selectedIdType.isEmpty()) {
            Toast.makeText(Verification_Page.this, getString(R.string.please_select_id_type), Toast.LENGTH_SHORT).show();
            return;
        }
                
                if (frontImageUrl.isEmpty()) {
                    Toast.makeText(Verification_Page.this, getString(R.string.please_upload_front), Toast.LENGTH_SHORT).show();
                    return;
                }
                
                if (backImageUrl.isEmpty()) {
                    Toast.makeText(Verification_Page.this, getString(R.string.please_upload_back), Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Navigate to selfie verification with both image URLs
                Intent intent = new Intent(Verification_Page.this, Selfie_verification.class);
                intent.putExtra("frontIdPhotoUrl", frontImageUrl);
                intent.putExtra("backIdPhotoUrl", backImageUrl);
                intent.putExtra("idType", selectedIdType);
                startActivity(intent);
                finish();
            }
        });
    }

    // Removed image source dialog and gallery path per requirements

    private void openCamera(boolean isFront) {
        try {
            Intent intent = new Intent(this, IdCameraCapture.class);
            intent.putExtra("isFrontSide", isFront);
            intent.putExtra("idType", selectedIdType);
            startActivityForResult(intent, ID_CAMERA_CAPTURE_REQUEST);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.error_opening_camera) + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // Removed gallery open method

    private void loadExistingPhotos() {
        // Load front photo
        StorageReference frontReference = storageReference.child("users/" + auth.getUid() + "/id_photos_front");
        frontReference.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
            @Override
            public void onSuccess(Uri uri) {
                // Display file name instead of image
                String fileName = "Front ID Photo";
                frontIdPhotoImageView.setText(fileName);
                frontImageUrl = uri.toString();
                frontIdPlaceholderText.setVisibility(View.GONE);
                isFrontImageSelected = true;
                checkNextButtonState();
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                // No front image exists yet
            }
        });

        // Load back photo
        StorageReference backReference = storageReference.child("users/" + auth.getUid() + "/id_photos_back");
        backReference.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
            @Override
            public void onSuccess(Uri uri) {
                // Display file name instead of image
                String fileName = "Back ID Photo";
                backIdPhotoImageView.setText(fileName);
                backImageUrl = uri.toString();
                backIdPlaceholderText.setVisibility(View.GONE);
                isBackImageSelected = true;
                checkNextButtonState();
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                // No back image exists yet
            }
        });
    }
    
    private void setupIdTypeDropdown() {
        String[] idTypes = {
            getString(R.string.philippine_passport),
            getString(R.string.drivers_license),
            getString(R.string.sss_id),
            getString(R.string.gsis_id),
            getString(R.string.philhealth_id),
            getString(R.string.voters_id),
            getString(R.string.senior_citizen_id),
            getString(R.string.umid),
            getString(R.string.postal_id),
            getString(R.string.nbi_clearance),
            getString(R.string.police_clearance),
            getString(R.string.barangay_id),
            getString(R.string.school_id),
            getString(R.string.company_id),
            getString(R.string.other_government_id)
                // nbi, police, student,company maybe not included
        };
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, idTypes);
        idTypeDropdown.setAdapter(adapter);
        
        idTypeDropdown.setOnItemClickListener((parent, view, position, id) -> {
            selectedIdType = idTypes[position];
            checkNextButtonState();
        });
    }

    private void checkNextButtonState() {
        boolean canProceed = !selectedIdType.isEmpty() && isFrontImageSelected && isBackImageSelected;
        nextButton.setEnabled(canProceed);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == ID_CAMERA_CAPTURE_REQUEST) {
                String imageUrl = data.getStringExtra("imageUrl");
                boolean isFront = data.getBooleanExtra("isFrontSide", true);
                
                if (imageUrl != null) {
                    // Start cropping process
                    startImageCropping(imageUrl, isFront);
                }
            } else if (requestCode == ID_CROP_REQUEST) {
                if (resultCode == RESULT_OK) {
                    handleCropResult(data);
                } else {
                    // Cropping was cancelled due to poor image quality
                    Toast.makeText(this, "Please retake the photo with better quality", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
    
    private void startImageCropping(String imagePath, boolean isFront) {
        try {
            Intent cropIntent = new Intent(this, IdCropActivity.class);
            cropIntent.putExtra("sourceImagePath", imagePath);
            cropIntent.putExtra("isFrontSide", isFront);
            cropIntent.putExtra("idType", selectedIdType);
            startActivityForResult(cropIntent, ID_CROP_REQUEST);
        } catch (Exception e) {
            Toast.makeText(this, "Error starting crop: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void handleCropResult(Intent data) {
        String croppedImagePath = data.getStringExtra("croppedImagePath");
        String croppedImageUri = data.getStringExtra("croppedImageUri");
        boolean isFront = data.getBooleanExtra("isFrontSide", true);
        
        if (croppedImagePath != null) {
            if (isFront) {
                frontImageUrl = croppedImageUri;
                frontCroppedImagePath = croppedImagePath;
                // Display file name instead of image
                String fileName = "Front ID Photo";
                frontIdPhotoImageView.setText(fileName);
                frontIdPlaceholderText.setVisibility(View.GONE);
                isFrontImageSelected = true;
                Toast.makeText(this, "Front ID photo cropped successfully", Toast.LENGTH_SHORT).show();
            } else {
                backImageUrl = croppedImageUri;
                backCroppedImagePath = croppedImagePath;
                // Display file name instead of image
                String fileName = "Back ID Photo";
                backIdPhotoImageView.setText(fileName);
                backIdPlaceholderText.setVisibility(View.GONE);
                isBackImageSelected = true;
                Toast.makeText(this, "Back ID photo cropped successfully", Toast.LENGTH_SHORT).show();
            }
            checkNextButtonState();
        } else {
            Toast.makeText(this, "Failed to crop image", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera(pendingIsFront);
            } else {
                Toast.makeText(this, "Camera permission is required to capture ID photo", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void uploadImage(Uri selectedImage, boolean isFront) {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        String path = isFront ? "id_photos_front" : "id_photos_back";
        StorageReference reference = storageReference.child("users/" + auth.getUid() + "/" + path);

        reference.putFile(selectedImage).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                reference.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                    @Override
                    public void onSuccess(Uri uri) {
                        if (isFront) {
                            // Display file name instead of image
                            String fileName = "Front ID Photo";
                            frontIdPhotoImageView.setText(fileName);
                            frontImageUrl = uri.toString();
                            frontIdPlaceholderText.setVisibility(View.GONE);
                            isFrontImageSelected = true;
                            Toast.makeText(Verification_Page.this, "Front ID photo uploaded successfully", Toast.LENGTH_SHORT).show();
                        } else {
                            // Display file name instead of image
                            String fileName = "Back ID Photo";
                            backIdPhotoImageView.setText(fileName);
                            backImageUrl = uri.toString();
                            backIdPlaceholderText.setVisibility(View.GONE);
                            isBackImageSelected = true;
                            Toast.makeText(Verification_Page.this, "Back ID photo uploaded successfully", Toast.LENGTH_SHORT).show();
                        }
                        checkNextButtonState();
                    }
                });
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(Verification_Page.this, "Image Upload Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void uploadBitmap(Bitmap bitmap, boolean isFront) {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        // Resize bitmap to fit properly in the frame
        Bitmap resizedBitmap = resizeBitmap(bitmap, 800, 600);

        // Convert bitmap to byte array
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos);
        byte[] data = baos.toByteArray();

        String path = isFront ? "id_photos_front" : "id_photos_back";
        StorageReference reference = storageReference.child("users/" + auth.getUid() + "/" + path);

        reference.putBytes(data).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                reference.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                    @Override
                    public void onSuccess(Uri uri) {
                        if (isFront) {
                            // Display file name instead of image
                            String fileName = "Front ID Photo";
                            frontIdPhotoImageView.setText(fileName);
                            frontImageUrl = uri.toString();
                            frontIdPlaceholderText.setVisibility(View.GONE);
                            isFrontImageSelected = true;
                            Toast.makeText(Verification_Page.this, "Front ID photo captured and uploaded successfully", Toast.LENGTH_SHORT).show();
                        } else {
                            // Display file name instead of image
                            String fileName = "Back ID Photo";
                            backIdPhotoImageView.setText(fileName);
                            backImageUrl = uri.toString();
                            backIdPlaceholderText.setVisibility(View.GONE);
                            isBackImageSelected = true;
                            Toast.makeText(Verification_Page.this, "Back ID photo captured and uploaded successfully", Toast.LENGTH_SHORT).show();
                        }
                        checkNextButtonState();
                    }
                });
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(Verification_Page.this, "Image Upload Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
}