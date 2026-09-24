package com.alameer.station.shifts;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.app.Activity;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** لوحة التحكم: حالة الورديات والصناديق والمواد والديون بألوان هوية التطبيق. */
public class ControlPanelActivity extends Activity {
    private Db db;
    private LinearLayout content;

    // الحدود تُقرأ من الإعدادات، والثوابت هنا قيم احتياطية فقط.
    private double LOW_CASH = 50000;
    private int STALE_DAYS = 21;
    private int LOW_STOCK = 25;
    private static final int AMBER = 0xffB86A00;

    private boolean debtsOpen = false;

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
        words.addView(text("لوحة التحكم", 19, Color.WHITE, true));
        words.addView(text(Branding.stationName(db) + "  •  " + ShiftDates.today(), 11, 0xffE6E6E6, false));
        header.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        shell.addView(header);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(10), dp(14), dp(24));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(14));
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(shell);Util.safeInsets(shell);
    }

    @Override protected void onResume() {
        super.onResume();
        if (isFinishing() || content == null) return;
        // كل حركة يدوية تدخل الدفتر، فيعبّر التوازن عن كل الأموال لا الورديات وحدها.
        // Opening a dashboard must never create or alter accounting entries.
        LOW_CASH = db.lowCash();
        STALE_DAYS = db.staleDays();
        LOW_STOCK = db.lowStockPercent();
        build();
    }

    private void build() {
        content.removeAllViews();
        int delay = 0;
        delay = add(capitalCard(), delay);
        delay = add(auditBoard(), delay);
        delay = add(currentShift(), delay);
        delay = add(statusRow(), delay);
        delay = add(collapsible("المخزون",stockSection()), delay);
        delay = add(collapsible("الصناديق",cashSection()), delay);
        add(debtSection(), delay);
    }

    private View collapsible(String title,View body){
        LinearLayout c=StationUi.column(this);body.setVisibility(View.GONE);
        c.addView(StationUi.button(this,title+"  ▾",false,()->body.setVisibility(body.getVisibility()==View.GONE?View.VISIBLE:View.GONE)));c.addView(body);return c;
    }
    private View currentShift(){
        LinearLayout c=StationUi.card(this);c.addView(StationUi.text(this,"مطابقة الوردية الحالية",18,true));
        try(Cursor row=db.getReadableDatabase().rawQuery("SELECT s.id,w.reviewed FROM shifts s JOIN shift_workspace w ON w.shift_id=s.id WHERE s.status='OPEN' ORDER BY s.id LIMIT 1",null)){
            if(row.moveToFirst()){long id=row.getLong(0);int r=row.getInt(1);c.addView(StationUi.text(this,db.shiftCode(id)+" • "+db.shiftDate(id),13,false));
                c.addView(StationUi.text(this,"العامل: "+((r&1)!=0?"تمت المطابقة":"قيد المطابقة")+" • فرق "+Calc.money(db.balance(id))+" ر.ي",15,false));
                c.addView(StationUi.text(this,"الصناديق: "+((r&2)!=0?"تمت المطابقة":"قيد المطابقة")+"
المواد: "+((r&4)!=0?"تمت المطابقة":"قيد المطابقة"),15,false));
            }else c.addView(StationUi.text(this,"لا توجد وردية مفتوحة",15,false));
        }return c;
    }

    /** رأس المال: بطاقة بارزة، والضغط عليها يكشف مكوّناته. */
    private View capitalCard() {
        final double cash = db.cashboxesTotal();
        final double debts = db.debtsTotal();
        final double credits = db.creditsTotal();
        final double netDebt = debts - credits;
        final double stock = db.stockValueTotal();
        // القاعدة الموحّدة: الموجب لنا والسالب علينا، فالرصيد يُجمع كما هو.
        final double owed = db.supplierBalance();
        final double capital = cash + netDebt + stock + owed;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(16), dp(18), dp(16));
        box.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                Util.round(Util.NAVY, dp(18)), null));
        box.setElevation(dp(3));
        box.setClickable(true);
        box.setOnClickListener(v -> showCapitalDetail(cash, netDebt, stock, owed, capital));

        box.addView(text("صافي الأصول التقديري", 13, 0xffE6E6E6, false));
        TextView grand = text(whole(capital) + "  ر.ي", 30,
                capital < -0.009 ? 0xffFFB3BC : Color.WHITE, true);
        grand.setTextDirection(View.TEXT_DIRECTION_LTR);
        grand.setPadding(0, dp(4), 0, dp(8));
        box.addView(grand);
        box.addView(text("الصناديق + الديون + المخزون + الموردين  •  اضغط للتفصيل",
                11, 0xffE6E6E6, false));
        return box;
    }

    /** تفصيل رأس المال: كل بند بقيمته وأثره. */
    private void showCapitalDetail(double cash, double netDebt, double stock,
                                   double owed, double capital) {
        StringBuilder sb = new StringBuilder();
        sb.append("الموجودات\n");
        sb.append("\n• نقد الصناديق\n   ").append(whole(cash)).append(" ر.ي\n");
        sb.append("\n• صافي الديون\n   ").append(whole(netDebt)).append(" ر.ي");
        double gross = db.debtsTotal(), owedUs = db.creditsTotal();
        sb.append("\n   لنا ").append(whole(gross));
        if (owedUs > 0.009) sb.append("  •  علينا ").append(whole(owedUs));
        sb.append("\n");
        // أكبر الأرصدة، لتُطابق بندًا ببند مع أي سجل خارجي.
        int shown = 0;
        try (Cursor c = db.debtors(false)) {
            while (c.moveToNext() && shown < 12) {
                double bal = c.getDouble(7);
                if (Math.abs(bal) < 0.01) continue;
                shown++;
                sb.append("\n   ").append(c.getString(1)).append("  ").append(whole(bal));
            }
        }
        if (shown > 0) sb.append("\n");
        sb.append("\n• قيمة المخزون بالتكلفة\n   ").append(whole(stock)).append(" ر.ي");
        // تفصيل كل مادة: اللترات وتكلفتها، ليسهل مطابقتها بجدولك.
        for (String m : Db.MATERIALS) {
            double litres = Math.max(0, db.materialSummary(m)[3]);
            double each = db.stockValue(m);
            if (litres < 0.01 && each < 0.01) continue;
            sb.append("\n   ").append(m).append("  ").append(whole(litres)).append(" لتر");
            sb.append("  =  ").append(whole(each));
        }
        sb.append("\n");
        // كل مورّد في جانبه: الموجب موجودات والسالب مطلوبات.
        double oil = db.supplierBalance("OIL"), gas = db.supplierBalance("GAS");
        double assetSide = cash + netDebt + stock + Math.max(0, oil) + Math.max(0, gas);
        if (oil > 0.009)
            sb.append("\n• رصيد لنا عند شركة النفط\n   ").append(whole(oil)).append(" ر.ي\n");
        if (gas > 0.009)
            sb.append("\n• رصيد لنا عند شركة الغاز\n   ").append(whole(gas)).append(" ر.ي\n");
        sb.append("\n   مجموع الموجودات ").append(whole(assetSide)).append(" ر.ي\n");

        double dueOil = oil < 0 ? -oil : 0, dueGas = gas < 0 ? -gas : 0;
        sb.append("\n\nالمطلوبات\n");
        sb.append("\n• مستحق لشركة النفط\n   ").append(whole(dueOil)).append(" ر.ي\n");
        sb.append("\n• مستحق لشركة الغاز\n   ").append(whole(dueGas)).append(" ر.ي\n");
        sb.append("\n   مجموع المطلوبات ").append(whole(dueOil + dueGas)).append(" ر.ي\n");

        sb.append("\n\nصافي الأصول التقديري = الموجودات − المطلوبات\n");
        sb.append("   ").append(whole(capital)).append(" ر.ي");
        if (capital < -0.009)
            sb.append("\n\n⚠ المطلوبات تتجاوز الموجودات.");

        new android.app.AlertDialog.Builder(this)
                .setTitle("تفصيل صافي الأصول التقديري")
                .setMessage(sb.toString())
                .setPositiveButton("حسنًا", null)
                .show();
    }

    private int add(View section, int delay) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(6), 0, dp(6));
        content.addView(section, p);
        section.setAlpha(0f);
        section.setTranslationY(dp(18));
        section.animate().alpha(1f).translationY(0)
                .setStartDelay(delay).setDuration(320)
                .setInterpolator(new DecelerateInterpolator()).start();
        return delay + 70;
    }

    // ==================== لوحة الرقابة المحاسبية ====================

    /**
     * أعلى الشاشة: إجمالي المدين والدائن والفرق بينهما وحالة النظام.
     * الضغط على الفرق يفتح الحركات المسبّبة له.
     */
    private View auditBoard() {
        double debit = db.journalDebit();
        double credit = db.journalCredit();
        double gap = debit - credit;

        int unbalanced = 0;
        try (Cursor c = db.unbalancedEntries()) { unbalanced = c.getCount(); }
        int unpostedCount = 0;
        try (Cursor c = db.unpostedShifts()) { unpostedCount = c.getCount(); }
        final int unposted = unpostedCount;
        int lockedPeriods = 0;
        try (Cursor c = db.periodLocks()) { lockedPeriods = c.getCount(); }

        boolean ok = Journal.balanced(gap) && unbalanced == 0 && unposted == 0;
        int tint = ok ? Util.GREEN : Util.RED;
        final int flagged = unbalanced + unposted;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(15), dp(16), dp(15));
        box.setBackground(Util.round(Util.NAVY, dp(18)));
        box.setElevation(dp(3));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("الرقابة المحاسبية — القيد المزدوج", 15, Color.WHITE, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        TextView state = text(ok ? "متوازن" : "يحتاج مراجعة", 12, Color.WHITE, true);
        state.setPadding(dp(12), dp(5), dp(12), dp(5));
        state.setBackground(Util.round(tint, dp(10)));
        head.addView(state);
        box.addView(head);

        LinearLayout sums = new LinearLayout(this);
        sums.setPadding(0, dp(14), 0, 0);
        sums.addView(sideCell("إجمالي المدين", debit), sideParams());
        sums.addView(sideCell("إجمالي الدائن", credit), sideParams());
        box.addView(sums);

        // الفرق: بطاقة قابلة للضغط تكشف القيود والورديات المسبّبة.
        LinearLayout diff = new LinearLayout(this);
        diff.setOrientation(LinearLayout.VERTICAL);
        diff.setPadding(dp(13), dp(11), dp(13), dp(11));
        diff.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                Util.round(ok ? 0x1A12805C : 0x33B42335, dp(13)), null));
        diff.setClickable(true);
        diff.setOnClickListener(v -> showAuditDetail());

        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.addView(text("الفرق", 12, 0xffE6E6E6, false), new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = text(money(Math.abs(gap)) + " ر.ي", 20, ok ? Color.WHITE : 0xffFFB3BC, true);
        value.setTextDirection(View.TEXT_DIRECTION_LTR);
        line.addView(value);
        diff.addView(line);

        // الفرق صفر مع وجود ورديات بلا قيد يعني دفترًا ناقصًا لا دفترًا سليمًا.
        String why;
        if (flagged == 0) why = Journal.explain(gap) + "  •  اضغط للتفصيل";
        else if (unposted > 0 && Journal.balanced(gap))
            why = unposted + " وردية مُغلقة لم تُرحّل إلى الدفتر  •  اضغط للترحيل";
        else why = Journal.explain(gap) + "  •  " + flagged + " بند يحتاج مراجعة  •  اضغط للتفصيل";
        TextView note = text(why, 11, 0xffE6E6E6, false);
        note.setPadding(0, dp(4), 0, 0);
        diff.addView(note);

        LinearLayout.LayoutParams gapParams = new LinearLayout.LayoutParams(-1, -2);
        gapParams.setMargins(0, dp(12), 0, 0);
        box.addView(diff, gapParams);

        // الحساب الوسيط: حركات بلا طرف محدّد، تُصرَّف من شاشتها.
        double suspense = db.suspenseBalance();
        if (Math.abs(suspense) >= 0.01) {
            TextView pending = text("⚠ حساب وسيط " + money(Math.abs(suspense))
                    + " ر.ي — اضغط لتصريفه", 12, Color.WHITE, true);
            pending.setGravity(Gravity.CENTER);
            pending.setPadding(dp(10), dp(9), dp(10), dp(9));
            pending.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                    Util.round(0xffB86A00, dp(11)), null));
            pending.setClickable(true);
            pending.setOnClickListener(v -> startActivity(LedgerActivity.intent(this,"journal")));
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2);
            pp.setMargins(0, dp(9), 0, 0);
            box.addView(pending, pp);
        }

        if (lockedPeriods > 0) {
            // سطر الفترات المقفلة قابل للضغط حتى يمكن فتحها من مكانها.
            TextView locks = text("🔒 " + lockedPeriods + " فترة مقفلة — اضغط لفتحها أو إدارتها", 11, 0xffFFD79A, true);
            locks.setPadding(dp(10), dp(8), dp(10), dp(8));
            locks.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                    Util.round(0x22FFFFFF, dp(10)), null));
            locks.setClickable(true);
            locks.setOnClickListener(v -> managePeriods());
            LinearLayout.LayoutParams lockParams = new LinearLayout.LayoutParams(-1, -2);
            lockParams.setMargins(0, dp(9), 0, 0);
            box.addView(locks, lockParams);
        }
        return box;
    }

    private LinearLayout.LayoutParams sideParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        p.setMargins(dp(3), 0, dp(3), 0);
        return p;
    }

    private View sideCell(String title, double amount) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(dp(12), dp(10), dp(12), dp(10));
        cell.setBackground(Util.round(0x22FFFFFF, dp(13)));
        cell.addView(text(title, 11, 0xffE6E6E6, false));
        TextView v = text(money(amount), 17, Color.WHITE, true);
        v.setTextDirection(View.TEXT_DIRECTION_LTR);
        v.setPadding(0, dp(3), 0, 0);
        cell.addView(v);
        return cell;
    }

    /** تفصيل الفرق: ميزان المراجعة، والقيود المختلّة، والورديات بلا قيد، والفترات المقفلة. */
    private void showAuditDetail() {
        StringBuilder sb = new StringBuilder();
        double gap = db.journalDebit() - db.journalCredit();
        sb.append(Journal.explain(gap)).append("\n");

        sb.append("\n— ميزان المراجعة —\n");
        try (Cursor c = db.trialBalance()) {
            if (c.getCount() == 0) sb.append("لا قيود مسجّلة بعد.\n");
            while (c.moveToNext()) {
                sb.append("\n• ").append(c.getString(0))
                  .append("\n   مدين ").append(money(c.getDouble(1)))
                  .append(" • دائن ").append(money(c.getDouble(2)))
                  .append("\n   الرصيد ").append(money(Math.abs(c.getDouble(3))))
                  .append(c.getDouble(3) >= 0 ? " مدين\n" : " دائن\n");
            }
        }

        try (Cursor c = db.unbalancedEntries()) {
            if (c.getCount() > 0) {
                sb.append("\n— قيود غير متوازنة —\n");
                while (c.moveToNext())
                    sb.append("\n• قيد #").append(c.getLong(0)).append(" — ").append(c.getString(1))
                      .append("\n   ").append(c.getString(2))
                      .append("\n   مدين ").append(money(c.getDouble(3)))
                      .append(" • دائن ").append(money(c.getDouble(4))).append("\n");
            }
        }

        try (Cursor c = db.doublePosted()) {
            if (c.getCount() > 0) {
                sb.append("\n— ورديات مُرحّلة أكثر من مرة —\n");
                while (c.moveToNext())
                    sb.append("\n• وردية #").append(c.getLong(0)).append(" — ").append(c.getString(1))
                      .append("\n   ").append(c.getString(2))
                      .append("  •  ").append(c.getInt(3)).append(" حركة مخزون\n");
            }
        }

        try (Cursor c = db.unpostedShifts()) {
            if (c.getCount() > 0) {
                sb.append("\n— ورديات مُغلقة بلا قيد —\n");
                while (c.moveToNext())
                    sb.append("\n• وردية #").append(c.getLong(0)).append(" — ").append(c.getString(1))
                      .append("\n   ").append(c.getString(2))
                      .append(" • فرق ").append(money(Math.abs(c.getDouble(3)))).append(" ر.ي\n");
            }
        }

        try (Cursor c = db.periodLocks()) {
            if (c.getCount() > 0) {
                sb.append("\n— فترات مقفلة —\n");
                while (c.moveToNext())
                    sb.append("\n• ").append(c.getString(0))
                      .append("  (أقفلها ").append(c.getString(2)).append(")\n");
            }
        }

        final java.util.List<Long> doubled = new java.util.ArrayList<>();
        try (Cursor c = db.doublePosted()) { while (c.moveToNext()) doubled.add(c.getLong(0)); }
        int waiting = 0;
        try (Cursor c = db.unpostedShifts()) { waiting = c.getCount(); }
        android.app.AlertDialog.Builder ask = new android.app.AlertDialog.Builder(this)
                .setTitle("تفصيل حالة التوازن")
                .setMessage(sb.toString())
                .setNeutralButton("سجل التدقيق", (d, w) -> showAuditLog());
        ask.setPositiveButton("حسنًا", null);
        ask.show();
    }

    /**
     * يرحّل الورديات القديمة إلى الدفتر.
     * إذا كانت فترتها مقفلة، يطلب السبب مرة واحدة ثم يفتحها ويرحّل ويعيد إقفالها تلقائيًا.
     */
    /** يصلح الورديات المرحّلة مرتين: يلغي الأثر المكرّر ويعيد الترحيل مرة واحدة. */
    private void repairDoubles(final java.util.List<Long> ids) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("إصلاح الترحيل المكرّر")
                .setMessage("سيُلغى أثر " + ids.size() + " وردية من الصناديق والديون والمخزون،"
                        + " ثم تُرحّل كل واحدة مرة واحدة فقط.\n\n"
                        + "القيود المكرّرة تُعكس ولا تُحذف، فيبقى الأثر في سجل التدقيق.")
                .setPositiveButton("إصلاح", (d, w) -> {
                    int done = 0;
                    StringBuilder failed = new StringBuilder();
                    for (long id : ids) {
                        try { db.repostShift(id); done++; }
                        catch (Exception e) { failed.append("\n• #").append(id).append(": ").append(e.getMessage()); }
                    }
                    build();
                    new android.app.AlertDialog.Builder(this).setTitle("اكتمل الإصلاح")
                            .setMessage("أُصلحت " + done + " وردية."
                                    + (failed.length() == 0 ? "" : "\n\nتعذّر:" + failed))
                            .setPositiveButton("حسنًا", null).show();
                })
                .setNegativeButton("إلغاء", null).show();
    }

    private void runBacklog() {
        final String blocked = db.blockingPeriod();
        if (blocked.isEmpty()) { doBacklog(""); return; }

        final EditText reason = new EditText(this);
        reason.setHint("سبب فتح الفترة مؤقتًا");
        reason.setText("ترحيل ورديات مُغلقة قبل تفعيل الدفتر");
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(24), dp(10), dp(24), 0);
        wrap.addView(reason);

        new android.app.AlertDialog.Builder(this)
                .setTitle("الفترة " + blocked + " مقفلة")
                .setMessage("سأفتح الفترة، وأرحّل الورديات، ثم أعيد إقفالها — كل ذلك مسجّل في سجل التدقيق.")
                .setView(wrap)
                .setPositiveButton("افتح ورحّل", (d, w) -> {
                    String why = reason.getText().toString().trim();
                    if (why.isEmpty()) why = "ترحيل ورديات مُغلقة";
                    try { db.unlockPeriod(blocked, why); }
                    catch (Exception e) {
                        Toast.makeText(this, String.valueOf(e.getMessage()), Toast.LENGTH_LONG).show();
                        return;
                    }
                    doBacklog(blocked);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    /** ينفّذ الترحيل، ثم يعيد إقفال الفترة إن كانت مقفلة قبله ونجح الترحيل كاملًا. */
    private void doBacklog(String relock) {
        String report;
        try { report = db.journalBacklog(); }
        catch (Exception e) { report = "تعذّر الترحيل: " + e.getMessage(); }

        int stillWaiting = 0;
        try (Cursor c = db.unpostedShifts()) { stillWaiting = c.getCount(); }
        if (!relock.isEmpty() && stillWaiting == 0) {
            try {
                db.lockPeriod(relock, "أُعيد الإقفال بعد الترحيل");
                report = report + "\n\nأُعيد إقفال الفترة " + relock + ".";
            } catch (Exception e) {
                report = report + "\n\nتعذّر إعادة الإقفال: " + e.getMessage();
            }
        } else if (!relock.isEmpty()) {
            report = report + "\n\nبقيت الفترة " + relock + " مفتوحة حتى تكتمل الورديات.";
        }
        build();

        int unexplained = 0;
        try (Cursor c = db.unexplainedShifts()) { unexplained = c.getCount(); }
        android.app.AlertDialog.Builder done = new android.app.AlertDialog.Builder(this)
                .setTitle("ترحيل الورديات")
                .setMessage(report);
        if (unexplained > 0) done.setPositiveButton("تعليل الفروقات", (d, w) -> settleDialog());
        done.setNegativeButton("حسنًا", null);
        done.show();
    }

    /** يطلب سبب الفرق لكل وردية مُغلقة بفرق بلا تعليل، واحدة تلو الأخرى. */
    private void settleDialog() {
        long id = 0; String who = ""; String date = ""; double gap = 0;
        try (Cursor c = db.unexplainedShifts()) {
            if (!c.moveToFirst()) { build(); return; }
            id = c.getLong(0); who = c.getString(1); date = c.getString(2); gap = c.getDouble(3);
        }
        final long shiftId = id;
        final EditText input = new EditText(this);
        input.setHint("سبب الفرق");
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(24), dp(10), dp(24), 0);
        wrap.addView(input);

        new android.app.AlertDialog.Builder(this)
                .setTitle("وردية #" + shiftId + " — " + who)
                .setMessage(date + "\nالفرق " + money(Math.abs(gap)) + " ر.ي "
                        + (gap > 0 ? "(عجز)" : "(زيادة)")
                        + "\n\nاكتب سبب الفرق حتى تُرحّل الوردية إلى الدفتر.")
                .setView(wrap)
                .setPositiveButton("حفظ وترحيل", (d, w) -> {
                    try {
                        db.settleShift(shiftId, input.getText().toString());
                    } catch (Exception e) {
                        Toast.makeText(this, String.valueOf(e.getMessage()), Toast.LENGTH_LONG).show();
                        return;
                    }
                    // السبب حُفظ؛ الترحيل قد يصطدم بفترة مقفلة فيُعالَج تلقائيًا.
                    try {
                        db.journalShift(shiftId);
                        build();
                        settleDialog();
                    } catch (Exception e) {
                        build();
                        runBacklog();
                    }
                })
                .setNegativeButton("لاحقًا", null)
                .show();
    }

    /** قائمة الفترات المقفلة: اختر واحدة لفتحها بسبب مكتوب. */
    private void managePeriods() {
        final java.util.List<String> periods = new java.util.ArrayList<>();
        final java.util.List<String> labels = new java.util.ArrayList<>();
        try (Cursor c = db.periodLocks()) {
            while (c.moveToNext()) {
                periods.add(c.getString(0));
                labels.add(c.getString(0) + "   (أقفلها " + c.getString(2) + ")");
            }
        }
        if (periods.isEmpty()) { lockPeriodDialog(); return; }
        new android.app.AlertDialog.Builder(this)
                .setTitle("الفترات المقفلة")
                .setItems(labels.toArray(new String[0]), (d, which) -> unlockDialog(periods.get(which)))
                .setPositiveButton("إقفال فترة جديدة", (d, w) -> lockPeriodDialog())
                .setNegativeButton("إغلاق", null)
                .show();
    }

    /** فتح فترة مقفلة بسبب مكتوب يُحفظ في سجل التدقيق. */
    private void unlockDialog(final String period) {
        final EditText reason = new EditText(this);
        reason.setHint("سبب فتح الفترة");
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(24), dp(10), dp(24), 0);
        wrap.addView(reason);
        new android.app.AlertDialog.Builder(this)
                .setTitle("فتح الفترة " + period)
                .setMessage("الفترة المقفلة ترفض أي قيد جديد، ومنها ترحيل الورديات المُغلقة فيها.")
                .setView(wrap)
                .setPositiveButton("فتح", (d, w) -> {
                    try {
                        db.unlockPeriod(period, reason.getText().toString());
                        Toast.makeText(this, "فُتحت الفترة " + period, Toast.LENGTH_SHORT).show();
                        build();
                    } catch (Exception e) {
                        Toast.makeText(this, String.valueOf(e.getMessage()), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    /** إقفال فترة محاسبية أو فتحها بسبب مكتوب. */
    private void lockPeriodDialog() {
        final EditText input = new EditText(this);
        input.setHint("الفترة بصيغة 2026-09");
        input.setText(ShiftDates.today().length() >= 7 ? ShiftDates.today().substring(0, 7) : "");
        input.setGravity(Gravity.CENTER);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(24), dp(10), dp(24), 0);
        wrap.addView(input);
        final EditText note = new EditText(this);
        note.setHint("ملاحظة أو سبب الفتح");
        wrap.addView(note);

        new android.app.AlertDialog.Builder(this)
                .setTitle("إقفال الفترات")
                .setMessage("الفترة المقفلة لا تقبل أي قيد جديد ولا قيدًا عكسيًا حتى تُفتح.")
                .setView(wrap)
                .setPositiveButton("إقفال", (d, w) -> {
                    try {
                        db.lockPeriod(input.getText().toString().trim(), note.getText().toString().trim());
                        Toast.makeText(this, "أُقفلت الفترة", Toast.LENGTH_SHORT).show();
                        build();
                    } catch (Exception e) {
                        Toast.makeText(this, String.valueOf(e.getMessage()), Toast.LENGTH_LONG).show();
                    }
                })
                .setNeutralButton("فتح", (d, w) -> {
                    try {
                        db.unlockPeriod(input.getText().toString().trim(), note.getText().toString().trim());
                        Toast.makeText(this, "فُتحت الفترة", Toast.LENGTH_SHORT).show();
                        build();
                    } catch (Exception e) {
                        Toast.makeText(this, String.valueOf(e.getMessage()), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    /** سجل التدقيق: من فعل ماذا ومتى وبأي قيمة قبل وبعد. */
    private void showAuditLog() {
        StringBuilder sb = new StringBuilder();
        try (Cursor c = db.auditLog(60)) {
            if (c.getCount() == 0) sb.append("لا حركات مسجّلة بعد.");
            while (c.moveToNext()) {
                sb.append("• ").append(c.getString(1)).append("  —  ").append(c.getString(2))
                  .append("\n   ").append(c.getString(3)).append("  ").append(c.getString(4));
                String before = c.getString(5), after = c.getString(6), reason = c.getString(7);
                if (!before.isEmpty() || !after.isEmpty())
                    sb.append("\n   ").append(before.isEmpty() ? "—" : before).append("  ←  ")
                      .append(after.isEmpty() ? "—" : after);
                if (!reason.isEmpty()) sb.append("\n   السبب: ").append(reason);
                sb.append("\n\n");
            }
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("سجل التدقيق")
                .setMessage(sb.toString())
                .setPositiveButton("حسنًا", null)
                .show();
    }

    // ==================== صف الحالة العلوي ====================

    /** ثلاث بطاقات: الورديات، الصناديق، المواد — سليم أو يحتاج انتباه. */
    private View statusRow() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);

        int pending = db.pendingCount();
        row.addView(statusCard("الورديات",
                pending == 0 ? "0" : String.valueOf(pending),
                pending == 0 ? "سليم" : "بانتظار الاعتماد", pending == 0), cell());

        int badBoxes = 0;
        double cash = db.cashboxesTotal();
        try (Cursor c = db.cashboxes(true)) {
            while (c.moveToNext()) if (c.getDouble(6) < LOW_CASH) badBoxes++;
        }
        row.addView(statusCard("الصناديق", money(cash) + " ر.ي",
                badBoxes == 0 ? "سليم" : badBoxes + " صندوق منخفض", badBoxes == 0), cell());

        int badMaterials = 0;
        for (String material : Db.MATERIALS) {
            double left = db.materialSummary(material)[3];
            if (left * 100 / capacity(material) < LOW_STOCK) badMaterials++;
        }
        row.addView(statusCard("المواد", badMaterials == 0 ? "كامل" : badMaterials + " مادة",
                badMaterials == 0 ? "سليم" : "تحتاج تعبئة", badMaterials == 0), cell());
        return row;
    }

    private LinearLayout.LayoutParams cell() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        p.setMargins(dp(4), 0, dp(4), 0);
        return p;
    }

    private View statusCard(String title, String value, String state, boolean good) {
        int tint = good ? Util.GREEN : AMBER;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(8), dp(14), dp(8), dp(14));
        box.setBackground(Util.round(Color.WHITE, dp(16)));
        box.setElevation(dp(2));

        TextView name = text(title, 12, 0xff626970, false);
        name.setGravity(Gravity.CENTER);
        box.addView(name, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER);
        line.setPadding(0, dp(7), 0, dp(6));
        View mark = new View(this);
        mark.setBackground(Util.round(tint, dp(6)));
        line.addView(mark, new LinearLayout.LayoutParams(dp(12), dp(12)));
        TextView number = text(value, 15, tint, true);
        number.setPadding(dp(7), 0, 0, 0);
        number.setTextDirection(View.TEXT_DIRECTION_LTR);
        line.addView(number);
        box.addView(line, new LinearLayout.LayoutParams(-1, -2));

        TextView badge = text(state, 10, tint, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(9), dp(3), dp(9), dp(3));
        badge.setBackground(Util.round(soften(tint), dp(8)));
        LinearLayout wrap = new LinearLayout(this);
        wrap.setGravity(Gravity.CENTER);
        wrap.addView(badge);
        box.addView(wrap, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }

    // ==================== المخزون (ظاهر دائمًا) ====================

    private View stockSection() {
        LinearLayout box = panel();
        box.addView(sectionHead("المخزون"));
        for (String material : Db.MATERIALS) {
            double left = Math.max(0, db.materialSummary(material)[3]);
            double cap = capacity(material);
            int percent = (int) Math.round(Math.min(100, left * 100 / cap));
            int tint = percent < 15 ? Util.RED : percent < 30 ? AMBER : Util.GREEN;

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(13), dp(12), dp(13), dp(13));
            card.setBackground(Util.round(0xffF7FAFE, dp(13)));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.setMargins(dp(10), dp(5), dp(10), dp(5));
            card.setLayoutParams(cp);

            LinearLayout top = new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout words = new LinearLayout(this);
            words.setOrientation(LinearLayout.VERTICAL);
            words.addView(text(material, 16, Util.NAVY, true));
            words.addView(text("السعة " + money(cap) + "  •  الحالي " + money(left), 11, 0xff626970, false));
            top.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
            TextView amount = text(money(left), 18, tint, true);
            amount.setTextDirection(View.TEXT_DIRECTION_LTR);
            top.addView(amount);
            card.addView(top);

            card.addView(bar(percent, tint), barParams());
            box.addView(card);
        }
        return box;
    }

    // ==================== الصناديق (ظاهرة دائمًا) ====================

    private View cashSection() {
        LinearLayout box = panel();
        box.addView(sectionHead("الصناديق"));

        // تُقرأ أولًا لتُرتَّب بالأكبر رصيدًا ويُعرف نصيب كل صندوق.
        final java.util.List<String> names = new java.util.ArrayList<>();
        final java.util.List<double[]> figures = new java.util.ArrayList<>();
        double total = 0, biggest = 0;
        int low = 0;
        try (Cursor c = db.cashboxes(true)) {
            while (c.moveToNext()) {
                double in = c.getDouble(4), out = c.getDouble(5), balance = c.getDouble(6);
                names.add(c.getString(1));
                figures.add(new double[]{balance, in, out});
                total += balance;
                if (balance > biggest) biggest = balance;
                if (balance < LOW_CASH) low++;
            }
        }
        if (names.isEmpty()) {
            box.addView(emptyLine("لم تُنشئ صناديق بعد."));
            return box;
        }

        // ترتيب تنازلي: الصندوق الأكبر أولًا.
        for (int i = 0; i < names.size(); i++)
            for (int j = i + 1; j < names.size(); j++)
                if (figures.get(j)[0] > figures.get(i)[0]) {
                    double[] f = figures.get(i); figures.set(i, figures.get(j)); figures.set(j, f);
                    String n = names.get(i); names.set(i, names.get(j)); names.set(j, n);
                }

        // الإجمالي في الأعلى، فهو أول ما يبحث عنه المدير.
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(14), dp(6), dp(14), dp(12));
        head.addView(text("إجمالي النقد في " + names.size() + " صندوق", 12, 0xff626970, false));
        TextView grand = text(money(total) + " ر.ي", 26, total < 0 ? Util.RED : Util.NAVY, true);
        grand.setTextDirection(View.TEXT_DIRECTION_LTR);
        head.addView(grand);
        if (low > 0)
            head.addView(text("⚠ " + low + " صندوق تحت حدّ التنبيه", 11, AMBER, true));
        box.addView(head);
        box.addView(divider());

        for (int i = 0; i < names.size(); i++) {
            double balance = figures.get(i)[0], in = figures.get(i)[1], out = figures.get(i)[2];
            int tint = balance < 0 ? Util.RED : balance < LOW_CASH ? AMBER : Util.GREEN;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(14), dp(11), dp(14), dp(11));
            // بعرض كامل، وإلا انهار الوزن والتصق المبلغ بالاسم.
            row.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            // الاسم في طرف والرصيد في الطرف المقابل.
            TextView name = text(names.get(i), 15, Util.NAVY, true);
            name.setMaxLines(1);
            name.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            line.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            TextView value = text(money(balance), 16, tint, true);
            value.setTextDirection(View.TEXT_DIRECTION_LTR);
            value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-2, -2);
            vp.setMargins(dp(12), 0, 0, 0);
            line.addView(value, vp);
            row.addView(line);

            // شريط يوضّح نصيب الصندوق من النقد كله.
            int share = biggest > 0 ? (int) Math.round(Math.max(0, balance) * 100 / biggest) : 0;
            LinearLayout track = new LinearLayout(this);
            track.setBackground(Util.round(0xffeef1f4, dp(3)));
            View fill = new View(this);
            fill.setBackground(Util.round(tint, dp(3)));
            track.addView(fill, new LinearLayout.LayoutParams(0, dp(5), Math.max(1, share)));
            View rest = new View(this);
            track.addView(rest, new LinearLayout.LayoutParams(0, dp(5), Math.max(1, 100 - share)));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, dp(5));
            tp.setMargins(0, dp(7), 0, dp(5));
            row.addView(track, tp);

            int percent = total > 0 ? (int) Math.round(Math.max(0, balance) * 100 / total) : 0;
            row.addView(text("وارد " + money(in) + "  •  صادر " + money(out)
                    + "  •  " + percent + "٪ من النقد", 11, 0xff626970, false));
            box.addView(row);
            if (i < names.size() - 1) box.addView(divider());
        }
        return box;
    }

    // ==================== الديون (قائمة منسدلة) ====================

    private View debtSection() {
        final LinearLayout box = panel();
        final LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);

        final TextView arrow = text(debtsOpen ? "▼" : "◀", 13, Util.ACCENT, true);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(14), dp(14), dp(14), dp(14));
        head.setClickable(true);
        head.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x14000000), null, null));
        head.addView(arrow);
        TextView title = text("الديون", 16, Util.NAVY, true);
        title.setPadding(dp(9), 0, 0, 0);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        double total = db.debtsTotal();
        TextView sum = text(money(total) + " ر.ي", 14, total > 0.009 ? Util.RED : Util.GREEN, true);
        sum.setTextDirection(View.TEXT_DIRECTION_LTR);
        head.addView(sum);
        head.setOnClickListener(v -> {
            debtsOpen = !debtsOpen;
            arrow.setText(debtsOpen ? "▼" : "◀");
            body.setVisibility(debtsOpen ? View.VISIBLE : View.GONE);
            if (debtsOpen) {
                body.setAlpha(0f);
                body.animate().alpha(1f).setDuration(260).start();
            }
        });
        box.addView(head);

        List<String[]> rows = new ArrayList<>();
        try (Cursor c = db.debtors(true)) {
            while (c.moveToNext()) {
                double balance = c.getDouble(7);
                if (Math.abs(balance) < 0.009) continue;
                rows.add(new String[]{c.getString(1), String.valueOf(balance),
                        String.valueOf(daysSince(db.debtorLastActivity(c.getLong(0))))});
            }
        }
        rows.sort((a, b) -> {
            double x = Double.parseDouble(a[1]), y = Double.parseDouble(b[1]);
            boolean xc = x < -0.009, yc = y < -0.009;
            if (xc != yc) return xc ? 1 : -1;
            return Double.compare(Math.abs(y), Math.abs(x));
        });
        if (rows.isEmpty()) body.addView(emptyLine("لا ديون مستحقة."));
        boolean creditHeaderShown = false;
        for (String[] row : rows) {
            if (Double.parseDouble(row[1]) < -0.009 && !creditHeaderShown) {
                creditHeaderShown = true;
                TextView creditHead = text("أرصدة لهم عندنا", 12, Util.GREEN, true);
                creditHead.setPadding(dp(14), dp(12), dp(14), dp(6));
                body.addView(creditHead);
            }
            double balance = Double.parseDouble(row[1]);
            int idle = Integer.parseInt(row[2]);
            // الرصيد السالب يعني أن الزبون دفع أكثر مما عليه.
            int tint = balance < -0.009 ? Util.GREEN : Util.RED;
            body.addView(flatRow(row[0], money(balance) + " ر.ي",
                    balance < -0.009 ? "له رصيد عندنا"
                    : idle >= STALE_DAYS ? "راكد " + idle + " يومًا" : null, tint));
            body.addView(divider());
        }
        body.setVisibility(debtsOpen ? View.VISIBLE : View.GONE);
        box.addView(body);
        return box;
    }

    // ==================== لبنات البناء ====================

    private LinearLayout panel() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Util.round(Color.WHITE, dp(16)));
        box.setElevation(dp(2));
        box.setPadding(0, 0, 0, dp(6));
        return box;
    }

    /** عنوان قسم ثابت غير قابل للطي. */
    private View sectionHead(String name) {
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(14), dp(14), dp(14), dp(10));
        View mark = new View(this);
        mark.setBackground(Util.round(Util.ACCENT, dp(2)));
        head.addView(mark, new LinearLayout.LayoutParams(dp(4), dp(18)));
        TextView t = text(name, 16, Util.NAVY, true);
        t.setPadding(dp(9), 0, 0, 0);
        head.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        return head;
    }

    private LinearLayout flatRow(String name, String value, int tint) {
        return flatRow(name, value, null, tint);
    }

    private LinearLayout flatRow(String name, String value, String note, int tint) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(13), dp(14), dp(13));
        // بعرض كامل، وإلا انهار الوزن والتصق المبلغ بالاسم.
        row.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(name, 15, Util.NAVY, true);
        title.setMaxLines(2);
        words.addView(title);
        if (note != null) words.addView(text(note, 10, tint == Util.GREEN ? Util.GREEN : AMBER, false));
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        TextView amount = text(value, 16, tint, true);
        amount.setTextDirection(View.TEXT_DIRECTION_LTR);
        amount.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-2, -2);
        ap.setMargins(dp(12), 0, 0, 0);
        row.addView(amount, ap);
        return row;
    }

    /** شريط امتلاء ينمو بحركة من الصفر. */
    private LinearLayout bar(int percent, int tint) {
        LinearLayout bar = new LinearLayout(this);
        bar.setBackground(Util.round(0xffe6eaef, dp(5)));
        final View fill = new View(this);
        fill.setBackground(Util.round(tint, dp(5)));
        final LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(0, dp(9), 0.01f);
        bar.addView(fill, fp);
        View rest = new View(this);
        bar.addView(rest, new LinearLayout.LayoutParams(0, dp(9), Math.max(0.01f, 100f - percent)));
        ValueAnimator grow = ValueAnimator.ofFloat(0.01f, Math.max(0.01f, percent));
        grow.setDuration(640);
        grow.setStartDelay(180);
        grow.setInterpolator(new DecelerateInterpolator());
        grow.addUpdateListener(a -> { fp.weight = (Float) a.getAnimatedValue(); fill.setLayoutParams(fp); });
        grow.start();
        return bar;
    }

    private LinearLayout.LayoutParams barParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(9));
        p.setMargins(0, dp(11), 0, 0);
        return p;
    }

    private View divider() {
        View line = new View(this);
        line.setBackgroundColor(0xffeef1f4);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(1));
        p.setMargins(dp(14), 0, dp(14), 0);
        line.setLayoutParams(p);
        return line;
    }

    private View emptyLine(String message) {
        TextView t = text(message, 14, 0xff626970, false);
        t.setPadding(dp(14), dp(10), dp(14), dp(14));
        return t;
    }

    private double capacity(String material) { return Math.max(1, db.capacity(material)); }

    private int soften(int color) {
        return Color.argb(30, Color.red(color), Color.green(color), Color.blue(color));
    }

    private int daysSince(String date) {
        if (date == null || date.isEmpty()) return 0;
        try {
            java.time.LocalDate then = java.time.LocalDate.parse(date);
            return (int) java.time.temporal.ChronoUnit.DAYS.between(then, java.time.LocalDate.now());
        } catch (Exception e) { return 0; }
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(Math.max(12,size));
        t.setTextColor(color);
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT, 1);
        return t;
    }

    /** رأس المال بالريالات الكاملة: لا كسور بعد الفاصلة. */
    private String whole(double value) {
        return String.format(Locale.US, "%,.0f", value);
    }

    private String money(double value) {
        return String.format(Locale.US, value == Math.rint(value) ? "%,.0f" : "%,.2f", value);
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }
}


