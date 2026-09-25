package com.alameer.station.shifts;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

/** Shared suggestions retain the accounting role, including same-name collisions. */
final class NameDirectory {
    static final String CUSTOMER="CUSTOMER", EXPENSE="EXPENSE";
    static final class Entry {
        final String name,role;
        Entry(String name,String role){this.name=name;this.role=role;}
        @Override public String toString(){return name+(role.isEmpty()?"":" — "+label(role));}
    }
    static String label(String role){return EXPENSE.equals(role)?"بند مخاريج":CUSTOMER.equals(role)?"حساب عميل":"اختر نوع الاسم عند الحفظ";}
    private static void add(Map<String,Set<String>> names,String name,String role){
        if(name==null||name.trim().isEmpty())return;
        Set<String> roles=names.get(name.trim());
        if(roles==null){roles=new TreeSet<>();names.put(name.trim(),roles);}
        if(!role.isEmpty())roles.add(role);
    }
    static ArrayList<Entry> entries(Db db){
        Map<String,Set<String>> names=new TreeMap<>();
        String sql="SELECT name,type FROM remembered_names UNION SELECT name,type FROM movements "+
            "UNION SELECT name,'CUSTOMER' FROM debtors "+
            "UNION SELECT category,'EXPENSE' FROM expense_entries "+
            "UNION SELECT driver_name,'CUSTOMER' FROM shift_operations WHERE driver_name<>'' "+
            "UNION SELECT name,'' FROM workers UNION SELECT worker_name,'' FROM shift_workspace";
        try(Cursor c=db.getReadableDatabase().rawQuery(sql,null)){
            while(c.moveToNext()){
                String type=c.getString(1);
                add(names,c.getString(0),EXPENSE.equals(type)?EXPENSE:Arrays.asList(CUSTOMER,"COLLECTION","DEBT").contains(type)?CUSTOMER:"");
            }
        }
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT party_name,note FROM shift_operations WHERE kind='EXPENSE'",null)){
            while(c.moveToNext())add(names,c.getString(0).isEmpty()?c.getString(1).split(" — ",2)[0]:c.getString(0),EXPENSE);
        }
        ArrayList<Entry> out=new ArrayList<>();
        for(Map.Entry<String,Set<String>> n:names.entrySet()){
            if(n.getValue().isEmpty())out.add(new Entry(n.getKey(),""));
            else for(String role:n.getValue())out.add(new Entry(n.getKey(),role));
        }
        return out;
    }
    static ArrayList<String> names(Db db){Set<String> names=new TreeSet<>();for(Entry e:entries(db))names.add(e.name);return new ArrayList<>(names);}
    static String role(Db db,String name){
        String role="";int found=0;
        for(Entry e:entries(db))if(e.name.equals(name.trim())&&!e.role.isEmpty()){role=e.role;found++;}
        return found==1?role:"";
    }
    static void remember(Db db,String name,String role){
        if(!Arrays.asList(CUSTOMER,EXPENSE).contains(role)||name==null||name.trim().isEmpty())throw new IllegalArgumentException("اختر نوع الاسم");
        ContentValues v=new ContentValues();v.put("name",name.trim());v.put("type",role);
        db.getWritableDatabase().insertWithOnConflict("remembered_names",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }
    static long addCash(Db db,long shift,long box,String direction,String currency,double amount,String name,String role,String note){
        if(!Arrays.asList("IN","OUT").contains(direction)||!Arrays.asList(CUSTOMER,EXPENSE).contains(role))throw new IllegalArgumentException("اختر نوع الحركة والاسم");
        if(name==null||name.trim().isEmpty())throw new IllegalArgumentException("اكتب الاسم");
        if(EXPENSE.equals(role)&&!"OUT".equals(direction))throw new IllegalArgumentException("هذا بند مخاريج؛ اختر صادر لتسجيل المصروف");
        String clean=name.trim(),memo=clean+(note==null||note.trim().isEmpty()?"":" — "+note.trim());
        SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
        try{
            ShiftWorkspace.openOnly(db,shift);
            long target=CUSTOMER.equals(role)?ShiftWorkspace.customer(db,clean):0;
            String kind=EXPENSE.equals(role)?"EXPENSE":"IN".equals(direction)?"COLLECTION":"LOAN";
            long id=ShiftWorkspace.addCash(db,shift,1,kind,box,target,currency,currency,amount,memo);
            sql.execSQL("UPDATE shift_operations SET party_name=? WHERE id=?",new Object[]{clean,id});
            remember(db,clean,role);sql.setTransactionSuccessful();return id;
        }finally{sql.endTransaction();}
    }
}
