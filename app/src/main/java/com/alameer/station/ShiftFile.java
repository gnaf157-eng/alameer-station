package com.alameer.station.shifts;

import java.util.ArrayList;
import java.util.List;

/**
 * ملف تسليم الوردية بين جهاز العامل وجهاز المدير.
 *
 * نص بسيط سطرًا بسطر حتى يمكن قراءته ومراجعته، ومختوم ببصمة تمنع العبث.
 * منطق خالص بلا اعتماد على أندرويد حتى يُختبر على JVM.
 */
public final class ShiftFile {
    private ShiftFile(){}

    public static final String HEAD = "TABIQ-SHIFT-1";
    private static final char SEP = '|';

    /** قراءة طرمبة واحدة. */
    public static final class Reading {
        public final String pump, fuel;
        public final double previous, current, price;
        public Reading(String pump, String fuel, double previous, double current, double price) {
            this.pump = pump; this.fuel = fuel;
            this.previous = previous; this.current = current; this.price = price;
        }
        public double litres() { return Math.max(0, current - previous); }
        public double amount() { return litres() * Math.max(0, price); }
    }

    /** حركة واحدة. */
    public static final class Move {
        public final String type, name;
        public final double amount;
        public Move(String type, String name, double amount) {
            this.type = type; this.name = name == null ? "" : name; this.amount = amount;
        }
    }

    /** وردية كاملة كما أرسلها العامل. */
    public static final class Shift {
        public String station = "", worker = "", date = "", reason = "", device = "";
        public long number;
        public final List<Reading> readings = new ArrayList<>();
        public final List<Move> moves = new ArrayList<>();

        public double sales() {
            double t = 0;
            for (Reading r : readings) t += r.amount();
            return t;
        }
        public double total(String type) {
            double t = 0;
            for (Move m : moves) if (m.type.equals(type)) t += m.amount;
            return t;
        }
        public double balance() {
            return Calc.balance(sales(), total("COLLECTION"), total("CASH"), total("DEBT"), total("EXPENSE"));
        }
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('|', '/').replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String num(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    /** يحوّل الوردية إلى نص مختوم ببصمته. */
    public static String write(Shift s) {
        StringBuilder b = new StringBuilder();
        b.append(HEAD).append('\n');
        b.append("S").append(SEP).append(clean(s.station)).append(SEP).append(clean(s.worker))
         .append(SEP).append(clean(s.date)).append(SEP).append(s.number)
         .append(SEP).append(clean(s.device)).append(SEP).append(clean(s.reason)).append('\n');
        for (Reading r : s.readings)
            b.append("R").append(SEP).append(clean(r.pump)).append(SEP).append(clean(r.fuel))
             .append(SEP).append(num(r.previous)).append(SEP).append(num(r.current))
             .append(SEP).append(num(r.price)).append('\n');
        for (Move m : s.moves)
            b.append("M").append(SEP).append(clean(m.type)).append(SEP).append(clean(m.name))
             .append(SEP).append(num(m.amount)).append('\n');
        String body = b.toString();
        return body + "H" + SEP + Calc.hash(body) + "\n";
    }

    /** يقرأ الملف ويتحقّق من بصمته. يرمي استثناءً بالعربية عند أي خلل. */
    public static Shift read(String text) {
        if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("الملف فارغ");
        String[] lines = text.replace("\r", "").split("\n");
        if (lines.length < 2 || !HEAD.equals(lines[0].trim()))
            throw new IllegalArgumentException("هذا ليس ملف وردية من التطبيق");

        StringBuilder body = new StringBuilder();
        String stamp = null;
        for (String line : lines) {
            if (line.startsWith("H" + SEP)) { stamp = line.substring(2).trim(); break; }
            if (!line.isEmpty()) body.append(line).append('\n');
        }
        if (stamp == null) throw new IllegalArgumentException("الملف بلا بصمة تحقّق");
        if (!stamp.equals(Calc.hash(body.toString())))
            throw new IllegalArgumentException("الملف مُعدَّل بعد تصديره ولا يمكن قبوله");

        Shift s = new Shift();
        boolean header = false;
        for (String line : body.toString().split("\n")) {
            if (line.equals(HEAD)) continue;
            String[] p = line.split("\\" + SEP, -1);
            if (p[0].equals("S") && p.length >= 7) {
                s.station = p[1]; s.worker = p[2]; s.date = p[3];
                s.number = (long) Calc.number(p[4]); s.device = p[5]; s.reason = p[6];
                header = true;
            } else if (p[0].equals("R") && p.length >= 6) {
                s.readings.add(new Reading(p[1], p[2], Calc.number(p[3]), Calc.number(p[4]), Calc.number(p[5])));
            } else if (p[0].equals("M") && p.length >= 4) {
                s.moves.add(new Move(p[1], p[2], Calc.number(p[3])));
            }
        }
        if (!header) throw new IllegalArgumentException("الملف بلا بيانات وردية");
        if (s.readings.isEmpty()) throw new IllegalArgumentException("الملف بلا قراءات طرمبات");
        return s;
    }

    /** اسم ملف واضح للمشاركة. */
    public static String fileName(Shift s) {
        return "wardiya-" + clean(s.date) + "-" + clean(s.worker).replace(' ', '-') + ".tabiq";
    }
}
