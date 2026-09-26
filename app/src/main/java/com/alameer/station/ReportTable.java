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
        if(ShiftWorkspace.exists(db,id)&&!db.shiftPosted(id))throw new IllegalStateException("يصدر التقرير الرسمي بعد ترحيل كامل الوردية");
        String issue=db.validateShift(id);
        if(!issue.isEmpty())throw new IllegalStateException(issue);
        String worker="",opened="",closed="",state="",reason="",note="";
        try(Cursor c=db.shiftHeader(id)){
            if(!c.moveToFirst())throw new IllegalArgumentException("الوردية غير موجودة");
            worker=ShiftWorkspace.workerName(db,id);opened=c.getString(1);closed=c.getString(2);
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
        add(false,"كود الوردية",db.shiftCode(id),"العامل",worker,"");
        add(false,"تاريخ الوردية",db.shiftDate(id),ShiftDates.day(db.shiftDate(id)),"","");
        add(false,"تاريخ الإدخال",opened,"وقت الإغلاق",closed,"");
        add(false,"الحالة",state,"سبب الفرق",reason,"");
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT previous,profit,expenses,expected,actual,gap,reason FROM capital_checks WHERE shift_id=?",new String[]{""+id})){if(c.moveToFirst()){
            add(true,"مطابقة رأس المال",c.getDouble(5)==0?"مطابق":"غير مطابق","","","");
            add(false,"السابق",c.getDouble(0),"ربح المبيعات",c.getDouble(1),"");
            add(false,"المخاريج",c.getDouble(2),"المتوقع",c.getDouble(3),"");
            add(false,"الفعلي",c.getDouble(4),"الفرق",c.getDouble(5),"");
            add(false,"سبب اعتماد الفرق",c.getString(6),"","","");
        }}
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
        add(true,"المخاريج","المقبوضات","الديون","البيان","الفلوس");
        List<Object[]> movements=new ArrayList<>();double[] totals=new double[4];
        List<String> types=Arrays.asList("EXPENSE","COLLECTION","DEBT","CASH");
        try(Cursor c=db.syncMovements(id)){
            while(c.moveToNext()){
                int col=types.indexOf(c.getString(0));if(col<0)throw new IllegalArgumentException("نوع حركة غير معروف");
                Object[] values=new Object[5];values[CELL[col]]=c.getDouble(2);values[3]=c.getString(1);
                totals[col]+=c.getDouble(2);movements.add(values);
            }
        }
        // الوردية المؤرشفة تُصدَّر ولو كان فيها فرق، ما دام سببه مكتوبًا؛
        // فالمنع إنما يخصّ الوردية المفتوحة قبل إغلاقها.
        if(!Calc.matched(Calc.balance(sales,totals[1],totals[3],totals[2],totals[0]))
                &&reason.trim().isEmpty())
            throw new IllegalStateException("لا يمكن مشاركة التقرير: يجب أن يكون باقي الوردية صفرًا أو أن يكون للفرق سبب مكتوب.");
        Collections.sort(movements,(a,b)->Integer.compare(movementOrder(column(a)),movementOrder(column(b))));
        first=rows.size()+1;
        for(Object[] movement:movements)add(false,movement);
        if(movements.isEmpty())add(false,null,null,null,"لا توجد حركات",null);
        last=rows.size();totalsStart=rows.size();
        int tr=add(true,f(sum("A",first,last),totals[0]),f(sum("B",first,last),totals[1]),f(sum("C",first,last),totals[2]),"الإجمالي",f(sum("E",first,last),totals[3]));
        // بيانات المطابقة في عمودين: البيان ثم القيمة.
        add(true,"بيانات المطابقة","","","","");
        add(false,"المبيعات",f("E"+salesRow,sales),"","","");
        add(false,"المقبوضات",f("B"+tr,totals[1]),"","","");
        add(false,"النقد المسلّم",f("E"+tr,totals[3]),"","","");
        add(false,"الديون",f("C"+tr,totals[2]),"","","");
        add(false,"المخاريج",f("A"+tr,totals[0]),"","","");
        add(true,"الباقي",f("E"+salesRow+"+B"+tr+"-E"+tr+"-C"+tr+"-A"+tr,sales+totals[1]-totals[3]-totals[2]-totals[0]),"","","");
        add(false,"سبب الفرق",reason,"","","");
        if(ShiftWorkspace.exists(db,id)){
            add(true,"حركة الصناديق","","","","");

            for(int section=1;section<=2;section++){
                if(section==1){cashSection(db,id);continue;}
                if(section==2)add(true,"حركة المواد الإضافية","","","","");
                add(true,"الحركة","البيان","الحساب / المادة","الكمية","القيمة ر.ي");
                double total=0;
                try(Cursor c=ShiftWorkspace.operations(db,id,section)){
                    while(c.moveToNext()){
                        String counterpart=section==2?c.getString(4):cashboxName(db,c.getLong(2));
                        String kind=c.getString(1);
                        if(kind.equals("TRANSFER"))counterpart+=" ← "+cashboxName(db,c.getLong(3));
                        if(kind.equals("COLLECTION")||kind.equals("LOAN"))counterpart+=" / "+db.debtorName(c.getLong(3));
                        if(kind.equals("COMPANY_PAYMENT"))counterpart=cashboxName(db,c.getLong(2));
                        if(kind.equals("SUPPLIER")||kind.equals("COMPANY_PAYMENT"))counterpart+=" / "+(c.getLong(3)==1?"شركة الغاز":"شركة النفط");
                        add(false,ShiftWorkspace.label(kind),c.getString(7),counterpart,section==2?c.getDouble(5):"",c.getDouble(6));total+=c.getDouble(6);
                        if(kind.equals("FUEL_SUPPLY"))try(Cursor detail=db.getReadableDatabase().rawQuery("SELECT driver_name,freight FROM shift_operations WHERE id=?",new String[]{""+c.getLong(0)})){detail.moveToFirst();add(false,"أجرة نقل مستحقة",detail.getString(0),"حساب السائق","",detail.getDouble(1));}
                        if(c.getLong(2)>0)try(Cursor fx=db.getReadableDatabase().rawQuery("SELECT rate,currency FROM shift_operations WHERE id=?",new String[]{""+c.getLong(0)})){fx.moveToFirst();String currency=fx.getString(1);add(false,"المبلغ الأصلي",c.getDouble(6)/fx.getDouble(0),Db.currencyName(currency),"سعر التحويل",fx.getDouble(0));}
                    }
                }
                add(true,"مجموع قيم الحركات (ليس صافي الرصيد)","","","",total);
            }
            add(true,"الجرد الفعلي","الحساب","المحسوب","الفعلي","الفرق");
            try(Cursor counts=db.getReadableDatabase().rawQuery("SELECT section,account,expected,actual FROM shift_counts WHERE shift_id=? ORDER BY section,account",new String[]{""+id})){while(counts.moveToNext()){String name=counts.getInt(0)==1?cashboxName(db,CashAccounts.box(counts.getString(1)))+" • "+Db.currencyName(CashAccounts.code(db,counts.getString(1))):counts.getString(1);add(false,counts.getInt(0)==1?"نقد":"لترات",name,counts.getDouble(2),counts.getDouble(3),counts.getDouble(3)-counts.getDouble(2));}}

        }
    }
    private void cashSection(Db db,long id){
        add(false,"","","","المبالغ بالريال اليمني","");
        add(true,"المخاريج","وارد","صادر","البيان","الجهة");
        double incoming=db.total(id,"CASH"),outgoing=0,expenses=0;
        if(incoming!=0)add(false,"",incoming,"",ShiftWorkspace.workerName(db,id)+"\nوارد — نقد العامل",cashboxName(db,ShiftWorkspace.box(db,id)));
        try(Cursor c=ShiftWorkspace.operations(db,id,1)){while(c.moveToNext()){
            String kind=c.getString(1),person=c.getString(7);double amount=c.getDouble(6);
            try(Cursor d=db.getReadableDatabase().rawQuery("SELECT party_name FROM shift_operations WHERE id=?",new String[]{""+c.getLong(0)})){if(d.moveToFirst()&&!d.getString(0).isEmpty())person=d.getString(0);}
            if(kind.equals("COLLECTION")||kind.equals("LOAN"))person=db.debtorName(c.getLong(3));
            boolean expense=kind.equals("EXPENSE"),in=kind.equals("COLLECTION");
            if(kind.equals("TRANSFER"))person=cashboxName(db,c.getLong(3));
            add(false,expense?amount:"",in?amount:"",!expense&&!in?amount:"",person+"\n"+(kind.equals("TRANSFER")?"صادر — تحويل":ShiftWorkspace.label(kind)),cashboxName(db,c.getLong(2)));
            if(expense)expenses+=amount;else if(in)incoming+=amount;else outgoing+=amount;
            if(kind.equals("TRANSFER")){add(false,"",amount,"",cashboxName(db,c.getLong(2))+"\nوارد — تحويل",cashboxName(db,c.getLong(3)));incoming+=amount;}
        }}
        add(true,expenses,incoming,outgoing,"الإجمالي","");
        add(true,"","","","صافي الحركة",incoming-outgoing-expenses);
    }
    private static String cashboxName(Db db,long id){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT name FROM cashboxes WHERE id=?",new String[]{""+id})){return c.moveToFirst()?c.getString(0):"غير محدد";}}
    /** عمود كل نوع حركة: مخاريج، مقبوضات، ديون، ثم الفلوس في آخر عمود. */
    static final int[] CELL={0,1,2,4};
    private static int movementOrder(int column){
        switch(column){case 2:return 0;case 1:return 1;case 0:return 2;case 4:return 3;default:return 5;}
    }
    private static int column(Object[] a){
        for(int i:CELL)if(a[i] instanceof Number||a[i] instanceof XlsxWorkbook.Formula)return i;
        return 5;
    }
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

