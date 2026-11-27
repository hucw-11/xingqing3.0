package cn.campus.schedule;

import org.json.*;
import java.time.*;
import java.util.*;

final class AgendaItems {
    static List<MainActivity.Entry> build(JSONObject data,LocalDate selected,LocalDateTime now){
        List<MainActivity.Entry> rows=new ArrayList<>();JSONArray courses=data.optJSONArray("courses");
        if(data.optBoolean("configured"))for(int i=0;i<courses.length();i++){JSONObject course=courses.optJSONObject(i);if(!CourseRules.occurs(LocalDate.parse(data.optString("semester")),selected,course,data.optInt("totalWeeks",20)))continue;MainActivity.Entry e=new MainActivity.Entry();e.source=course;e.course=true;e.title=course.optString("title");e.note=course.optString("room");String start=data.optJSONArray("starts").optString(course.optInt("from")-1),end=data.optJSONArray("ends").optString(course.optInt("to")-1);e.time=start+"–"+end;e.minute=ScheduleRules.minutes(start);e.done=!selected.atTime(LocalTime.parse(end)).isAfter(now);rows.add(e);}
        JSONArray events=data.optJSONArray("events");for(int i=0;i<events.length();i++)for(JSONObject task:TaskProgress.forDay(events.optJSONObject(i),selected,now.toLocalDate())){
            MainActivity.Entry e=task(task);rows.add(e);
            if(task.optBoolean("continuous")&&!task.optBoolean("done")&&selected.equals(now.toLocalDate())){JSONArray children=TaskRules.children(task);for(int j=0;j<children.length();j++){JSONObject child=children.optJSONObject(j);if(!TaskProgress.focused(child,selected))continue;MainActivity.Entry focus=task(task);focus.continuous=false;focus.childId=child.optString("id");focus.title=child.optString("title");focus.note="来自 · "+task.optString("title");focus.done=child.optBoolean("done");focus.allDay=true;focus.time="全天";focus.minute=-1;rows.add(focus);}}
        }
        JSONArray moods=data.optJSONArray("moods");if(moods!=null)for(int i=0;i<moods.length();i++){JSONObject mood=moods.optJSONObject(i);if(!mood.optString("date").equals(selected.toString()))continue;MainActivity.Entry e=new MainActivity.Entry();e.source=mood;e.mood=true;e.title=mood.optString("title").isEmpty()?"此刻的心情":mood.optString("title");e.note=mood.optString("body");e.time=mood.optString("time");e.minute=ScheduleRules.minutes(e.time);rows.add(e);}
        rows.sort(Comparator.comparing((MainActivity.Entry e)->e.done).thenComparingInt(e->e.minute).thenComparing(e->e.title));return DayPlanning.arrange(data,rows,selected);
    }
    static MainActivity.Entry task(JSONObject source){MainActivity.Entry e=new MainActivity.Entry();e.source=source;e.title=source.optString("title");e.note=source.optString("progressSummary",source.optString("note"));e.progress=source.has("progressSummary");e.continuous=source.optBoolean("continuous")&&!source.optBoolean("done");e.done=source.optBoolean("done");e.allDay=source.optBoolean("allDay")||source.optBoolean("carried");e.time=e.allDay?"全天":source.optString("start")+(source.optString("end").isEmpty()?"":"–"+source.optString("end"));e.minute=e.allDay?-1:ScheduleRules.minutes(source.optString("start"));return e;}
}
