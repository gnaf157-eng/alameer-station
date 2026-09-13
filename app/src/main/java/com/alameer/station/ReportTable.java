package com.alameer.station.shifts;
import android.database.Cursor;
import java.util.*;

/** One five-column layout shared by PDF and Excel. */
final class ReportTable {
    static final class Row {
        final boolean heading; final Object[] cells;
        Row(boolean h,Object... c){heading=h;cells=c;}
    }
    final List<Row> rows=new ArrayList<>();
    int movementHeader=-1, totalsStart=-1;
    private int add(boolean h,Object... cells){rows.add(new Row(h,cells));return rows.size();}
    private static XlsxWorkbook.Formula f(String s,double v){return new XlsxWorkbook.Formula(s,v);}
    private static String fuel(String s){
        s=s.trim();
        if(s.equals("البترول")||s.equals("بنزين")||s.equals("البنزين"))return "بترول";
        if(s.equals("الديزل"))return "ديزل";if(s.equals("الغاز"))return "غاز";return s;
    }
    private static String sum(String col,int start,int end){return end<start?"0":"SUM("+col+start+":"+col+end+")";}
    ReportTable(Db db,long id){
        String worker="",opened="",closed="",state="",reason="",note="";
        try(Cursor c=db.shiftHeader(id)){
            if(!c.moveToFirst())throw new IllegalArgumentException("الوردية غير موجودة");
            worker=c.getString(0);opened=c.getString(1);closed=c.getString(2);
            state=Calc.arabicStatus(c.getString(3));reason=c.getString(4);note=c.getString(5);
        }
        List<Object[]> readings=new ArrayList<>();
        Map<String,Set<Double>> prices=new LinkedHashMap<>();
        for(String type:new String[]{"بترول","ديزل","غاز"})prices.put(type,new LinkedHashSet<>());
        try(Cursor c=db.shiftReadings(id)){
            while(c.moveToNext()){
                String type=fuel(c.getString(2));
                if(!prices.containsKey(type))prices.put(type,new LinkedHashSet<>());
                prices.get(type).add(c.getDouble(5));
                readings.add(new Object[]{c.getString(1),type,c.getDouble(3),c.isNull(4)?null:c.getDouble(4),c.getDouble(5)});
            }
        }
        add(true,Branding.stationName(db)+" — تقرير الوردية","","","","");
        add(false,"رقم الوردية",id,"العامل",worker,"");
        add(false,"تاريخ الوردية",db.shiftDate(id),ShiftDates.day(db.shiftDate(id)),"","");
        add(false,"تاريخ الإدخال",opened,"وقت الإغلاق",closed,"");
        add(false,"الحالة",state,"سبب الفرق",reason,"");
        add(true,"سعر البترول","سعر الديزل","سعر الغاز","","");
        int priceRow=add(false,price(prices.get("بترول")),price(prices.get("ديزل")),price(prices.get("غاز")),"ريال / لتر","");
        if(!note.isEmpty())add(false,"ملاحظة المدير",note,"","","");
        add(true,"الطرمبة","السابقة","الحالية","اللترات","المبيعات");
        int first=rows.size()+1;
        double sales=0;
        Map<String,List<Integer>> fuelRows=new LinkedHashMap<>();
        Map<String,Double> litres=new LinkedHashMap<>(),amounts=new LinkedHashMap<>();
        for(String type:prices.keySet()){fuelRows.put(type,new ArrayList<>());litres.put(type,0d);amounts.put(type,0d);}
        for(Object[] reading:readings){
            int r=rows.size()+1;String type=(String)reading[1];
            double previous=(Double)reading[2],price=(Double)reading[4];
            Double current=(Double)reading[3];
            double volume=current==null?0:Math.max(0,current-previous),amount=volume*Math.max(0,price);
            String priceRef=Double.toString(price);
            int index=Arrays.asList("بترول","ديزل","غاز").indexOf(type);
            if(index>=0&&prices.get(type).size()==1)priceRef=""+(char)('A'+index)+priceRow;
            add(false,reading[0],previous,current,f("IF(C"+r+"=\"\",0,MAX(0,C"+r+"-B"+r+"))",volume),f("D"+r+"*MAX(0,"+priceRef+")",amount));
            fuelRows.get(type).add(r);litres.put(type,litres.get(type)+volume);amounts.put(type,amounts.get(type)+amount);sales+=amount;
        }
        int last=rows.size(),salesRow=add(true,"إجمالي المبيعات","","","",f(sum("E",first,last),sales));
        // Three material columns with units and notes in the last two columns.
        add(true,"بترول","ديزل","غاز","ملخص المواد","ملاحظة الوردية");
        add(false,f(fuelSum("D",fuelRows.get("بترول")),litres.get("بترول")),f(fuelSum("D",fuelRows.get("ديزل")),litres.get("ديزل")),f(fuelSum("D",fuelRows.get("غاز")),litres.get("غاز")),"اللترات",reason);
        add(false,f(fuelSum("E",fuelRows.get("بترول")),amounts.get("بترول")),f(fuelSum("E",fuelRows.get("ديزل")),amounts.get("ديزل")),f(fuelSum("E",fuelRows.get("غاز")),amounts.get("غاز")),"المبيعات (ريال)","");
        for(String type:prices.keySet())if(!Arrays.asList("بترول","ديزل","غاز").contains(type)){
            add(false,type,"لترات",f(fuelSum("D",fuelRows.get(type)),litres.get(type)),"مبيعات",f(fuelSum("E",fuelRows.get(type)),amounts.get(type)));
        }
        movementHeader=rows.size();
        add(true,"المخاريج","المقبوضات","الديون","الفلوس","البيان");
        List<Object[]> movements=new ArrayList<>();double[] totals=new double[4];
        List<String> types=Arrays.asList("EXPENSE","COLLECTION","DEBT","CASH");
        try(Cursor c=db.syncMovements(id)){
            while(c.moveToNext()){
                int col=types.indexOf(c.getString(0));if(col<0)throw new IllegalArgumentException("نوع حركة غير معروف");
                Object[] values=new Object[5];values[col]=c.getDouble(2);values[4]=c.getString(1);
                totals[col]+=c.getDouble(2);movements.add(values);
            }
        }
        Collections.sort(movements,(a,b)->Integer.compare(movementOrder(column(a)),movementOrder(column(b))));
        first=rows.size()+1;
        for(Object[] movement:movements)add(false,movement);
        if(movements.isEmpty())add(false,null,null,null,null,"لا توجد حركات");
        last=rows.size();totalsStart=rows.size();
        int tr=add(true,f(sum("A",first,last),totals[0]),f(sum("B",first,last),totals[1]),f(sum("C",first,last),totals[2]),f(sum("D",first,last),totals[3]),"الإجمالي");
        add(true,"المبيعات","المقبوضات","النقد المسلّم","الديون","المخاريج");
        add(false,f("E"+salesRow,sales),f("B"+tr,totals[1]),f("D"+tr,totals[3]),f("C"+tr,totals[2]),f("A"+tr,totals[0]));
        add(true,"الباقي",f("E"+salesRow+"+B"+tr+"-D"+tr+"-C"+tr+"-A"+tr,sales+totals[1]-totals[3]-totals[2]-totals[0]),"سبب الفرق",reason,"");
    }
    private static int movementOrder(int column){
        switch(column){case 2:return 0;case 1:return 1;case 0:return 2;case 3:return 3;default:return 4;}
    }
    private static int column(Object[] a){for(int i=0;i<4;i++)if(a[i]!=null)return i;return 4;}
    private static Object price(Set<Double> values){
        if(values==null||values.isEmpty())return "—";
        if(values.size()==1)return values.iterator().next();
        StringBuilder s=new StringBuilder();
        for(double p:values){if(s.length()>0)s.append(" / ");s.append(format(p));}
        return s.toString();
    }
    private static String fuelSum(String col,List<Integer> rs){
        if(rs.isEmpty())return "0";
        StringBuilder s=new StringBuilder("SUM(");
        for(int i=0;i<rs.size();i++){if(i>0)s.append(',');s.append(col).append(rs.get(i));}
        return s.append(')').toString();
    }
    static String format(double n){return String.format(java.util.Locale.US,n==Math.rint(n)?"%,.0f":"%,.2f",n);}
}
