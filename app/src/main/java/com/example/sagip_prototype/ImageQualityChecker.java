package com.example.sagip_prototype;

import android.graphics.Bitmap;
import android.util.Log;

public class ImageQualityChecker {
    
    private static final String TAG = "ImageQualityChecker";
    private static final double BLUR_THRESHOLD = 100.0; // Lower values = more blur
    
    /**
     * Check if an image is too blurred to be acceptable for ID verification
     * @param bitmap The image to check
     * @return true if image is too blurred, false if acceptable
     */
    public static boolean isImageTooBlurred(Bitmap bitmap) {
        try {
            if (bitmap == null) {
                Log.w(TAG, "Bitmap is null");
                return true;
            }
            
            // Convert to grayscale for analysis
            Bitmap grayscale = convertToGrayscale(bitmap);
            
            // Calculate Laplacian variance (blur detection)
            double variance = calculateLaplacianVariance(grayscale);
            
            Log.d(TAG, "Image blur variance: " + variance);
            
            // Lower variance indicates more blur
            return variance < BLUR_THRESHOLD;
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking image quality: " + e.getMessage(), e);
            return true; // Assume blurred if error occurs
        }
    }
    
    /**
     * Convert bitmap to grayscale
     */
    private static Bitmap convertToGrayscale(Bitmap original) {
        int width = original.getWidth();
        int height = original.getHeight();
        Bitmap grayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixel = original.getPixel(x, y);
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;
                
                // Convert to grayscale using luminance formula
                int gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                int grayPixel = (0xFF << 24) | (gray << 16) | (gray << 8) | gray;
                grayscale.setPixel(x, y, grayPixel);
            }
        }
        
        return grayscale;
    }
    
    /**
     * Calculate Laplacian variance for blur detection
     */
    private static double calculateLaplacianVariance(Bitmap grayscale) {
        int width = grayscale.getWidth();
        int height = grayscale.getHeight();
        
        // Laplacian kernel
        int[][] laplacianKernel = {
            {0, -1, 0},
            {-1, 4, -1},
            {0, -1, 0}
        };
        
        double sum = 0;
        double sumSquared = 0;
        int pixelCount = 0;
        
        // Apply Laplacian kernel (skip edges)
        for (int x = 1; x < width - 1; x++) {
            for (int y = 1; y < height - 1; y++) {
                double laplacianValue = 0;
                
                // Apply kernel
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        int pixel = grayscale.getPixel(x + i, y + j);
                        int gray = pixel & 0xFF;
                        laplacianValue += gray * laplacianKernel[i + 1][j + 1];
                    }
                }
                
                sum += laplacianValue;
                sumSquared += laplacianValue * laplacianValue;
                pixelCount++;
            }
        }
        
        if (pixelCount == 0) return 0;
        
        double mean = sum / pixelCount;
        double variance = (sumSquared / pixelCount) - (mean * mean);
        
        return variance;
    }
    
    /**
     * Check if image has sufficient resolution for ID verification
     */
    public static boolean hasMinimumResolution(Bitmap bitmap, int minWidth, int minHeight) {
        if (bitmap == null) return false;
        return bitmap.getWidth() >= minWidth && bitmap.getHeight() >= minHeight;
    }
    
    /**
     * Check if image is too dark for ID verification
     */
    public static boolean isImageTooDark(Bitmap bitmap) {
        if (bitmap == null) return true;
        
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        long totalBrightness = 0;
        int pixelCount = 0;
        
        // Sample every 10th pixel for performance
        for (int x = 0; x < width; x += 10) {
            for (int y = 0; y < height; y += 10) {
                int pixel = bitmap.getPixel(x, y);
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;
                
                // Calculate brightness
                int brightness = (r + g + b) / 3;
                totalBrightness += brightness;
                pixelCount++;
            }
        }
        
        if (pixelCount == 0) return true;
        
        double averageBrightness = (double) totalBrightness / pixelCount;
        Log.d(TAG, "Image average brightness: " + averageBrightness);
        
        return averageBrightness < 50; // Threshold for too dark
    }
}
