package com.alameer.station.shifts;

import java.util.ArrayList;
import java.util.List;

/**
 * منطق الرقابة المحاسبية بالقيد المزدوج، بلا أي اعتماد على أندرويد حتى يُختبر على JVM.
 *
 * القاعدة: كل وردية مُغلقة قيد مزدوج كامل.
 *   الطرف الدائن  = المبيعات + المقبوضات.
 *   الطرف المدين  = النقد المسلّم + الديون + المخاريج + الباقي مع العامل.
 * إذا تساوى الطرفان فالنظام متوازن، وأي فرق يعود إلى ورديات بعينها يمكن تتبّعها.
 */
public final class Ledger {
    private Ledger(){}

    /** الفرق المقبول: كسور الريال. */
    public static final double EPS = 0.01;

    /** سطر قيد واحد: مصدره ومبلغاه وفرقه. */
    public static final class Row {
        public final long shiftId;
        public final String label;
        public final String date;
        public final double debit;
        public final double credit;
        public Row(long shiftId, String label, String date, double debit, double credit) {
            this.shiftId = shiftId; this.label = label; this.date = date;
            this.debit = debit; this.credit = credit;
        }
        public double gap() { return debit - credit; }
        public boolean balanced() { return Math.abs(gap()) < EPS; }
    }

    /** الطرف المدين لوردية: ما خرج من يد العامل في صورة نقد أو دين أو مخاريج أو باقٍ معه. */
    public static double debit(double cash, double debts, double expenses, double balance) {
        return cash + debts + expenses + balance;
    }

    /** الطرف الدائن لوردية: المبيعات وما حصّله من ديون سابقة. */
    public static double credit(double sales, double collections) {
        return sales + collections;
    }

    public static boolean balanced(double gap) { return Math.abs(gap) < EPS; }

    public static double totalDebit(List<Row> rows) {
        double t = 0;
        for (Row r : rows) t += r.debit;
        return t;
    }

    public static double totalCredit(List<Row> rows) {
        double t = 0;
        for (Row r : rows) t += r.credit;
        return t;
    }

    public static double gap(List<Row> rows) { return totalDebit(rows) - totalCredit(rows); }

    /** الأسطر المسبّبة للفرق وحدها، مرتّبة بالأكبر أثرًا. */
    public static List<Row> offenders(List<Row> rows) {
        List<Row> bad = new ArrayList<>();
        for (Row r : rows) if (!r.balanced()) bad.add(r);
        bad.sort((a, b) -> Double.compare(Math.abs(b.gap()), Math.abs(a.gap())));
        return bad;
    }

    /** حالة النظام كنص عربي. */
    public static String state(double gap) {
        return balanced(gap) ? "متوازن" : "غير متوازن";
    }

    /** شرح الفرق: مدين أكبر يعني نقصًا في الإيراد المسجّل، والعكس صحيح. */
    public static String explain(double gap) {
        if (balanced(gap)) return "مجموع المدين يساوي مجموع الدائن";
        return gap > 0 ? "المدين يزيد على الدائن بـ " + Calc.money(gap) + " ر.ي"
                       : "الدائن يزيد على المدين بـ " + Calc.money(-gap) + " ر.ي";
    }

    public static List<Row> newList() { return new ArrayList<>(); }
}
