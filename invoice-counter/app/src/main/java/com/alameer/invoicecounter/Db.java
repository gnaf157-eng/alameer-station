package com.alameer.invoicecounter;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class Db extends SQLiteOpenHelper {
    private static final String DB_NAME="invoice_counter.db";
    private static final int DB_VERSION=1;

    public static class Item{
        public String name;public double qty;public String unit;public double price;
        public Item(String n,double q,String u,double p){name=n;qty=q;unit=u;price=p;}
    }

    public Db(Context c){super(c,DB_NAME,null,DB_VERSION);}

    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE invoices(id INTEGER PRIMARY KEY AUTOINCREMENT,date TEXT NOT NULL,store TEXT NOT NULL DEFAULT '',total REAL NOT NULL DEFAULT 0,created_at TEXT NOT NULL,image_path TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE invoice_items(id INTEGER PRIMARY KEY AUTOINCREMENT,invoice_id INTEGER NOT NULL,name TEXT NOT NULL,qty REAL NOT NULL DEFAULT 0,unit TEXT NOT NULL DEFAULT '',price REAL NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE item_names(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL UNIQUE)");
        db.execSQL("CREATE INDEX idx_items_invoice ON invoice_items(invoice_id)");
        db.execSQL("CREATE INDEX idx_invoices_date ON invoices(date)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldV,int newV){}

    public long insertInvoice(String date,String store,double total,String imagePath,List<Item> items){
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            ContentValues v=new ContentValues();
            v.put("date",date);v.put("store",store==null?"":store);v.put("total",total);
            v.put("created_at",Util.now());v.put("image_path",imagePath==null?"":imagePath);
            long id=db.insertOrThrow("invoices",null,v);
            insertItems(db,id,items);
            db.setTransactionSuccessful();
            return id;
        }finally{db.endTransaction();}
    }
    public void updateInvoice(long id,String date,String store,double total,String imagePath,List<Item> items){
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            ContentValues v=new ContentValues();
            v.put("date",date);v.put("store",store==null?"":store);v.put("total",total);
            if(imagePath!=null&&imagePath.length()>0)v.put("image_path",imagePath);
            db.update("invoices",v,"id=?",new String[]{String.valueOf(id)});
            db.delete("invoice_items","invoice_id=?",new String[]{String.valueOf(id)});
            insertItems(db,id,items);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    private void insertItems(SQLiteDatabase db,long id,List<Item> items){
        for(Item it:items){
            if(it.name==null)continue;
            String n=it.name.trim();
            if(n.isEmpty())continue;
            ContentValues v=new ContentValues();
            v.put("invoice_id",id);v.put("name",n);v.put("qty",it.qty);
            v.put("unit",it.unit==null?"":it.unit.trim());v.put("price",it.price);
            db.insertOrThrow("invoice_items",null,v);
            db.execSQL("INSERT OR IGNORE INTO item_names(name) VALUES(?)",new Object[]{n});
        }
    }
    public void setImage(long id,String path){
        ContentValues v=new ContentValues();
        v.put("image_path",path==null?"":path);
        getWritableDatabase().update("invoices",v,"id=?",new String[]{String.valueOf(id)});
    }
    public void deleteInvoice(long id){
        SQLiteDatabase db=getWritableDatabase();
        db.delete("invoice_items","invoice_id=?",new String[]{String.valueOf(id)});
        db.delete("invoices","id=?",new String[]{String.valueOf(id)});
    }
    public Cursor invoices(){
        return getReadableDatabase().rawQuery(
            "SELECT invoices.id,date,store,total,(SELECT COUNT(*) FROM invoice_items WHERE invoice_id=invoices.id) AS items FROM invoices ORDER BY date DESC,invoices.id DESC",null);
    }
    public String[] metaOf(long id){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT date,store,total,image_path FROM invoices WHERE id=?",new String[]{String.valueOf(id)})){
            if(c.moveToFirst())return new String[]{c.getString(0),c.getString(1),String.valueOf(c.getDouble(2)),c.getString(3)};
        }
        return null;
    }
    public Item[] itemsOf(long id){
        ArrayList<Item> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT name,qty,unit,price FROM invoice_items WHERE invoice_id=? ORDER BY id",new String[]{String.valueOf(id)})){
            while(c.moveToNext())out.add(new Item(c.getString(0),c.getDouble(1),c.getString(2),c.getDouble(3)));
        }
        return out.toArray(new Item[0]);
    }
    public ArrayList<String> itemNames(){
        ArrayList<String> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT name FROM item_names ORDER BY name",null)){
            while(c.moveToNext())out.add(c.getString(0));
        }
        return out;
    }
    public Cursor report(String from,String to,String filter){
        StringBuilder sql=new StringBuilder(
            "SELECT it.name AS name,SUM(it.qty) AS qty,SUM(it.qty*it.price) AS amount,COUNT(DISTINCT it.invoice_id) AS inv_count FROM invoice_items it JOIN invoices i ON i.id=it.invoice_id WHERE 1=1");
        ArrayList<String> args=new ArrayList<>();
        if(from!=null&&!from.isEmpty()){sql.append(" AND i.date>=?");args.add(from);}
        if(to!=null&&!to.isEmpty()){sql.append(" AND i.date<=?");args.add(to);}
        if(filter!=null&&!filter.trim().isEmpty()){sql.append(" AND it.name LIKE ?");args.add("%"+filter.trim()+"%");}
        sql.append(" GROUP BY it.name ORDER BY qty DESC,name");
        return getReadableDatabase().rawQuery(sql.toString(),args.toArray(new String[0]));
    }
    public Cursor rangeSummary(String from,String to){
        StringBuilder sql=new StringBuilder(
            "SELECT COUNT(DISTINCT i.id) AS invoices,COALESCE(SUM(cnt.c),0) AS items,COALESCE(SUM(i.total),0) AS total FROM invoices i LEFT JOIN (SELECT invoice_id,COUNT(*) c FROM invoice_items GROUP BY invoice_id) cnt ON cnt.invoice_id=i.id WHERE 1=1");
        ArrayList<String> args=new ArrayList<>();
        if(from!=null&&!from.isEmpty()){sql.append(" AND i.date>=?");args.add(from);}
        if(to!=null&&!to.isEmpty()){sql.append(" AND i.date<=?");args.add(to);}
        return getReadableDatabase().rawQuery(sql.toString(),args.toArray(new String[0]));
    }
    public String unitOf(String name,String from,String to){
        StringBuilder sql=new StringBuilder("SELECT it.unit FROM invoice_items it JOIN invoices i ON i.id=it.invoice_id WHERE it.name=? AND it.unit<>'' AND 1=1");
        ArrayList<String> args=new ArrayList<>();
        if(from!=null&&!from.isEmpty()){sql.append(" AND i.date>=?");args.add(from);}
        if(to!=null&&!to.isEmpty()){sql.append(" AND i.date<=?");args.add(to);}
        sql.append(" GROUP BY it.unit ORDER BY COUNT(*) DESC LIMIT 1");
        try(Cursor c=getReadableDatabase().rawQuery(sql.toString(),args.toArray(new String[0]))){
            return c.moveToFirst()?c.getString(0):"";
        }
    }
    public Cursor itemDetail(String name,String from,String to){
        StringBuilder sql=new StringBuilder("SELECT i.date,it.qty,it.unit,it.qty*it.price FROM invoice_items it JOIN invoices i ON i.id=it.invoice_id WHERE it.name=? AND 1=1");
        ArrayList<String> args=new ArrayList<>();
        if(from!=null&&!from.isEmpty()){sql.append(" AND i.date>=?");args.add(from);}
        if(to!=null&&!to.isEmpty()){sql.append(" AND i.date<=?");args.add(to);}
        sql.append(" ORDER BY i.date DESC,i.id DESC");
        return getReadableDatabase().rawQuery(sql.toString(),args.toArray(new String[0]));
    }
}
