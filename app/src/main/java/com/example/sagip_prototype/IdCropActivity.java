package com.example.sagip_prototype;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class IdCropActivity extends AppCompatActivity {

    private static final String TAG = "IdCropActivity";
    private static final int CROP_REQUEST_CODE = 1001;
    
    private String sourceImagePath;
    private boolean isFrontSide;
    private String idType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Get data from intent
        sourceImagePath = getIntent().getStringExtra("sourceImagePath");
        isFrontSide = getIntent().getBooleanExtra("isFrontSide", true);
        idType = getIntent().getStringExtra("idType");
        
        if (sourceImagePath == null) {
            Toast.makeText(this, "No image to crop", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        startCrop();
    }
    
    private void startCrop() {
        try {
            // Use Android's built-in crop functionality
            Uri sourceUri = Uri.fromFile(new File(sourceImagePath));
            Intent cropIntent = new Intent("com.android.camera.action.CROP");
            cropIntent.setDataAndType(sourceUri, "image/*");
            cropIntent.putExtra("crop", "true");
            cropIntent.putExtra("aspectX", 16);
            cropIntent.putExtra("aspectY", 10);
            cropIntent.putExtra("outputX", 800);
            cropIntent.putExtra("outputY", 500);
            cropIntent.putExtra("return-data", true);
            cropIntent.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString());
            
            startActivityForResult(cropIntent, CROP_REQUEST_CODE);
                
        } catch (Exception e) {
            Log.e(TAG, "Error starting crop", e);
            // Fallback: return original image if cropping fails
            returnOriginalImage();
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == CROP_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                handleCropResult(data);
            } else {
                // User cancelled cropping or error occurred
                returnOriginalImage();
            }
        }
    }
    
    private void handleCropResult(Intent data) {
        try {
            // Get cropped bitmap from intent
            Bitmap croppedBitmap = data.getParcelableExtra("data");
            
            if (croppedBitmap != null) {
                // Check image quality for ID verification before proceeding
                if (isImageQualityAcceptable(croppedBitmap)) {
                    // Save cropped image to a permanent location
                    String croppedImagePath = saveCroppedImage(croppedBitmap);
                    
                    // Return result to calling activity
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("croppedImagePath", croppedImagePath);
                    resultIntent.putExtra("croppedImageUri", Uri.fromFile(new File(croppedImagePath)).toString());
                    resultIntent.putExtra("isFrontSide", isFrontSide);
                    resultIntent.putExtra("idType", idType);
                    
                    setResult(RESULT_OK, resultIntent);
                    finish();
                } else {
                    // Image quality is not acceptable for ID verification
                    Toast.makeText(this, "Image is too blurred or poor quality for ID verification. Please take a clearer photo.", Toast.LENGTH_LONG).show();
                    setResult(RESULT_CANCELED);
                    finish();
                }
            } else {
                Toast.makeText(this, "Failed to get cropped image", Toast.LENGTH_SHORT).show();
                returnOriginalImage();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling crop result: " + e.getMessage(), e);
            Toast.makeText(this, "Error processing cropped image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            returnOriginalImage();
        }
    }
    
    private boolean isImageQualityAcceptable(Bitmap bitmap) {
        // Check for minimum resolution for ID verification
        if (!ImageQualityChecker.hasMinimumResolution(bitmap, 400, 300)) {
            Toast.makeText(this, "Image resolution is too low for ID verification. Please take a higher quality photo.", Toast.LENGTH_LONG).show();
            return false;
        }
        
        // Check if image is too dark for ID verification
        if (ImageQualityChecker.isImageTooDark(bitmap)) {
            Toast.makeText(this, "Image is too dark for ID verification. Please ensure good lighting.", Toast.LENGTH_LONG).show();
            return false;
        }
        
        // Check for blur for ID verification
        if (ImageQualityChecker.isImageTooBlurred(bitmap)) {
            Toast.makeText(this, "Image is too blurred for ID verification. Please hold the camera steady and try again.", Toast.LENGTH_LONG).show();
            return false;
        }
        
        return true;
    }
    
    private void returnOriginalImage() {
        // If cropping fails, return the original image
        try {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("croppedImagePath", sourceImagePath);
            resultIntent.putExtra("croppedImageUri", Uri.fromFile(new File(sourceImagePath)).toString());
            resultIntent.putExtra("isFrontSide", isFrontSide);
            resultIntent.putExtra("idType", idType);
            
            setResult(RESULT_OK, resultIntent);
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Error returning original image", e);
            setResult(RESULT_CANCELED);
            finish();
        }
    }
    
    private String saveCroppedImage(Bitmap bitmap) throws IOException {
        // Create a unique filename
        String fileName = isFrontSide ? 
            "front_id_cropped_" + System.currentTimeMillis() + ".jpg" : 
            "back_id_cropped_" + System.currentTimeMillis() + ".jpg";
        
        File croppedFile = new File(getFilesDir(), fileName);
        
        // Save bitmap to file
        FileOutputStream fos = new FileOutputStream(croppedFile);
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
        fos.flush();
        fos.close();
        
        return croppedFile.getAbsolutePath();
    }
}