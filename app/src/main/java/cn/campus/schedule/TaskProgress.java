package cn.campus.schedule;

import org.json.*;
import java.time.*;
import java.util.*;

/** Progress belongs to the original occurrence; a daily focus is a reference to its child. */
final class TaskProgress {
    static String key(JSONObject task){return task.optString("id")+"@"+task.optString("occurrence",task.optString("date"));}
    static String completedDay(JSONObject task){return task.optString("completedDate",task.optString("occurrence",task.optString("date")));}
    static boolean focused(JSONObject child,LocalDate day){String date=child.optString("focusDate");return !date.isEmpty()&&!LocalDate.parse(date).isAfter(day)&&(!child.optBoolean("done")||child.optString("completedDate").equals(day.toString()));}
    static void record(JSONObject before,JSONObject after,LocalDateTime now){
        JSONObject old=before==null?new JSONObject():before;
        JSONObject log=old.optJSONObject("progressLog")==null?new JSONObject():AgendaFeatures.cloneJson(old.optJSONObject("progressLog"));
        if(after.optJSONObject("progressLog")!=null)after.optJSONObject("progressLog").keys().forEachRemaining(k->AgendaFeatures.put(log,k,after.optJSONObject("progressLog").opt(k)));
        Map<String,Object> previous=SyncMerge.index(TaskRules.children(old));JSONArray children=TaskRules.children(after);
        for(int i=0;i<children.length();i++){JSONObject child=children.optJSONObject(i),prior=(JSONObject)previous.get(child.optString("id"));boolean was=prior!=null&&prior.optBoolean("done"),done=child.optBoolean("done");
            if(was!=done){AgendaFeatures.put(child,"stateChangeId",UUID.randomUUID().toString());if(done)AgendaFeatures.put(child,"completedDate",now.toLocalDate().toString());else child.remove("completedDate");append(log,after,child,done,now);}else if(done&&prior!=null&&prior.has("completedDate"))AgendaFeatures.put(child,"completedDate",prior.optString("completedDate"));
        }
        AgendaFeatures.put(after,"children",children);
        if(old.optBoolean("done")!=after.optBoolean("done")){AgendaFeatures.put(after,"stateChangeId",UUID.randomUUID().toString());append(log,after,null,after.optBoolean("done"),now);}else if(old.has("stateChangeId"))AgendaFeatures.put(after,"stateChangeId",old.optString("stateChangeId"));
        if(after.optBoolean("done"))AgendaFeatures.put(after,"completedDate",old.optBoolean("done")&&!old.optString("completedDate").isEmpty()?old.optString("completedDate"):now.toLocalDate().toString());else after.remove("completedDate");
        if(log.length()>0)AgendaFeatures.put(after,"progressLog",log);
    }
    static void append(JSONObject log,JSONObject parent,JSONObject child,boolean done,LocalDateTime now){String id=UUID.randomUUID().toString();AgendaFeatures.put(log,id,AiProtocol.obj("id",id,"date",now.toLocalDate().toString(),"time",now.toLocalTime().withNano(0).toString(),"parentTitle",parent.optString("title"),"childId",child==null?"":child.optString("id"),"title",child==null?parent.optString("title"):child.optString("title"),"done",done));}
    static List<JSONObject> history(JSONObject task,LocalDate date){List<JSONObject> result=new ArrayList<>();JSONObject log=task.optJSONObject("progressLog");if(log!=null)log.keys().forEachRemaining(k->{JSONObject item=log.optJSONObject(k);if(item!=null&&item.optString("date").equals(date.toString()))result.add(item);});result.sort(Comparator.comparing(o->o.optString("time")));return result;}
    static String historyText(List<JSONObject> log){List<String> parts=new ArrayList<>();for(JSONObject item:log)parts.add((item.optBoolean("done")?"完成：":"重新打开：")+item.optString("title"));return String.join("；",parts);}
    static List<JSONObject> forDay(JSONObject event,LocalDate selected,LocalDate today){
        Map<String,JSONObject> rows=new LinkedHashMap<>();
        for(JSONObject task:TaskRules.visible(event,selected,today)){
            if(task.optBoolean("done")){if(completedDay(task).equals(selected.toString()))rows.put(key(task),task);continue;}
            if(task.optBoolean("continuous")&&!selected.equals(today))continue;
            if(task.optBoolean("rollover")&&selected.isBefore(today))continue;
            rows.put(key(task),task);
        }
        // A finished rolled task belongs to the day it was actually completed, even if its plan was earlier.
        List<JSONObject> candidates=TaskRules.scheduled(event,selected.isAfter(today)?selected:today);
        if(event.optString("repeat","none").equals("none")&&event.optBoolean("done")){JSONObject direct=AgendaFeatures.cloneJson(event);AgendaFeatures.put(direct,"occurrence",event.optString("date"));candidates.add(direct);}
        JSONObject overrides=event.optJSONObject("overrides");if(overrides!=null)overrides.keys().forEachRemaining(k->{JSONObject o=overrides.optJSONObject(k);if(o!=null&&o.optBoolean("done")&&o.optString("completedDate").equals(selected.toString())){JSONObject occurrence=AgendaFeatures.occurrence(event,LocalDate.parse(k));if(occurrence!=null)candidates.add(occurrence);}});
        for(JSONObject task:candidates)if(task.optBoolean("done")&&completedDay(task).equals(selected.toString()))rows.put(key(task),task);
        if(selected.isBefore(today)){
            List<JSONObject> sources=new ArrayList<>();sources.add(event);if(overrides!=null)overrides.keys().forEachRemaining(k->{JSONObject o=overrides.optJSONObject(k);if(o!=null){JSONObject copy=AgendaFeatures.cloneJson(o);AgendaFeatures.put(copy,"id",event.optString("id"));AgendaFeatures.put(copy,"occurrence",k);sources.add(copy);}});
            for(JSONObject source:sources){List<JSONObject> log=history(source,selected);if(log.isEmpty()||rows.containsKey(key(source)))continue;JSONObject brief=AiProtocol.obj("id",source.optString("id"),"occurrence",source.optString("occurrence",source.optString("date",event.optString("date"))),"date",selected.toString(),"title",log.get(0).optString("parentTitle",source.optString("title")),"progressSummary",historyText(log),"start",log.get(0).optString("time").substring(0,5),"allDay",false,"tags",source.optJSONArray("tags")==null?new JSONArray():source.optJSONArray("tags"));rows.put(key(source),brief);}
        }
        return new ArrayList<>(rows.values());
    }
}
