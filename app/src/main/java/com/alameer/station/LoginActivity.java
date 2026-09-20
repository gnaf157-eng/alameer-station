package com.alameer.station.shifts;

import android.app.*;import android.os.*;import android.content.*;import android.database.Cursor;import android.graphics.Color;import android.text.InputType;import android.view.*;import android.widget.*;

public class LoginActivity extends Activity {
    private Db db; private EditText pin;
    private int attempts = 0; private long lockedUntil = 0;
    private static final int MAX_ATTEMPTS = 5; private static final long LOCK_MS = 60000;

    @Override public void onCreate(Bundle b){super.onCreate(b);db=new Db(this);build();}
    @Override protected void onResume(){super.onResume();build();}

    private void build(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(28,64,28,28);root.setBackgroundColor(Util.BG);
        root.addView(Util.title(this,"محطة الأمير\nمطابقة الورديات"),Util.spaced());
        if(db.openAccess()) buildOpenAccess(root); else buildPinEntry(root);
        ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);Util.safeInsets(scroll);
    }

    /** وضع التجربة: اختيار الحساب من قائمة بلا رمز. */
    private void buildOpenAccess(LinearLayout root){
        root.addView(Util.label(this,"اختر حسابك للدخول"));
        try(Cursor c=db.activeAccounts()){
            while(c.moveToNext()){
                final int id=c.getInt(0);final String name=c.getString(1),role=c.getString(2);
                boolean admin="ADMIN".equals(role);
                Button b=admin?Util.goldButton(this,name+" — مدير"):Util.button(this,name);
                b.setOnClickListener(v->enter(id,name,role));
                root.addView(b,Util.spaced());
            }
        }
        TextView note=Util.card(this,"وضع التجربة مفعّل: الدخول بلا رمز.\nيوقفه المدير من الإعدادات عند ربط العمال بالمنظومة.");
        note.setTextSize(13);note.setTextColor(Util.RED);
        root.addView(note,Util.spaced());
    }

    private void buildPinEntry(LinearLayout root){
        root.addView(Util.label(this,"أدخل رمز الدخول الخاص بك"));
        pin=new EditText(this);pin.setHint("PIN");pin.setTextDirection(View.TEXT_DIRECTION_LTR);
        pin.setGravity(Gravity.CENTER);pin.setTextSize(24);
        pin.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setBackground(Util.round(Color.WHITE,18));pin.setPadding(16,18,16,18);
        root.addView(pin,Util.spaced());
        Button login=Util.goldButton(this,"دخول آمن");
        login.setOnClickListener(v->login());
        root.addView(login,Util.spaced());
        if(BuildConfig.DEBUG){
            TextView note=Util.card(this,"رموز التجربة (نسخة التطوير فقط)\nالمدير 0000 | الديزل 1111 | البترول 2222\nالغاز 3333 | الليل 4444");
            note.setTextSize(13);root.addView(note,Util.spaced());
        }
    }

    private void enter(int workerId,String name,String role){
        Intent i=new Intent(this,DashboardActivity.class);
        i.putExtra("workerId",workerId);i.putExtra("name",name);i.putExtra("role",role);
        startActivity(i);
    }

    private void login(){
        long now=System.currentTimeMillis();
        if(now<lockedUntil){Toast.makeText(this,"محاولات كثيرة. انتظر "+((lockedUntil-now)/1000+1)+" ثانية.",Toast.LENGTH_LONG).show();return;}
        String entered=pin.getText().toString().trim();
        if(entered.isEmpty()){pin.setError("أدخل الرمز");return;}
        try(Cursor c=db.login(entered)){
            if(c.moveToFirst()){
                attempts=0;pin.setText("");
                enter(c.getInt(0),c.getString(1),c.getString(2));
            }else{
                attempts++;pin.setText("");
                if(attempts>=MAX_ATTEMPTS){lockedUntil=now+LOCK_MS;attempts=0;Toast.makeText(this,"تم قفل الدخول دقيقة واحدة بعد 5 محاولات خاطئة.",Toast.LENGTH_LONG).show();}
                else Toast.makeText(this,"رمز الدخول غير صحيح ("+attempts+"/"+MAX_ATTEMPTS+")",Toast.LENGTH_SHORT).show();
            }
        }
    }
}

