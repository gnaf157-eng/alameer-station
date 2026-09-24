package com.alameer.station.shifts;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;
import java.text.*;
import java.util.*;

public final class Util {
    public static final int NAVY=Color.rgb(35,35,35), NAVY_LIGHT=Color.rgb(64,64,64), GOLD=Color.rgb(255,205,17), ACCENT=Color.rgb(255,205,17), ACCENT_SOFT=Color.rgb(255,246,210), GREEN=Color.rgb(18,128,92), RED=Color.rgb(180,35,53), BG=Color.rgb(242,242,242);
    /** Android 15 edge-to-edge: preserve content padding and keep controls clear of system bars. */
    public static void safeInsets(View root){
        if(android.os.Build.VERSION.SDK_INT<35)return;
        final int l=root.getPaddingLeft(),t=root.getPaddingTop(),r=root.getPaddingRight(),b=root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((view,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            android.graphics.Insets keyboard=insets.getInsets(WindowInsets.Type.ime());
            view.setPadding(l+bars.left,t+bars.top,r+bars.right,b+Math.max(bars.bottom,keyboard.bottom));
            return WindowInsets.CONSUMED;
        });
        root.requestApplyInsets();
    }
    public static String now(){return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date());}
    public static TextView title(android.content.Context c,String text){TextView v=new TextView(c);v.setText(text);v.setTextSize(25);v.setTextColor(Color.WHITE);v.setTypeface(android.graphics.Typeface.DEFAULT,1);v.setGravity(Gravity.CENTER);v.setPadding(20,38,20,38);v.setBackground(round(NAVY,22));return v;}
    public static TextView label(android.content.Context c,String text){TextView v=new TextView(c);v.setText(text);v.setTextSize(17);v.setTextColor(NAVY);v.setPadding(16,16,16,12);v.setTextDirection(View.TEXT_DIRECTION_RTL);return v;}
    public static TextView card(android.content.Context c,String text){TextView v=label(c,text);v.setTextSize(16);v.setBackground(round(Color.WHITE,18));v.setPadding(22,22,22,22);v.setElevation(3);return v;}
    public static Button button(android.content.Context c,String text){Button b=new Button(c);b.setText(text);b.setTextSize(17);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setGravity(Gravity.CENTER);b.setPadding(14,10,14,10);b.setBackgroundTintList(ColorStateList.valueOf(NAVY_LIGHT));b.setMinHeight((int)(56*c.getResources().getDisplayMetrics().density));b.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x22000000),round(NAVY_LIGHT,24),null));return b;}
    public static Button goldButton(android.content.Context c,String text){Button b=button(c,text);b.setTextColor(NAVY);b.setBackgroundTintList(ColorStateList.valueOf(ACCENT));return b;}
    public static Button dangerButton(android.content.Context c,String text){Button b=button(c,text);b.setBackgroundTintList(ColorStateList.valueOf(RED));return b;}
    public static GradientDrawable round(int color,float radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(radius);return g;}
    public static LinearLayout.LayoutParams spaced(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,8,0,8);return p;}
    public static double number(String s){return Calc.number(s);}
}

