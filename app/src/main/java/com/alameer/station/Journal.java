package com.alameer.station.shifts;

import java.util.ArrayList;
import java.util.List;

/**
 * القيد المزدوج: منطق خالص بلا اعتماد على أندرويد حتى يُختبر على JVM.
 *
 * كل عملية مالية قيدٌ واحد له طرفان أو أكثر، ولا يُحفظ القيد إلا إذا
 * تساوى مجموع المدين مع مجموع الدائن. لا تعديل ولا حذف بعد الاعتماد؛
 * التصحيح يكون بقيد عكسي يحفظ الأثر كاملًا.
 */
public final class Journal {
    private Journal(){}

    /** الفرق المقبول: كسور الريال. */
    public static final double EPS = 0.005;

    public static final String DEBIT = "DEBIT";
    public static final String CREDIT = "CREDIT";

    // الحسابات المعتمدة في دفتر المحطة.
    public static final String CASH = "الصندوق";
    public static final String RECEIVABLE = "ذمم المدينين";
    public static final String SALES = "إيراد المبيعات";
    public static final String EXPENSE = "المخاريج";
    public static final String WORKER = "عهدة العامل";
    public static final String INVENTORY = "مخزون الوقود";
    public static final String EQUITY = "أرصدة افتتاحية";

    /** طرف واحد من القيد. */
    public static final class Line {
        public final String account;
        public final String side;
        public final double amount;
        public final String party;
        public Line(String account, String side, double amount, String party) {
            if (account == null || account.trim().isEmpty())
                throw new IllegalArgumentException("الحساب مطلوب في كل طرف");
            if (!DEBIT.equals(side) && !CREDIT.equals(side))
                throw new IllegalArgumentException("طرف القيد يجب أن يكون مدينًا أو دائنًا");
            if (!(amount > 0) || !Double.isFinite(amount))
                throw new IllegalArgumentException("مبلغ الطرف يجب أن يكون أكبر من صفر");
            this.account = account.trim();
            this.side = side;
            this.amount = amount;
            this.party = party == null ? "" : party.trim();
        }
        public boolean debit() { return DEBIT.equals(side); }
        /** الأثر الموقّع على الحساب: المدين موجب والدائن سالب. */
        public double signed() { return debit() ? amount : -amount; }
    }

    /** قيد كامل: بيانه وتاريخه وأطرافه. */
    public static final class Entry {
        public final String memo;
        public final String date;
        public final String source;
        public final long sourceId;
        public final List<Line> lines = new ArrayList<>();
        public Entry(String memo, String date, String source, long sourceId) {
            this.memo = memo == null ? "" : memo.trim();
            this.date = date == null ? "" : date.trim();
            this.source = source == null ? "" : source.trim();
            this.sourceId = sourceId;
        }
        public Entry debit(String account, double amount, String party) {
            lines.add(new Line(account, DEBIT, amount, party));
            return this;
        }
        public Entry credit(String account, double amount, String party) {
            lines.add(new Line(account, CREDIT, amount, party));
            return this;
        }
        public double totalDebit() { return sumSide(lines, true); }
        public double totalCredit() { return sumSide(lines, false); }
        public double gap() { return totalDebit() - totalCredit(); }
        public boolean balanced() { return Journal.balanced(gap()); }
    }

    public static double sumSide(List<Line> lines, boolean debitSide) {
        double t = 0;
        for (Line l : lines) if (l.debit() == debitSide) t += l.amount;
        return t;
    }

    public static boolean balanced(double gap) { return Math.abs(gap) < EPS; }

    /**
     * بوابة الحفظ: تعيد نصًا فارغًا إذا جاز حفظ القيد، وإلا سبب الرفض.
     * لا يُحفظ قيد بلا طرفين، ولا قيد غير متوازن، ولا قيد بصفر.
     */
    public static String rejectReason(Entry e) {
        if (e == null) return "لا يوجد قيد";
        if (e.lines.size() < 2) return "القيد يحتاج طرفًا مدينًا وآخر دائنًا على الأقل";
        if (e.totalDebit() <= 0) return "القيد بلا مبالغ";
        if (!e.balanced())
            return "القيد غير متوازن: مدين " + Calc.money(e.totalDebit())
                 + " مقابل دائن " + Calc.money(e.totalCredit())
                 + " (فرق " + Calc.money(Math.abs(e.gap())) + " ر.ي)";
        if (e.date.isEmpty()) return "تاريخ القيد مطلوب";
        return "";
    }

    public static boolean valid(Entry e) { return rejectReason(e).isEmpty(); }

    /** يبني القيد العكسي: نفس الأطراف بجهات مقلوبة. */
    public static Entry reverseOf(Entry e, String reason, String date) {
        Entry r = new Entry("عكس: " + e.memo + " — " + reason, date, e.source, e.sourceId);
        for (Line l : e.lines)
            r.lines.add(new Line(l.account, l.debit() ? CREDIT : DEBIT, l.amount, l.party));
        return r;
    }

    /** حالة التوازن العامة كنص عربي. */
    public static String state(double gap) { return balanced(gap) ? "متوازن" : "غير متوازن"; }

    public static String explain(double gap) {
        if (balanced(gap)) return "مجموع المدين يساوي مجموع الدائن";
        return gap > 0 ? "المدين يزيد على الدائن بـ " + Calc.money(gap) + " ر.ي"
                       : "الدائن يزيد على المدين بـ " + Calc.money(-gap) + " ر.ي";
    }

    /** قيد وردية كاملة: المبيعات والمقبوضات دائنة، والنقد والديون والمخاريج والعهدة مدينة. */
    public static Entry shiftEntry(long shiftId, String date, double sales, double collections,
                                   double cash, double debts, double expenses, double balance) {
        return shiftEntry(shiftId, "", date, sales, collections, cash, debts, expenses, balance);
    }

    /** نفس القيد مع كود الوردية في البيان، فيُتتبَّع الرصيد إلى مصدره. */
    public static Entry shiftEntry(long shiftId, String code, String date, double sales, double collections,
                                   double cash, double debts, double expenses, double balance) {
        String tag = code == null || code.trim().isEmpty() ? "#" + shiftId : code.trim();
        Entry e = new Entry("ترحيل وردية " + tag, date, "SHIFT", shiftId);
        if (sales > 0) e.credit(SALES, sales, "");
        if (collections > 0) e.credit(RECEIVABLE, collections, "");
        if (cash > 0) e.debit(CASH, cash, "");
        if (debts > 0) e.debit(RECEIVABLE, debts, "");
        if (expenses > 0) e.debit(EXPENSE, expenses, "");
        // الباقي مع العامل عهدة عليه؛ والسالب يعني زيادة يردّها الصندوق.
        if (balance > EPS) e.debit(WORKER, balance, "");
        else if (balance < -EPS) e.credit(WORKER, -balance, "");
        return e;
    }

    /** الفترة المحاسبية لتاريخ ما: yyyy-MM. */
    public static String periodOf(String date) {
        return date != null && date.length() >= 7 ? date.substring(0, 7) : "";
    }

    public static List<Entry> newList() { return new ArrayList<>(); }
}
