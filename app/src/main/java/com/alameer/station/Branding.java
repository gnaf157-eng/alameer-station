package com.alameer.station.shifts;
import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.util.AtomicFile;
import java.io.*;

final class Branding {
    static final String CREDIT="طابق ورحّل | أبوقناف للأتمتة";
    static String stationName(Db db){return db.setting("station_name","محطة الأمير");}
    private static File file(Context c){return new File(c.getFilesDir(),"station-logo.png");}
    static Bitmap logo(Context c){
        try(InputStream in=new AtomicFile(file(c)).openRead()){return BitmapFactory.decodeStream(in);}
        catch(IOException e){return null;}
    }
    static void removeLogo(Context c){new AtomicFile(file(c)).delete();}
    static void importLogo(Context c,Uri uri)throws IOException{
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
        try(InputStream in=c.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,o);}
        if(o.outWidth<=0||o.outHeight<=0)throw new IOException("صورة غير صالحة");
        o.inJustDecodeBounds=false;o.inSampleSize=1;
        while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>512)o.inSampleSize*=2;
        Bitmap bitmap;
        try(InputStream in=c.getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(in,null,o);}
        if(bitmap==null)throw new IOException("تعذر قراءة الصورة");
        try{
            try(InputStream in=c.getContentResolver().openInputStream(uri)){
                int orientation=new android.media.ExifInterface(in).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);
                Matrix m=new Matrix();
                switch(orientation){
                    case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;
                    case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;
                    case 7:m.setRotate(270);m.postScale(-1,1);break;case 8:m.setRotate(270);break;
                }
                if(!m.isIdentity()){Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),m,true);if(rotated!=bitmap){bitmap.recycle();bitmap=rotated;}}
            }catch(IOException ignored){}
            AtomicFile target=new AtomicFile(file(c));FileOutputStream out=null;
            try{out=target.startWrite();if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("تعذر حفظ الصورة");target.finishWrite(out);}
            catch(IOException e){target.failWrite(out);throw e;}
        }finally{bitmap.recycle();}
    }
}
