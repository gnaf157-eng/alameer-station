package com.alameer.invoicecounter;

import com.google.mlkit.vision.text.Text;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * تحويل سطور النص المعترف عليها (OCR) إلى أصناف: اسم + عدد + وحدة + سعر.
 * القواعد تقريبية — المستخدم يراجع ويصحح قبل الحفظ.
 */
public final class OcrParser {
    public static class Parsed{
        public String name;public double qty;public String unit;public double price;
        public Parsed(String n,double q,String u,double p){name=n;qty=q;unit=u;price=p;}
    }

    private static final Set<String> UNITS=new HashSet<>(Arrays.asList(
        "كجم","جم","جرام","جرامات","غرام","غرامات","غ","ك.ج","كيلو","كيلوجرام","لتر","لترات","مل","مللي",
        "حبة","عبوة","علبة","علبة.","كيس","كيس.","شنطة","برطمان","صندوق","صناديق","قطعة","قطع","باكو",
        "رزمة","رزمات","زجاجة","قارورة","طبق","كرتون","كارتون","بالة","جوال","جوال.","شال"));

    private static final String[] NOISE={"الإجمالي","الإجمالى","الاجمالي","الاجمالى","اجمالي","مجموع","المجموع","القيمة","قيمة","صافي","الصافي","الخصم","خصم","الكمية","الكميات","كاشير","كاشييه","خدمة","خدمه","الشحن","شحن","نقل","تاريخ","التاريخ","الساعة","رقم","فاتورة","فاتوره","شكرا","شكراً","مرحبا","مرحباً","العنوان","الهاتف","تليفون","موبايل","واتساب","انستجرام","فيسبوك","الدفع","دفع","بالتوفيق","الرجاء","بقالة","محلات","محل","مخزن","مستودع","تجارية","محطة","اجل","الاجل","الجملة","نسخة","فرع","شوارع","شارع","total","subtotal","tax","cash","card","credit","debit","balance","customer","cashier","invoice","item","price","discount","grand","welcome","thank","thanks","please"};
    private static final Set<String> NOISE_SET;
    static{
        NOISE_SET=new HashSet<>();
        for(String s:NOISE)NOISE_SET.add(s.toLowerCase(Locale.US));
    }
    private static final Set<String> SEPARATORS=new HashSet<>(Arrays.asList(
        "|","¦","│","ǀ","ı","İ",";","؛","-","–","—","·","•","*","/","\\","I","l","i"));

    public static List<Parsed> parse(Text text){
        List<Parsed> out=new ArrayList<>();
        for(Text.TextBlock b:text.getTextBlocks()){
            for(Text.Line ln:b.getLines()){
                Parsed p=parseLine(ln);
                if(p!=null)out.add(p);
            }
        }
        return out;
    }

    /** تخمين اسم المحل: أول سطر بدون أرقام في بداية الفاتورة. */
    public static String guessStore(Text text){
        int count=0;
        for(Text.TextBlock b:text.getTextBlocks()){
            for(Text.Line ln:b.getLines()){
                count++;
                if(count>8)return null;
                String s=clean(ln.getText());
                if(s.length()<3||s.length()>60)continue;
                if(hasNumber(s))continue;
                if(isNoise(s))continue;
                return s;
            }
        }
        return null;
    }

    public static boolean hasNumber(String s){
        for(int i=0;i<s.length();i++)if(Character.isDigit(s.charAt(i)))return true;
        return false;
    }

