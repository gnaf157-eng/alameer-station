package com.alameer.station.shifts;

import android.app.*;import android.os.*;import android.content.*;import android.database.Cursor;import android.graphics.Color;import android.text.InputType;import android.view.*;import android.widget.*;

public class LoginActivity extends Activity {
    private Db db; private EditText pin;
    @Override public void onCreate(Bundle b){super.onCreate(b);db=new Db(this);build();}
    private void build(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(28,64,28,28);root.setBackgroundColor(Util.BG);root.addView(Util.title(this,"محطة الأمير\nمطابقة الورديات"),Util.spaced());root.addView(Util.label(this,"أدخل رمز الدخول الخاص بك"));pin=new EditText(this);pin.setHint("PIN");pin.setTextDirection(View.TEXT_DIRECTION_LTR);pin.setGravity(Gravity.CENTER);pin.setTextSize(24);pin.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);pin.setBackground(Util.round(Color.WHITE,18));pin.setPadding(16,18,16,18);root.addView(pin,Util.spaced());Button login=Util.goldButton(this,"دخول آمن");login.setOnClickListener(v->login());root.addView(login,Util.spaced());TextView note=Util.card(this,"رموز التجربة\nالمدير 0000 | الديزل 1111 | البترول 2222\nالغاز 3333 | الليل 4444");note.setTextSize(13);root.addView(note,Util.spaced());setContentView(root);}
    private void login(){try(Cursor c=db.login(pin.getText().toString())){if(c.moveToFirst()){Intent i=new Intent(this,DashboardActivity.class);i.putExtra("workerId",c.getInt(0));i.putExtra("name",c.getString(1));i.putExtra("role",c.getString(2));startActivity(i);}else Toast.makeText(this,"رمز الدخول غير صحيح",Toast.LENGTH_SHORT).show();}}
}
