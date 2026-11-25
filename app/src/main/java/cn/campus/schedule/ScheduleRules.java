package cn.campus.schedule;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.TreeSet;

public final class ScheduleRules {
    private ScheduleRules() {}
    public static LocalDate monday(LocalDate date) { return date.minusDays(date.getDayOfWeek().getValue() - 1); }
    public static int week(LocalDate semester, LocalDate date) {
        return (int) Math.floorDiv(ChronoUnit.DAYS.between(monday(semester), date), 7) + 1;
    }
    public static Set<Integer> weeks(String text, int max) {
        Set<Integer> result = new TreeSet<>();
        for (String token : text.replace('，', ',').replace('–', '-').replace('—', '-').split(",", -1)) {
            String[] ends = token.trim().split("-", -1);
            if (ends.length > 2) throw new IllegalArgumentException("周次格式示例：1-16 或 1,3,5-8");
            int from = Integer.parseInt(ends[0].trim());
            int to = ends.length == 2 ? Integer.parseInt(ends[1].trim()) : from;
            if (from < 1 || to > max || from > to) throw new IllegalArgumentException("周次须在 1 到 " + max + " 之间");
            for (int i = from; i <= to; i++) result.add(i);
        }
        return result;
    }
    public static boolean occurs(LocalDate semester, LocalDate date, int weekday, Set<Integer> weeks) {
        return date.getDayOfWeek().getValue() == weekday && weeks.contains(week(semester, date));
    }
    public static int minutes(String time) {
        if (!time.matches("\\d{2}:\\d{2}")) throw new IllegalArgumentException("时间格式为 HH:mm");
        int h = Integer.parseInt(time.substring(0, 2)), m = Integer.parseInt(time.substring(3));
        if (h > 23 || m > 59) throw new IllegalArgumentException("时间超出范围");
        return h * 60 + m;
    }
}
