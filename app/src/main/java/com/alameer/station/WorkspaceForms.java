package com.alameer.station.shifts;
import android.app.*;
import android.database.Cursor;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Input stays attached to its shift until the single final posting transaction. */
final class WorkspaceForms {
 final ShiftActivity a;final Db db;final long shift;final int section;final LinearLayout root;
 WorkspaceForms(ShiftActivity a,LinearLayout root,int section){this.a=a;db=a.db;shift=a.shiftId;this.section=section;this.root=root;render();}
 int dp(int n){return StationUi.dp(a,n);}
 TextView text(String s,int size){TextView t=StationUi.text(a,s,size,false);t.setPadding(0,dp(6),0,dp(6));return t;}
 void error(Exception e){new AlertDialog.Builder(a).setTitle("راجع البيانات").setMessage(e.getMessage()).setPositiveButton("حسنًا",null).show();}
 Button button(LinearLayout parent,String title,boolean primary,Runnable r){Button b=StationUi.button(a,title,primary,()->{try{r.run();}catch(RuntimeException e){error(e);}});parent.addView(b,StationUi.space(a));return b;}
 LinearLayout card(String title){LinearLayout c=StationUi.card(a);c.addView(StationUi.text(a,title,18,true));root.addView(c,StationUi.space(a));return c;}
 void render(){
  root.removeAllViews();root.addView(StationUi.text(a,section==1?"حركة الصناديق":"حركة المواد",23,true));
  root.addView(text(db.shiftCode(shift)+"  •  "+db.shiftDate(shift),13));
  root.addView(text("تُرحّل بيانات الوردية كاملة من تبويب المواد بعد المراجعة.",13));
  if(Capital.enabled(db)){LinearLayout capital=card("رأس المال");capital.addView(text(Capital.preview(db,shift),14));}
  if(section==1){
   LinearLayout receipt=card("استلام نقد العامل");receipt.addView(text("النقد المسلّم: "+Calc.money(db.total(shift,"CASH"))+" ر.ي",17));
   Choices boxes=new Choices("cashboxes");Spinner pick=boxes.spinner();int chosen=boxes.ids.indexOf(ShiftWorkspace.box(db,shift));if(chosen>=0)pick.setSelection(chosen);LinearLayout receiveRow=new LinearLayout(a);receiveRow.addView(pick,new LinearLayout.LayoutParams(0,dp(48),1));receipt.addView(receiveRow);Spinner receiveCurrency=spinner(Db.CURRENCY_NAMES);receiveCurrency.setSelection(Arrays.asList(Db.CURRENCIES).indexOf(CashAccounts.receiptCurrency(db,shift)));receiveRow.addView(receiveCurrency,new LinearLayout.LayoutParams(0,dp(48),1));
   button(receipt,"اعتماد صندوق الاستلام",false,()->{if(boxes.id(pick)==0)throw new IllegalArgumentException("اختر صندوق الاستلام");ShiftWorkspace.selectBox(db,shift,boxes.id(pick),Db.CURRENCIES[receiveCurrency.getSelectedItemPosition()]);render();});
   LinearLayout entry=card("اختر الصندوق");entry.addView(text("الرصيد يشمل الحركات المسجّلة. اضغط الصندوق لإضافة وارد أو صادر أو تحويل.",13));
   for(int i=1;i<boxes.ids.size();i+=2){
    LinearLayout tiles=new LinearLayout(a);tiles.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    for(int col=0;col<2;col++){
     LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(4),dp(4),dp(4),dp(4));
     if(i+col<boxes.ids.size())tiles.addView(CashEntryCard.tile(this,boxes.ids.get(i+col)),p);
     else tiles.addView(new View(a),new LinearLayout.LayoutParams(0,0,1));
    }
    entry.addView(tiles,new LinearLayout.LayoutParams(-1,-2));
   }
   if(boxes.ids.size()==1)entry.addView(text("أضف الصناديق من الضبط ← تعريف الصناديق.",14));
   root.removeView(entry);root.addView(entry,3,StationUi.space(a));
  }else{
   LinearLayout companies=card("اختر حساب الشركة");
   LinearLayout tiles=new LinearLayout(a);tiles.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);tiles.setTag("company-tiles");
   for(String company:Db.SUPPLIERS){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(4),dp(4),dp(4),dp(4));tiles.addView(CompanyEntryCard.tile(this,company),p);}
   companies.addView(tiles,new LinearLayout.LayoutParams(-1,-2));
  }
  operations();
  int checked=ShiftWorkspace.reviewed(db,shift);
  root.addView(text("تأكيدات الوردية\n"+((checked&1)!=0?"✓":"○")+" مطابقة العامل\n"+((checked&2)!=0?"✓":"○")+" مراجعة الصناديق\n"+((checked&4)!=0?"✓":"○")+" مراجعة المواد",15));
  root.addView(text(section==1?"راجع رصيد الصندوق الظاهر مع رصيد الصراف في مجموعة الواتساب، ثم أكد المراجعة. لا يُشترط فرق صفري هنا.":"سجّل حركات المواد وراجعها، ثم أكد المراجعة. شرط تصفير فرق العامل مستقل.",13));
  button(root,section==1?"تأكيد مراجعة الصناديق":"تأكيد مراجعة المواد",false,()->{ShiftWorkspace.review(db,shift,section);render();});
  if(section==1)button(root,"متابعة إلى المواد",true,()->a.workspacePage(6));
  else button(root,"ترحيل الوردية بالكامل",true,()->a.requestPostShift());
 }
 void operations(){LinearLayout list=card("الحركات المسجّلة في الوردية");try(Cursor c=ShiftWorkspace.operations(db,shift,section)){
  if(!c.moveToFirst()){list.addView(text("لا توجد حركات إضافية",14));return;}
  do{long row=c.getLong(0);String kind=c.getString(1);if(section==1){cashOperation(list,c);continue;}String line=ShiftWorkspace.label(kind)+" • "+c.getString(7)+"\n"+(c.getDouble(5)>0?c.getString(4)+" • "+Calc.money(c.getDouble(5))+" لتر\n":"")+Calc.money(c.getDouble(6))+" ر.ي";
   if(kind.equals("FUEL_SUPPLY"))try(Cursor f=db.getReadableDatabase().rawQuery("SELECT driver_name,freight FROM shift_operations WHERE id=?",new String[]{""+row})){f.moveToFirst();line+="\nأجرة "+f.getString(0)+": "+Calc.money(f.getDouble(1))+" ر.ي";}
   list.addView(text(line,15));button(list,"حذف الحركة",false,()->new AlertDialog.Builder(a).setMessage("حذف الحركة من هذه الوردية؟").setPositiveButton("حذف",(d,w)->{ShiftWorkspace.delete(db,shift,row);render();}).setNegativeButton("رجوع",null).show());
  }while(c.moveToNext());}}
 void cashOperation(LinearLayout list,Cursor c){
  final long id=c.getLong(0);String kind=c.getString(1),box=CashAccounts.name(db,c.getLong(2)),note=c.getString(7);
  String unit="YER";double rate=1;
  try(Cursor fx=db.getReadableDatabase().rawQuery("SELECT currency,rate FROM shift_operations WHERE id=?",new String[]{""+id})){if(fx.moveToFirst()){unit=fx.getString(0);rate=fx.getDouble(1);}}
  String amount=Calc.money(c.getDouble(6)/(rate>0?rate:1))+" "+Db.currencyName(unit);
  boolean incoming=kind.equals("COLLECTION"),transfer=kind.equals("TRANSFER");
  String type=transfer?"تحويل":incoming?"وارد":"صادر";
  int tint=transfer?0xff626870:incoming?Util.GREEN:Util.RED;
  String description=box+" · "+(note==null?"":note);
  final String details=ShiftWorkspace.label(kind)+"\nالصندوق: "+box
    +(transfer?"\nإلى: "+CashAccounts.name(db,c.getLong(3)):"")+"\nالمبلغ: "+amount
    +(note==null||note.isEmpty()?"":"\n"+note);
  LinearLayout row=new LinearLayout(a);row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);row.setGravity(Gravity.CENTER_VERTICAL);
  row.setPadding(dp(4),0,dp(4),0);row.setMinimumHeight(dp(48));row.setTag("cash-operation-"+id);
  row.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x12000000),Util.round(android.graphics.Color.WHITE,dp(4)),null));
  View mark=new View(a);mark.setBackground(Util.round(tint,dp(2)));row.addView(mark,new LinearLayout.LayoutParams(dp(3),dp(18)));
  TextView label=StationUi.text(a,type,12,true);label.setTextColor(tint);label.setSingleLine(true);label.setPadding(dp(6),0,dp(6),0);row.addView(label);
  TextView name=StationUi.text(a,description.replace('\n',' ').replace('\r',' '),13,false);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);name.setPadding(dp(6),0,0,0);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
  TextView value=StationUi.text(a,amount,13,true);value.setSingleLine(true);row.addView(value);
  row.setFocusable(true);row.setContentDescription(details+"، اضغط للتفاصيل");
  row.setOnClickListener(v->new AlertDialog.Builder(a).setTitle("تفاصيل الحركة").setMessage(details).setPositiveButton("إغلاق",null)
    .setNegativeButton("حذف الحركة",(d,w)->new AlertDialog.Builder(a).setMessage("حذف الحركة من هذه الوردية؟").setPositiveButton("حذف",(confirm,which)->{try{ShiftWorkspace.delete(db,shift,id);render();}catch(RuntimeException e){error(e);}}).setNegativeButton("رجوع",null).show()).show());
  list.addView(row,new LinearLayout.LayoutParams(-1,-2));
  View divider=new View(a);divider.setBackgroundColor(0xffEEEEEE);list.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
 }
 static Double validCount(String raw){
  StringBuilder normalized=new StringBuilder();for(char ch:raw.trim().toCharArray()){if(ch>='٠'&&ch<='٩')normalized.append((char)('0'+ch-'٠'));else if(ch>='۰'&&ch<='۹')normalized.append((char)('0'+ch-'۰'));else if(ch=='٫')normalized.append('.');else if(ch!=','&&ch!='٬')normalized.append(ch);}
  String value=normalized.toString();if(!value.matches("([0-9]+(\\.[0-9]*)?|\\.[0-9]+)"))return null;try{double n=Double.parseDouble(value);return Double.isFinite(n)&&n>=0?n:null;}catch(NumberFormatException e){return null;}
 }
 LinearLayout form(){LinearLayout f=StationUi.column(a);f.setPadding(dp(18),dp(10),dp(18),dp(10));return f;}
 void label(LinearLayout f,String s){StationUi.label(a,f,s);}
 EditText number(String hint){EditText e=new EditText(a);StationUi.input(a,e);e.setHint(hint);e.setSingleLine(true);e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);e.setTextDirection(View.TEXT_DIRECTION_LTR);return e;}
 EditText note(){EditText e=new EditText(a);StationUi.input(a,e);e.setHint("ملاحظة اختيارية");return e;}
 AutoCompleteTextView name(String hint){AutoCompleteTextView e=new AutoCompleteTextView(a);StationUi.input(a,e);e.setHint(hint);e.setThreshold(1);ArrayList<String> names=NameDirectory.names(db);e.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_dropdown_item_1line,names));return e;}
 Spinner spinner(String[] values){Spinner s=new Spinner(a);s.setMinimumHeight(dp(48));ArrayAdapter<String> adapter=new ArrayAdapter<>(a,android.R.layout.simple_spinner_item,values);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(adapter);s.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(48)));return s;}
 final class Choices {final ArrayList<Long> ids=new ArrayList<>();final ArrayList<String> names=new ArrayList<>();Choices(String table){this(table,0); }Choices(String table,long excluded){ids.add(0L);names.add("اختر الصندوق");try(Cursor c=db.getReadableDatabase().rawQuery("SELECT id,name FROM "+table+" WHERE active=1 ORDER BY name",null)){while(c.moveToNext()){if(c.getLong(0)==excluded)continue;ids.add(c.getLong(0));names.add(c.getString(1));}}}Spinner spinner(){return WorkspaceForms.this.spinner(names.toArray(new String[0]));}long id(Spinner s){return ids.get(s.getSelectedItemPosition());}}
}
