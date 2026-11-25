package cn.campus.schedule;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.LocalDate;
import java.util.Set;

public class ScheduleRulesTest {
    @Test public void semesterBoundary() {
        LocalDate start = LocalDate.of(2026,9,2);
        assertEquals(1, ScheduleRules.week(start, LocalDate.of(2026,8,31)));
        assertEquals(0, ScheduleRules.week(start, LocalDate.of(2026,8,30)));
        assertEquals(2, ScheduleRules.week(start, LocalDate.of(2026,9,7)));
    }
    @Test public void selectedWeeksAndWeekday() {
        LocalDate start = LocalDate.of(2026,9,1);
        Set<Integer> weeks = ScheduleRules.weeks("1,3,5-8", 20);
        assertTrue(ScheduleRules.occurs(start, LocalDate.of(2026,9,2),3,weeks));
        assertFalse(ScheduleRules.occurs(start, LocalDate.of(2026,9,9),3,weeks));
        assertFalse(ScheduleRules.occurs(start, LocalDate.of(2026,9,3),3,weeks));
    }
    @Test(expected=IllegalArgumentException.class) public void invalidWeekRange() { ScheduleRules.weeks("8-3",20); }
    @Test(expected=IllegalArgumentException.class) public void beyondSemester() { ScheduleRules.weeks("1-21",20); }
    @Test public void timeBoundary() { assertEquals(720,ScheduleRules.minutes("12:00")); assertEquals(590,ScheduleRules.minutes("09:50")); }
    @Test(expected=IllegalArgumentException.class) public void invalidTime() { ScheduleRules.minutes("24:00"); }
}
