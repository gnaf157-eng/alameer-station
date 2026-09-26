package com.alameer.station.shifts;

import android.app.AlertDialog;
import android.graphics.Color;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** One persistent card for a selected cashbox. The three choices describe cash direction. */
final class CashEntryCard {
 static final String[] LABELS={"وارد","صادر","تحويل"};
 final WorkspaceForms host;final ShiftActivity a;final Db db;final long shift,boxId;
 final WorkspaceForms.Choices boxes;
 final Spinner currency,to,targetCurrency;final AutoCompleteTextView person;final EditText amount,note;
 final TextView current,after,targetBalance,feedback,partyLabel,partyRole;final LinearLayout targetGroup,targetCurrencyGroup;
 final Button[] kinds=new Button[3];final AlertDialog dialog;AlertDialog classificationDialog;
 ArrayList<NameDirectory.Entry> directory;
 int kind;String selectedName="",selectedRole="";
 CashEntryCard(WorkspaceForms h,long selectedBox){
  host=h;a=h.a;db=h.db;shift=h.shift;boxId=selectedBox;CashAccounts.requireBox(db,boxId);boxes=h.new Choices("cashboxes",boxId);
  boxes.names.set(0,"اختر وجهة التحويل");boxes.ids.add(-1L);boxes.names.add("شركة النفط");boxes.ids.add(-2L);boxes.names.add("شركة الغاز");
  LinearLayout form=h.form();form.setPadding(h.dp(16),h.dp(8),h.dp(16),h.dp(8));
  currency=h.spinner(Db.CURRENCY_NAMES);to=boxes.spinner();targetCurrency=h.spinner(Db.CURRENCY_NAMES);
  currency.setTag("cash-currency");currency.setSelection(Arrays.asList(Db.CURRENCIES).indexOf(ShiftWorkspace.boxCurrency(db,boxId)));
  h.label(form,"العملة");form.addView(currency);
  LinearLayout balances=new LinearLayout(a);balances.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
  current=balanceCell(balances,"الرصيد الحالي",false);after=balanceCell(balances,"بعد الحركة",true);form.addView(balances,StationUi.space(a));
  current.setTag("cash-current");after.setTag("cash-after");
  form.addView(h.text("يشمل الرصيد حركات الوردية المسجّلة ونقد العامل المستلم.",12));
  LinearLayout types=new LinearLayout(a);types.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
  for(int i=0;i<3;i++){
   final int index=i;Button b=StationUi.button(a,LABELS[i],false,()->chooseKind(index));b.setTextSize(14);b.setPadding(0,h.dp(4),0,h.dp(4));b.setMinWidth(0);b.setMinimumWidth(0);b.setTag("cash-kind-"+i);
   ShiftActivity.MoveIcon icon=new ShiftActivity.MoveIcon(i==2?4:i,Util.NAVY);icon.setBounds(0,0,h.dp(22),h.dp(22));b.setCompoundDrawables(null,icon,null,null);kinds[i]=b;
   LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,h.dp(68),1);p.setMargins(h.dp(3),0,h.dp(3),0);types.addView(b,p);
  }
  form.addView(types,StationUi.space(a));
  partyLabel=h.text("الاسم",13);form.addView(partyLabel);
  person=new AutoCompleteTextView(a);StationUi.input(a,person);person.setSingleLine(true);person.setThreshold(1);person.setHint("عميل أو بند مخاريج");person.setTag("cash-person");form.addView(person);
  partyRole=h.text("",12);partyRole.setTag("cash-party-role");form.addView(partyRole);
  refreshNames();
  person.setOnItemClickListener((parent,view,position,id)->{
   NameDirectory.Entry entry=(NameDirectory.Entry)parent.getItemAtPosition(position);
   person.setText(entry.name,false);selectedName=entry.name;selectedRole=entry.role;showRole();
  });
  person.addTextChangedListener(watch(()->{
   String name=person.getText().toString().trim();
   if(!name.equals(selectedName)){selectedName=name;selectedRole="";int matches=0;for(NameDirectory.Entry entry:directory)if(entry.name.equals(name)&&!entry.role.isEmpty()){selectedRole=entry.role;matches++;}if(matches!=1)selectedRole="";}
   showRole();
  }));
  targetGroup=StationUi.column(a);h.label(targetGroup,"إلى صندوق أو شركة");targetGroup.addView(to);targetCurrencyGroup=StationUi.column(a);h.label(targetCurrencyGroup,"عملة الاستلام");targetCurrencyGroup.addView(targetCurrency);targetGroup.addView(targetCurrencyGroup);targetBalance=h.text("",14);targetGroup.addView(targetBalance);form.addView(targetGroup);
  h.label(form,"المبلغ");amount=h.number("المبلغ بعملة الصندوق");amount.setTag("cash-amount");form.addView(amount);
  h.label(form,"ملاحظة اختيارية");note=h.note();form.addView(note);
  feedback=h.text("",13);feedback.setTextColor(Util.GREEN);feedback.setTag("cash-feedback");form.addView(feedback);
  ScrollView scroll=new ScrollView(a);scroll.addView(form);dialog=new AlertDialog.Builder(a).setTitle(CashAccounts.name(db,boxId)).setView(scroll).setPositiveButton("إضافة الحركة",null).setNegativeButton("إلغاء",null).create();
  dialog.setCanceledOnTouchOutside(false);dialog.setOnDismissListener(x->host.render());
  changed(currency,this::refresh);changed(to,()->{if(boxes.id(to)>0)targetCurrency.setSelection(Arrays.asList(Db.CURRENCIES).indexOf(ShiftWorkspace.boxCurrency(db,boxes.id(to))));refresh();});changed(targetCurrency,this::refresh);
  amount.addTextChangedListener(watch(this::refresh));
  dialog.setOnShowListener(x->{Button save=dialog.getButton(-1);save.setTextColor(Util.NAVY);save.setBackgroundTintList(null);save.setBackground(Util.round(Util.ACCENT,h.dp(10)));save.setMinHeight(h.dp(48));save.setPadding(h.dp(16),h.dp(8),h.dp(16),h.dp(8));dialog.getButton(-2).setTextColor(Util.NAVY);save.setOnClickListener(v->save());});
  chooseKind(0);
 }
 static TextWatcher watch(Runnable change){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int co,int af){}public void onTextChanged(CharSequence s,int st,int be,int co){}public void afterTextChanged(Editable e){change.run();}};}
 void show(){dialog.show();dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
 void refreshNames(){directory=NameDirectory.entries(db);person.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_dropdown_item_1line,directory));}
 void showRole(){partyRole.setText(person.getText().toString().trim().isEmpty()?"":NameDirectory.label(selectedRole));}
 static int boxTint(long box){return Color.HSVToColor(new float[]{(float)((box*137.508)%360),0.72f,0.55f});}
 static LinearLayout tile(WorkspaceForms h,long boxId){
  LinearLayout box=StationUi.column(h.a);int tint=boxTint(boxId);box.setGravity(Gravity.CENTER);box.setPadding(h.dp(8),h.dp(12),h.dp(8),h.dp(12));box.setMinimumHeight(h.dp(126));
  box.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x18000000),Util.round(Color.argb(24,Color.red(tint),Color.green(tint),Color.blue(tint)),h.dp(16)),null));
  box.setFocusable(true);box.setContentDescription(CashAccounts.name(h.db,boxId));box.setTag("cashbox-tile-"+boxId);
  ImageView art=new ImageView(h.a);art.setPadding(h.dp(8),h.dp(8),h.dp(8),h.dp(8));art.setBackground(Util.round(Color.WHITE,h.dp(19)));art.setImageDrawable(new ShiftActivity.MoveIcon(1,tint));box.addView(art,new LinearLayout.LayoutParams(h.dp(38),h.dp(38)));
  TextView name=StationUi.text(h.a,CashAccounts.name(h.db,boxId),16,true);name.setTextColor(tint);name.setGravity(Gravity.CENTER);name.setPadding(0,h.dp(6),0,h.dp(4));box.addView(name);
  for(String code:CashAccounts.currencies(h.db,boxId,h.shift)){
   TextView balance=StationUi.text(h.a,Calc.money(CashAccounts.expected(h.db,h.shift,boxId,code))+" "+Db.currencyName(code),13,false);balance.setTextColor(tint);balance.setGravity(Gravity.CENTER);box.addView(balance);
  }
  box.setOnClickListener(v->new CashEntryCard(h,boxId).show());return box;
 }
 TextView balanceCell(LinearLayout row,String title,boolean highlight){LinearLayout card=StationUi.column(a);card.setPadding(host.dp(8),host.dp(10),host.dp(8),host.dp(10));card.setBackground(Util.round(highlight?Util.ACCENT_SOFT:0xffEEEEEE,host.dp(12)));TextView label=StationUi.text(a,title,12,false);label.setGravity(Gravity.CENTER);card.addView(label);TextView value=StationUi.text(a,"—",18,true);value.setGravity(Gravity.CENTER);card.addView(value);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(host.dp(3),0,host.dp(3),0);row.addView(card,p);return value;}
 static void changed(Spinner spinner,Runnable action){spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){action.run();}});}
 void chooseKind(int index){
  kind=index;for(int i=0;i<3;i++){kinds[i].setSelected(i==kind);kinds[i].setBackground(Util.round(i==kind?Util.ACCENT:0xffEEEEEE,host.dp(10)));}
  person.setVisibility(kind<2?View.VISIBLE:View.GONE);partyLabel.setVisibility(kind<2?View.VISIBLE:View.GONE);partyRole.setVisibility(kind<2?View.VISIBLE:View.GONE);targetGroup.setVisibility(kind==2?View.VISIBLE:View.GONE);feedback.setText("");refresh();
 }
 String code(){return Db.CURRENCIES[currency.getSelectedItemPosition()];}
 String company(){return boxes.id(to)==-1?"OIL":boxes.id(to)==-2?"GAS":"";}
 String targetCode(){return Db.CURRENCIES[targetCurrency.getSelectedItemPosition()];}
 void refresh(){
  String code=code(),unit=Db.currencyName(code);double balance=CashAccounts.expected(db,shift,boxId,code);Double entered=WorkspaceForms.validCount(amount.getText().toString());double value=entered==null?0:entered;
  current.setText(Calc.money(balance)+" "+unit);after.setText(Calc.money(balance+(kind==0?value:-value))+" "+unit);after.setTextColor(balance+(kind==0?value:-value)<0?Util.RED:Util.NAVY);
  targetCurrencyGroup.setVisibility(company().isEmpty()?View.VISIBLE:View.GONE);
  if(kind==2&&!company().isEmpty()){double before=ShiftWorkspace.expectedCompany(db,shift,company());targetBalance.setText("رصيد الشركة الحالي: "+Calc.money(before)+" ر.ي\nبعد التحويل: "+Calc.money(before+value*db.rate(code))+" ر.ي");}
  else if(kind==2&&boxes.id(to)>0){double before=CashAccounts.expected(db,shift,boxes.id(to),targetCode()),converted=value*db.rate(code)/db.rate(targetCode());targetBalance.setText("رصيد المستلم الحالي: "+Calc.money(before)+" "+Db.currencyName(targetCode())+"\nبعد التحويل: "+Calc.money(before+converted)+" "+Db.currencyName(targetCode()));}else targetBalance.setText("");
 }
 void save(){
  try{
   Double value=WorkspaceForms.validCount(amount.getText().toString());if(value==null||value<=0)throw new IllegalArgumentException("أدخل مبلغًا أكبر من صفر");
   if(kind<2){
    String name=person.getText().toString().trim();if(name.isEmpty())throw new IllegalArgumentException("اكتب الاسم");
    if(selectedRole.isEmpty()){
     classificationDialog=new AlertDialog.Builder(a).setTitle("نوع الاسم: "+name).setItems(new String[]{"حساب عميل","بند مخاريج"},(d,w)->{selectedName=name;selectedRole=w==0?NameDirectory.CUSTOMER:NameDirectory.EXPENSE;showRole();save();}).setNegativeButton("إلغاء",null).show();return;
    }
    NameDirectory.addCash(db,shift,boxId,kind==0?"IN":"OUT",code(),value,name,selectedRole,note.getText().toString());
   }else if(!company().isEmpty())ShiftWorkspace.addCash(db,shift,1,"COMPANY_PAYMENT",boxId,company().equals("GAS")?1:0,code(),"YER",value,"تحويل إلى "+Db.supplierName(company())+(note.getText().length()==0?"":" — "+note.getText()));
   else ShiftWorkspace.addCash(db,shift,1,"TRANSFER",boxId,boxes.id(to),code(),targetCode(),value,"تحويل إلى "+CashAccounts.name(db,boxes.id(to))+(note.getText().length()==0?"":" — "+note.getText()));
   feedback.setText("✓ أضيفت "+LABELS[kind]+" • "+Calc.money(value)+" "+Db.currencyName(code()));amount.setText("");person.setText("");note.setText("");refreshNames();refresh();(kind<2?person:amount).requestFocus();
  }catch(RuntimeException e){host.error(e);}
 }
}
