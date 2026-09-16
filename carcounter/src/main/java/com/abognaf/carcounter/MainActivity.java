package com.abognaf.carcounter;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.abognaf.carcounter.core.CrossingCounter;
import com.abognaf.carcounter.core.DetectedBox;
import com.abognaf.carcounter.core.OverlayFrame;
import com.abognaf.carcounter.detect.FramePreparer;
import com.abognaf.carcounter.detect.VehicleDetector;
import com.abognaf.carcounter.ui.SettingsStore;
import com.abognaf.carcounter.view.OverlayView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * الشاشة الرئيسية: بث الكاميرا في الأعلى، خط العبور القابل للسحب فوقه،
 * وثلاثة عدادات (إجمالي / داخل / خارج) وأزرار التحكم أسفل الفيديو.
 *
 * <p>كل المعالجة محلية داخل الهاتف: لا إنترنت، ولا إرسال أو حفظ للفيديو.
 */
public class MainActivity extends AppCompatActivity implements OverlayView.LineChangeListener {

    private static final String TAG = "CarCounter";
    private static final int REQUEST_CAMERA = 1001;

    private PreviewView previewView;
    private OverlayView overlayView;
    private TextView valueTotal;
    private TextView valueIn;
    private TextView valueOut;
    private TextView statusText;
    private TextView cameraMessage;
    private TextView tipText;
    private TextView scoreValue;
    private SeekBar scoreSeek;
    private View loadingPanel;
    private View linePanel;
    private MaterialButton btnToggle;
    private MaterialButton btnLine;

    private final SettingsStore settings = new SettingsStore();
    private final CrossingCounter counter = new CrossingCounter(480f, 640f);
    private final FramePreparer framePreparer = new FramePreparer();
    private final List<DetectedBox> detectionBuffer = new ArrayList<>();

    private VehicleDetector detector = new VehicleDetector();
    private ExecutorService analysisExecutor;
    private ExecutorService modelExecutor;
    private ProcessCameraProvider cameraProvider;

    private volatile boolean counting = false;
    private volatile boolean modelReady = false;
    private volatile float frameWidth = 480f;
    private volatile float frameHeight = 640f;

    private long lastProcessMs;
    private long fpsWindowStartMs;
    private int fpsFrames;
    private float fps;
    private int shownTotal = -1;
    private int shownIn = -1;
    private int shownOut = -1;
    private boolean tipDismissed = false;

    // ------------------------------------------------------------ دورة الحياة

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        settings.load(this);
        analysisExecutor = Executors.newSingleThreadExecutor();
        modelExecutor = Executors.newSingleThreadExecutor();

