package com.alameer.invoicecounter;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;

/**
 * مزوّد ملفات بسيط لخدمة الصور الكاميرا ومشاركة ملفات التقارير
 * دون الاعتماد على مكتبات خارجية.
 */
public class AppFileProvider extends ContentProvider {
    public static final String AUTHORITY="com.alameer.invoicecounter.files";
    private File root;

    public static Uri uriFor(File f){
        return Uri.fromParts("content",AUTHORITY,f.getName());
    }

    @Override public boolean onCreate(){
        root=getContext().getExternalFilesDir(null);
        return root!=null;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException{
        if(root==null)throw new FileNotFoundException();
        File f=new File(root,uri.getLastPathSegment());
        String base;
        try{base=root.getCanonicalPath()+File.separator;}catch(Exception e){throw new FileNotFoundException();}
        String p;
        try{p=f.getCanonicalPath();}catch(Exception e){throw new FileNotFoundException();}
        if(!p.startsWith(base)||!f.isFile())throw new FileNotFoundException();
        int flags=ParcelFileDescriptor.MODE_READ_ONLY;
        if(mode!=null&&mode.indexOf('w')>=0)flags=ParcelFileDescriptor.MODE_READ_WRITE|ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE;
        return ParcelFileDescriptor.open(f,flags);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder){return null;}
    @Override public String getType(Uri uri){return "application/octet-stream";}
    @Override public Uri insert(Uri uri,ContentValues values){return null;}
    @Override public int delete(Uri uri,String selection,String[] selectionArgs){return 0;}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] selectionArgs){return 0;}
}
