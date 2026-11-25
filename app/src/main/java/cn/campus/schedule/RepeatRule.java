package cn.campus.schedule;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/** Calendar dates, not milliseconds: repetition is unaffected by daylight-saving changes. */
public final class RepeatRule {
    private RepeatRule() {}
    public static boolean matches(LocalDate first, LocalDate date, String mode, int interval,
                                  Set<Integer> weekdays, Set<LocalDate> dates, LocalDate until, int count) {
        if (date.isBefore(first) || (until != null && date.isAfter(until))) return false;
        if (!candidate(first,date,mode,interval,weekdays,dates)) return false;
        if (count <= 0) return true;
        int rank=0;
        for(LocalDate d=first;!d.isAfter(date);d=d.plusDays(1)) {
            if(candidate(first,d,mode,interval,weekdays,dates) && ++rank>count)return false;
        }
        return true;
    }
    private static boolean candidate(LocalDate first,LocalDate d,String mode,int interval,Set<Integer> days,Set<LocalDate> dates){
        int n=Math.max(1,interval);
        switch(mode){
            case "daily": return ChronoUnit.DAYS.between(first,d)%n==0;
            case "weekly": return days.contains(d.getDayOfWeek().getValue()) && ChronoUnit.WEEKS.between(ScheduleRules.monday(first),ScheduleRules.monday(d))%n==0;
            case "dates": return dates.contains(d);
            default: return first.equals(d);
        }
    }
}
