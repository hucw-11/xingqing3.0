package cn.campus.schedule;
import org.junit.Test;
import java.time.LocalDate;
import java.util.Set;
import static org.junit.Assert.*;
public class RepeatRuleTest {
    private final LocalDate first=LocalDate.of(2026,11,25);
    private boolean daily(LocalDate date,int interval,int count){return RepeatRule.matches(first,date,"daily",interval,Set.of(),Set.of(),null,count);}
    @Test public void threeDays(){assertTrue(daily(first.plusDays(2),1,3));assertFalse(daily(first.plusDays(3),1,3));assertFalse(daily(first.minusDays(1),1,3));}
    @Test public void intervals(){assertTrue(daily(first.plusDays(4),2,3));assertFalse(daily(first.plusDays(5),2,0));assertFalse(daily(first.plusDays(6),2,3));}
    @Test public void weeklyCountAndAnchor(){
        assertTrue(RepeatRule.matches(first,first.plusDays(1),"weekly",2,Set.of(2,4),Set.of(),null,2));
        assertFalse(RepeatRule.matches(first,first.plusDays(6),"weekly",2,Set.of(2,4),Set.of(),null,0));
        assertTrue(RepeatRule.matches(first,first.plusDays(13),"weekly",2,Set.of(2,4),Set.of(),null,2));
        assertFalse(RepeatRule.matches(first,first.plusDays(15),"weekly",2,Set.of(2,4),Set.of(),null,2));
    }
    @Test public void customDatesAndInclusiveEnd(){
        Set<LocalDate> dates=Set.of(first,first.plusDays(2),first.plusDays(5));
        assertTrue(RepeatRule.matches(first,first.plusDays(2),"dates",1,Set.of(),dates,first.plusDays(2),0));
        assertFalse(RepeatRule.matches(first,first.plusDays(5),"dates",1,Set.of(),dates,first.plusDays(2),0));
    }
}
