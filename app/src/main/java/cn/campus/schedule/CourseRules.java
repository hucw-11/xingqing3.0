package cn.campus.schedule;
import java.time.LocalDate;
import java.util.*;
import org.json.*;

final class CourseRules {
    static Set<Integer> days(JSONObject c){Set<Integer> result=new TreeSet<>();JSONArray ds=c.optJSONArray("days");if(ds==null)result.add(c.optInt("day",1));else for(int i=0;i<ds.length();i++)result.add(ds.optInt(i));return result;}
    static boolean occurs(LocalDate semester,LocalDate date,JSONObject course,int weeks){return days(course).contains(date.getDayOfWeek().getValue())&&ScheduleRules.weeks(course.optString("weeks"),weeks).contains(ScheduleRules.week(semester,date));}
    static boolean overlaps(JSONObject a,JSONObject b,int max){Set<Integer> ds=days(a);ds.retainAll(days(b));if(ds.isEmpty()||a.optInt("from")>b.optInt("to")||b.optInt("from")>a.optInt("to"))return false;Set<Integer> weeks=ScheduleRules.weeks(a.optString("weeks"),max);weeks.retainAll(ScheduleRules.weeks(b.optString("weeks"),max));return !weeks.isEmpty();}
}
