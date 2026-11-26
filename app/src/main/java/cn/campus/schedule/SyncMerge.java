package cn.campus.schedule;

import org.json.*;
import java.util.*;

/** Three-way merge uses versions, never device wall clocks. */
final class SyncMerge {
    static final class Result { Object value; boolean conflict; Result(Object value,boolean conflict){this.value=value;this.conflict=conflict;} }
    static String canonical(Object value){
        if(value==null||value==JSONObject.NULL)return "null";
        if(value instanceof JSONObject){JSONObject o=(JSONObject)value;List<String> keys=new ArrayList<>();o.keys().forEachRemaining(keys::add);Collections.sort(keys);List<String> parts=new ArrayList<>();for(String k:keys)parts.add(JSONObject.quote(k)+":"+canonical(o.opt(k)));return "{"+String.join(",",parts)+"}";}
        if(value instanceof JSONArray){JSONArray a=(JSONArray)value;List<String> parts=new ArrayList<>();for(int i=0;i<a.length();i++)parts.add(canonical(a.opt(i)));return "["+String.join(",",parts)+"]";}
        return value instanceof String?JSONObject.quote((String)value):String.valueOf(value);
    }
    static boolean same(Object a,Object b){return canonical(a).equals(canonical(b));}
    static Object copy(Object a){try{return a instanceof JSONObject?new JSONObject(a.toString()):a instanceof JSONArray?new JSONArray(a.toString()):a;}catch(JSONException e){throw new IllegalArgumentException(e);}}
    static Result merge(Object base,Object local,Object remote){
        if(same(local,remote)||same(base,remote))return new Result(copy(local),false);
        if(same(base,local))return new Result(copy(remote),false);
        if(local instanceof JSONObject&&remote instanceof JSONObject&&(base==null||base==JSONObject.NULL||base instanceof JSONObject)){
            JSONObject b=base instanceof JSONObject?(JSONObject)base:new JSONObject(),l=(JSONObject)local,r=(JSONObject)remote,out=new JSONObject();Set<String> keys=new TreeSet<>();b.keys().forEachRemaining(keys::add);l.keys().forEachRemaining(keys::add);r.keys().forEachRemaining(keys::add);boolean conflict=false;
            for(String k:keys){Result child=merge(b.opt(k),l.opt(k),r.opt(k));if(child.value!=null)AgendaFeatures.put(out,k,child.value);conflict|=child.conflict;}return new Result(out,conflict);
        }
        if(base instanceof JSONArray&&local instanceof JSONArray&&remote instanceof JSONArray&&identified((JSONArray)base)&&identified((JSONArray)local)&&identified((JSONArray)remote)){
            Map<String,Object> b=index((JSONArray)base),l=index((JSONArray)local),r=index((JSONArray)remote);Set<String> keys=new LinkedHashSet<>();keys.addAll(l.keySet());keys.addAll(r.keySet());keys.addAll(b.keySet());JSONArray out=new JSONArray();boolean conflict=false;
            for(String k:keys){Result child=merge(b.get(k),l.get(k),r.get(k));if(child.value!=null&&child.value!=JSONObject.NULL)out.put(child.value);conflict|=child.conflict;}return new Result(out,conflict);
        }
        return new Result(copy(local),true);
    }
    static boolean identified(JSONArray a){for(int i=0;i<a.length();i++)if(!(a.opt(i) instanceof JSONObject)||a.optJSONObject(i).optString("id").isEmpty())return false;return true;}
    static Map<String,Object> index(JSONArray a){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<a.length();i++)m.put(a.optJSONObject(i).optString("id"),a.opt(i));return m;}
    static Map<String,JSONObject> records(JSONObject data){Map<String,JSONObject> out=new LinkedHashMap<>();for(String kind:new String[]{"courses","events","moods","labels"}){JSONArray a=data.optJSONArray(kind);if(a!=null)for(int i=0;i<a.length();i++){JSONObject item=a.optJSONObject(i);if(item==null||item.optString("id").isEmpty())throw new IllegalArgumentException("记录缺少标识，已保留原数据");out.put(kind+":"+item.optString("id"),(JSONObject)copy(item));}}
        if(data.optBoolean("configured")){JSONObject settings=new JSONObject();for(String k:new String[]{"semester","configured","totalWeeks","starts","ends","studyLabel"})if(data.has(k))AgendaFeatures.put(settings,k,data.opt(k));out.put("settings:timetable",settings);}return out;}
    static JSONObject project(JSONObject template,Map<String,JSONObject> records){JSONObject result=(JSONObject)copy(template);for(String kind:new String[]{"courses","events","moods","labels"}){JSONArray values=new JSONArray();for(Map.Entry<String,JSONObject> e:records.entrySet())if(e.getKey().startsWith(kind+":"))values.put(copy(e.getValue()));AgendaFeatures.put(result,kind,values);}JSONObject settings=records.get("settings:timetable");if(settings!=null)settings.keys().forEachRemaining(k->AgendaFeatures.put(result,k,settings.opt(k)));String study=result.optString("studyLabel");if(!study.isEmpty()&&!records.containsKey("labels:"+study)){study="";JSONArray labels=result.optJSONArray("labels");for(int i=0;i<labels.length();i++)if(labels.optJSONObject(i).optString("name").equals("学习"))study=labels.optJSONObject(i).optString("id");AgendaFeatures.put(result,"studyLabel",study);}return result;}
}