        previewView = findViewById(R.id.preview);
        overlayView = findViewById(R.id.overlay);
        valueTotal = findViewById(R.id.valueTotal);
        valueIn = findViewById(R.id.valueIn);
        valueOut = findViewById(R.id.valueOut);
        statusText = findViewById(R.id.statusText);
        cameraMessage = findViewById(R.id.cameraMessage);
        tipText = findViewById(R.id.tipText);
        loadingPanel = findViewById(R.id.loadingPanel);
        linePanel = findViewById(R.id.linePanel);
        btnToggle = findViewById(R.id.btnToggle);
        btnLine = findViewById(R.id.btnLine);

        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);

        overlayView.setLineChangeListener(this);
        overlayView.setLine(settings.lineX1, settings.lineY1, settings.lineX2, settings.lineY2);
        overlayView.setFlipped(settings.lineFlipped);
        overlayView.setShowBoxes(settings.showBoxes);
        overlayView.setShowStats(settings.showStats);

        final float lx1 = settings.lineX1;
        final float ly1 = settings.lineY1;
        final float lx2 = settings.lineX2;
        final float ly2 = settings.lineY2;
        final boolean lf = settings.lineFlipped;
        analysisExecutor.execute(() -> counter.setLine(lx1, ly1, lx2, ly2, lf));

        btnToggle.setOnClickListener(v -> toggleCounting());
        findViewById(R.id.btnReset).setOnClickListener(v -> resetCounters());
        btnLine.setOnClickListener(v -> setLineEditMode(!overlayView.isEditMode()));
        findViewById(R.id.btnFlip).setOnClickListener(v -> flipDirection());
        findViewById(R.id.btnLineDone).setOnClickListener(v -> setLineEditMode(false));
        findViewById(R.id.btnSettings).setOnClickListener(v -> showSettingsDialog());
        findViewById(R.id.btnAbout).setOnClickListener(v -> showAboutDialog());

        updateCountersUi(0, 0, 0);
        updateToggleUi();
        updateStatusText();
        setLineEditMode(false);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
            loadModel();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // إيقاف العدّ عند مغادرة الشاشة (الكاميرا تتوقف تلقائيًا مع دورة الحياة)
        counting = false;
        updateToggleUi();
        updateStatusText();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (analysisExecutor != null) {
            analysisExecutor.shutdown();
        }
        if (modelExecutor != null) {
            modelExecutor.shutdown();
        }
        detector.close();
        framePreparer.release();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_CAMERA) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            cameraMessage.setVisibility(View.GONE);
            startCamera();
            loadModel();
        } else {
            showCameraMessage(getString(R.string.permission_denied),
                    getString(R.string.permission_grant), v -> openAppSettings());
        }
    }

    // ------------------------------------------------------------ الكاميرا

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindUseCases();
            } catch (Throwable tr) {
                Log.e(TAG, "camera provider failed", tr);
                showCameraMessage(getString(R.string.camera_error), null, null);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindUseCases() {
        if (cameraProvider == null) {
            return;
        }
        cameraProvider.unbindAll();
        int rotation = Surface.ROTATION_0;
        if (previewView.getDisplay() != null) {
            rotation = previewView.getDisplay().getRotation();
        }

        ResolutionSelector previewSelector = new ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build();
        Preview preview = new Preview.Builder()
                .setResolutionSelector(previewSelector)
                .setTargetRotation(rotation)
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ResolutionSelector analysisSelector = new ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(new ResolutionStrategy(
                        new Size(settings.analysisWidth(), settings.analysisHeight()),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                .build();
        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setResolutionSelector(analysisSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setTargetRotation(rotation)
                .build();
        analysis.setAnalyzer(analysisExecutor, this::processFrame);

        try {
            cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
            cameraMessage.setVisibility(View.GONE);
        } catch (Throwable tr) {
            Log.e(TAG, "bind failed", tr);
            showCameraMessage(getString(R.string.camera_error), null, null);
        }
    }

    /** تحميل النموذج في الخلفية ثم تسليمه لخيط المعالجة. */
    private void loadModel() {
        loadingPanel.setVisibility(View.VISIBLE);
        modelExecutor.execute(() -> {
            try {
                final VehicleDetector loaded = new VehicleDetector();
                loaded.init(getApplicationContext(), settings.threads, settings.scoreThreshold,
                        settings.countMotorcycles, settings.countBicycles, settings.acceptAllLabels);
                analysisExecutor.execute(() -> {
                    detector.close();
                    detector = loaded;
                    modelReady = true;
                    runOnUiThread(() -> {
                        loadingPanel.setVisibility(View.GONE);
                        updateStatusText();
                        updateToggleUi();
                        if (!tipDismissed) {
                            tipText.setVisibility(View.VISIBLE);
                        }
                    });
                });
            } catch (final Throwable tr) {
                Log.e(TAG, "model load failed", tr);
                runOnUiThread(() -> {
                    loadingPanel.setVisibility(View.GONE);
                    showCameraMessage(getString(R.string.model_error), null, null);
                    updateStatusText();
                });
            }
        });
    }

    // ------------------------------------------------------------ المعالجة

    private void processFrame(ImageProxy image) {
        try {
            if (!counting || !modelReady || detector == null || !detector.isReady()) {
                return;
            }
            long now = SystemClock.elapsedRealtime();
            if ((now - lastProcessMs) < settings.detectIntervalMs()) {
                return;
            }
            lastProcessMs = now;

            Bitmap input = framePreparer.prepare(image, BuildConfig.MODEL_INPUT_SIZE);
            if (input == null) {
                return;
            }
            if (framePreparer.uprightWidth != (int) frameWidth
                    || framePreparer.uprightHeight != (int) frameHeight) {
                frameWidth = framePreparer.uprightWidth;
                frameHeight = framePreparer.uprightHeight;
                counter.setFrameSize(frameWidth, frameHeight);
                runOnUiThread(() -> overlayView.setFrameSize(frameWidth, frameHeight));
            }

            long inferenceStart = SystemClock.elapsedRealtime();
            List<DetectedBox> detections = detector.detect(input, frameWidth, frameHeight, detectionBuffer);
            long inferenceMs = SystemClock.elapsedRealtime() - inferenceStart;

            fpsFrames++;
            if (fpsWindowStartMs == 0L) {
                fpsWindowStartMs = now;
                fpsFrames = 0;
            } else if ((now - fpsWindowStartMs) >= 1000L) {
                fps = fpsFrames * 1000f / Math.max(1L, now - fpsWindowStartMs);
                fpsFrames = 0;
                fpsWindowStartMs = now;
            }

            OverlayFrame frame = counter.process(detections, now, inferenceMs, fps,
                    detector.lastRawCount());
            final String stats = settings.showStats ? buildStatsText(inferenceMs) : null;
            final int total = frame.total;
            final int inside = frame.in;
            final int outside = frame.out;
            runOnUiThread(() -> {
                overlayView.submit(frame);
                updateCountersUi(total, inside, outside);
                if (stats != null) {
                    overlayView.setStatsText(stats);
                }
            });
        } catch (Throwable tr) {
            Log.e(TAG, "processFrame error", tr);
        } finally {
            image.close();
        }
    }

    private String buildStatsText(long inferenceMs) {
        return "الكشف " + String.format(Locale.ROOT, "%.1f", fps) + "/ث"
                + " · استدلال " + inferenceMs + " م.ث"
                + " · كشوف " + detector.lastRawCount()
                + (detector.lastLabelsText().isEmpty() ? "" : (" · " + detector.lastLabelsText()));
    }

    // ------------------------------------------------------------ التحكم

    private void toggleCounting() {
        if (!modelReady) {
            toast(getString(R.string.model_loading_short));
            return;
        }
        counting = !counting;
        if (counting) {
            lastProcessMs = 0L;
            fpsWindowStartMs = 0L;
            tipDismissed = true;
            tipText.setVisibility(View.GONE);
        } else {
            overlayView.clearTracks();
        }
        updateToggleUi();
        updateStatusText();
    }

    private void resetCounters() {
        analysisExecutor.execute(() -> counter.reset());
        overlayView.clearTracks();
        overlayView.clearFlashes();
        updateCountersUi(0, 0, 0);
        toast(getString(R.string.counters_reset));
    }

    private void flipDirection() {
        final boolean flipped = !overlayView.isFlipped();
        overlayView.setFlipped(flipped);
        settings.lineFlipped = flipped;
        settings.save();
        final float x1 = settings.lineX1;
        final float y1 = settings.lineY1;
        final float x2 = settings.lineX2;
        final float y2 = settings.lineY2;
        analysisExecutor.execute(() -> counter.setLine(x1, y1, x2, y2, flipped));
        toast(getString(R.string.direction_flipped));
    }

    private void setLineEditMode(boolean edit) {
        overlayView.setEditMode(edit);
        linePanel.setVisibility(edit ? View.VISIBLE : View.GONE);
        btnLine.setText(edit ? R.string.done_editing : R.string.btn_line);
    }

    @Override
    public void onLineChanged(float x1, float y1, float x2, float y2) {
        settings.lineX1 = x1;
        settings.lineY1 = y1;
        settings.lineX2 = x2;
        settings.lineY2 = y2;
        final boolean flipped = overlayView.isFlipped();
        analysisExecutor.execute(() -> counter.setLine(x1, y1, x2, y2, flipped));
    }

    @Override
    public void onLineEditFinished() {
        settings.save();
    }

    // ------------------------------------------------------------ الواجهة

    private void updateCountersUi(int total, int inside, int outside) {
        if (total != shownTotal) {
            valueTotal.setText(String.valueOf(total));
            shownTotal = total;
        }
        if (inside != shownIn) {
            valueIn.setText(String.valueOf(inside));
            shownIn = inside;
        }
        if (outside != shownOut) {
            valueOut.setText(String.valueOf(outside));
            shownOut = outside;
        }
    }

    private void updateToggleUi() {
        btnToggle.setText(counting ? R.string.btn_stop : R.string.btn_start);
        btnToggle.setEnabled(modelReady);
    }

    private void updateStatusText() {
        String text;
        if (!modelReady) {
            text = getString(R.string.model_loading_short);
        } else if (counting) {
            text = getString(R.string.status_counting);
        } else {
            text = getString(R.string.status_stopped);
        }
        statusText.setText(text);
    }

    private void showCameraMessage(String message, @Nullable String actionLabel,
                                   @Nullable View.OnClickListener action) {
        cameraMessage.setText(message);
        cameraMessage.setVisibility(View.VISIBLE);
        if (actionLabel != null && action != null) {
            cameraMessage.setOnClickListener(action);
        }
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------ الإعدادات

    private void showSettingsDialog() {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        final RadioGroup groupDetect = content.findViewById(R.id.groupDetect);
        final RadioGroup groupQuality = content.findViewById(R.id.groupQuality);
        final RadioGroup groupThreads = content.findViewById(R.id.groupThreads);
        scoreSeek = content.findViewById(R.id.seekScore);
        scoreValue = content.findViewById(R.id.scoreValue);
        final SwitchMaterial swMotorcycles = content.findViewById(R.id.swMotorcycles);
        final SwitchMaterial swBicycles = content.findViewById(R.id.swBicycles);
        final SwitchMaterial swBoxes = content.findViewById(R.id.swBoxes);
        final SwitchMaterial swStats = content.findViewById(R.id.swStats);
        final SwitchMaterial swAcceptAll = content.findViewById(R.id.swAcceptAll);

        checkRadio(groupDetect, settings.detectMode);
        checkRadio(groupQuality, settings.analysisQuality);
        checkRadio(groupThreads, settings.threads == 1 ? 0 : (settings.threads >= 4 ? 2 : 1));
        scoreSeek.setProgress(Math.max(0, Math.min(60,
                Math.round((settings.scoreThreshold - 0.2f) * 100f))));
        updateScoreLabel();
        swMotorcycles.setChecked(settings.countMotorcycles);
        swBicycles.setChecked(settings.countBicycles);
        swBoxes.setChecked(settings.showBoxes);
        swStats.setChecked(settings.showStats);
        swAcceptAll.setChecked(settings.acceptAllLabels);
        scoreSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateScoreLabel();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_title)
                .setView(content)
                .setPositiveButton(R.string.apply, (DialogInterface dialog, int which) -> {
                    int previousQuality = settings.analysisQuality;
                    settings.detectMode = radioIndex(groupDetect, SettingsStore.MODE_BALANCED);
                    settings.analysisQuality = radioIndex(groupQuality, SettingsStore.QUALITY_MEDIUM);
                    int threadIndex = radioIndex(groupThreads, 1);
                    settings.threads = threadIndex == 0 ? 1 : (threadIndex == 2 ? 4 : 2);
                    settings.scoreThreshold = 0.2f + scoreSeek.getProgress() / 100f;
                    settings.countMotorcycles = swMotorcycles.isChecked();
                    settings.countBicycles = swBicycles.isChecked();
                    settings.showBoxes = swBoxes.isChecked();
                    settings.showStats = swStats.isChecked();
                    settings.acceptAllLabels = swAcceptAll.isChecked();
                    settings.save();
                    applySettingsChanges(previousQuality != settings.analysisQuality);
                })
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void applySettingsChanges(boolean resolutionChanged) {
        overlayView.setShowBoxes(settings.showBoxes);
        overlayView.setShowStats(settings.showStats);
        final int threads = settings.threads;
        analysisExecutor.execute(() -> {
            detector.setOptions(settings.scoreThreshold, settings.countMotorcycles,
                    settings.countBicycles, settings.acceptAllLabels);
            try {
                if (detector.isReady()) {
                    detector.setThreads(getApplicationContext(), threads);
                }
            } catch (Throwable tr) {
                Log.e(TAG, "reload detector failed", tr);
            }
        });
        if (resolutionChanged) {
            bindUseCases();
        }
    }

    private void updateScoreLabel() {
        if (scoreValue != null && scoreSeek != null) {
            int percent = Math.round((0.2f + scoreSeek.getProgress() / 100f) * 100f);
            scoreValue.setText(String.format(Locale.ROOT, "%d%%", percent));
        }
    }

    private static int radioIndex(RadioGroup group, int fallback) {
        int id = group.getCheckedRadioButtonId();
        if (id == View.NO_ID) {
            return fallback;
        }
        View checked = group.findViewById(id);
        for (int i = 0; i < group.getChildCount(); i++) {
            if (group.getChildAt(i) == checked && group.getChildAt(i) instanceof RadioButton) {
                return i;
            }
        }
        return fallback;
    }

    private static void checkRadio(RadioGroup group, int index) {
        if (index >= 0 && index < group.getChildCount()) {
            View child = group.getChildAt(index);
            if (child instanceof RadioButton) {
                ((RadioButton) child).setChecked(true);
            }
        }
    }

    private void showAboutDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.about_title)
                .setMessage(getString(R.string.about_body))
                .setPositiveButton(R.string.close, null)
                .show();
    }
}
