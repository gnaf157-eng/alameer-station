package com.alameer.station.shifts;
import org.junit.Test;
import static org.junit.Assert.*;
public class ReportBalanceTest {
 @Test public void completeReconciliationAllowsReport(){assertTrue(Calc.matched(Calc.balance(1000,200,900,200,100)));}
 @Test public void shortagesAndSurplusesBlockReport(){assertFalse(Calc.matched(Calc.balance(1000,200,899,200,100)));assertFalse(Calc.matched(Calc.balance(1000,200,901,200,100)));}
 @Test public void invalidBalancesBlockReport(){assertFalse(Calc.matched(Double.NaN));assertFalse(Calc.matched(Double.POSITIVE_INFINITY));assertFalse(Calc.matched(Double.NEGATIVE_INFINITY));}
 @Test public void decimalRoundingNoiseDoesNotBlockBalancedShift(){assertTrue(Calc.matched(Calc.balance(0.1+0.2,0,0.3,0,0)));assertFalse(Calc.matched(0.01));assertFalse(Calc.matched(-0.01));}
}