package com.alameer.station.shifts;

import android.app.AlertDialog;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import java.util.Arrays;

/** Entry belongs to the company whose card was opened. */
final class CompanyEntryCard {
 final WorkspaceForms host;final ShiftActivity a;final Db db;final long shift;final String company;
 final WorkspaceForms.Choices boxes;final Spinner box,currency,material;
 final EditText amount,quantity,note;final AutoCompleteTextView driver;
 final LinearLayout paymentGroup,supplyGroup;final TextView current,after,cashBalance,price,total;
 final Button[] kinds=new Button[2];final AlertDialog dialog;int kind;
 CompanyEntryCard(WorkspaceForms h,String selectedCompany){
  if(!Arrays.asList(Db.SUPPLIERS).contains(selectedCompany))throw new IllegalArgumentException("اختر الشركة");
  host=h;a=h.a;db=h.db;shift=h.shift;company=selectedCompany;
  LinearLayout form=h.form();
  LinearLayout balances=new LinearLayout(a);balances.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
  current=balanceCell(balances,"رصيد الشركة الحالي",false);after=balanceCell(balances,"بعد الحركة",true);form.addView(balances,StationUi.space(a));
  current.setTag("company-current");after.setTag("company-after");
  LinearLayout types=new LinearLayout(a);types.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
  for(int i=0;i<2;i++){
   final int index=i;Button b=StationUi.button(a,i==0?"توريد مبلغ":"توريد مواد",false,()->chooseKind(index));b.setTextSize(15);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(h.dp(4),h.dp(6),h.dp(4),h.dp(6));b.setTag("company-kind-"+i);kinds[i]=b;
   LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,h.dp(52),1);p.setMargins(h.dp(3),0,h.dp(3),0);types.addView(b,p);
  }
  form.addView(types,StationUi.space(a));
  paymentGroup=StationUi.column(a);boxes=h.new Choices("cashboxes");box=boxes.spinner();currency=h.spinner(Db.CURRENCY_NAMES);amount=h.number("المبلغ بعملة الصندوق");
  box.setTag("company-box");currency.setTag("company-currency");amount.setTag("company-amount");
  h.label(paymentGroup,"الصندوق الذي سنسحب منه");paymentGroup.addView(box);h.label(paymentGroup,"العملة");paymentGroup.addView(currency);
  h.label(paymentGroup,"المبلغ");paymentGroup.addView(amount);cashBalance=h.text("",14);cashBalance.setTag("company-cash-balance");paymentGroup.addView(cashBalance);form.addView(paymentGroup);
  supplyGroup=StationUi.column(a);material=h.spinner(Db.materialsOf(company));quantity=h.number("الكمية باللتر");driver=h.name("اسم السائق — يُحفظ تلقائيًا");driver.setSingleLine(true);
  material.setTag("company-material");quantity.setTag("company-quantity");driver.setTag("company-driver");
  h.label(supplyGroup,"نوع المادة");supplyGroup.addView(material);h.label(supplyGroup,"الكمية باللترات");supplyGroup.addView(quantity);
  price=h.text("",12);supplyGroup.addView(price);h.label(supplyGroup,"اسم السائق");supplyGroup.addView(driver);total=h.text("",14);total.setTag("company-supply-total");supplyGroup.addView(total);form.addView(supplyGroup);
  note=h.note();form.addView(note,StationUi.space(a));
  ScrollView scroll=new ScrollView(a);scroll.addView(form);dialog=new AlertDialog.Builder(a).setTitle("حساب "+Db.supplierName(company)).setView(scroll).setPositiveButton("موافق",null).setNegativeButton("إلغاء",null).create();
  dialog.setCanceledOnTouchOutside(false);dialog.setOnDismissListener(x->host.render());
  CashEntryCard.changed(box,()->{if(boxes.id(box)>0)currency.setSelection(Arrays.asList(Db.CURRENCIES).indexOf(ShiftWorkspace.boxCurrency(db,boxes.id(box))));refresh();});
  CashEntryCard.changed(currency,this::refresh);CashEntryCard.changed(material,this::refresh);amount.addTextChangedListener(CashEntryCard.watch(this::refresh));quantity.addTextChangedListener(CashEntryCard.watch(this::refresh));
  dialog.setOnShowListener(x->{Button save=dialog.getButton(-1);save.setTextColor(Util.NAVY);save.setBackgroundTintList(null);save.setBackground(Util.round(Util.ACCENT,h.dp(10)));save.setMinHeight(h.dp(48));save.setPadding(h.dp(20),h.dp(8),h.dp(20),h.dp(8));dialog.getButton(-2).setTextColor(Util.NAVY);save.setOnClickListener(v->save());});
  chooseKind(0);
 }
 void show(){dialog.show();dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
 void chooseKind(int index){kind=index;for(int i=0;i<2;i++){kinds[i].setSelected(i==kind);kinds[i].setBackground(Util.round(i==kind?Util.ACCENT:0xffEEEEEE,host.dp(10)));}paymentGroup.setVisibility(kind==0?View.VISIBLE:View.GONE);supplyGroup.setVisibility(kind==1?View.VISIBLE:View.GONE);refresh();}
 String code(){return Db.CURRENCIES[currency.getSelectedItemPosition()];}
 static String balance(double value){return (value<0?"علينا ":"لنا ")+Calc.money(Math.abs(value))+" ر.ي";}
 void refresh(){
  double before=ShiftWorkspace.expectedCompany(db,shift,company);current.setText(balance(before));
  Double entered=WorkspaceForms.validCount((kind==0?amount:quantity).getText().toString());double value=entered==null?0:entered;
  String mat=(String)material.getSelectedItem();double cost=value*db.buyPrice(mat),freight=value*db.freightPrice(mat);
  double projected=before+(kind==0?value*db.rate(code()):-cost);after.setText(balance(projected));after.setTextColor(projected<0?Util.RED:Util.NAVY);
  if(boxes.id(box)>0){double cash=CashAccounts.expected(db,shift,boxes.id(box),code());String unit=Db.currencyName(code());cashBalance.setText("رصيد الصندوق: "+Calc.money(cash)+" "+unit+"\nبعد السداد: "+Calc.money(cash-(kind==0?value:0))+" "+unit);}else cashBalance.setText("اختر صندوق السداد لعرض رصيده.");
  price.setText("شراء اللتر: "+Calc.money(db.buyPrice(mat))+" • نقل اللتر: "+Calc.money(db.freightPrice(mat))+" ر.ي\nالأسعار من الضبط.");
  total.setText("قيمة المواد: "+Calc.money(kind==1?cost:0)+" ر.ي\nمستحق السائق: "+Calc.money(kind==1?freight:0)+" ر.ي");
 }
 void save(){
  Button save=dialog.getButton(-1);save.setEnabled(false);
  try{
   Double value=WorkspaceForms.validCount((kind==0?amount:quantity).getText().toString());if(value==null||value<=0)throw new IllegalArgumentException(kind==0?"أدخل مبلغًا أكبر من صفر":"أدخل كمية أكبر من صفر");
   if(kind==0)ShiftWorkspace.addCash(db,shift,2,"COMPANY_PAYMENT",boxes.id(box),company.equals("GAS")?1:0,code(),"YER",value,"توريد مبلغ — "+Db.supplierName(company)+(note.getText().length()==0?"":" — "+note.getText()));
   else ShiftWorkspace.addCompanySupply(db,shift,company,(String)material.getSelectedItem(),value,driver.getText().toString(),note.getText().toString());
   dialog.dismiss();
  }catch(RuntimeException e){save.setEnabled(true);host.error(e);}
 }
 TextView balanceCell(LinearLayout row,String title,boolean highlight){
  LinearLayout cell=StationUi.column(a);cell.setPadding(host.dp(6),host.dp(10),host.dp(6),host.dp(10));cell.setBackground(Util.round(highlight?Util.ACCENT_SOFT:0xffEEEEEE,host.dp(12)));
  TextView label=StationUi.text(a,title,12,false);label.setGravity(Gravity.CENTER);cell.addView(label);TextView value=StationUi.text(a,"",16,true);value.setGravity(Gravity.CENTER);cell.addView(value);
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(host.dp(3),0,host.dp(3),0);row.addView(cell,p);return value;
 }
 static LinearLayout tile(WorkspaceForms h,String company){
  boolean oil=company.equals("OIL");int tint=oil?0xff8B6400:0xff526293,bg=oil?0xffFFF0B3:0xffE9ECF6;
  LinearLayout card=StationUi.column(h.a);card.setGravity(Gravity.CENTER);card.setPadding(h.dp(8),h.dp(14),h.dp(8),h.dp(14));card.setMinimumHeight(h.dp(156));
  card.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x18000000),Util.round(bg,h.dp(16)),null));card.setFocusable(true);card.setContentDescription("حساب "+Db.supplierName(company));card.setTag("company-tile-"+company);
  ImageView art=new ImageView(h.a);art.setPadding(h.dp(8),h.dp(8),h.dp(8),h.dp(8));art.setBackground(Util.round(Color.WHITE,h.dp(22)));art.setImageDrawable(new CompanyIcon(oil,tint));card.addView(art,new LinearLayout.LayoutParams(h.dp(44),h.dp(44)));
  TextView title=StationUi.text(h.a,"حساب\n"+Db.supplierName(company),17,true);title.setTextColor(tint);title.setGravity(Gravity.CENTER);title.setPadding(0,h.dp(6),0,h.dp(6));card.addView(title);
  TextView amount=StationUi.text(h.a,balance(ShiftWorkspace.expectedCompany(h.db,h.shift,company)),13,false);amount.setTextColor(tint);amount.setGravity(Gravity.CENTER);card.addView(amount);
  card.setOnClickListener(v->new CompanyEntryCard(h,company).show());return card;
 }
 static final class CompanyIcon extends Drawable {
  final boolean oil;final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
  CompanyIcon(boolean oil,int color){this.oil=oil;pen.setColor(color);pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(1.8f);pen.setStrokeCap(Paint.Cap.ROUND);pen.setStrokeJoin(Paint.Join.ROUND);}
  @Override public void draw(Canvas c){Rect b=getBounds();c.save();c.translate(b.left,b.top);c.scale(b.width()/24f,b.height()/24f);
   if(oil){Path drop=new Path();drop.moveTo(12,2);drop.cubicTo(10,6,4,11,4,15);drop.cubicTo(4,25,20,25,20,15);drop.cubicTo(20,11,14,6,12,2);c.drawPath(drop,pen);c.drawArc(7,12,16,20,90,80,false,pen);}
   else{c.drawRoundRect(5,7,19,21,4,4,pen);c.drawRect(9,3,15,7,pen);c.drawLine(8,2,16,2,pen);c.drawLine(6,12,18,12,pen);c.drawLine(8,22,16,22,pen);}
   c.restore();}
  @Override public void setAlpha(int alpha){pen.setAlpha(alpha);}@Override public void setColorFilter(ColorFilter filter){pen.setColorFilter(filter);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
}
