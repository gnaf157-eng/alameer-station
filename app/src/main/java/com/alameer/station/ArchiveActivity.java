package com.alameer.station.shifts;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.util.Locale;

/**
 * أرشيف الدفاتر: الورديات التي رُحّلت واعتُمدت.
 * تُعرض كتقارير PDF للقراءة والمشاركة فقط، بلا تعديل.
 */
public class ArchiveActivity extends Activity {
    private Db db;
    private LinearLayout listBox;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        db = new Db(this);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        shell.setBackgroundColor(Util.BG);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(15), dp(16), dp(15));
        header.setBackgroundColor(Util.NAVY);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text("الأرشيف", 19, Color.WHITE, true));
        words.addView(text("الورديات المرحّلة — تقارير PDF و Excel", 11, 0xffCFE2FA, false));
        header.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        shell.addView(header);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(24));
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(listBox);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(shell);
    }

    @Override protected void onResume() {
        super.onResume();
        if (listBox != null) refresh();
    }

    private void refresh() {
        listBox.removeAllViews();
        int count = 0;
        double totalSales = 0;
        try (Cursor c = db.postedShifts()) {
            while (c.moveToNext()) {
                count++;
                final long id = c.getLong(0);
                String who = c.getString(1);
                String date = c.getString(2);
                double sales = c.getDouble(3);
                totalSales += sales;
                int pumps = c.getInt(5);

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(16), dp(14), dp(16), dp(14));
                card.setBackground(new android.graphics.drawable.RippleDrawable(
                        android.content.res.ColorStateList.valueOf(0x18000000),
                        Util.round(Color.WHITE, dp(16)), null));
                card.setElevation(dp(2));
                card.setClickable(true);
                card.setOnClickListener(v -> openReport(id));

                LinearLayout top = new LinearLayout(this);
                top.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout lines = new LinearLayout(this);
                lines.setOrientation(LinearLayout.VERTICAL);
                lines.addView(text(who, 18, Util.NAVY, true));
                lines.addView(text(db.shiftCode(id) + "  •  " + pumps + " طرمبة", 13, Util.NAVY, true));
                lines.addView(text(date, 12, 0xff8b9097, false));
                top.addView(lines, new LinearLayout.LayoutParams(0, -2, 1));
                TextView badge = text("PDF · XLS", 11, Color.WHITE, true);
                badge.setPadding(dp(10), dp(5), dp(10), dp(5));
                badge.setBackground(Util.round(Util.RED, dp(9)));
                top.addView(badge);
                card.addView(top);

                TextView money = text("المبيعات " + money(sales) + " ر.ي", 15, Util.GREEN, true);
                money.setPadding(0, dp(6), 0, 0);
                card.addView(money);
                card.addView(text("مُعتمدة ومُرحّلة  •  اضغط لـ PDF أو Excel", 12, 0xff8b9097, false));

                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
                cp.setMargins(0, dp(6), 0, dp(6));
                listBox.addView(card, cp);
            }
        }
        if (count == 0) {
            listBox.addView(text("لا توجد ورديات مرحّلة بعد.\nالورديات المعتمدة تظهر هنا كتقارير.",
                    14, 0xff777d84, false));
            return;
        }
        TextView sum = text(count + " وردية  •  إجمالي المبيعات " + money(totalSales) + " ر.ي",
                14, Util.NAVY, true);
        sum.setGravity(Gravity.CENTER);
        sum.setPadding(dp(12), dp(14), dp(12), dp(14));
        sum.setBackground(Util.round(Util.ACCENT_SOFT, dp(14)));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.setMargins(0, dp(10), 0, 0);
        listBox.addView(sum, sp);
    }

    /** يعرض صيغ التقرير المتاحة للوردية المؤرشفة. */
    private void openReport(final long id) {
        new AlertDialog.Builder(this).setTitle("تقرير الوردية " + db.shiftCode(id))
                .setMessage("التقرير للقراءة والمشاركة فقط، ولا يمكن تعديل الوردية بعد ترحيلها.")
                .setItems(new String[]{"فتح PDF", "مشاركة PDF",
                        "مشاركة Excel", "حفظ Excel في التنزيلات"}, (d, which) -> {
                    if (which == 0) share(id, true, false);
                    else if (which == 1) share(id, false, false);
                    else if (which == 2) share(id, false, true);
                    else saveToDownloads(id);
                })
                .setNegativeButton("إلغاء", null).show();
    }

    /**
     * يحفظ ملف الإكسل في مجلد التنزيلات مباشرة.
     * لا يعتمد على قبول التطبيقات الأخرى للملف، فيعمل دائمًا.
     */
    private void saveToDownloads(final long id) {
        Toast.makeText(this, "جارٍ تجهيز الملف...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String message;
            try {
                java.io.File file = new ExcelReport(this, db).build(id);
                String name = file.getName();

                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name);
                values.put(android.provider.MediaStore.Downloads.MIME_TYPE,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                android.net.Uri target = getContentResolver().insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (target == null) throw new java.io.IOException("تعذر إنشاء الملف في التنزيلات");

                try (java.io.InputStream in = new java.io.FileInputStream(file);
                     java.io.OutputStream out = getContentResolver().openOutputStream(target)) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                }
                message = "حُفظ الملف في التنزيلات باسم:\n" + name;
            } catch (Exception e) {
                message = "تعذر الحفظ: " + e.getMessage();
            }
            final String shown = message;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                new AlertDialog.Builder(this).setTitle("حفظ Excel")
                        .setMessage(shown).setPositiveButton("حسنًا", null).show();
            });
        }).start();
    }

    /**
     * يبني التقرير ويشاركه. الصيغة إمّا PDF أو Excel،
     * والبناء في خيط منفصل حتى لا تتجمّد الشاشة.
     */
    private void share(final long id, final boolean view, final boolean excel) {
        Toast.makeText(this, "جارٍ تجهيز التقرير...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                final java.io.File file = excel
                        ? new ExcelReport(this, db).build(id)
                        : new PdfReport(this, db).build(id);
                // نوع عام عند المشاركة حتى تقبله واتساب وبقية التطبيقات،
                // والنوع الدقيق عند الفتح ليختار برنامج الجداول.
                final String mime = excel
                        ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        : "application/pdf";
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    try {
                        android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                                this, getPackageName() + ".files", file);
                        Intent intent = view ? new Intent(Intent.ACTION_VIEW) : new Intent(Intent.ACTION_SEND);
                        if (view) {
                            intent.setDataAndType(uri, mime);
                        } else {
                            intent.setType(mime);
                            intent.putExtra(Intent.EXTRA_STREAM, uri);
                            intent.putExtra(Intent.EXTRA_SUBJECT, "وردية " + db.shiftCode(id));
                            intent.setClipData(android.content.ClipData.newRawUri("تقرير الوردية", uri));
                        }
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_ACTIVITY_NEW_TASK);
                        // بعض الأجهزة لا تمنح الإذن تلقائيًا، فيُمنح لكل تطبيق مرشَّح.
                        for (android.content.pm.ResolveInfo r :
                                getPackageManager().queryIntentActivities(intent, 0)) {
                            grantUriPermission(r.activityInfo.packageName, uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        }
                        if (view) {
                            // إن لم يوجد تطبيق للفتح، تُعرض قائمة المشاركة بدل الفشل.
                            if (intent.resolveActivity(getPackageManager()) != null) startActivity(intent);
                            else share(id, false, excel);
                        } else {
                            startActivity(Intent.createChooser(intent, "مشاركة التقرير"));
                        }
                    } catch (Exception e) {
                        // السبب الحقيقي يُعرض بدل رسالة عامة تخفي الخلل.
                        new AlertDialog.Builder(this).setTitle("تعذرت المشاركة")
                                .setMessage(String.valueOf(e.getMessage()))
                                .setPositiveButton("حسنًا", null).show();
                    }
                });
            } catch (Exception e) {
                final String why = String.valueOf(e.getMessage());
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed())
                        Toast.makeText(this, "تعذر إنشاء التقرير: " + why, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        t.setPadding(0, dp(2), 0, dp(2));
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT, 1);
        return t;
    }

    private String money(double value) {
        return String.format(Locale.US, value == Math.rint(value) ? "%,.0f" : "%,.2f", value);
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }
}
