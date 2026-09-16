package com.abognaf.carcounter;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * كاشف المركبات باستخدام TensorFlow Lite Interpreter مباشرة (بدون Task Library).
 * النموذج: SSD-MobileNet-v1 (COCO, Quantized) مع ما بعد المعالجة المدمجة — يعمل محليًا دون إنترنت.
 */
public class VehicleDetector {

    private static final String MODEL = "detect.tflite";
    // فهارس COCO (بدون خلفية): car=2, motorcycle=3, bus=5, truck=7
    private static final int[] VEHICLE_CLASSES = {2, 3, 5, 7};

    private final Interpreter interpreter;
    private final float threshold;
    private final int inW, inH;
    private final boolean quantizedInput;
    private final ByteBuffer inputBuf;
    private final int[] pixels;
    private final Bitmap inputBitmap;
    private final Canvas inputCanvas;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);

    private final int idxBoxes, idxClasses, idxScores, idxCount, maxDet;
    private final float[][][] boxes;
    private final float[][] classes;
    private final float[][] scores;
    private final float[] count;

    public VehicleDetector(Context ctx, float scoreThreshold) throws IOException {
        threshold = scoreThreshold;
        Interpreter.Options opts = new Interpreter.Options();
        opts.setNumThreads(3);
        interpreter = new Interpreter(loadModel(ctx), opts);

        Tensor in = interpreter.getInputTensor(0);
        int[] s = in.shape(); // [1, H, W, 3]
        inH = s[1];
        inW = s[2];
        quantizedInput = in.dataType() == DataType.UINT8;
        inputBuf = ByteBuffer.allocateDirect(inW * inH * 3 * (quantizedInput ? 1 : 4));
        inputBuf.order(ByteOrder.nativeOrder());
        pixels = new int[inW * inH];
        inputBitmap = Bitmap.createBitmap(inW, inH, Bitmap.Config.ARGB_8888);
        inputCanvas = new Canvas(inputBitmap);

        // تحديد ترتيب المخرجات اعتمادًا على الأشكال (أسماء المخرجات غير موثوقة)
        // المتوقع: boxes [1,N,4] ، classes [1,N] ، scores [1,N] ، count [1]
        int b = -1, c = -1, sc = -1, n = -1, md = 25;
        int outCount = interpreter.getOutputTensorCount();
        StringBuilder desc = new StringBuilder();
        List<Integer> vec = new ArrayList<>();
        for (int i = 0; i < outCount; i++) {
            Tensor t = interpreter.getOutputTensor(i);
            int[] sh = t.shape();
            desc.append(i).append(':').append(t.name()).append(java.util.Arrays.toString(sh)).append(' ');
            int elems = 1; for (int d : sh) elems *= d;
            if (sh.length >= 2 && sh[sh.length - 1] == 4 && elems > 4) { b = i; md = elems / 4; }
            else if (elems == 1) n = i;
            else vec.add(i);
        }
        // التمييز بين classes و scores: حسب الاسم إن وُجد، وإلا حسب الترتيب المعتاد (classes ثم scores)
        for (int i : vec) {
            String name = interpreter.getOutputTensor(i).name().toLowerCase();
            if (name.contains("score")) sc = i;
            else if (name.contains("class")) c = i;
        }
        if ((sc < 0 || c < 0) && vec.size() >= 2) {
            // ترتيب TFLite_Detection_PostProcess: boxes(0), classes(1), scores(2), count(3)
            java.util.Collections.sort(vec);
            c = vec.get(0); sc = vec.get(1);
        }
        if (b < 0 || c < 0 || sc < 0) throw new IOException("مخرجات غير متوقعة: " + desc);
        android.util.Log.i("CarCounter", "outputs: " + desc + " -> boxes=" + b + " classes=" + c + " scores=" + sc + " count=" + n);
        idxBoxes = b; idxClasses = c; idxScores = sc; idxCount = n; maxDet = md;
        boxes = new float[1][maxDet][4];
        classes = new float[1][maxDet];
        scores = new float[1][maxDet];
        count = new float[1];
    }

    private static MappedByteBuffer loadModel(Context ctx) throws IOException {
        try (AssetFileDescriptor fd = ctx.getAssets().openFd(MODEL);
             FileInputStream is = new FileInputStream(fd.getFileDescriptor())) {
            return is.getChannel().map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
        }
    }

    /**
     * @param bitmap   الإطار الحالي (بإحداثيات المستشعر)
     * @param rotation دوران الصورة بالدرجات
     * @return صناديق المركبات بإحداثيات نسبية (0..1) للصورة المستقيمة
     */
    public synchronized List<RectF> detect(Bitmap bitmap, int rotation) {
        // تدوير + تغيير حجم إلى مدخل النموذج في خطوة رسم واحدة
        int srcW = bitmap.getWidth(), srcH = bitmap.getHeight();
        boolean swap = rotation == 90 || rotation == 270;
        float upW = swap ? srcH : srcW, upH = swap ? srcW : srcH;
        Matrix m = new Matrix();
        m.postTranslate(-srcW / 2f, -srcH / 2f);
        m.postRotate(rotation);
        m.postScale(inW / upW, inH / upH);
        m.postTranslate(inW / 2f, inH / 2f);
        inputCanvas.drawBitmap(bitmap, m, paint);

        inputBitmap.getPixels(pixels, 0, inW, 0, 0, inW, inH);
        inputBuf.rewind();
        if (quantizedInput) {
            for (int p : pixels) {
                inputBuf.put((byte) ((p >> 16) & 0xFF));
                inputBuf.put((byte) ((p >> 8) & 0xFF));
                inputBuf.put((byte) (p & 0xFF));
            }
        } else {
            for (int p : pixels) {
                inputBuf.putFloat(((p >> 16) & 0xFF) / 255f);
                inputBuf.putFloat(((p >> 8) & 0xFF) / 255f);
                inputBuf.putFloat((p & 0xFF) / 255f);
            }
        }
        inputBuf.rewind();

        Map<Integer, Object> outputs = new HashMap<>();
        outputs.put(idxBoxes, boxes);
        outputs.put(idxClasses, classes);
        outputs.put(idxScores, scores);
        if (idxCount >= 0) outputs.put(idxCount, count);
        interpreter.runForMultipleInputsOutputs(new Object[]{inputBuf}, outputs);

        int n = idxCount >= 0 ? Math.min((int) count[0], maxDet) : maxDet;
        List<RectF> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (scores[0][i] < threshold) continue;
            if (!isVehicle((int) classes[0][i])) continue;
            float[] bx = boxes[0][i]; // ymin, xmin, ymax, xmax (نسبية)
            RectF r = new RectF(clamp(bx[1]), clamp(bx[0]), clamp(bx[3]), clamp(bx[2]));
            if (r.width() > 0.01f && r.height() > 0.01f) out.add(r);
        }
        return out;
    }

    private static boolean isVehicle(int cls) {
        for (int v : VEHICLE_CLASSES) if (v == cls) return true;
        return false;
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }

    public void close() { interpreter.close(); }
}
