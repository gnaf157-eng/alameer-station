package com.alameer.station.shifts;

import android.app.*;import android.os.*;import android.database.Cursor;import android.graphics.Color;import android.widget.*;import java.util.*;

public class ArchiveActivity extends Activity {
    @Override public void onCreate(Bundle b){super.onCreate(b);Db db=new Db(this);int workerId=getIntent().getIntExtra("workerId",0);boolean admin=getIntent().getBooleanExtra("admin",false);ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(18,24,18,40);root.setBackgroundColor(Util.BG);root.addView(Util.title(this,admin?"أرشيف جميع الورديات":"أرشيف وردياتي"),Util.spaced());try(Cursor c=db.archive(workerId,admin)){if(c.getCount()==0)root.addView(Util.card(this,"لا توجد ورديات محفوظة بعد."),Util.spaced());while(c.moveToNext()){double bal=c.getDouble(5);TextView card=Util.card(this,"وردية #"+c.getLong(0)+" — "+c.getString(1)+"\n"+c.getString(2)+"\nالحالة: "+status(c.getString(3))+" | المبيعات: "+fmt(c.getDouble(4))+"\nالباقي: "+fmt(bal)+" | المزامنة: "+sync(c.getString(6)));card.setTextColor(Math.abs(bal)<0.01?Util.GREEN:Util.RED);root.addView(card,Util.spaced());}}scroll.addView(root);setContentView(scroll);}
    private String status(String s){if("OPEN".equals(s))return "مفتوحة";if("SUBMITTED".equals(s))return "مرسلة للمدير";if("APPROVED".equals(s))return "معتمدة";return s;}
    private String sync(String s){return "LOCAL".equals(s)?"محلي":"PENDING".equals(s)?"بانتظار الإنترنت":"تمت";}
    private String fmt(double n){return n==Math.rint(n)?String.format(Locale.US,"%.0f",n):String.format(Locale.US,"%.2f",n);}
}
