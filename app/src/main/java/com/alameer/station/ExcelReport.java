package com.alameer.station.shifts;

import android.content.Context;
import android.database.Cursor;
import java.io.File;
import java.util.*;

public final class ExcelReport {
    private final Context context; private final Db db;
    public ExcelReport(Context context,Db db){this.context=context;this.db=db;}
    private static XlsxWorkbook.Formula formula(String f,double value){return new XlsxWorkbook.Formula(f,value);}
    private static String fuel(String s){
        s=s.trim();
        if(s.equals("البترول")||s.equals("بنزين")||s.equals("البنزين"))return "بترول";
        if(s.equals("الديزل"))return "ديزل";
        if(s.equals("الغاز"))return "غاز";
        return s;
    }
    public File build(long id)throws Exception{
        XlsxWorkbook w=new XlsxWorkbook();
        w.row(true,"محطة الأمير — تقرير الوردية");
        try(Cursor c=db.shiftHeader(id)){
            if(!c.moveToFirst())throw new IllegalArgumentException("الوردية غير موجودة");
            w.row(false,"رقم الوردية",id,"العامل",c.getString(0));
            w.row(false,"وقت الفتح",c.getString(1),"وقت الإغلاق",c.getString(2));
            w.row(false,"الحالة",Calc.arabicStatus(c.getString(3)));
            if(!c.getString(4).isEmpty())w.row(false,"سبب الفرق",c.getString(4));
            if(!c.getString(5).isEmpty())w.row(false,"ملاحظة المدير",c.getString(5));
        }
        w.row(true,"الطرمبة","الوقود","السابقة","الحالية","السعر","اللترات","المبيعات");
        int firstReading=w.nextRow();
        double sales=0;
        Map<String,Double> litres=new LinkedHashMap<>();
        litres.put("بترول",0d);litres.put("ديزل",0d);litres.put("غاز",0d);
        try(Cursor c=db.shiftReadings(id)){
            while(c.moveToNext()){
                int r=w.nextRow();
                String type=fuel(c.getString(2));
                double previous=c.getDouble(3),price=c.getDouble(5);
                Double current=c.isNull(4)?null:c.getDouble(4);
                double volume=current==null?0:Math.max(0,current-previous);
                double amount=price>0?volume*price:0;
                sales+=amount;litres.put(type,litres.containsKey(type)?litres.get(type)+volume:volume);
                w.row(false,c.getString(1),type,previous,current,price,
                    formula("IF(D"+r+"=\"\",0,MAX(0,D"+r+"-C"+r+"))",volume),
                    formula("F"+r+"*MAX(0,E"+r+")",amount));
            }
        }
        int lastReading=w.nextRow()-1;
        int salesRow=w.nextRow();
        w.row(true,"إجمالي المبيعات",null,null,null,null,null,
            formula(lastReading>=firstReading?"SUM(G"+firstReading+":G"+lastReading+")":"0",sales));
        w.row(true,litres.keySet().toArray());
        Object[] fuelTotals=new Object[litres.size()];int fi=0;
        for(Map.Entry<String,Double> e:litres.entrySet()){
            String f=lastReading>=firstReading?"SUMIF(B"+firstReading+":B"+lastReading+",\""+e.getKey().replace("\"","\"\"")+"\",F"+firstReading+":F"+lastReading+")":"0";
            fuelTotals[fi++]=formula(f,e.getValue());
        }
        w.row(false,fuelTotals);
        w.row(false,"إجمالي اللترات لكل نوع");
        w.row(true,"المخاريج","المقبوضات","الديون","الفلوس","البيان");
        String[] types={"EXPENSE","COLLECTION","DEBT","CASH"};
        List<Object[]> movements=new ArrayList<>();
        double[] totals=new double[4];
        try(Cursor c=db.syncMovements(id)){
            while(c.moveToNext()){
                int col=-1;
                for(int i=0;i<4;i++)if(types[i].equals(c.getString(0)))col=i;
                if(col<0)throw new IllegalArgumentException("نوع حركة غير معروف");
                Object[] values=new Object[5];values[col]=c.getDouble(2);values[4]=c.getString(1);
                totals[col]+=c.getDouble(2);movements.add(values);
            }
        }
        Collections.sort(movements,(a,b)->Integer.compare(movementColumn(a),movementColumn(b)));
        int firstMovement=w.nextRow();
        for(Object[] row:movements)w.row(false,row);
        int lastMovement=w.nextRow()-1,totalRow=w.nextRow();
        Object[] totalValues=new Object[5];
        for(int i=0;i<4;i++)totalValues[i]=formula(lastMovement>=firstMovement?
            "SUM("+(char)('A'+i)+firstMovement+":"+(char)('A'+i)+lastMovement+")":"0",totals[i]);
        totalValues[4]="الإجمالي";w.row(true,totalValues);
        w.row(true,"المطابقة — ريال يمني","المبلغ");
        w.row(false,"المبيعات",formula("G"+salesRow,sales));
        w.row(false,"المقبوضات",formula("B"+totalRow,totals[1]));
        w.row(false,"النقد المسلّم",formula("D"+totalRow,totals[3]));
        w.row(false,"الديون",formula("C"+totalRow,totals[2]));
        w.row(false,"المخاريج",formula("A"+totalRow,totals[0]));
        w.row(true,"الباقي",formula("G"+salesRow+"+B"+totalRow+"-D"+totalRow+"-C"+totalRow+"-A"+totalRow,
            sales+totals[1]-totals[3]-totals[2]-totals[0]));
        File dir=new File(context.getCacheDir(),"exports");
        if(!dir.isDirectory()&&!dir.mkdirs())throw new java.io.IOException("تعذر إنشاء مجلد التقرير");
        File out=new File(dir,"alameer-shift-"+id+"-"+System.currentTimeMillis()+".xlsx");
        w.write(out);return out;
    }
    private static int movementColumn(Object[] row){
        for(int i=0;i<4;i++)if(row[i]!=null)return i;
        return 4;
    }
}
