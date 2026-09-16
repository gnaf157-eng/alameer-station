package com.abognaf.carcounter.ui;

import android.content.Context;
import android.content.SharedPreferences;

import com.abognaf.carcounter.core.CountingLine;

/**
 * إعدادات التطبيق (مكان خط العبور + خيارات الكشف).
 * تُحفظ محليًا على الجهاز فقط. أما العدادات نفسها فتبقى في الذاكرة
 * أثناء تشغيل التطبيق كما هو مطلوب في هذه النسخة.
 */
public final class SettingsStore {

    private static final String PREFS = "car_counter_settings";

    private static final String KEY_DETECT_MODE = "detect_mode";
    private static final String KEY_QUALITY = "analysis_quality";
    private static final String KEY_SCORE = "score_threshold";
    private static final String KEY_THREADS = "threads";
    private static final String KEY_MOTORCYCLES = "count_motorcycles";
    private static final String KEY_BICYCLES = "count_bicycles";
    private static final String KEY_BOXES = "show_boxes";
    private static final String KEY_STATS = "show_stats";
    private static final String KEY_ACCEPT_ALL = "accept_all_labels";
    private static final String KEY_LINE_SAVED = "line_saved";
    private static final String KEY_LINE_X1 = "line_x1";
    private static final String KEY_LINE_Y1 = "line_y1";
    private static final String KEY_LINE_X2 = "line_x2";
    private static final String KEY_LINE_Y2 = "line_y2";
    private static final String KEY_LINE_FLIPPED = "line_flipped";

    public static final int MODE_FAST = 0;
    public static final int MODE_BALANCED = 1;
    public static final int MODE_ECO = 2;

    public static final int QUALITY_LOW = 0;
    public static final int QUALITY_MEDIUM = 1;
    public static final int QUALITY_HIGH = 2;

    public volatile int detectMode = MODE_BALANCED;
    public volatile int analysisQuality = QUALITY_MEDIUM;
    public volatile float scoreThreshold = 0.45f;
    public volatile int threads = 2;
    public volatile boolean countMotorcycles = true;
    public volatile boolean countBicycles = false;
    public volatile boolean showBoxes = true;
    public volatile boolean showStats = false;
    public volatile boolean acceptAllLabels = false;

    public volatile float lineX1 = CountingLine.DEFAULT_X1;
    public volatile float lineY1 = CountingLine.DEFAULT_Y1;
    public volatile float lineX2 = CountingLine.DEFAULT_X2;
    public volatile float lineY2 = CountingLine.DEFAULT_Y2;
    public volatile boolean lineFlipped = false;
    public volatile boolean lineSaved = false;

    private SharedPreferences prefs;

    public void load(Context context) {
        if (prefs == null) {
            prefs = context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }
        detectMode = prefs.getInt(KEY_DETECT_MODE, MODE_BALANCED);
        analysisQuality = prefs.getInt(KEY_QUALITY, QUALITY_MEDIUM);
        scoreThreshold = prefs.getFloat(KEY_SCORE, 0.45f);
        threads = prefs.getInt(KEY_THREADS, 2);
        countMotorcycles = prefs.getBoolean(KEY_MOTORCYCLES, true);
        countBicycles = prefs.getBoolean(KEY_BICYCLES, false);
        showBoxes = prefs.getBoolean(KEY_BOXES, true);
        showStats = prefs.getBoolean(KEY_STATS, false);
        acceptAllLabels = prefs.getBoolean(KEY_ACCEPT_ALL, false);
        lineSaved = prefs.getBoolean(KEY_LINE_SAVED, false);
        lineX1 = prefs.getFloat(KEY_LINE_X1, CountingLine.DEFAULT_X1);
        lineY1 = prefs.getFloat(KEY_LINE_Y1, CountingLine.DEFAULT_Y1);
        lineX2 = prefs.getFloat(KEY_LINE_X2, CountingLine.DEFAULT_X2);
        lineY2 = prefs.getFloat(KEY_LINE_Y2, CountingLine.DEFAULT_Y2);
        lineFlipped = prefs.getBoolean(KEY_LINE_FLIPPED, false);
    }

    public void save() {
        if (prefs == null) {
            return;
        }
        prefs.edit()
                .putInt(KEY_DETECT_MODE, detectMode)
                .putInt(KEY_QUALITY, analysisQuality)
                .putFloat(KEY_SCORE, scoreThreshold)
                .putInt(KEY_THREADS, threads)
                .putBoolean(KEY_MOTORCYCLES, countMotorcycles)
                .putBoolean(KEY_BICYCLES, countBicycles)
                .putBoolean(KEY_BOXES, showBoxes)
                .putBoolean(KEY_STATS, showStats)
                .putBoolean(KEY_ACCEPT_ALL, acceptAllLabels)
                .putBoolean(KEY_LINE_SAVED, true)
                .putFloat(KEY_LINE_X1, lineX1)
                .putFloat(KEY_LINE_Y1, lineY1)
                .putFloat(KEY_LINE_X2, lineX2)
                .putFloat(KEY_LINE_Y2, lineY2)
                .putBoolean(KEY_LINE_FLIPPED, lineFlipped)
                .apply();
        lineSaved = true;
    }

    /** فترة الكشف بالمللي ثانية: كل ما زادت قلّ الحمل وزادت سرعة التطبيق. */
    public int detectIntervalMs() {
        switch (detectMode) {
            case MODE_FAST:
                return 90;
            case MODE_ECO:
                return 280;
            default:
                return 150;
        }
    }

    /** أبعاد تدفق التحليل (عرض × ارتفاع) حسب الجودة المختارة. */
    public int analysisWidth() {
        switch (analysisQuality) {
            case QUALITY_LOW:
                return 480;
            case QUALITY_HIGH:
                return 960;
            default:
                return 640;
        }
    }

    public int analysisHeight() {
        switch (analysisQuality) {
            case QUALITY_LOW:
                return 360;
            case QUALITY_HIGH:
                return 720;
            default:
                return 480;
        }
    }
}
