package com.alameer.station.shifts;
import android.app.*;
import android.text.InputType;
import android.widget.EditText;
/** PIN is requested only for settings and correction actions. */
final class ManagerAccess {
 static void run(Activity a,Db db,Runnable action){run(a,db,action,()->{});}
 static void run(Activity a,Db db,Runnable action,Runnable cancel){
  EditText pin=new EditText(a);StationUi.input(a,pin);pin.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);pin.setHint(db.lockPinSet()?"رمز المدير":"أنشئ رمزًا من 4 أرقام أو أكثر");
  AlertDialog d=new AlertDialog.Builder(a).setTitle(db.lockPinSet()?"رمز المدير":"حماية الإعدادات والتصحيح").setView(pin).setPositiveButton("متابعة",null).setNegativeButton("رجوع",(x,w)->cancel.run()).setOnCancelListener(x->cancel.run()).create();
  d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{
   long until=0;try{until=Long.parseLong(db.setting("manager_pin_blocked","0"));}catch(Exception ignored){}
   if(System.currentTimeMillis()<until){pin.setError("انتظر دقيقة ثم حاول مجددًا");return;}
   String s=pin.getText().toString();if(!db.lockPinSet()){if(!s.matches("[0-9]{4,12}")){pin.setError("اكتب من 4 إلى 12 رقمًا");return;}db.setLockPin(s);}
   else if(!db.checkLockPin(s)){int tries=Integer.parseInt(db.setting("manager_pin_attempts","0"))+1;db.setSetting("manager_pin_attempts",""+(tries%5));if(tries>=5)db.setSetting("manager_pin_blocked",""+(System.currentTimeMillis()+60000));pin.setError("الرمز غير صحيح");return;}
   db.setSetting("manager_pin_attempts","0");d.dismiss();action.run();
  }));d.show();
 }
}
