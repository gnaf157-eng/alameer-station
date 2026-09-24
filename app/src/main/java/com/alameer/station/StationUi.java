package com.alameer.station.shifts;
import android.app.Activity;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.view.*;
import android.widget.*;

/** Shared accessible yellow/charcoal surfaces for the native screens. */
final class StationUi {
 static int dp(android.content.Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
 static TextView text(Activity a,String s,int size,boolean bold){TextView t=new TextView(a);t.setText(s);t.setTextSize(size);t.setTextColor(Util.NAVY);t.setTextDirection(View.TEXT_DIRECTION_RTL);if(bold)t.setTypeface(null,1);return t;}
 static LinearLayout column(Activity a){LinearLayout l=new LinearLayout(a);l.setOrientation(1);l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return l;}
 static LinearLayout card(Activity a){LinearLayout l=column(a);int p=dp(a,16);l.setPadding(p,p,p,p);l.setBackground(Util.round(Color.WHITE,dp(a,18)));return l;}
 static LinearLayout.LayoutParams space(Activity a){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(a,8),0,dp(a,8));return p;}
 static Button button(Activity a,String s,boolean primary,Runnable action){Button b=new Button(a);b.setText(s);b.setAllCaps(false);b.setTextSize(15);b.setTextColor(Util.NAVY);b.setTypeface(null,1);b.setMinHeight(dp(a,52));b.setPadding(dp(a,10),dp(a,10),dp(a,10),dp(a,10));b.setBackgroundTintList(null);b.setStateListAnimator(null);b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000),Util.round(primary?Util.ACCENT:0xffEAEAEA,dp(a,12)),null));b.setOnClickListener(v->action.run());return b;}
 static void label(Activity a,LinearLayout parent,String s){TextView t=text(a,s,13,false);t.setPadding(0,dp(a,10),0,dp(a,5));parent.addView(t);}
 static void input(Activity a,EditText e){e.setTextColor(Util.NAVY);e.setHintTextColor(0xff686868);e.setTextSize(16);e.setMinHeight(dp(a,48));e.setPadding(dp(a,12),dp(a,10),dp(a,12),dp(a,10));e.setBackground(Util.round(0xffEEEEEE,dp(a,10)));}
}
