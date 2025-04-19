package com.example.sagip_prototype;

import android.content.Intent;
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

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.squareup.picasso.Picasso;

public class Verification_Page extends AppCompatActivity {

    Button uploadIdPhotoButton;
    ImageView idPhotoImageView;

    StorageReference storageReference;

    FirebaseAuth auth;
    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_verification_page);

        // Initialize Firebase components
        storageReference = FirebaseStorage.getInstance().getReference();
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Find views
        uploadIdPhotoButton = findViewById(R.id.uploadIdPhotoButton);
        idPhotoImageView = findViewById(R.id.idPhotoImageView);

        // Check if user already has an ID photo and display it
        if (auth.getCurrentUser() != null) {
            StorageReference profileReference = storageReference.child("users/" + auth.getUid() + "/id_photos");
            profileReference.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                @Override
                public void onSuccess(Uri uri) {
                    Picasso.get().load(uri).into(idPhotoImageView);
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    // No profile image exists yet, which is fine
                }
            });
        }

        // Set click listener for upload button
        uploadIdPhotoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent openGallery = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    startActivityForResult(openGallery, 1000);
                } catch (Exception e) {
                    Toast.makeText(Verification_Page.this, "Error opening gallery: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1000) {
            if (resultCode == RESULT_OK && data != null) {
                Uri selectedImage = data.getData();
                if (selectedImage != null) {
                    uploadImage(selectedImage);
                } else {
                    Toast.makeText(this, "Failed to get image", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void uploadImage(Uri selectedImage) {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        StorageReference reference = storageReference.child("users/" + auth.getUid() + "/id_photos");

        reference.putFile(selectedImage).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                // Get the download URL after upload completes
                reference.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                    @Override
                    public void onSuccess(Uri uri) {
                        // Display the image
                        Picasso.get().load(uri).into(idPhotoImageView);

                        // Get the image URL
                        String imageUrl = uri.toString();

                        // Pass the image URL to the next activity
                        Toast.makeText(Verification_Page.this, "Take a selfie for verification", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(Verification_Page.this, Selfie_verification.class);

                        // Add the image URL as an extra in the intent
                        intent.putExtra("idPhotoUrl", imageUrl);
                        startActivity(intent);
                        finish();
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
}