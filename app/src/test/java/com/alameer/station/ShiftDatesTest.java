package com.alameer.station.shifts;
import org.junit.Test;
import static org.junit.Assert.*;
public class ShiftDatesTest {
 @Test public void arabicWeekday(){assertEquals("الأحد",ShiftDates.day("2026-09-13"));assertEquals("السبت",ShiftDates.day("2026-09-12"));}
 @Test public void acceptsPastAndToday(){ShiftDates.validate("2020-02-29");ShiftDates.validate(ShiftDates.today());}
 @Test(expected=IllegalArgumentException.class) public void rejectsFuture(){ShiftDates.validate(java.time.LocalDate.now().plusDays(1).toString());}
 @Test(expected=java.time.format.DateTimeParseException.class) public void rejectsInvalid(){ShiftDates.validate("2026-02-30");}
}