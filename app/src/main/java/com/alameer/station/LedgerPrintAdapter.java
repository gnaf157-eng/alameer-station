package com.alameer.station.shifts;

import android.os.*;
import android.print.*;
import java.io.*;
import java.util.concurrent.*;

/** Android owns printer choice and confirmation; writing happens off the UI thread. */
final class LedgerPrintAdapter extends PrintDocumentAdapter {
 final LedgerPdf report;final ExecutorService worker=Executors.newSingleThreadExecutor();final Handler main=new Handler(Looper.getMainLooper());
 private PrintAttributes attributes;
 LedgerPrintAdapter(LedgerPdf report){this.report=report;}
 @Override public void onLayout(PrintAttributes old,PrintAttributes next,CancellationSignal cancel,LayoutResultCallback callback,Bundle extras){
  if(cancel.isCanceled()){callback.onLayoutCancelled();return;}attributes=next;
  callback.onLayoutFinished(new PrintDocumentInfo.Builder("كشف حركة "+report.statement.name+".pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(report.pageCount()).build(),!next.equals(old));
 }
 @Override public void onWrite(PageRange[] pages,ParcelFileDescriptor destination,CancellationSignal cancel,WriteResultCallback callback){
  final PrintAttributes settings=attributes;
  worker.execute(()->{try(FileOutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(destination)){
    PageRange[] written=report.write(out,pages,cancel,settings);main.post(()->{if(cancel.isCanceled())callback.onWriteCancelled();else callback.onWriteFinished(written);});
   }catch(OperationCanceledException e){main.post(callback::onWriteCancelled);}catch(Exception e){main.post(()->callback.onWriteFailed("تعذر تجهيز صفحات الطباعة: "+e.getMessage()));}
  });
 }
 @Override public void onFinish(){worker.shutdown();}
}
