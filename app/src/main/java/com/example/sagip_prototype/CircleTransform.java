package com.example.sagip_prototype;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;

import com.squareup.picasso.Transformation;

public class CircleTransform implements Transformation {
    @Override
    public Bitmap transform(Bitmap source) {
        int size = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - size) / 2;
        int y = (source.getHeight() - size) / 2;
        
        // Ensure we don't go out of bounds
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        if (x + size > source.getWidth()) size = source.getWidth() - x;
        if (y + size > source.getHeight()) size = source.getHeight() - y;
        
        Bitmap squaredBitmap = Bitmap.createBitmap(source, x, y, size, size);
        if (squaredBitmap != source) {
            source.recycle();
        }
        
        // Create a new bitmap with the same config as source
        Bitmap.Config config = source.getConfig() != null ? source.getConfig() : Bitmap.Config.ARGB_8888;
        Bitmap bitmap = Bitmap.createBitmap(size, size, config);
        
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        paint.setDither(true);
        
        // Create shader for the circular image
        BitmapShader shader = new BitmapShader(squaredBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        paint.setShader(shader);
        
        float r = size / 2f;
        canvas.drawCircle(r, r, r, paint);
        
        squaredBitmap.recycle();
        return bitmap;
    }
    
    @Override
    public String key() {
        return "circle";
    }
}
