package com.alameer.station.shifts;

import android.app.AlertDialog;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.print.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import androidx.test.platform.app.InstrumentationRegistry;

import static org.junit.Assert.*;

@RunWith(org.junit.runners.JUnit4.class)
public class LedgerPdfDeviceTest {
 Context context;Db db;long customer;
 @Before public void start(){context=InstrumentationRegistry.getInstrumentation().getTargetContext();context.deleteDatabase("alameer_station.db");db=new Db(context);db.setTelegramOn(false);db.setSetting("station_name","محطة الأمير");customer=db.addDebtor("السائق سالم عبدالله","",1000);}
 @After public void stop(){db.close();context.deleteDatabase("alameer_station.db");}
 static String repeated(String value,int count){StringBuilder b=new StringBuilder();for(int i=0;i<count;i++)b.append(value);return b.toString();}
 void debt(String direction,double amount,String note){db.getWritableDatabase().execSQL("INSERT INTO debt_entries(debtor_id,entry_date,direction,amount,note,created_at) VALUES(?,'2026-01-15',?,?,?,'2026-01-15')",new Object[]{customer,direction,amount,note});}
 LedgerStatement customerStatement(){return LedgerStatement.load(db,"debt_entries",""+customer,"السائق سالم عبدالله","2026-01-01","2026-01-31");}
 File artifact(LedgerPdf report,String name)throws Exception{File pdf=report.build(context,new CancellationSignal());byte[] bytes=Files.readAllBytes(pdf.toPath());assertEquals("%PDF-",new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII));assertTrue(bytes.length>1000);File dir=new File(context.getFilesDir(),"ledger-qa");assertTrue(dir.isDirectory()||dir.mkdirs());File out=new File(dir,name+".pdf");Files.copy(pdf.toPath(),out.toPath(),StandardCopyOption.REPLACE_EXISTING);return pdf;}
 @Test public void allAccountKindsCreatePrintableArabicStatements()throws Exception{
  debt("DEBT",350,"وقود على الحساب");debt("PAID",1500,"أجرة نقل مستحقة للسائق - تسوية الرصيد");LedgerPdf customerPdf=new LedgerPdf(context,customerStatement());assertEquals(1,customerPdf.pageCount());artifact(customerPdf,"ledger-customer");
  long box=db.addCashbox("الصراف",0);db.setRate("SAR",100);CashAccounts.setOpening(db,box,"SAR",-50);db.getWritableDatabase().execSQL("INSERT INTO cashbox_entries(box_id,direction,amount,orig_amount,rate,currency,note,entry_date,created_at) VALUES(?,'IN',20000,200,100,'SAR','توريد للصندوق','2026-01-15','2026-01-15')",new Object[]{box});
  artifact(new LedgerPdf(context,LedgerStatement.load(db,"cashbox_entries",CashAccounts.key(box,"SAR"),"الصراف • سعودي","","2026-01-31")),"ledger-cash");
  db.getWritableDatabase().execSQL("INSERT INTO material_entries(material,direction,litres,note,entry_date,created_at) VALUES('ديزل','IN',15640,'توريد من الشركة','2026-01-01','2026-01-01'),('ديزل','OUT',125.375,'مبيعات وردية','2026-01-15','2026-01-15')");
  artifact(new LedgerPdf(context,LedgerStatement.load(db,"material_entries","ديزل","الديزل","2026-01-10","2026-01-31")),"ledger-material");
  db.getWritableDatabase().execSQL("INSERT INTO supplier_entries(supplier,kind,amount,material,litres,note,entry_date,created_at) VALUES('OIL','PAY',3000000,'',0,'توريد من صندوق المحطة','2026-01-01','2026-01-01'),('OIL','BUY',2000000,'ديزل',10000,'السائق سالم عبدالله','2026-01-15','2026-01-15')");
  artifact(new LedgerPdf(context,LedgerStatement.load(db,"supplier_entries","OIL","حساب شركة النفط","2026-01-10","2026-01-31")),"ledger-company");
  db.getWritableDatabase().execSQL("INSERT INTO expense_entries(category,amount,note,entry_date,created_at) VALUES('كهرباء',25000,'فاتورة المحطة','2026-01-15','2026-01-15')");
  artifact(new LedgerPdf(context,LedgerStatement.load(db,"expense_entries","كهرباء","مصروف الكهرباء","2026-01-01","2026-01-31")),"ledger-expense");
 }
 @Test public void longNotesContinueAcrossPagesWithoutClippingAndSelectedPagesAreRespected()throws Exception{
  String longNote=repeated("نقل وقود إلى المحطة ومراجعة الكمية وأجرة السائق ",90)+"نهاية البيان الطويل";debt("DEBT",25,longNote);for(int i=0;i<62;i++)debt(i%2==0?"DEBT":"PAID",10,"حركة رقم "+(i+1));
  LedgerStatement s=customerStatement();LedgerPdf report=new LedgerPdf(context,s);assertTrue(report.pageCount()>2);int totals=0;StringBuilder text=new StringBuilder();
  for(ArrayList<LedgerPdf.Block> page:report.pages){int bottom=report.bodyTop;for(LedgerPdf.Block block:page){bottom+=block.height;text.append(block.cells[1].getText());if(block.kind==2)totals++;}assertTrue(bottom<=LedgerPdf.BOTTOM);}
  assertEquals(1,totals);assertTrue(text.toString().contains("نهاية البيان الطويل"));assertEquals(1025,s.closing,0.00001);artifact(report,"ledger-multipage");
  File selected=new File(context.getFilesDir(),"ledger-qa/ledger-selected-page.pdf");try(FileOutputStream out=new FileOutputStream(selected)){PageRange[] written=report.write(out,new PageRange[]{new PageRange(1,1)},new CancellationSignal(),null);assertEquals(1,written.length);assertEquals(new PageRange(1,1),written[0]);}
  PrintAttributes landscape=new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape()).setMinMargins(new PrintAttributes.Margins(500,500,500,500)).setColorMode(PrintAttributes.COLOR_MODE_COLOR).build();
  try(FileOutputStream out=new FileOutputStream(new File(context.getFilesDir(),"ledger-qa/ledger-landscape.pdf"))){report.write(out,new PageRange[]{new PageRange(0,0)},new CancellationSignal(),landscape);}
 }
 @Test public void emptyPeriodKeepsOpeningAndCancellationDoesNotCreateAFile()throws Exception{
  LedgerPdf report=new LedgerPdf(context,customerStatement());assertEquals(1,report.pageCount());artifact(report,"ledger-no-movements");CancellationSignal cancel=new CancellationSignal();cancel.cancel();File dir=new File(context.getCacheDir(),"exports");int before=Objects.requireNonNull(dir.list()).length;
  try{report.build(context,cancel);fail("Expected cancellation");}catch(OperationCanceledException expected){}assertEquals(before,Objects.requireNonNull(dir.list()).length);
 }
 @Test public void sharedPdfHasReadableFileProviderUriAndNoWriteGrant()throws Exception{
  File file=artifact(new LedgerPdf(context,customerStatement()),"ledger-share");Intent intent=LedgerExportDialog.shareIntent(context,file,"سالم");assertEquals("application/pdf",intent.getType());assertTrue((intent.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0);assertEquals(0,intent.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION);android.net.Uri uri=intent.getParcelableExtra(Intent.EXTRA_STREAM);assertEquals("content",uri.getScheme());assertNotNull(intent.getClipData());try(InputStream in=context.getContentResolver().openInputStream(uri)){assertEquals('%',in.read());}
 }
}
