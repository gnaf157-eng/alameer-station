package com.alameer.station.shifts;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** A batch input card: keep the chosen cashbox/currency until the manager cancels it. */
final class CashEntryCard {
 static final String[] KINDS={"COLLECTION","LOAN","EXPENSE","TRANSFER"};
 static final String[] LABELS={"مقبوضات","صرف لعميل / سائق","مخاريج","تحويل"};
 final WorkspaceForms host;final ShiftActivity a;final Db db;final long shift;final WorkspaceForms.Choices boxes;
 final Spinner from,currency,to,targetCurrency;final AutoCompleteTextView person;final EditText category,amount,note;
 final TextView current,after,targetBalance,feedback,partyLabel;final LinearLayout targetGroup;
 final Button[] kinds=new Button[4];final AlertDialog dialog;int kind;
 CashEntryCard(WorkspaceForms h,int selected){
  host=h;a=h.a;db=h.db;shift=h.shift;kind=selected;boxes=h.new Choices("cashboxes");
  LinearLayout form=h.form();form.setPadding(h.dp(16),h.dp(8),h.dp(16),h.dp(8));
  from=boxes.spinner();currency=h.spinner(Db.CURRENCY_NAMES);to=boxes.spinner();targetCurrency=h.spinner(Db.CURRENCY_NAMES);
  from.setTag("cash-from");currency.setTag("cash-currency");
  h.label(form,"الصندوق");form.addView(from);h.label(form,"العملة");form.addView(currency);
  LinearLayout balances=new LinearLayout(a);balances.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
  current=balanceCell(balances,"الرصيد الحالي",false);after=balanceCell(balances,"بعد إضافة الحركة",true);form.addView(balances,StationUi.space(a));
  current.setTag("cash-current");after.setTag("cash-after");
  TextView help=h.text("الرصيد الحالي يشمل حركات الوردية المسجّلة.",12);form.addView(help);
  LinearLayout types=new LinearLayout(a);types.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
  for(int i=0;i<4;i++){final int index=i;Button b=StationUi.button(a,LABELS[i],false,()->chooseKind(index));b.setTextSize(11);b.setPadding(0,h.dp(4),0,h.dp(4));b.setMinWidth(0);b.setMinimumWidth(0);b.setTag("cash-kind-"+i);ShiftActivity.MoveIcon icon=new ShiftActivity.MoveIcon(icon(i),Util.NAVY);icon.setBounds(0,0,h.dp(22),h.dp(22));b.setCompoundDrawables(null,icon,null,null);kinds[i]=b;LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,h.dp(74),1);p.setMargins(h.dp(2),0,h.dp(2),0);types.addView(b,p);}form.addView(types,StationUi.space(a));
  partyLabel=h.text("",13);form.addView(partyLabel);person=h.name("اسم العميل أو السائق");person.setTag("cash-person");form.addView(person);
  category=h.note();category.setHint("بند المصروف");category.setTag("cash-category");form.addView(category);
  targetGroup=StationUi.column(a);h.label(targetGroup,"إلى الصندوق");targetGroup.addView(to);h.label(targetGroup,"عملة الاستلام");targetGroup.addView(targetCurrency);targetBalance=h.text("",14);targetGroup.addView(targetBalance);form.addView(targetGroup);
  h.label(form,"المبلغ");amount=h.number("المبلغ بعملة الصندوق");amount.setTag("cash-amount");form.addView(amount);
  h.label(form,"ملاحظة");note=h.note();form.addView(note);feedback=h.text("",13);feedback.setTextColor(Util.GREEN);feedback.setTag("cash-feedback");form.addView(feedback);
  ScrollView scroll=new ScrollView(a);scroll.addView(form);dialog=new AlertDialog.Builder(a).setTitle("إضافة حركة صندوق").setView(scroll).setPositiveButton("إضافة الحركة",null).setNegativeButton("إلغاء",null).create();
  dialog.setCanceledOnTouchOutside(false);dialog.setOnDismissListener(x->host.render());
  changed(from,()->{long box=boxes.id(from);if(box>0)currency.setSelection(Arrays.asList(Db.CURRENCIES).indexOf(ShiftWorkspace.boxCurrency(db,box)));refresh();});
  changed(currency,this::refresh);changed(to,this::refresh);changed(targetCurrency,this::refresh);
  amount.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int co,int af){}public void onTextChanged(CharSequence s,int st,int be,int co){}public void afterTextChanged(Editable e){refresh();}});
  dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(v->save()));chooseKind(selected);
 }
 void show(){dialog.show();dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
 static int icon(int kind){return kind==0?0:kind==1?1:kind==2?3:4;}
 static int tint(int kind){return kind==0?Util.GREEN:kind==1?Util.RED:kind==2?0xffB86A00:Util.NAVY;}
 static LinearLayout tile(WorkspaceForms h,int kind){
  LinearLayout box=StationUi.column(h.a);int tint=tint(kind);box.setGravity(Gravity.CENTER);box.setPadding(h.dp(6),h.dp(10),h.dp(6),h.dp(10));box.setBackground(Util.round(Color.argb(26,Color.red(tint),Color.green(tint),Color.blue(tint)),h.dp(16)));box.setFocusable(true);box.setContentDescription(LABELS[kind]);box.setTag("cash-tile-"+kind);
  ImageView art=new ImageView(h.a);art.setPadding(h.dp(8),h.dp(8),h.dp(8),h.dp(8));art.setBackground(Util.round(Color.WHITE,h.dp(19)));art.setImageDrawable(new ShiftActivity.MoveIcon(icon(kind),tint));box.addView(art,new LinearLayout.LayoutParams(h.dp(38),h.dp(38)));
  TextView name=StationUi.text(h.a,LABELS[kind],14,true);name.setTextColor(tint);name.setGravity(Gravity.CENTER);name.setPadding(0,h.dp(7),0,h.dp(2));box.addView(name);
  double sum=0;try(Cursor c=h.db.getReadableDatabase().rawQuery("SELECT COALESCE(SUM(amount),0) FROM shift_operations WHERE shift_id=? AND section=1 AND kind=?",new String[]{""+h.shift,KINDS[kind]})){c.moveToFirst();sum=c.getDouble(0);}TextView total=StationUi.text(h.a,Calc.money(sum)+" ر.ي",12,false);total.setTextColor(tint);box.addView(total);
  box.setOnClickListener(v->new CashEntryCard(h,kind).show());return box;
 }
 TextView balanceCell(LinearLayout row,String title,boolean highlight){LinearLayout card=StationUi.column(a);card.setPadding(host.dp(8),host.dp(10),host.dp(8),host.dp(10));card.setBackground(Util.round(highlight?Util.ACCENT_SOFT:0xffEEEEEE,host.dp(12)));TextView label=StationUi.text(a,title,12,false);label.setGravity(Gravity.CENTER);card.addView(label);TextView value=StationUi.text(a,"—",18,true);value.setGravity(Gravity.CENTER);card.addView(value);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(host.dp(3),0,host.dp(3),0);row.addView(card,p);return value;}
 static void changed(Spinner spinner,Runnable action){spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){action.run();}});}
 void chooseKind(int index){kind=index;for(int i=0;i<4;i++){kinds[i].setSelected(i==kind);kinds[i].setBackground(Util.round(i==kind?Util.ACCENT:0xffEEEEEE,host.dp(10)));}person.setVisibility(kind<2?View.VISIBLE:View.GONE);category.setVisibility(kind==2?View.VISIBLE:View.GONE);targetGroup.setVisibility(kind==3?View.VISIBLE:View.GONE);partyLabel.setVisibility(kind==3?View.GONE:View.VISIBLE);partyLabel.setText(kind==2?"بند المصروف":"اسم العميل أو السائق");refresh();}
 String code(){return Db.CURRENCIES[currency.getSelectedItemPosition()];}
 String targetCode(){return Db.CURRENCIES[targetCurrency.getSelectedItemPosition()];}
 void refresh(){
  long box=boxes.id(from);if(box==0){current.setText("اختر الصندوق");after.setText("—");targetBalance.setText("");return;}
  String code=code(),unit=Db.currencyName(code);double balance=CashAccounts.expected(db,shift,box,code);Double entered=WorkspaceForms.validCount(amount.getText().toString());double value=entered==null?0:entered;
  current.setText(Calc.money(balance)+" "+unit);after.setText(Calc.money(balance+(kind==0?value:-value))+" "+unit);after.setTextColor(balance+(kind==0?value:-value)<0?Util.RED:Util.NAVY);
  if(kind==3&&boxes.id(to)>0){double before=CashAccounts.expected(db,shift,boxes.id(to),targetCode()),converted=value*db.rate(code)/db.rate(targetCode());targetBalance.setText("رصيد المستلم: "+Calc.money(before)+" ← "+Calc.money(before+converted)+" "+Db.currencyName(targetCode()));}else targetBalance.setText("");
 }
 void save(){
  try{Double value=WorkspaceForms.validCount(amount.getText().toString());if(value==null||value<=0)throw new IllegalArgumentException("أدخل مبلغًا أكبر من صفر");CashAccounts.requireBox(db,boxes.id(from));
   String description=kind<2?person.getText().toString().trim():kind==2?category.getText().toString().trim():"تحويل بين الصناديق";if(description.isEmpty())throw new IllegalArgumentException("اكتب الاسم أو بند المصروف");
   SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();try{long target=kind<2?ShiftWorkspace.customer(db,description):kind==3?boxes.id(to):0;ShiftWorkspace.addCash(db,shift,1,KINDS[kind],boxes.id(from),target,code(),targetCode(),value,description+(note.getText().length()==0?"":" — "+note.getText()));sql.setTransactionSuccessful();}finally{sql.endTransaction();}
   feedback.setText("✓ أضيفت "+LABELS[kind]+" • "+Calc.money(value)+" "+Db.currencyName(code()));amount.setText("");person.setText("");category.setText("");note.setText("");refresh();(kind<2?person:kind==2?category:amount).requestFocus();
  }catch(RuntimeException e){host.error(e);}
 }
}
