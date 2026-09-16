package com.abognaf.carcounter.detect;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;

import androidx.camera.core.ImageProxy;

/**
 * تجهيز إطار الكاميرا للنموذج: تدوير إلى الوضع الرأسي + تصغير إلى مربّع
 * بحجم مدخلات النموذج، مع إعادة استخدام نفس الكائنات لتفادي الحجز المتكرر
 * في الذاكرة (أداء أخف على الهواتف المتوسطة).
 */
public final class FramePreparer {

    private static final String TAG = "FramePreparer";

    private Bitmap square;
    private Canvas canvas;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);

    /** أبعاد الإطار بعد التدوير (بالبكسل) — تُستخدم كمرجع لإحداثيات التتبّع. */
    public int uprightWidth;
    public int uprightHeight;

    /**
     * @return صورة مربّعة جاهزة للنموذج (تتغيّر محتوياتها في كل نداء)، أو null عند الفشل
     */
    public Bitmap prepare(ImageProxy proxy, int squareSize) {
        Bitmap frame = null;
        try {
            frame = proxy.toBitmap();
        } catch (Throwable tr) {
            Log.w(TAG, "toBitmap failed", tr);
        }
        if (frame == null) {
            return null;
        }
        int rotation = proxy.getImageInfo().getRotationDegrees();
        int width = frame.getWidth();
        int height = frame.getHeight();
        boolean swapped = rotation == 90 || rotation == 270;
        uprightWidth = swapped ? height : width;
        uprightHeight = swapped ? width : height;

        if (square == null || square.getWidth() != squareSize) {
            if (square != null) {
                square.recycle();
            }
            square = Bitmap.createBitmap(squareSize, squareSize, Bitmap.Config.ARGB_8888);
            canvas = new Canvas(square);
        }
        canvas.drawColor(Color.BLACK);
        canvas.save();
        canvas.translate(squareSize * 0.5f, squareSize * 0.5f);
        // التمديد إلى مربّع بعد التدوير: يحافظ على الإحداثيات النسبية للنتائج
        canvas.scale(squareSize / (float) uprightWidth, squareSize / (float) uprightHeight);
        canvas.rotate(rotation);
        canvas.translate(-width * 0.5f, -height * 0.5f);
        canvas.clipRect(0, 0, width, height);
        canvas.drawBitmap(frame, 0f, 0f, paint);
        canvas.restore();
        return square;
    }

    public void release() {
        if (square != null) {
            square.recycle();
            square = null;
        }
        canvas = null;
    }
}
