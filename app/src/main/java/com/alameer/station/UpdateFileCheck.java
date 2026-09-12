package com.alameer.station.shifts;
import java.io.*;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.ZipFile;
/** Reject incomplete downloads, HTML, and GitHub artifact ZIPs before installation. */
public final class UpdateFileCheck {
    public static void verify(File file,long expectedSize,String expectedHash)throws Exception{
        if(expectedSize>0&&file.length()!=expectedSize)throw new IOException("تنزيل غير مكتمل أو ملف مختلف. أعد تنزيل التحديث.");
        try(ZipFile zip=new ZipFile(file)){
            if(zip.getEntry("AndroidManifest.xml")==null||zip.getEntry("classes.dex")==null)
                throw new IOException("تم تنزيل ملف مضغوط بدل APK. لم يبدأ التثبيت.");
        }catch(java.util.zip.ZipException e){throw new IOException("استُلمت صفحة أو ملف تالف بدل APK. أعد المحاولة.");}
        if(!expectedHash.isEmpty()){
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream in=new FileInputStream(file)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}
            StringBuilder actual=new StringBuilder();for(byte b:digest.digest())actual.append(String.format(Locale.US,"%02x",b&255));
            if(!actual.toString().equalsIgnoreCase(expectedHash))throw new IOException("فشل التحقق من سلامة التحديث. أعد التنزيل.");
        }
    }
}
