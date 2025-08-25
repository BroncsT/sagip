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
import com.squareup.picasso.Picasso;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class Verification_Page extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int GALLERY_REQUEST_CODE_FRONT = 1000;
    private static final int CAMERA_REQUEST_CODE_FRONT = 1001;
    private static final int GALLERY_REQUEST_CODE_BACK = 1002;
    private static final int CAMERA_REQUEST_CODE_BACK = 1003;
    private static final int ID_CAMERA_CAPTURE_REQUEST = 2000;

    Button uploadIdPhotoButton, captureIdPhotoButton, nextButton;
    ImageView frontIdPhotoImageView, backIdPhotoImageView;
    TextView frontIdPlaceholderText, backIdPlaceholderText;
    AutoCompleteTextView idTypeDropdown;

    StorageReference storageReference;

    FirebaseAuth auth;
    FirebaseFirestore db;
    
    private String selectedIdType = "";
    private String frontImageUrl = "";
    private String backImageUrl = "";
    private boolean isFrontImageSelected = false;
    private boolean isBackImageSelected = false;

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

        // Find views
        uploadIdPhotoButton = findViewById(R.id.uploadIdPhotoButton);
        captureIdPhotoButton = findViewById(R.id.captureIdPhotoButton);
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
                showImageSourceDialog(true);
            }
        });

        // Set click listeners for back ID photo
        backIdPhotoImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showImageSourceDialog(false);
            }
        });

        // Set click listener for upload button (gallery)
        uploadIdPhotoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showImageSourceDialog(true); // Default to front
            }
        });

        // Set click listener for capture button (camera)
        captureIdPhotoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ContextCompat.checkSelfPermission(Verification_Page.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(Verification_Page.this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
                    return;
                }
                showImageSourceDialog(true); // Default to front
            }
        });
        
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

    private void showImageSourceDialog(boolean isFront) {
        String[] options = {getString(R.string.camera), getString(R.string.gallery)};
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.select_image_source));
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                // Camera
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
                    return;
                }
                openCamera(isFront);
            } else {
                // Gallery
                openGallery(isFront);
            }
        });
        builder.show();
    }

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

    private void openGallery(boolean isFront) {
        try {
            Intent openGallery = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            if (isFront) {
                startActivityForResult(openGallery, GALLERY_REQUEST_CODE_FRONT);
            } else {
                startActivityForResult(openGallery, GALLERY_REQUEST_CODE_BACK);
            }
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.error_opening_gallery) + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void loadExistingPhotos() {
        // Load front photo
        StorageReference frontReference = storageReference.child("users/" + auth.getUid() + "/id_photos_front");
        frontReference.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
            @Override
            public void onSuccess(Uri uri) {
                Picasso.get().load(uri).into(frontIdPhotoImageView);
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
                Picasso.get().load(uri).into(backIdPhotoImageView);
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
            if (requestCode == GALLERY_REQUEST_CODE_FRONT || requestCode == GALLERY_REQUEST_CODE_BACK) {
                Uri selectedImage = data.getData();
                if (selectedImage != null) {
                    boolean isFront = (requestCode == GALLERY_REQUEST_CODE_FRONT);
                    uploadImage(selectedImage, isFront);
                } else {
                    Toast.makeText(this, "Failed to get image from gallery", Toast.LENGTH_SHORT).show();
                }
            } else if (requestCode == CAMERA_REQUEST_CODE_FRONT || requestCode == CAMERA_REQUEST_CODE_BACK) {
                Bundle extras = data.getExtras();
                if (extras != null) {
                    Bitmap imageBitmap = (Bitmap) extras.get("data");
                    if (imageBitmap != null) {
                        boolean isFront = (requestCode == CAMERA_REQUEST_CODE_FRONT);
                        uploadBitmap(imageBitmap, isFront);
                    } else {
                        Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show();
                    }
                }
            } else if (requestCode == ID_CAMERA_CAPTURE_REQUEST) {
                String imageUrl = data.getStringExtra("imageUrl");
                boolean isFront = data.getBooleanExtra("isFrontSide", true);
                
                if (imageUrl != null) {
                    if (isFront) {
                        frontImageUrl = imageUrl;
                        Picasso.get().load(imageUrl).into(frontIdPhotoImageView);
                        frontIdPlaceholderText.setVisibility(View.GONE);
                        isFrontImageSelected = true;
                        Toast.makeText(this, "Front ID photo captured successfully", Toast.LENGTH_SHORT).show();
                    } else {
                        backImageUrl = imageUrl;
                        Picasso.get().load(imageUrl).into(backIdPhotoImageView);
                        backIdPlaceholderText.setVisibility(View.GONE);
                        isBackImageSelected = true;
                        Toast.makeText(this, "Back ID photo captured successfully", Toast.LENGTH_SHORT).show();
                    }
                    checkNextButtonState();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, show dialog again
                showImageSourceDialog(true);
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
                            Picasso.get().load(uri).into(frontIdPhotoImageView);
                            frontImageUrl = uri.toString();
                            frontIdPlaceholderText.setVisibility(View.GONE);
                            isFrontImageSelected = true;
                            Toast.makeText(Verification_Page.this, "Front ID photo uploaded successfully", Toast.LENGTH_SHORT).show();
                        } else {
                            Picasso.get().load(uri).into(backIdPhotoImageView);
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
                            Picasso.get().load(uri).into(frontIdPhotoImageView);
                            frontImageUrl = uri.toString();
                            frontIdPlaceholderText.setVisibility(View.GONE);
                            isFrontImageSelected = true;
                            Toast.makeText(Verification_Page.this, "Front ID photo captured and uploaded successfully", Toast.LENGTH_SHORT).show();
                        } else {
                            Picasso.get().load(uri).into(backIdPhotoImageView);
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