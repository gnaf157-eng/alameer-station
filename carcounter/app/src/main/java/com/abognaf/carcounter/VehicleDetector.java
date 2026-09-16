package com.abognaf.carcounter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;

import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.Rot90Op;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * كاشف المركبات: نموذج TensorFlow Lite محلي (EfficientDet-Lite0 مدرّب على COCO).
 * يعمل بالكامل على الجهاز دون أي اتصال بالإنترنت.
 */
public class VehicleDetector {

    private static final String MODEL = "efficientdet_lite0.tflite";
    private static final Set<String> VEHICLE_LABELS =
            new HashSet<>(Arrays.asList("car", "truck", "bus", "motorcycle"));

    private final ObjectDetector detector;

    public VehicleDetector(Context ctx, float scoreThreshold) throws IOException {
        BaseOptions base = BaseOptions.builder().setNumThreads(3).build();
        ObjectDetector.ObjectDetectorOptions opts = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(base)
                .setScoreThreshold(scoreThreshold)
                .setMaxResults(10)
                .build();
        detector = ObjectDetector.createFromFileAndOptions(ctx, MODEL, opts);
    }

    /**
     * @param bitmap    الإطار الحالي
     * @param rotation  دوران الصورة بالدرجات (0/90/180/270)
     * @return صناديق المركبات بإحداثيات الصورة المدوَّرة (المستقيمة)
     */
    public List<RectF> detect(Bitmap bitmap, int rotation) {
        ImageProcessor proc = new ImageProcessor.Builder()
                .add(new Rot90Op(-rotation / 90))
                .build();
        TensorImage img = proc.process(TensorImage.fromBitmap(bitmap));
        List<Detection> results = detector.detect(img);
        List<RectF> out = new ArrayList<>();
        for (Detection d : results) {
            if (d.getCategories().isEmpty()) continue;
            String label = d.getCategories().get(0).getLabel();
            if (VEHICLE_LABELS.contains(label)) {
                out.add(new RectF(d.getBoundingBox()));
            }
        }
        return out;
    }

    public void close() {
        detector.close();
    }
}
