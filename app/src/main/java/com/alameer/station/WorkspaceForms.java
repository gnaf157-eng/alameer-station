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
  root.removeAllViews();root.addView(StationUi.text(a,section==1?"مطابقة الصناديق":"مطابقة المواد",23,true));
  root.addView(text(db.shiftCode(shift)+"  •  "+db.shiftDate(shift),13));
  root.addView(text("تُرحّل بيانات الوردية كاملة بعد اكتمال المطابقات الثلاث.",13));
  if(section==1){
   LinearLayout receipt=card("١  استلام نقد العامل");receipt.addView(text("النقد المسلّم: "+Calc.money(db.total(shift,"CASH"))+" ر.ي",17));
   Choices boxes=new Choices("cashboxes");Spinner pick=boxes.spinner();int chosen=boxes.ids.indexOf(ShiftWorkspace.box(db,shift));if(chosen>=0)pick.setSelection(chosen);receipt.addView(pick);
   button(receipt,"اعتماد صندوق الاستلام",false,()->{if(boxes.id(pick)==0)throw new IllegalArgumentException("اختر صندوق الاستلام");ShiftWorkspace.selectBox(db,shift,boxes.id(pick));render();});
   LinearLayout entry=card("٢  الحركات الإضافية");entry.addView(text("القبض والصرف والتحويل بين الصناديق. نقد العامل محسوب أعلاه مرة واحدة.",13));button(entry,"＋ قبض / صرف / تحويل",true,this::cashEntry);
  }else{
   LinearLayout companies=card("١  حسابات الشركات");
   for(String company:new String[]{"OIL","GAS"}){double balance=ShiftWorkspace.expectedCompany(db,shift,company);companies.addView(text(Db.supplierName(company)+"  •  "+(balance>=0?"لنا ":"علينا ")+Calc.money(Math.abs(balance))+" ر.ي",16));}
   button(companies,"توريد مبلغ للشركة",true,this::companyPayment);
   button(companies,"توريد مواد من الشركة",true,this::supplyEntry);
  }
  operations();counts();
  if(section==1)button(root,"تأكيد مطابقة الصناديق والمتابعة إلى المواد",true,()->{ShiftWorkspace.review(db,shift,1);a.workspacePage(6);});
  else{
   button(root,"تأكيد مطابقة المواد / لا توجد حركة أخرى",false,()->{ShiftWorkspace.review(db,shift,2);render();});
   button(root,"ترحيل الوردية بالكامل",true,()->{ShiftWorkspace.review(db,shift,2);a.requestPostShift();});
  }
 }
 void operations(){LinearLayout list=card("الحركات المسجّلة في الوردية");try(Cursor c=ShiftWorkspace.operations(db,shift,section)){
  if(!c.moveToFirst()){list.addView(text("لا توجد حركات إضافية",14));return;}
  do{long row=c.getLong(0);String kind=c.getString(1);String line=ShiftWorkspace.label(kind)+" • "+c.getString(7)+"\n"+(c.getDouble(5)>0?c.getString(4)+" • "+Calc.money(c.getDouble(5))+" لتر\n":"")+Calc.money(c.getDouble(6))+" ر.ي";
   if(kind.equals("FUEL_SUPPLY"))try(Cursor f=db.getReadableDatabase().rawQuery("SELECT driver_name,freight FROM shift_operations WHERE id=?",new String[]{""+row})){f.moveToFirst();line+="\nأجرة "+f.getString(0)+": "+Calc.money(f.getDouble(1))+" ر.ي";}
   list.addView(text(line,15));button(list,"حذف الحركة",false,()->new AlertDialog.Builder(a).setMessage("حذف الحركة من هذه الوردية؟").setPositiveButton("حذف",(d,w)->{ShiftWorkspace.delete(db,shift,row);render();}).setNegativeButton("رجوع",null).show());
  }while(c.moveToNext());}}
 void counts(){LinearLayout c=card(section==1?"٣  الأرصدة الفعلية للصناديق":"٢  الجرد الفعلي باللترات");
  if(section==1)try(Cursor boxes=db.getReadableDatabase().rawQuery("SELECT id,name FROM cashboxes WHERE active=1 ORDER BY name",null)){while(boxes.moveToNext()){long box=boxes.getLong(0);countRow(c,""+box,boxes.getString(1),ShiftWorkspace.expectedCash(db,shift,box),Db.currencyName(ShiftWorkspace.boxCurrency(db,box)));}}
  else for(String mat:Db.MATERIALS)countRow(c,mat,mat,ShiftWorkspace.expectedMaterial(db,shift,mat),"لتر");
 }
 void countRow(LinearLayout parent,String account,String label,double expected,String unit){
  parent.addView(text(label+" • المحسوب بعد الحركات: "+Calc.money(expected)+" "+unit,15));
  EditText actual=number("الرصيد الفعلي — "+unit);Double previous=ShiftWorkspace.counted(db,shift,section,account);if(previous!=null)actual.setText(Double.toString(previous));parent.addView(actual,StationUi.space(a));
  TextView difference=text("",13);parent.addView(difference);Runnable update=()->{String raw=actual.getText().toString().trim();if(raw.isEmpty()){difference.setText("أدخل الرصيد الفعلي للمطابقة");difference.setTextColor(Util.NAVY);return;}double gap=Calc.number(raw)-expected;difference.setText("الفرق: "+Calc.money(gap)+" "+unit);difference.setTextColor(Math.abs(gap)<=0.0000001?Util.GREEN:Util.RED);};
  actual.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int co,int af){}public void onTextChanged(CharSequence s,int st,int be,int co){}public void afterTextChanged(Editable e){update.run();String raw=e.toString().trim();if(raw.isEmpty()){db.getWritableDatabase().delete("shift_counts","shift_id=? AND section=? AND account=?",new String[]{""+shift,""+section,account});db.getWritableDatabase().execSQL("UPDATE shift_workspace SET reviewed=reviewed & ~? WHERE shift_id=?",new Object[]{section==1?6:4,shift});}else try{ShiftWorkspace.count(db,shift,section,account,Calc.number(raw));}catch(RuntimeException ignored){}}});update.run();

 }
 LinearLayout form(){LinearLayout f=StationUi.column(a);f.setPadding(dp(18),dp(10),dp(18),dp(10));return f;}
 void label(LinearLayout f,String s){StationUi.label(a,f,s);}
 EditText number(String hint){EditText e=new EditText(a);StationUi.input(a,e);e.setHint(hint);e.setSingleLine(true);e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);e.setTextDirection(View.TEXT_DIRECTION_LTR);return e;}
 EditText note(){EditText e=new EditText(a);StationUi.input(a,e);e.setHint("ملاحظة اختيارية");return e;}
 AutoCompleteTextView name(String hint){AutoCompleteTextView e=new AutoCompleteTextView(a);StationUi.input(a,e);e.setHint(hint);e.setThreshold(1);ArrayList<String> names=new ArrayList<>();try(Cursor c=db.getReadableDatabase().rawQuery("SELECT name FROM debtors UNION SELECT driver_name AS name FROM shift_operations WHERE driver_name<>'' ORDER BY name",null)){while(c.moveToNext())names.add(c.getString(0));}e.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_dropdown_item_1line,names));return e;}
 Spinner spinner(String[] values){Spinner s=new Spinner(a);s.setMinimumHeight(dp(48));s.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,values));return s;}
 void dialog(String title,LinearLayout f,Runnable save){ScrollView scroll=new ScrollView(a);scroll.addView(f);AlertDialog d=new AlertDialog.Builder(a).setTitle(title).setView(scroll).setPositiveButton("موافق",null).setNegativeButton("رجوع",null).create();d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{try{save.run();d.dismiss();render();}catch(RuntimeException e){error(e);}}));d.show();}
 void companyPayment(){LinearLayout f=form();Spinner company=spinner(new String[]{"شركة النفط","شركة الغاز"});Choices choices=new Choices("cashboxes");Spinner box=choices.spinner();EditText amount=number("مبلغ السداد بعملة الصندوق"),note=note();label(f,"الشركة");f.addView(company);label(f,"صندوق السداد");f.addView(box);label(f,"المبلغ");f.addView(amount);label(f,"البيان");f.addView(note);
  dialog("توريد مبلغ للشركة",f,()->ShiftWorkspace.addCash(db,shift,2,"COMPANY_PAYMENT",choices.id(box),company.getSelectedItemPosition(),Calc.number(amount.getText().toString()),"توريد مبلغ — "+company.getSelectedItem()+" "+note.getText()));
 }
 void supplyEntry(){LinearLayout f=form();Spinner mat=spinner(Db.MATERIALS);EditText qty=number("الكمية باللتر"),note=note();AutoCompleteTextView driver=name("اسم السائق — يُحفظ تلقائيًا");TextView price=text("",14),total=text("",16);
  label(f,"المادة");f.addView(mat);label(f,"الكمية");f.addView(qty);f.addView(price);label(f,"السائق");f.addView(driver);f.addView(total);label(f,"ملاحظات");f.addView(note);
  Runnable calculate=()->{String m=(String)mat.getSelectedItem();double q=Calc.number(qty.getText().toString());price.setText("شراء اللتر: "+Calc.money(db.buyPrice(m))+" • نقل اللتر: "+Calc.money(db.freightPrice(m))+" ر.ي");total.setText("قيمة الوقود: "+Calc.money(q*db.buyPrice(m))+" ر.ي\nمستحق السائق: "+Calc.money(q*db.freightPrice(m))+" ر.ي");};
  mat.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){calculate.run();}});qty.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int co,int af){}public void onTextChanged(CharSequence s,int st,int be,int co){}public void afterTextChanged(Editable e){calculate.run();}});calculate.run();
  dialog("توريد مواد من الشركة",f,()->ShiftWorkspace.addSupply(db,shift,(String)mat.getSelectedItem(),Calc.number(qty.getText().toString()),driver.getText().toString(),note.getText().toString()));
 }
 void cashEntry(){LinearLayout f=form();Spinner kind=spinner(new String[]{"قبض من عميل","صرف لعميل / سائق","مصروف","تحويل بين صندوقين"});Choices boxes=new Choices("cashboxes");Spinner from=boxes.spinner(),to=boxes.spinner();AutoCompleteTextView person=name("اسم العميل أو السائق");EditText category=note();category.setHint("بند المصروف");EditText amount=number("المبلغ بعملة الصندوق"),note=note();
  label(f,"نوع الحركة");f.addView(kind);label(f,"الصندوق");f.addView(from);TextView target=text("الطرف المقابل",13);f.addView(target);f.addView(person);f.addView(category);f.addView(to);label(f,"المبلغ");f.addView(amount);label(f,"ملاحظات");f.addView(note);
  kind.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){person.setVisibility(pos<2?View.VISIBLE:View.GONE);category.setVisibility(pos==2?View.VISIBLE:View.GONE);to.setVisibility(pos==3?View.VISIBLE:View.GONE);}});
  dialog("إضافة حركة صندوق",f,()->{int k=kind.getSelectedItemPosition();String[] types={"COLLECTION","LOAN","EXPENSE","TRANSFER"};long dest=k<2?ShiftWorkspace.customer(db,person.getText().toString()):k==3?boxes.id(to):0;String description=k<2?person.getText().toString():k==2?category.getText().toString():"تحويل بين الصناديق";if(description.trim().isEmpty())throw new IllegalArgumentException("اكتب الاسم أو بند المصروف");ShiftWorkspace.addCash(db,shift,1,types[k],boxes.id(from),dest,Calc.number(amount.getText().toString()),description+(note.getText().length()==0?"":" — "+note.getText()));});
 }
 final class Choices {final ArrayList<Long> ids=new ArrayList<>();final ArrayList<String> names=new ArrayList<>();Choices(String table){ids.add(0L);names.add("اختر الصندوق");try(Cursor c=db.getReadableDatabase().rawQuery("SELECT id,name FROM "+table+" WHERE active=1 ORDER BY name",null)){while(c.moveToNext()){ids.add(c.getLong(0));names.add(c.getString(1)+" • "+Db.currencyName(ShiftWorkspace.boxCurrency(db,c.getLong(0))));}}}Spinner spinner(){return WorkspaceForms.this.spinner(names.toArray(new String[0]));}long id(Spinner s){return ids.get(s.getSelectedItemPosition());}}
}
