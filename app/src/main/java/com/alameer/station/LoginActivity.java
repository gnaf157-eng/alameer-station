package com.alameer.station;

import android.app.*;import android.os.*;import android.content.*;import android.database.Cursor;import android.graphics.Color;import android.text.InputType;import android.view.*;import android.widget.*;

public class LoginActivity extends Activity {
    private Db db; private EditText pin;
    @Override public void onCreate(Bundle b){super.onCreate(b);db=new Db(this);build();}
    private void build(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(28,70,28,28);root.setBackgroundColor(Color.rgb(247,250,252));root.addView(Util.title(this,"محطة الأمير\nمطابقة الورديات"),new LinearLayout.LayoutParams(-1,-2));root.addView(Util.label(this,"رمز الدخول PIN"));pin=new EditText(this);pin.setHint("أدخل الرمز");pin.setTextDirection(View.TEXT_DIRECTION_LTR);pin.setGravity(Gravity.CENTER);pin.setTextSize(24);pin.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);root.addView(pin,new LinearLayout.LayoutParams(-1,-2));Button login=Util.button(this,"دخول");login.setOnClickListener(v->login());root.addView(login,new LinearLayout.LayoutParams(-1,-2));TextView note=Util.label(this,"النسخة الأولى: المدير 0000، الديزل 1111، البترول 2222، الغاز 3333، الليل 4444\nيغيّر المدير الأسماء والرموز قبل التشغيل الرسمي.");note.setTextSize(13);root.addView(note);setContentView(root);}
    private void login(){try(Cursor c=db.login(pin.getText().toString())){if(c.moveToFirst()){Intent i=new Intent(this,DashboardActivity.class);i.putExtra("workerId",c.getInt(0));i.putExtra("name",c.getString(1));i.putExtra("role",c.getString(2));startActivity(i);}else Toast.makeText(this,"رمز الدخول غير صحيح",Toast.LENGTH_SHORT).show();}}
}
