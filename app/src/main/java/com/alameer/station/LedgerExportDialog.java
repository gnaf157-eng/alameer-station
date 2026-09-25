package com.alameer.station.shifts;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.print.*;
import android.widget.*;
import java.io.File;
import java.time.LocalDate;
import java.util.Locale;

/** Export one account, never the whole directory or unposted shift input. */
final class LedgerExportDialog {
 final Activity a;final String source,key,name;final AlertDialog dialog;
 final Button fromButton,toButton,printButton,shareButton,allButton;final TextView progress;
 String from="",to=ShiftDates.today();final CancellationSignal cancel=new CancellationSignal();boolean busy;
 LedgerExportDialog(Activity activity,String source,String key,String name){
  a=activity;this.source=source;this.key=key;this.name=name;
  LinearLayout form=StationUi.column(a);int pad=StationUi.dp(a,18);form.setPadding(pad,pad,pad,pad);
  form.addView(StationUi.text(a,name,18,true));form.addView(StationUi.text(a,"اختر فترة الكشف. يشمل رصيد البداية وجميع الحركات خلالها ورصيد النهاية.",13,false));
  fromButton=StationUi.button(a,"",false,()->pick(true));toButton=StationUi.button(a,"",false,()->pick(false));fromButton.setTag("ledger-from");toButton.setTag("ledger-to");
  form.addView(fromButton,StationUi.space(a));form.addView(toButton,StationUi.space(a));
  allButton=StationUi.button(a,"من بداية الحساب حتى اليوم",false,()->{from="";to=ShiftDates.today();dates();});form.addView(allButton,StationUi.space(a));
  printButton=StationUi.button(a,"طباعة الكشف",true,()->export(true));shareButton=StationUi.button(a,"مشاركة PDF",false,()->export(false));printButton.setTag("ledger-print");shareButton.setTag("ledger-share");form.addView(printButton,StationUi.space(a));form.addView(shareButton,StationUi.space(a));
  progress=StationUi.text(a,"",13,false);form.addView(progress);ScrollView scroll=new ScrollView(a);scroll.addView(form);
  dialog=new AlertDialog.Builder(a).setTitle("كشف حركة الحساب").setView(scroll).setNegativeButton("إلغاء",null).create();dialog.setOnDismissListener(d->cancel.cancel());dates();
 }
 void show(){dialog.show();}
 void dates(){fromButton.setText(from.isEmpty()?"من: بداية الحساب":"من: "+from);toButton.setText("إلى: "+to);}
 void pick(boolean start){LocalDate current=LocalDate.parse(start&&!from.isEmpty()?from:to);DatePickerDialog picker=new DatePickerDialog(a,(view,y,m,d)->{String value=String.format(Locale.US,"%04d-%02d-%02d",y,m+1,d);if(start)from=value;else to=value;dates();},current.getYear(),current.getMonthValue()-1,current.getDayOfMonth());picker.getDatePicker().setMaxDate(System.currentTimeMillis());picker.show();}
 void enabled(boolean value){busy=!value;for(Button b:new Button[]{printButton,shareButton,fromButton,toButton,allButton})b.setEnabled(value);}
 void export(boolean print){
  if(busy)return;try{LedgerStatement.validateRange(from,to);}catch(RuntimeException e){progress.setText(e.getMessage());return;}
  final String first=from,last=to;enabled(false);progress.setText("جاري تجهيز كشف الحساب…");
  new Thread(()->{
   File file=null;
   try{cancel.throwIfCanceled();LedgerStatement data;try(Db db=new Db(a.getApplicationContext())){data=LedgerStatement.load(db,source,key,name,first,last);}cancel.throwIfCanceled();LedgerPdf report=new LedgerPdf(a.getApplicationContext(),data);if(!print)file=report.build(a.getApplicationContext(),cancel);final File ready=file;
    a.runOnUiThread(()->{if(cancel.isCanceled()||a.isFinishing()||a.isDestroyed()){if(ready!=null)ready.delete();return;}try{
      if(print){PrintManager manager=(PrintManager)a.getSystemService(Context.PRINT_SERVICE);if(manager==null)throw new IllegalStateException("خدمة الطباعة غير متاحة؛ يمكنك مشاركة PDF");manager.print("كشف "+name,new LedgerPrintAdapter(report),new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setColorMode(PrintAttributes.COLOR_MODE_COLOR).build());}
      else a.startActivity(Intent.createChooser(shareIntent(a,ready,name),"مشاركة كشف الحساب"));dialog.dismiss();
     }catch(RuntimeException e){enabled(true);progress.setText("تعذر فتح الطباعة أو المشاركة: "+e.getMessage());}
    });
   }catch(Exception e){if(file!=null)file.delete();a.runOnUiThread(()->{if(!cancel.isCanceled()&&!a.isFinishing()&&!a.isDestroyed()){enabled(true);progress.setText("تعذر تجهيز الكشف: "+e.getMessage());}});}
  },"ledger-export").start();
 }
 static Intent shareIntent(Context context,File file,String name){Uri uri=androidx.core.content.FileProvider.getUriForFile(context,context.getPackageName()+".files",file);Intent intent=new Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM,uri).putExtra(Intent.EXTRA_SUBJECT,"كشف حركة "+name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri("كشف حساب",uri));return intent;}
}
