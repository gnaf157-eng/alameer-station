package com.abognaf.carcounter.detect;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;

import com.abognaf.carcounter.core.DetectedBox;
import com.abognaf.carcounter.core.Labels;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;

import java.io.IOException;
import java.util.List;

/**
 * غلاف حول نموذج TensorFlow Lite المحلي لكشف الأجسام.
 *
 * <p>النموذج مُضمَّن داخل التطبيق داخل مجلد assets ويعمل بالكامل على المعالج
 * دون أي اتصال بالإنترنت، ولا يُرسَل أي شيء خارج الهاتف.
 */
public final class VehicleDetector implements AutoCloseable {

    public static final String MODEL_ASSET = "model.tflite";

    private static final int MAX_RESULTS = 15;

    private ObjectDetector detector;
    private TensorImage tensorImage;

    private float scoreThreshold = 0.45f;
    private boolean countMotorcycles = true;
    private boolean countBicycles = false;
    private boolean acceptAllLabels = false;

    private int lastRawCount;
    private String lastLabelsText = "";

    public boolean isReady() {
        return detector != null;
    }

    /** تهيئة النموذج (يجب أن تُنفَّذ خارج خيط الواجهة). */
    public void init(Context context, int threads, float scoreThreshold,
                     boolean countMotorcycles, boolean countBicycles, boolean acceptAllLabels)
            throws IOException {
        close();
        this.scoreThreshold = scoreThreshold;
        this.countMotorcycles = countMotorcycles;
        this.countBicycles = countBicycles;
        this.acceptAllLabels = acceptAllLabels;

        BaseOptions baseOptions = BaseOptions.builder()
                .setNumThreads(Math.max(1, threads))
                .build();
        ObjectDetector.ObjectDetectorOptions options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setScoreThreshold(Math.max(0.05f, scoreThreshold))
                .setMaxResults(MAX_RESULTS)
                .build();
        detector = ObjectDetector.createFromFileAndOptions(context, MODEL_ASSET, options);
        tensorImage = new TensorImage(DataType.UINT8);
    }

    public void setOptions(float scoreThreshold, boolean countMotorcycles, boolean countBicycles,
                           boolean acceptAllLabels) {
        this.scoreThreshold = scoreThreshold;
        this.countMotorcycles = countMotorcycles;
        this.countBicycles = countBicycles;
        this.acceptAllLabels = acceptAllLabels;
    }

    public void setThreads(Context context, int threads) throws IOException {
        init(context, threads, scoreThreshold, countMotorcycles, countBicycles, acceptAllLabels);
    }

    /**
     * كشف الأجسام في صورة الإدخال وإرجاع المركبات فقط بإحداثيات إطار الفيديو.
     *
     * @param input  صورة مربّعة مهيّأة للنموذج (نتيجة {@link FramePreparer})
     * @param frameW عرض إطار الفيديو الأصلي (بالبكسل بعد التدوير)
     * @param frameH ارتفاع إطار الفيديو الأصلي
     * @param out    قائمة يُعاد استخدامها لتقليل الحجز في الذاكرة
     */
    public List<DetectedBox> detect(Bitmap input, float frameW, float frameH, List<DetectedBox> out) {
        out.clear();
        lastRawCount = 0;
        lastLabelsText = "";
        if (detector == null || tensorImage == null || input == null) {
            return out;
        }
        tensorImage.load(input);
        List<Detection> results = detector.detect(tensorImage);
        if (results == null || results.isEmpty()) {
            return out;
        }
        float scaleX = frameW / input.getWidth();
        float scaleY = frameH / input.getHeight();
        StringBuilder labels = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            Detection detection = results.get(i);
            if (detection.getCategories() == null || detection.getCategories().isEmpty()) {
                continue;
            }
            String label = detection.getCategories().get(0).getLabel();
            float score = detection.getCategories().get(0).getScore();
            lastRawCount++;
            if (labels.length() < 90) {
                if (labels.length() > 0) {
                    labels.append("، ");
                }
                labels.append(Labels.arabicName(label)).append(' ')
                        .append(Math.round(score * 100f)).append('%');
            }
            if (!acceptAllLabels && !Labels.isVehicle(label, countMotorcycles, countBicycles)) {
                continue;
            }
            if (score < scoreThreshold) {
                continue;
            }
            RectF box = detection.getBoundingBox();
            if (box == null) {
                continue;
            }
            float x1 = clamp(box.left * scaleX, 0f, frameW);
            float y1 = clamp(box.top * scaleY, 0f, frameH);
            float x2 = clamp(box.right * scaleX, 0f, frameW);
            float y2 = clamp(box.bottom * scaleY, 0f, frameH);
            if ((x2 - x1) < 4f || (y2 - y1) < 4f) {
                continue;
            }
            out.add(new DetectedBox(label, score, x1, y1, x2, y2));
        }
        lastLabelsText = labels.toString();
        return out;
    }

    /** عدد الأجسام التي رصدها النموذج في آخر إطار (قبل الفلترة) — للتشخيص. */
    public int lastRawCount() {
        return lastRawCount;
    }

    /** نص مختصر بأسماء ما رصده النموذج — للتشخيص. */
    public String lastLabelsText() {
        return lastLabelsText;
    }

    @Override
    public void close() {
        if (detector != null) {
            detector.close();
            detector = null;
        }
        tensorImage = null;
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }
}
