package com.alameer.station.shifts;
import android.app.Activity;
import android.os.Bundle;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
/** Immutable ledger viewer. Legacy rows remain visible and clearly labelled. */
public final class LedgerActivity extends Activity {
    public static Intent intent(Context c,String table){return new Intent(c,LedgerActivity.class).putExtra("table",table);}
    @Override public void onCreate(Bundle b){super.onCreate(b);Db db=new Db(this);String table=getIntent().getStringExtra("table");
        if(!java.util.Arrays.asList(ShiftWorkspace.LEDGERS).contains(table)){finish();return;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);root.setPadding(24,24,24,24);root.setBackgroundColor(Util.BG);
        TextView title=new TextView(this);title.setText("الدفتر الرسمي • عرض فقط");title.setTextSize(23);title.setTextColor(Util.NAVY);root.addView(title);
        TextView help=new TextView(this);help.setText("آخر 500 حركة مرحّلة. السجلات السابقة محفوظة. الإدخال من الوردية فقط.");help.setTextSize(15);help.setTextColor(Util.NAVY);root.addView(help);
        Button back=new Button(this);back.setText("العودة للرئيسية");back.setOnClickListener(v->finish());root.addView(back);
        ScrollView scroll=new ScrollView(this);LinearLayout rows=new LinearLayout(this);rows.setOrientation(1);scroll.addView(rows);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        String name=table.equals("cashbox_entries")?"(SELECT name FROM cashboxes WHERE id=e.box_id)":table.equals("debt_entries")?"(SELECT name FROM debtors WHERE id=e.debtor_id)":table.equals("material_entries")?"e.material":table.equals("expense_entries")?"e.category":table.equals("supplier_entries")?"e.supplier":"e.source";
        String amount=table.equals("material_entries")?"e.litres":table.equals("journal")?"e.total":"e.amount";
        String note=table.equals("journal")?"e.memo":"e.note";
        String direction=table.equals("cashbox_entries")||table.equals("debt_entries")||table.equals("material_entries")?"e.direction":table.equals("supplier_entries")?"e.kind":"''";
        String code="COALESCE((SELECT s.shift_code FROM shift_links l JOIN shifts s ON s.id=l.shift_id WHERE l.entity='"+table+"' AND l.row_id=e.id),";
        code+=java.util.Arrays.asList("cashbox_entries","debt_entries","expense_entries","material_entries").contains(table)?"(SELECT COALESCE(NULLIF(shift_code,''),'#'||id) FROM shifts WHERE id=e.source_shift),":"";
        code+="'سجل سابق')";
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT e.entry_date,"+name+","+amount+","+note+","+direction+","+code+" FROM "+table+" e ORDER BY e.entry_date DESC,e.id DESC LIMIT 500",null)){
            while(c.moveToNext()){String dir=c.getString(4);String translated=dir.equals("IN")?"وارد":dir.equals("OUT")?"صادر":dir.equals("DEBT")?"عليه":dir.equals("PAID")?"سداد":dir;TextView row=new TextView(this);row.setText(c.getString(1)+" • "+translated+" • "+Calc.money(c.getDouble(2))+"\n"+c.getString(3)+"\n"+c.getString(0)+" | "+c.getString(5));row.setTextSize(16);row.setTextColor(Util.NAVY);row.setTextIsSelectable(true);row.setPadding(18,18,18,18);row.setBackground(Util.round(Color.WHITE,16));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,12,0,0);rows.addView(row,lp);}
        }finally{db.close();}setContentView(root);Util.safeInsets(root);
    }
}