    public static Parsed parseLine(Text.Line ln){
        String raw=clean(ln.getText());
        if(raw.length()<2)return null;
        if(isNoise(raw))return null;
        String normAll=normalizeDigits(raw);
        if(normAll.matches(".*[0-9]{4}[-/][0-9]{1,2}[-/][0-9]{1,2}.*"))return null;

        String[] tokens=raw.split("\\s+");
        ArrayList<String> names=new ArrayList<>();
        ArrayList<double[]> nums=new ArrayList<>();
        for(String t:tokens){
            String tn=normalizeDigits(t);
            String cur=tn.replace("ج.م","").replace("ج م","").replace("$","").replace("ر.ي","").replace("ريال","").replace("جنيه","").replace("جنيهات","").replace("درهم","").trim();
            if(isNumeric(cur)){
                double v=parseNum(cur);
                if(Double.isNaN(v))continue;
                if(v>=1e9)continue; // رقم هاتف
                nums.add(new double[]{v,decPlaces(cur)});
            }else{
                names.add(t);
            }
        }
        String name=String.join(" ",names).trim();
        name=name.replaceAll("[؛;.،,]+$","").trim();
        if(name.length()<2)return null;
        if(isNoise(name))return null;
        if(nums.isEmpty())return null; // سطر بلا أرقام (اسم محل/عنوان) ليس صنفًا

        double qty=0,price=0;
        int n=nums.size();
        if(n==0){
            qty=1;
        }else if(n==1){
            double v=nums.get(0)[0];
            double pl=nums.get(0)[1];
            if(pl==0){
                if(v<=100)qty=v;else{price=v;qty=1;}
            }else{
                if(weightName(name))qty=v;else{price=v;qty=1;}
            }
        }else if(n==2){
            double v1=nums.get(0)[0],v2=nums.get(1)[0];
            boolean d1=nums.get(0)[1]>0,d2=nums.get(1)[1]>0;
            if(!d1&&!d2){
                if(v1<=100){qty=v1;price=v2;}
                else{price=v1;qty=v2;}
            }else if(!d1&&d2){
                qty=v1;price=v2;
            }else if(d1&&!d2){
                if(weightName(name)&&v1<v2){qty=v1;price=v2;}
                else{price=v1;qty=v2;}
            }else{
                if(nums.get(0)[1]>=3&&nums.get(1)[1]>=3){price=v1;qty=1;}
                else if(v1>v2){price=v1;qty=v2;}
                else if(v2>v1){price=v2;qty=v1;}
                else{price=v1;qty=v2;}
            }
        }else{
            // محاولة: آخر رقم في السطر هو الإجمالي = العدد × سعر الوحدة
            // (نمط فواتير الجملة الجدولية) — نختار الزوج الذي يحققها
            double total=nums.get(n-1)[0];
            boolean solved=false;
            if(total>0){
                if(pairMatches(nums,n-3,n-2,total)){
                    qty=nums.get(n-3)[0];price=nums.get(n-2)[0];solved=true;
                }else{
                    for(int i=n-4;i>=0&&!solved;i--){
                        if(pairMatches(nums,i,n-2,total)){qty=nums.get(i)[0];price=nums.get(n-2)[0];solved=true;}
                    }
                    for(int j=n-3;j>=1&&!solved;j--){
                        for(int i=j-1;i>=0&&!solved;i--){
                            if(pairMatches(nums,i,j,total)){qty=nums.get(i)[0];price=nums.get(j)[0];solved=true;}
                        }
                    }
                }
            }
            if(!solved){
                double v1=nums.get(0)[0];
                if(nums.get(0)[1]>0&&weightName(name)){
                    qty=v1;
                    price=nums.get(1)[0];
                }else{
                    qty=(nums.get(0)[1]==0)?v1:1;
                    price=0;
                    for(int i=0;i<n;i++){
                        if(nums.get(i)[1]>0){price=nums.get(i)[0];break;}
                    }
                    if(price==0)price=nums.get(1)[0];
                }
            }
        }
        if(qty<0)qty=0;
        if(price<0)price=0;

        String unit="";
        String[] parts=name.split("\\s+");
        if(parts.length>1){
            String last=parts[parts.length-1];
            if(UNITS.contains(last)){
                unit=last;
                name=String.join(" ",Arrays.copyOf(parts,parts.length-1)).trim();
                if(name.length()<2)return null;
            }
        }
        return new Parsed(name,qty,unit,price);
    }

    private static boolean weightName(String n){
        String x=n.toLowerCase(Locale.US);
        return x.contains("كجم")||x.contains("كيلو")||x.contains("جرام")||x.contains("لتر")||x.contains("مل")||x.contains("جم")||x.contains("ك ج")||x.contains("غرام");
    }

    private static boolean pairMatches(ArrayList<double[]> nums,int i,int j,double total){
        if(i<0||j<0||i>=j)return false;
        double a=nums.get(i)[0],b=nums.get(j)[0];
        if(a<=0||b<=0)return false;
        double prod=a*b;
        return Math.abs(prod-total)<=Math.max(total*0.02,0.01);
    }

    private static boolean isNoise(String s){
        String x=s.trim().toLowerCase(Locale.US);
        if(x.isEmpty())return true;
        for(String n:NOISE_SET){
            if(n.length()>=3&&x.contains(n))return true;
        }
        return false;
    }

    private static String clean(String s){
        if(s==null)return "";
        s=s.replace("\u00a0"," ").replace("\u200f"," ").replace("\t"," ");
        String[] t=s.trim().split("\\s+");
        ArrayList<String> out=new ArrayList<>();
        for(String w:t){
            String x=w.trim();
            if(x.isEmpty())continue;
            if(SEPARATORS.contains(x))continue;
            out.add(x);
        }
        return String.join(" ",out);
    }

    private static String normalizeDigits(String s){
        StringBuilder sb=new StringBuilder();
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            if(c>='٠'&&c<='٩')sb.append((char)(c-'٠'+'0'));
            else if(c=='٫')sb.append('.');
            else sb.append(c);
        }
        return sb.toString();
    }

    private static boolean isNumeric(String s){
        return s.matches("[0-9]+([.,][0-9]+)?");
    }

    private static double parseNum(String s){
        String x=s;
        if(x.contains(".")&&x.contains(","))x=x.replace(",","");
        else if(x.contains(",")){
            int i=x.lastIndexOf(',');
            int after=x.length()-i-1;
            x=(after==1||after==2)?x.replace(",","."):x.replace(",","");
        }
        try{return Double.parseDouble(x);}catch(Exception e){return Double.NaN;}
    }

    private static int decPlaces(String s){
        int i=Math.max(s.lastIndexOf('.'),s.lastIndexOf(','));
        return i<0?0:s.length()-i-1;
    }
}
