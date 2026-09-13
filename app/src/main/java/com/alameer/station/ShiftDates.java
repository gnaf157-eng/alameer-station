package com.alameer.station.shifts;
import java.time.LocalDate;
final class ShiftDates {
    static String today(){return LocalDate.now().toString();}
    static void validate(String date){
        LocalDate d=LocalDate.parse(date);
        if(d.isAfter(LocalDate.now()))throw new IllegalArgumentException("لا يمكن اختيار تاريخ مستقبلي");
    }
    static String day(String date){
        try{
            String[] days={"الاثنين","الثلاثاء","الأربعاء","الخميس","الجمعة","السبت","الأحد"};
            return days[LocalDate.parse(date).getDayOfWeek().getValue()-1];
        }catch(Exception e){return "";}
    }
}
