package com.alameer.station;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.text.*;
import java.util.*;

public final class Util {
    public static String now(){return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date());}
    public static TextView title(android.content.Context c,String text){TextView v=new TextView(c);v.setText(text);v.setTextSize(24);v.setTextColor(Color.WHITE);v.setGravity(Gravity.CENTER);v.setPadding(20,34,20,34);v.setBackgroundColor(Color.rgb(16,42,67));return v;}
    public static TextView label(android.content.Context c,String text){TextView v=new TextView(c);v.setText(text);v.setTextSize(17);v.setTextColor(Color.rgb(16,42,67));v.setPadding(12,12,12,8);return v;}
    public static Button button(android.content.Context c,String text){Button b=new Button(c);b.setText(text);b.setTextSize(17);b.setAllCaps(false);return b;}
    public static double number(String s){try{return Double.parseDouble(s.trim());}catch(Exception e){return 0;}}
}
