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
        try {
            StringBuilder normalized=new StringBuilder();
            for(char ch:value.trim().toCharArray()){
                if(ch>='٠'&&ch<='٩')normalized.append((char)('0'+ch-'٠'));
                else if(ch>='۰'&&ch<='۹')normalized.append((char)('0'+ch-'۰'));
                else if(ch=='٫')normalized.append('.');
                else if(ch!=','&&ch!='٬')normalized.append(ch);
            }
            double result=Double.parseDouble(normalized.toString());
            return Double.isFinite(result)?result:0;
        } catch (Exception e) { return 0; }
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

    /** اسم الشهر بالعربية من صيغة yyyy-MM. */
    public static String arabicMonth(String yyyyMM){
        String[] names = {"يناير","فبراير","مارس","أبريل","مايو","يونيو","يوليو","أغسطس","سبتمبر","أكتوبر","نوفمبر","ديسمبر"};
        try {
            int m = Integer.parseInt(yyyyMM.substring(5, 7));
            if (m < 1 || m > 12) return yyyyMM;
            return names[m - 1] + " " + yyyyMM.substring(0, 4);
        } catch (Exception e) { return yyyyMM; }
    }

    public static String arabicType(String t){
        if ("COLLECTION".equals(t)) return "مقبوضات";
        if ("CASH".equals(t)) return "نقد مسلّم";
        if ("DEBT".equals(t)) return "ديون";
        return "مخاريج";
    }

    public static String arabicStatus(String s){
        if ("OPEN".equals(s)) return "جارية الآن";
        if ("SUBMITTED".equals(s)) return "مُغلقة";
        if ("RETURNED".equals(s)) return "مُرجعة للتصحيح";
        if ("APPROVED".equals(s)) return "مُغلقة";
        return s;
    }
    /**
     * كود الوردية الفريد: يجمع الجهاز والتاريخ ورقم الوردية في بصمة قصيرة.
     * نفس الوردية تعطي نفس الكود دائمًا، فيُمنع تكرار الترحيل ويُتتبَّع كل رصيد.
     */
    public static String shiftCode(String device, String date, long number) {
        String clean = date == null ? "" : date.trim();
        String seed = "shift::" + (device == null ? "" : device.trim()) + "::" + clean + "::" + number;
        String hash = hash(seed).toUpperCase(java.util.Locale.US);
        StringBuilder b = new StringBuilder("W-");
        String compact = clean.replace("-", "");
        b.append(compact.length() >= 8 ? compact.substring(2) : compact).append('-');
        int taken = 0;
        for (int i = 0; i < hash.length() && taken < 5; i++) {
            char c = hash.charAt(i);
            if ("ABCDEF23456789".indexOf(c) >= 0) { b.append(c); taken++; }
        }
        while (taken++ < 5) b.append('7');
        return b.toString();
    }

    /** تنسيق مبلغ للعرض داخل الرسائل. */
    public static String money(double value){
        return String.format(java.util.Locale.US,value==Math.rint(value)?"%,.0f":"%,.2f",value);
    }
}

