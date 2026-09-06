package com.alameer.station.shifts;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * منطق حسابي خالص بلا أي اعتماد على أندرويد، حتى يمكن اختباره على JVM مباشرة.
 */
public final class Calc {
    private Calc(){}

    /** يحوّل نصًا إلى رقم، ويرجع صفرًا عند أي إدخال غير صالح. */
    public static double number(String value){
        if (value == null) return 0;
        try { return Double.parseDouble(value.trim()); } catch (Exception e) { return 0; }
    }

    /** الباقي = المبيعات + المقبوضات − النقد المسلّم − الديون − المخاريج. */
    public static double balance(double sales, double collections, double cash, double debts, double expenses){
        return sales + collections - cash - debts - expenses;
    }

    /** مبيعات طرمبة = (القراءة الحالية − السابقة) × سعر اللتر. */
    public static double pumpSales(double previous, double current, double price){
        return (current - previous) * price;
    }

    public static boolean matched(double balance){ return Math.abs(balance) < 0.01; }

    /** تجزئة الرمز بـ SHA-256 مع ملح ثابت، حتى لا يُخزَّن الرمز كنص صريح. */
    public static String hash(String pin){
        if (pin == null) pin = "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest(("alameer::" + pin.trim()).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : out) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return "plain:" + pin.trim(); }
    }

    public static String arabicType(String t){
        if ("COLLECTION".equals(t)) return "مقبوضات";
        if ("CASH".equals(t)) return "نقد مسلّم";
        if ("DEBT".equals(t)) return "ديون";
        return "مخاريج";
    }

    public static String arabicStatus(String s){
        if ("OPEN".equals(s)) return "مفتوحة";
        if ("SUBMITTED".equals(s)) return "مرسلة للمدير";
        if ("RETURNED".equals(s)) return "مُرجعة للتصحيح";
        if ("APPROVED".equals(s)) return "معتمدة";
        return s;
    }
}
