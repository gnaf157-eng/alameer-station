package com.alameer.invoicecounter;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class Util {
    public static final int NAVY=Color.rgb(37,40,43),NAVY_LIGHT=Color.rgb(62,65,67),GOLD=Color.rgb(244,196,0),GREEN=Color.rgb(18,128,92),RED=Color.rgb(180,35,53),BG=Color.rgb(243,244,242),MUTED=Color.rgb(112,118,124);

    public static int dp(Context c,int v){return (int)(v*c.getResources().getDisplayMetrics().density);}

    public static String now(){return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date());}
    public static String today(){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());}
    public static String fmtDate(String iso){
        if(iso==null)return "";
        if(iso.length()<10)return iso;
        return iso.substring(8,10)+"/"+iso.substring(5,7)+"/"+iso.substring(0,4);
    }
    public static double number(String s){
        if(s==null)return 0;
        String x=s.trim().replace("٬",",").replace("،",",");
        x=x.replace("ج.م","").replace("ج م","").replace("جنيه","").replace("درهم","").replace("$","").replace("ر.ي","").trim();
        if(x.isEmpty())return 0;
        try{return Double.parseDouble(x);}catch(Exception e){return 0;}
    }
    public static String money(double v){return String.format(Locale.US,"%.2f",v);}
    public static String qty(double v){
        if(Double.isNaN(v)||Double.isInfinite(v))return "0";
        if(v==Math.rint(v))return String.format(Locale.US,"%.0f",v);
        return String.format(Locale.US,"%.2f",v);
    }

    public static GradientDrawable round(int color,float radius){
        GradientDrawable g=new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    public static TextView text(Context c,String s,float size,int color,boolean bold){
        TextView v=new TextView(c);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);
        v.setTextDirection(View.TEXT_DIRECTION_RTL);
        return v;
    }
    public static TextView heading(Context c,String s){
        TextView v=text(c,s,19,NAVY,true);
        v.setPadding(dp(c,2),dp(c,2),dp(c,2),dp(c,2));
        return v;
    }
    public static LinearLayout panel(Context c,int color){
        LinearLayout p=new LinearLayout(c);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setBackground(round(color,18));
        p.setElevation(dp(c,3));
        p.setPadding(dp(c,14),dp(c,14),dp(c,14),dp(c,14));
        return p;
    }
    public static LinearLayout.LayoutParams spaced(Context c){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,0,0,dp(c,10));
        return p;
    }
    public static Button button(Context c,String s){
        Button b=new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c,54));
        b.setPadding(dp(c,12),dp(c,6),dp(c,12),dp(c,6));
        b.setBackgroundTintList(ColorStateList.valueOf(NAVY_LIGHT));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000),round(NAVY_LIGHT,22),null));
        return b;
    }
    public static Button goldButton(Context c,String s){
        Button b=button(c,s);
        b.setTextColor(NAVY);
        b.setBackgroundTintList(ColorStateList.valueOf(GOLD));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000),round(GOLD,22),null));
        return b;
    }
    public static Button greenButton(Context c,String s){
        Button b=button(c,s);
        b.setBackgroundTintList(ColorStateList.valueOf(GREEN));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000),round(GREEN,22),null));
        return b;
    }
    public static void styleInput(EditText v,Context c){
        v.setBackground(round(Color.WHITE,12));
        v.setPadding(dp(c,10),dp(c,9),dp(c,10),dp(c,9));
        v.setTextSize(15);
        v.setSingleLine(true);
    }
    public static LinearLayout brandBar(Context c,String title,String sub,View.OnClickListener back){
        LinearLayout bar=new LinearLayout(c);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(c,14),dp(c,10),dp(c,14),dp(c,10));
        bar.setBackgroundColor(NAVY);
        if(back!=null){
            TextView b=text(c,"رجوع",15,Color.WHITE,false);
            b.setPadding(dp(c,12),dp(c,8),dp(c,12),dp(c,8));
            b.setBackground(round(NAVY_LIGHT,14));
            b.setOnClickListener(back);
            bar.addView(b,new LinearLayout.LayoutParams(-2,-2));
            View sp=new View(c);
            bar.addView(sp,new LinearLayout.LayoutParams(0,0,1));
        }
        LinearLayout words=new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setGravity(Gravity.START);
        words.addView(text(c,title,19,Color.WHITE,true));
        if(sub!=null&&!sub.isEmpty())words.addView(text(c,sub,12,0xffc9ccce,false));
        bar.addView(words,new LinearLayout.LayoutParams(0,-2,back==null?0f:1f));
        return bar;
    }

    public static Bitmap decodeScaled(InputStream boundsStream,InputStream dataStream,int maxSide){
        try{
            BitmapFactory.Options o=new BitmapFactory.Options();
            o.inJustDecodeBounds=true;
            BitmapFactory.decodeStream(boundsStream,null,o);
            boundsStream.close();
            if(o.outWidth<=0||o.outHeight<=0)return null;
            int s=1;
            while(o.outWidth/s>maxSide||o.outHeight/s>maxSide)s*=2;
            o.inJustDecodeBounds=false;
            o.inSampleSize=s;
            return BitmapFactory.decodeStream(dataStream,null,o);
        }catch(Exception e){return null;}
    }
    public static Bitmap decodeScaledFile(File f,int maxSide){
        if(f==null||!f.isFile())return null;
        try{
            return decodeScaled(new FileInputStream(f),new FileInputStream(f),maxSide);
        }catch(Exception e){return null;}
    }
    public static Bitmap decodeScaledUri(ContentResolver cr,Uri uri,int maxSide){
        try{
            InputStream in1=cr.openInputStream(uri);
            InputStream in2=cr.openInputStream(uri);
            return decodeScaled(in1,in2,maxSide);
        }catch(Exception e){return null;}
    }
}
