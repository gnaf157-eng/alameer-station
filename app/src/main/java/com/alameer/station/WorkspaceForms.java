package com.alameer.station.shifts;
import android.app.*;
import android.database.Cursor;
import android.graphics.Color;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Mobile forms staging movements against one shift, never against the live ledgers. */
final class WorkspaceForms {
    final ShiftActivity a;final Db db;final long shift;final int section;final LinearLayout root;
    WorkspaceForms(ShiftActivity a,LinearLayout root,int section){this.a=a;this.db=a.db;this.shift=a.shiftId;this.section=section;this.root=root;render();}
    TextView text(String s,int size){TextView t=new TextView(a);t.setText(s);t.setTextColor(Util.NAVY);t.setTextSize(size);t.setPadding(12,12,12,12);return t;}
    Button button(String s,Runnable r){Button b=new Button(a);b.setText(s);b.setTextColor(Util.NAVY);b.setAllCaps(false);b.setMinHeight(52);b.setOnClickListener(v->{try{r.run();}catch(RuntimeException e){error(e);}});root.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
    void error(Exception e){new AlertDialog.Builder(a).setTitle("راجع البيانات").setMessage(e.getMessage()).setPositiveButton("حسنًا",null).show();}
    void render(){
        root.removeAllViews();root.addView(text(section==1?"حركة الصناديق":"حركة المواد",23));
        root.addView(text(db.shiftCode(shift)+"  •  "+db.shiftDate(shift),14));
        root.addView(text("مسودة الوردية — تُرحّل جميع التبويبات عند الإغلاق فقط",14));
        if(section==1){
            root.addView(text("النقد المسلّم من العامل: "+Calc.money(db.total(shift,"CASH"))+" ر.ي — يُضاف تلقائيًا مرة واحدة",16));
            root.addView(text("صندوق استلام نقد العامل",14));
            Choices boxes=new Choices("cashboxes");Spinner pick=boxes.spinner();int selected=boxes.ids.indexOf(ShiftWorkspace.box(db,shift));if(selected>=0)pick.setSelection(selected);root.addView(pick);
            button("حفظ صندوق الاستلام",()->{ShiftWorkspace.selectBox(db,shift,boxes.id(pick));render();});
            button("＋ تعريف صندوق",()->newAccount(false));
            button("＋ تعريف عميل",()->newAccount(true));
        }
        button(section==1?"＋ إضافة حركة صندوق":"＋ إضافة حركة مواد",this::entry);
        try(Cursor c=ShiftWorkspace.operations(db,shift,section)){
            if(c.getCount()==0)root.addView(text("لا توجد حركات إضافية",15));
            while(c.moveToNext()){
                final long row=c.getLong(0);
                TextView line=text(ShiftWorkspace.label(c.getString(1))+" • "+c.getString(7)+"\n"+(section==2?c.getString(4)+" • "+Calc.money(c.getDouble(5))+" — ":"")+Calc.money(c.getDouble(6))+" ر.ي",17);
                line.setBackground(Util.round(Color.WHITE,16));root.addView(line);
                Button remove=new Button(a);remove.setText("حذف من المسودة");remove.setOnClickListener(v->new AlertDialog.Builder(a).setMessage("حذف هذه الحركة من مسودة الوردية؟").setPositiveButton("حذف",(d,w)->{try{ShiftWorkspace.delete(db,shift,row);render();}catch(RuntimeException e){error(e);}}).setNegativeButton("رجوع",null).show());root.addView(remove);
            }
        }
        button((ShiftWorkspace.reviewed(db,shift)&(1<<section))!=0?"✓ تمت مراجعة هذا التبويب":"تأكيد المراجعة / لا توجد حركة أخرى",()->{ShiftWorkspace.review(db,shift,section);render();});
        button(section==1?"متابعة إلى المواد":"مراجعة الوردية وإغلاقها",()->a.workspacePage(section==1?6:2));
    }
    void newAccount(boolean customer){EditText name=new EditText(a);name.setHint(customer?"اسم العميل":"اسم الصندوق");AlertDialog dialog=new AlertDialog.Builder(a).setTitle(customer?"تعريف عميل دون رصيد افتتاحي":"تعريف صندوق دون رصيد افتتاحي").setView(name).setPositiveButton("حفظ",null).setNegativeButton("رجوع",null).create();dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(v->{try{String n=name.getText().toString().trim();if(n.isEmpty())throw new IllegalArgumentException("أدخل الاسم");if(customer)db.addDebtor(n,"",0);else db.addCashbox(n,0);dialog.dismiss();render();}catch(RuntimeException e){name.setError(e.getMessage());}}));dialog.show();}
    final class Choices {
        final ArrayList<Long> ids=new ArrayList<>();final ArrayList<String> names=new ArrayList<>();
        Choices(String table){ids.add(0L);names.add("اختر الحساب");try(Cursor c=db.getReadableDatabase().rawQuery("SELECT id,name FROM "+table+" WHERE active=1 ORDER BY name",null)){while(c.moveToNext()){ids.add(c.getLong(0));names.add(c.getString(1));}}}
        Spinner spinner(){Spinner s=new Spinner(a);s.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,names));return s;}
        long id(Spinner s){return ids.get(s.getSelectedItemPosition());}
    }
    Spinner spinner(String[] options){Spinner s=new Spinner(a);s.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,options));return s;}
    void entry(){
        LinearLayout form=new LinearLayout(a);form.setOrientation(1);form.setPadding(24,12,24,12);
        String[] kinds=section==1?new String[]{"EXPENSE","COLLECTION","LOAN","TRANSFER","SUPPLIER"}:new String[]{"BUY_CREDIT","BUY_CASH","LOSS"};
        String[] labels=new String[kinds.length];for(int i=0;i<kinds.length;i++)labels[i]=ShiftWorkspace.label(kinds[i]);
        Spinner kind=spinner(labels);form.addView(text("نوع الحركة",14));form.addView(kind);
        Choices boxes=new Choices("cashboxes"),customers=new Choices("debtors");Spinner box=boxes.spinner(),other=boxes.spinner(),person=customers.spinner();
        Spinner supplier=spinner(new String[]{"شركة النفط","شركة الغاز"}),material=spinner(Db.MATERIALS);
        form.addView(text("الصندوق (للدفع أو القبض النقدي)",14));form.addView(box);
        TextView targetLabel=text("الحساب المقابل",14);form.addView(targetLabel);form.addView(other);form.addView(person);form.addView(supplier);
        EditText qty=new EditText(a);qty.setHint("الكمية");qty.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText amount=new EditText(a);amount.setHint(section==2?"القيمة الإجمالية بسعر التكلفة — ر.ي":"المبلغ — ر.ي");amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText note=new EditText(a);note.setHint("البيان / رقم الفاتورة");
        if(section==2){form.addView(text("المادة",14));form.addView(material);form.addView(qty);form.addView(text("التوريد الآجل على شركة النفط أو الغاز حسب المادة. الفاقد بسعر التكلفة. أجرة النقل تُسجّل بحركة منفصلة مع بيانها.",14));}
        else form.addView(text("هذه حركات إضافية مستقلة؛ لا تكرر المقبوضات والمخاريج المسجلة في مطابقة العامل.",14));
        form.addView(amount);form.addView(note);
        kind.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int position,long id){String k=kinds[position];other.setVisibility(k.equals("TRANSFER")?View.VISIBLE:View.GONE);person.setVisibility(k.equals("COLLECTION")||k.equals("LOAN")?View.VISIBLE:View.GONE);supplier.setVisibility(k.equals("SUPPLIER")?View.VISIBLE:View.GONE);targetLabel.setVisibility(section==1&&!k.equals("EXPENSE")?View.VISIBLE:View.GONE);box.setEnabled(section==1||k.equals("BUY_CASH"));}});
        ScrollView scroll=new ScrollView(a);scroll.addView(form);
        String draftKey="workspace_"+shift+"_"+section;String[] date={db.shiftDate(shift)};EntryDraft.restore(a,draftKey,date,qty,amount,note);
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("إضافة إلى مسودة الوردية").setView(scroll).setPositiveButton("إضافة",null).setNeutralButton("حفظ الخانات لاحقًا",null).setNegativeButton("رجوع",null).create();
        dialog.setOnShowListener(x->{dialog.getButton(-3).setOnClickListener(v->{if(EntryDraft.save(a,draftKey,date[0],qty,amount,note))dialog.dismiss();});dialog.getButton(-1).setOnClickListener(v->{try{String k=kinds[kind.getSelectedItemPosition()];long target=k.equals("TRANSFER")?boxes.id(other):k.equals("SUPPLIER")?supplier.getSelectedItemPosition():customers.id(person);ShiftWorkspace.add(db,shift,section,k,boxes.id(box),target,Db.MATERIALS[material.getSelectedItemPosition()],Calc.number(qty.getText().toString()),Calc.number(amount.getText().toString()),note.getText().toString());EntryDraft.clear(a,draftKey);dialog.dismiss();render();}catch(RuntimeException e){amount.setError(e.getMessage());}});});dialog.show();
    }
}
