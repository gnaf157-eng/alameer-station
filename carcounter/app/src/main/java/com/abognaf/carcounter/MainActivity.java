package com.abognaf.carcounter;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Size;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements VehicleTracker.Listener {

    private static final int REQ_CAMERA = 10;
    private static final float SCORE_THRESHOLD = 0.45f;

    private PreviewView previewView;
    private OverlayView overlay;
    private TextView totalText, inText, outText, statusText;
    private Button toggleButton, resetButton, swapButton;

    private ExecutorService analysisExecutor;
    private VehicleDetector detector;
    private VehicleTracker tracker;

    private volatile boolean counting = false;
    private volatile boolean busy = false;
    private int inCount = 0, outCount = 0;
    private Bitmap frameBitmap;
    private byte[] rowBuffer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        overlay = findViewById(R.id.overlay);
        totalText = findViewById(R.id.totalText);
        inText = findViewById(R.id.inText);
        outText = findViewById(R.id.outText);
        statusText = findViewById(R.id.statusText);
        toggleButton = findViewById(R.id.toggleButton);
        resetButton = findViewById(R.id.resetButton);
        swapButton = findViewById(R.id.swapButton);

        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);

        tracker = new VehicleTracker(this);
        tracker.setLine(overlay.getA(), overlay.getB());
        overlay.setLineListener((a, b) -> tracker.setLine(a, b));

        toggleButton.setOnClickListener(v -> {
            counting = !counting;
            if (!counting) tracker.reset();
            toggleButton.setText(counting ? R.string.stop : R.string.start);
            toggleButton.setBackgroundTintList(ContextCompat.getColorStateList(this,
                    counting ? R.color.out_color : R.color.accent));
            statusText.setText(counting ? R.string.status_running : R.string.status_stopped);
            if (!counting) overlay.setTracks(new ArrayList<>());
        });

        resetButton.setOnClickListener(v -> {
            inCount = 0; outCount = 0;
            tracker.reset();
            updateCounters();
        });

        swapButton.setOnClickListener(v -> {
            boolean s = !tracker.isSwapDirection();
            tracker.setSwapDirection(s);
            overlay.setSwapped(s);
        });

        analysisExecutor = Executors.newSingleThreadExecutor();

        try {
            detector = new VehicleDetector(this, SCORE_THRESHOLD);
        } catch (Exception e) {
            Toast.makeText(this, R.string.model_error, Toast.LENGTH_LONG).show();
            toggleButton.setEnabled(false);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, R.string.camera_permission_needed, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                ResolutionSelector previewSel = new ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .build();
                Preview preview = new Preview.Builder()
                        .setResolutionSelector(previewSel)
                        .build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // دقة منخفضة للتحليل لتخفيف الحمل على الهواتف المتوسطة
                ResolutionSelector analysisSel = new ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .setResolutionStrategy(new ResolutionStrategy(new Size(640, 480),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                        .build();
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setResolutionSelector(analysisSel)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build();
                analysis.setAnalyzer(analysisExecutor, this::analyze);

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
            } catch (Exception e) {
                Toast.makeText(this, "تعذر تشغيل الكاميرا", Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /** يعمل في خيط التحليل. لا يُحفظ أي إطار؛ يُعالج ثم يُهمل مباشرة. */
    private void analyze(@NonNull ImageProxy image) {
        try {
            if (!counting || detector == null || busy) return;
            busy = true;

            if (frameBitmap == null || frameBitmap.getWidth() != image.getWidth()
                    || frameBitmap.getHeight() != image.getHeight()) {
                frameBitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
            }
            ImageProxy.PlaneProxy plane = image.getPlanes()[0];
            java.nio.ByteBuffer buf = plane.getBuffer();
            buf.rewind();
            int rowStride = plane.getRowStride();
            int width = image.getWidth(), height = image.getHeight();
            if (rowStride == width * 4) {
                frameBitmap.copyPixelsFromBuffer(buf);
            } else {
                // بعض الأجهزة تضيف حشوًا في نهاية كل صف؛ ننسخ صفًا صفًا
                if (rowBuffer == null || rowBuffer.length != width * height * 4) {
                    rowBuffer = new byte[width * height * 4];
                }
                for (int y = 0; y < height; y++) {
                    buf.position(y * rowStride);
                    buf.get(rowBuffer, y * width * 4, width * 4);
                }
                frameBitmap.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(rowBuffer));
            }
            int rotation = image.getImageInfo().getRotationDegrees();

            List<RectF> boxes = detector.detect(frameBitmap, rotation);

            // تحويل إلى إحداثيات نسبية للصورة المستقيمة
            boolean swapWH = rotation == 90 || rotation == 270;
            float w = swapWH ? image.getHeight() : image.getWidth();
            float h = swapWH ? image.getWidth() : image.getHeight();
            overlay.setImageAspect(w / h);
            List<RectF> norm = new ArrayList<>(boxes.size());
            for (RectF r : boxes) {
                norm.add(new RectF(r.left / w, r.top / h, r.right / w, r.bottom / h));
            }

            tracker.update(norm);
            overlay.setTracks(tracker.getTracks());
        } catch (Exception ignored) {
        } finally {
            busy = false;
            image.close();
        }
    }

    @Override
    public void onCrossed(TrackedVehicle v, boolean inDirection) {
        runOnUiThread(() -> {
            if (inDirection) inCount++; else outCount++;
            updateCounters();
        });
    }

    private void updateCounters() {
        totalText.setText(String.format(Locale.US, "%d", inCount + outCount));
        inText.setText(String.format(Locale.US, "%d", inCount));
        outText.setText(String.format(Locale.US, "%d", outCount));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (analysisExecutor != null) analysisExecutor.shutdown();
        if (detector != null) detector.close();
    }
}
