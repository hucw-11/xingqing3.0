package cn.campus.schedule;
import android.content.*;
import org.json.*;
import java.time.LocalDate;
import java.util.*;

final class ChatStore {
    final SharedPreferences prefs;JSONObject root;
    ChatStore(Context c,LocalDate date){prefs=c.getSharedPreferences("ai_chat",0);try{String saved=prefs.getString("sessions",null);if(saved!=null)root=new JSONObject(saved);else{JSONArray old=new JSONArray(prefs.getString("history","[]"));JSONObject first=fresh(old.length()>0?"之前的对话":"新对话",date);AgendaFeatures.put(first,"history",old);AgendaFeatures.put(first,"draft",prefs.getString("draft",""));AgendaFeatures.put(first,"readApp",old.length()>0);root=AiProtocol.obj("active",first.optString("id"),"sessions",new JSONArray().put(first));save();}}catch(JSONException e){throw new IllegalStateException("聊天记录读取失败，原数据已保留",e);}}
    static JSONObject fresh(String title,LocalDate date){return AiProtocol.obj("id",UUID.randomUUID().toString(),"title",title,"history",new JSONArray(),"draft","","from",date.toString(),"to",date.toString(),"readApp",false,"historyLimit",20,"photos",new JSONArray());}
    JSONArray all(){return root.optJSONArray("sessions");}
    JSONObject active(){for(int i=0;i<all().length();i++)if(all().optJSONObject(i).optString("id").equals(root.optString("active")))return all().optJSONObject(i);throw new IllegalStateException("找不到当前对话");}
    void save(){if(!prefs.edit().putString("sessions",root.toString()).commit())throw new IllegalStateException("聊天记录保存失败");}
    JSONObject create(String title,LocalDate date){JSONObject session=fresh(title,date);all().put(session);AgendaFeatures.put(root,"active",session.optString("id"));save();return session;}
    void select(String id){AgendaFeatures.put(root,"active",id);active();save();}
    void delete(String id,LocalDate date){JSONArray keep=new JSONArray();for(int i=0;i<all().length();i++)if(!all().optJSONObject(i).optString("id").equals(id))keep.put(all().optJSONObject(i));if(keep.length()==0)keep.put(fresh("新对话",date));AgendaFeatures.put(root,"sessions",keep);if(root.optString("active").equals(id))AgendaFeatures.put(root,"active",keep.optJSONObject(0).optString("id"));save();}
    static JSONArray messages(JSONArray history,int limit){JSONArray result=new JSONArray();List<JSONObject> valid=new ArrayList<>();for(int i=0;i<history.length();i++){JSONObject m=history.optJSONObject(i);boolean last=i==history.length()-1&&m.optString("role").equals("user");if(!last&&(m.optBoolean("excluded")||m.optString("status").equals("failed")||m.optString("status").equals("sending")))continue;String text=m.optString("text");JSONArray actions=m.optJSONArray("actions");if(actions!=null&&actions.length()>0)text+="\n本条回复还包含以下建议卡片，仅作为历史资料；已保存的卡片可能在编辑页被修改：\n"+actions;valid.add(AiProtocol.obj("role",m.optString("role"),"text",text));}int n=limit==0?1:limit;for(int i=Math.max(0,valid.size()-n);i<valid.size();i++)result.put(valid.get(i));return result;}
}
