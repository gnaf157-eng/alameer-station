package com.alameer.station.shifts;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * نسخ احتياطي واستعادة لقاعدة البيانات كاملة.
 * النسخة ملف SQLite واحد يمكن حفظه في درايف أو إرساله عبر واتساب.
 */
public final class Backup {
    public static final int REQUEST_RESTORE = 4201;
    private final Activity activity;

    public Backup(Activity activity){ this.activity = activity; }

    /** ينشئ نسخة ويفتح قائمة المشاركة لحفظها خارج الجهاز. */
    public void export(){
        try {
            File source = activity.getDatabasePath("alameer_station.db");
            if (!source.exists()) { toast("لا توجد بيانات لحفظها بعد."); return; }
            // ندمج سجل WAL في الملف الأساسي حتى لا تضيع آخر الحركات.
            new Db(activity).getWritableDatabase().rawQuery("PRAGMA wal_checkpoint(FULL)", null).close();

            File dir = new File(activity.getCacheDir(), "exports");
            dir.mkdirs();
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());
            File target = new File(dir, "alameer-backup-" + stamp + ".db");
            copy(source, target);

            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".files", target);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/octet-stream");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_SUBJECT, "نسخة احتياطية — محطة الأمير " + stamp);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent, "حفظ النسخة الاحتياطية"));
        } catch (Exception e) {
            toast("تعذر إنشاء النسخة الاحتياطية.");
        }
    }

    /** يفتح منتقي الملفات لاختيار نسخة سابقة. */
    public void pickForRestore(){
        new AlertDialog.Builder(activity)
            .setTitle("استعادة نسخة احتياطية")
            .setMessage("سيُستبدل كل ما في التطبيق حاليًا ببيانات النسخة المختارة. لا يمكن التراجع.\n\nيُفضّل حفظ نسخة من الوضع الحالي أولًا.")
            .setPositiveButton("اختيار الملف", (d, w) -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                activity.startActivityForResult(Intent.createChooser(intent, "اختر ملف النسخة"), REQUEST_RESTORE);
            })
            .setNegativeButton("إلغاء", null)
            .show();
    }

    /** يستعيد قاعدة البيانات من الملف المختار بعد التحقق من صلاحيته. */
    public void restoreFrom(Uri uri){
        File database = activity.getDatabasePath("alameer_station.db");
        File staging = new File(activity.getCacheDir(), "restore-staging.db");
        File rollback = new File(activity.getCacheDir(), "rollback.db");
        try {
            try (InputStream in = activity.getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(staging)) {
                if (in == null) { toast("تعذر قراءة الملف."); return; }
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            }
            if (!looksLikeOurDatabase(staging)) {
                toast("هذا الملف ليس نسخة احتياطية صالحة للتطبيق.");
                staging.delete();
                return;
            }
            if (database.exists()) copy(database, rollback);
            // تُدمج الكتابات المعلّقة ثم يُغلق الاتصال، وإلا كتب فوق الملف الجديد.
            Db open = new Db(activity);
            try {
                open.getWritableDatabase().rawQuery("PRAGMA wal_checkpoint(FULL)", null).close();
            } catch (Exception ignored) {}
            open.close();
            deleteSideFiles(database);
            copy(staging, database);
            staging.delete();
            rollback.delete();

            new AlertDialog.Builder(activity)
                .setTitle("تمت الاستعادة")
                .setMessage("استُعيدت البيانات بنجاح. سيُغلق التطبيق الآن، افتحه من جديد.")
                .setCancelable(false)
                .setPositiveButton("إغلاق", (d, w) -> {
                    activity.finishAffinity();
                    System.exit(0);
                })
                .show();
        } catch (Exception e) {
            try { if (rollback.exists()) copy(rollback, database); } catch (Exception ignored) {}
            toast("فشلت الاستعادة. بقيت بياناتك كما هي.");
        }
    }

    /** يتحقق أن الملف قاعدة SQLite تحتوي جداول التطبيق. */
    private boolean looksLikeOurDatabase(File file){
        android.database.sqlite.SQLiteDatabase probe = null;
        try {
            probe = android.database.sqlite.SQLiteDatabase.openDatabase(
                    file.getPath(), null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY);
            try (android.database.Cursor c = probe.rawQuery(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('workers','pumps','shifts','readings','movements')", null)) {
                return c.moveToFirst() && c.getInt(0) == 5;
            }
        } catch (Exception e) {
            return false;
        } finally {
            if (probe != null) try { probe.close(); } catch (Exception ignored) {}
        }
    }

    private void deleteSideFiles(File database){
        new File(database.getPath() + "-wal").delete();
        new File(database.getPath() + "-shm").delete();
        new File(database.getPath() + "-journal").delete();
    }

    private void copy(File from, File to) throws Exception {
        try (InputStream in = new java.io.FileInputStream(from);
             OutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
        }
    }

    private void toast(String message){ Toast.makeText(activity, message, Toast.LENGTH_LONG).show(); }
}
