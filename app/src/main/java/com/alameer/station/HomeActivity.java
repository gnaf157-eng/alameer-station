package com.alameer.station.shifts;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** The official ledgers are read-only. The three bottom actions are the entry workflow. */
public class HomeActivity extends Activity {
 private Db db;private LinearLayout nav;
 @Override protected void onCreate(Bundle state){super.onCreate(state);db=new Db(this);Db.signIn(Branding.stationName(db));build();new AppUpdater(this).check(false);}
 private int dp(int n){return StationUi.dp(this,n);}
 private void build(){
  LinearLayout shell=StationUi.column(this);shell.setBackgroundColor(Util.BG);shell.setPadding(dp(16),dp(12),dp(16),dp(10));
  LinearLayout head=new LinearLayout(this);head.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(4),dp(8),dp(4),dp(8));head.setBackground(Util.round(Color.WHITE,dp(16)));
  head.addView(StationUi.button(this,"▥\nلوحة التحكم",false,()->startActivity(new Intent(this,ControlPanelActivity.class))),new LinearLayout.LayoutParams(dp(82),-2));
  TextView title=StationUi.text(this,Branding.stationName(db),21,true);title.setGravity(Gravity.CENTER);title.setMaxLines(2);head.addView(title,new LinearLayout.LayoutParams(0,-2,1));
  head.addView(StationUi.button(this,"⚙\nالإعدادات",false,()->{Intent i=new Intent(this,ShiftActivity.class);i.putExtra("openSettings",true);startActivity(i);}),new LinearLayout.LayoutParams(dp(82),-2));shell.addView(head);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout grid=StationUi.column(this);grid.setGravity(Gravity.CENTER_VERTICAL);grid.setPadding(0,dp(14),0,dp(14));
  TextView label=StationUi.text(this,"الدفاتر الرسمية",16,true);label.setGravity(Gravity.CENTER);grid.addView(label,StationUi.space(this));
  LinearLayout row1=new LinearLayout(this);row1.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);row1.addView(tile("العملاء","الأرصدة وكشوف الحساب",4,()->ledger("debt_entries")),cell());row1.addView(tile("المواد","المخزون وحسابات الشركات",6,()->ledger("material_entries")),cell());grid.addView(row1);
  LinearLayout row2=new LinearLayout(this);row2.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);row2.addView(tile("الصناديق","حركة النقد والأرصدة",3,()->ledger("cashbox_entries")),cell());row2.addView(tile("المصاريف","البنود وكشوف المصروفات",5,()->ledger("expense_entries")),cell());grid.addView(row2,StationUi.space(this));
  LinearLayout archive=tile("الأرشيف","تفاصيل الورديات المرحّلة وتقاريرها",8,()->startActivity(new Intent(this,ArchiveActivity.class)));archive.getChildAt(2).setVisibility(View.GONE);grid.addView(archive,new LinearLayout.LayoutParams(-1,dp(130)));
  scroll.addView(grid);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));nav=new LinearLayout(this);nav.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);nav.setPadding(dp(2),dp(6),dp(2),dp(6));nav.setBackground(Util.round(Color.WHITE,dp(16)));shell.addView(nav);
  setContentView(shell);Util.safeInsets(shell);refreshNavigation();
 }
 private void ledger(String table){startActivity(LedgerActivity.intent(this,table));}
 private LinearLayout.LayoutParams cell(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(156),1);p.setMargins(dp(4),0,dp(4),0);return p;}
 private LinearLayout tile(String title,String note,int kind,Runnable action){
  LinearLayout box=StationUi.card(this);box.setGravity(Gravity.CENTER);box.setOnClickListener(v->action.run());box.setContentDescription(title);box.setFocusable(true);
  ImageView icon=new ImageView(this);icon.setImageDrawable(new HomeIcon(kind));icon.setPadding(dp(12),dp(12),dp(12),dp(12));icon.setBackground(Util.round(Util.ACCENT,dp(28)));box.addView(icon,new LinearLayout.LayoutParams(dp(56),dp(56)));
  TextView name=StationUi.text(this,title,18,true);name.setGravity(Gravity.CENTER);name.setPadding(0,dp(7),0,dp(3));box.addView(name);TextView subtitle=StationUi.text(this,note,12,false);subtitle.setGravity(Gravity.CENTER);subtitle.setTextColor(0xff686868);box.addView(subtitle);return box;
 }
 private void refreshNavigation(){if(nav==null)return;nav.removeAllViews();int reviewed=0;try(Cursor c=db.getReadableDatabase().rawQuery("SELECT w.reviewed FROM shift_workspace w JOIN shifts s ON s.id=w.shift_id WHERE s.status='OPEN' ORDER BY s.id LIMIT 1",null)){if(c.moveToFirst())reviewed=c.getInt(0);}int stage=(reviewed&1)==0?0:(reviewed&2)==0?1:2;
  String[] names={"مطابقة العامل","الصناديق","المواد"};int[] pages={0,5,6};
  for(int k=0;k<3;k++){final int page=pages[k];Button b=StationUi.button(this,names[k],k==stage,()->startActivity(new Intent(this,ShiftActivity.class).putExtra("startPage",page)));b.setTextSize(13);b.setSelected(k==stage);HomeIcon art=new HomeIcon(k==0?0:k==1?3:6);art.setBounds(0,0,dp(24),dp(24));b.setCompoundDrawables(null,art,null,null);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(78),1);p.setMargins(dp(3),0,dp(3),0);nav.addView(b,p);}
 }
 @Override protected void onResume(){super.onResume();build();}
 @Override protected void onDestroy(){if(db!=null)db.close();super.onDestroy();}
    private static class HomeIcon extends Drawable {
        final int kind;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        HomeIcon(int kind) { this.kind = kind; }
        public void draw(Canvas c) {
            c.save();
            c.translate(getBounds().left, getBounds().top);
            c.scale(getBounds().width() / 24f, getBounds().height() / 24f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(Util.NAVY);
            if (kind == 0) {
                c.drawRoundRect(4, 3, 14, 21, 1.5f, 1.5f, paint);
                c.drawLine(2, 21, 16, 21, paint);
                c.drawLine(14, 11, 17, 11, paint);
                c.drawLine(17, 11, 17, 18, paint);
                c.drawArc(17, 16, 21, 20, 0, 180, false, paint);
                c.drawLine(21, 18, 21, 7, paint);
                c.drawLine(21, 7, 18, 4, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Util.ACCENT);
                c.drawRoundRect(6, 5.5f, 12, 10, 1, 1, paint);
            } else if (kind == 2) {
                // لوحة تحكم: إطار وأعمدة بيانية.
                c.drawRoundRect(2.5f, 3.5f, 21.5f, 20.5f, 2f, 2f, paint);
                c.drawLine(2.5f, 7.6f, 21.5f, 7.6f, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Util.ACCENT);
                c.drawRoundRect(6, 13.5f, 8.4f, 17.8f, 0.8f, 0.8f, paint);
                c.drawRoundRect(10.8f, 10.5f, 13.2f, 17.8f, 0.8f, 0.8f, paint);
                c.drawRoundRect(15.6f, 12f, 18, 17.8f, 0.8f, 0.8f, paint);
            } else if (kind == 3) {
                // صندوق نقدي.
                c.drawRoundRect(2.5f, 7, 21.5f, 20, 2, 2, paint);
                c.drawLine(2.5f, 11, 21.5f, 11, paint);
                c.drawLine(7, 7, 7, 4.5f, paint);
                c.drawLine(17, 7, 17, 4.5f, paint);
                c.drawLine(7, 4.5f, 17, 4.5f, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Util.ACCENT);
                c.drawCircle(12, 15.5f, 3, paint);
            } else if (kind == 4) {
                // ورقة نقدية للديون.
                c.drawRoundRect(2.5f, 6, 21.5f, 18, 1.8f, 1.8f, paint);
                c.drawLine(5.6f, 6, 5.6f, 18, paint);
                c.drawLine(18.4f, 6, 18.4f, 18, paint);
                c.drawCircle(12, 12, 3.1f, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Util.ACCENT);
                c.drawCircle(12, 12, 1.5f, paint);
            } else if (kind == 5) {
                // فاتورة المخاريج.
                android.graphics.Path receipt = new android.graphics.Path();
                receipt.moveTo(4.5f, 2.5f);
                receipt.lineTo(19.5f, 2.5f);
                receipt.lineTo(19.5f, 21.5f);
                receipt.lineTo(17, 19.6f);
                receipt.lineTo(14.5f, 21.5f);
                receipt.lineTo(12, 19.6f);
                receipt.lineTo(9.5f, 21.5f);
                receipt.lineTo(7, 19.6f);
                receipt.lineTo(4.5f, 21.5f);
                receipt.close();
                c.drawPath(receipt, paint);
                paint.setColor(Util.ACCENT);
                c.drawLine(8, 8, 16, 8, paint);
                c.drawLine(8, 12, 16, 12, paint);
                c.drawLine(8, 15.6f, 13, 15.6f, paint);
            } else if (kind == 6) {
                // خرطوم وقود للمواد.
                c.drawRoundRect(5, 4, 14.5f, 21, 1.6f, 1.6f, paint);
                c.drawLine(3, 21, 16.5f, 21, paint);
                c.drawLine(14.5f, 9.5f, 17, 9.5f, paint);
                c.drawLine(17, 9.5f, 17, 16.5f, paint);
                android.graphics.Path hose = new android.graphics.Path();
                hose.moveTo(17, 16.5f);
                hose.cubicTo(17, 19.4f, 21, 19.4f, 21, 16.5f);
                hose.lineTo(21, 6.5f);
                hose.lineTo(18.4f, 3.6f);
                c.drawPath(hose, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Util.ACCENT);
                c.drawRoundRect(7, 6.5f, 12.5f, 11.5f, 0.8f, 0.8f, paint);
            } else if (kind == 7) {
                // مستند وارد مع سهم نازل: ورديات العامل.
                c.drawRoundRect(4.5f, 2.5f, 19.5f, 21.5f, 2f, 2f, paint);
                paint.setColor(Util.ACCENT);
                c.drawLine(12, 7.5f, 12, 15, paint);
                c.drawLine(12, 15, 9, 12, paint);
                c.drawLine(12, 15, 15, 12, paint);
                c.drawLine(8, 18, 16, 18, paint);
            } else if (kind == 8) {
                // دفتر مفتوح: الدفاتر الرسمية.
                c.drawLine(12, 6.5f, 12, 20, paint);
                android.graphics.Path left = new android.graphics.Path();
                left.moveTo(12, 6.5f);
                left.cubicTo(9.5f, 4.2f, 6, 4.2f, 3, 5.5f);
                left.lineTo(3, 18.5f);
                left.cubicTo(6, 17.2f, 9.5f, 17.2f, 12, 20);
                c.drawPath(left, paint);
                android.graphics.Path right = new android.graphics.Path();
                right.moveTo(12, 6.5f);
                right.cubicTo(14.5f, 4.2f, 18, 4.2f, 21, 5.5f);
                right.lineTo(21, 18.5f);
                right.cubicTo(18, 17.2f, 14.5f, 17.2f, 12, 20);
                c.drawPath(right, paint);
                paint.setColor(Util.ACCENT);
                c.drawLine(5.5f, 9, 9.5f, 9, paint);
                c.drawLine(14.5f, 9, 18.5f, 9, paint);
                c.drawLine(5.5f, 12.5f, 9.5f, 12.5f, paint);
                c.drawLine(14.5f, 12.5f, 18.5f, 12.5f, paint);
            } else if (kind == 9) {
                android.graphics.Path drop = new android.graphics.Path();
                drop.moveTo(12, 2.5f);
                drop.cubicTo(17.5f, 9, 20, 12.5f, 20, 15.5f);
                drop.cubicTo(20, 19.6f, 16.4f, 22, 12, 22);
                drop.cubicTo(7.6f, 22, 4, 19.6f, 4, 15.5f);
                drop.cubicTo(4, 12.5f, 6.5f, 9, 12, 2.5f);
                drop.close();
                c.drawPath(drop, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Util.ACCENT);
                c.drawRect(8, 13.5f, 16, 15.2f, paint);
                c.drawRect(11.1f, 10.4f, 12.9f, 18.3f, paint);
            } else {
                android.graphics.Path shield = new android.graphics.Path();
                shield.moveTo(12, 2.2f);
                shield.lineTo(20.5f, 5.6f);
                shield.lineTo(20.5f, 12.4f);
                shield.cubicTo(20.5f, 17.4f, 16.9f, 20.6f, 12, 22.3f);
                shield.cubicTo(7.1f, 20.6f, 3.5f, 17.4f, 3.5f, 12.4f);
                shield.lineTo(3.5f, 5.6f);
                shield.close();
                c.drawPath(shield, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(Util.ACCENT);
                c.drawRoundRect(8, 11.4f, 16, 17, 1.2f, 1.2f, paint);
                android.graphics.Path arc = new android.graphics.Path();
                arc.addArc(9.6f, 7.4f, 14.4f, 13.4f, 180, 180);
                c.drawPath(arc, paint);
            }
            c.restore();
        }
        public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
        public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}


