package cn.campus.schedule;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.json.*;

final class TaskRules {
    static List<JSONObject> scheduled(JSONObject event,LocalDate through){
        List<JSONObject> result=new ArrayList<>();LocalDate first=LocalDate.parse(event.optString("date"));JSONObject rule=AgendaFeatures.cloneJson(event);rule.remove("count");int count=event.optInt("count"),rank=0;boolean repeat=!event.optString("repeat","none").equals("none");
        LocalDate last=repeat?through:first;if(!event.optString("until").isEmpty()&&LocalDate.parse(event.optString("until")).isBefore(last))last=LocalDate.parse(event.optString("until"));if(last.isAfter(through))last=through;JSONObject overrides=event.optJSONObject("overrides");
        for(LocalDate d=first;!d.isAfter(last);d=d.plusDays(1)){if(!AgendaFeatures.occurs(rule,d))continue;if(count>0&&++rank>count)break;JSONObject o=overrides==null?null:overrides.optJSONObject(d.toString());if(o!=null&&o.optBoolean("deleted"))continue;JSONObject r=AgendaFeatures.cloneJson(o==null?event:o);r.remove("overrides");AgendaFeatures.put(r,"id",event.optString("id"));AgendaFeatures.put(r,"occurrence",d.toString());AgendaFeatures.put(r,"repeat",event.optString("repeat","none"));if(o==null&&repeat){AgendaFeatures.put(r,"done",false);resetChildren(r);adjustDeadline(event,r,d);}result.add(r);}return result;
    }
    static void adjustDeadline(JSONObject event,JSONObject occurrence,LocalDate date){if(!event.optString("deadline").isEmpty())AgendaFeatures.put(occurrence,"deadline",date.plusDays(ChronoUnit.DAYS.between(LocalDate.parse(event.optString("date")),LocalDate.parse(event.optString("deadline")))).toString());}
    static JSONArray children(JSONObject event){JSONArray original=event.optJSONArray("children");try{return original==null?new JSONArray():new JSONArray(original.toString());}catch(JSONException e){throw new IllegalStateException(e);}}
    static int completed(JSONArray children){int n=0;for(int i=0;i<children.length();i++)if(children.optJSONObject(i).optBoolean("done"))n++;return n;}
    static void resetChildren(JSONObject event){JSONArray children=children(event);for(int i=0;i<children.length();i++){JSONObject child=children.optJSONObject(i);AgendaFeatures.put(child,"done",false);child.remove("completedDate");child.remove("focusDate");}event.remove("progressLog");event.remove("completedDate");AgendaFeatures.put(event,"children",children);}
    static List<JSONObject> visible(JSONObject event,LocalDate selected,LocalDate today){
        List<JSONObject> results=new ArrayList<>();LocalDate first=LocalDate.parse(event.optString("date"));
        if(first.isAfter(selected))return results;
        JSONObject unbounded=AgendaFeatures.cloneJson(event);unbounded.remove("count");int count=event.optInt("count"),rank=0;
        boolean repeating=!event.optString("repeat","none").equals("none");
        LocalDate last=event.optBoolean("rollover")?selected:first;
        // A per-occurrence override can also enable rollover on an otherwise fixed series.
        JSONObject overrides=event.optJSONObject("overrides");if(overrides!=null)for(Iterator<String> it=overrides.keys();it.hasNext();)if(overrides.optJSONObject(it.next()).optBoolean("rollover")){last=selected;break;}
        if(!repeating)last=first;else if(last.equals(first))last=selected;
        for(LocalDate d=first;!d.isAfter(last);d=d.plusDays(1)){
            if(!AgendaFeatures.occurs(unbounded,d))continue;if(count>0&&++rank>count)break;
            JSONObject o=overrides==null?null:overrides.optJSONObject(d.toString());if(o!=null&&o.optBoolean("deleted"))continue;
            JSONObject r=AgendaFeatures.cloneJson(o==null?event:o);r.remove("overrides");AgendaFeatures.put(r,"id",event.optString("id"));AgendaFeatures.put(r,"occurrence",d.toString());AgendaFeatures.put(r,"repeat",event.optString("repeat","none"));
            if(o==null&&repeating){AgendaFeatures.put(r,"done",false);resetChildren(r);adjustDeadline(event,r,d);}
            if(d.equals(selected)){results.add(r);continue;}
            if(selected.isAfter(today)||!r.optBoolean("rollover"))continue;
            String finished=r.optString("completedDate");if(r.optBoolean("done")&&(finished.isEmpty()||selected.isAfter(LocalDate.parse(finished))))continue;
            AgendaFeatures.put(r,"carried",true);AgendaFeatures.put(r,"carryDays",ChronoUnit.DAYS.between(d,selected));results.add(r);
        }
        return results;
    }
}
