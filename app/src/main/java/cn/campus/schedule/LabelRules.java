package cn.campus.schedule;
import org.json.*;
import java.util.*;
final class LabelRules {
    static JSONObject remove(JSONObject data,String id){JSONObject next=AgendaFeatures.cloneJson(data);JSONArray labels=next.optJSONArray("labels"),keep=new JSONArray();for(int i=0;i<labels.length();i++)if(!labels.optJSONObject(i).optString("id").equals(id))keep.put(labels.optJSONObject(i));AgendaFeatures.put(next,"labels",keep);if(next.optString("studyLabel").equals(id))AgendaFeatures.put(next,"studyLabel","");unlink(next.optJSONArray("courses"),id);unlink(next.optJSONArray("events"),id);return next;}
    static void unlink(Object node,String id){if(node instanceof JSONArray){JSONArray a=(JSONArray)node;for(int i=0;i<a.length();i++)unlink(a.opt(i),id);}else if(node instanceof JSONObject){JSONObject o=(JSONObject)node;JSONArray tags=o.optJSONArray("tags");if(tags!=null){JSONArray keep=new JSONArray();for(String tag:AgendaFeatures.strings(tags))if(!tag.equals(id))keep.put(tag);AgendaFeatures.put(o,"tags",keep);}List<String> keys=new ArrayList<>();o.keys().forEachRemaining(keys::add);for(String key:keys)if(!key.equals("tags"))unlink(o.opt(key),id);}}
}
