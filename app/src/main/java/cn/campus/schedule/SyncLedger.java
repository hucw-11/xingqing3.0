package cn.campus.schedule;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

/** Persistent outbox, server baselines and unresolved conflicts survive process death. */
final class SyncLedger extends SQLiteOpenHelper {
    SyncLedger(Context c){super(c,"cloud-sync.db",null,1);}
    @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE records (key TEXT PRIMARY KEY, value TEXT, base TEXT, revision INTEGER NOT NULL DEFAULT 0, dirty INTEGER NOT NULL DEFAULT 1, mutation TEXT NOT NULL, conflict TEXT)");db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY,value TEXT NOT NULL)");}
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){throw new IllegalStateException("不支持的同步数据库版本");}
    static JSONObject parse(String s){try{return s==null?null:new JSONObject(s);}catch(JSONException e){throw new IllegalStateException(e);}}
    static final class Row {String key,mutation;JSONObject value,base,conflict;long revision;boolean dirty;}
    List<Row> rows(){List<Row> rows=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT key,value,base,revision,dirty,mutation,conflict FROM records ORDER BY key",null)){while(c.moveToNext()){Row r=new Row();r.key=c.getString(0);r.value=parse(c.getString(1));r.base=parse(c.getString(2));r.revision=c.getLong(3);r.dirty=c.getInt(4)!=0;r.mutation=c.getString(5);r.conflict=parse(c.getString(6));rows.add(r);}}return rows;}
    Row find(String key){for(Row r:rows())if(r.key.equals(key))return r;return null;}
    void put(Row r){ContentValues v=new ContentValues();v.put("key",r.key);v.put("value",r.value==null?null:r.value.toString());v.put("base",r.base==null?null:r.base.toString());v.put("revision",r.revision);v.put("dirty",r.dirty?1:0);v.put("mutation",r.mutation);v.put("conflict",r.conflict==null?null:r.conflict.toString());getWritableDatabase().insertWithOnConflict("records",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    String meta(String key){try(Cursor c=getReadableDatabase().rawQuery("SELECT value FROM meta WHERE key=?",new String[]{key})){return c.moveToFirst()?c.getString(0):"";}}
    void meta(String key,String value){ContentValues v=new ContentValues();v.put("key",key);v.put("value",value);getWritableDatabase().insertWithOnConflict("meta",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    void capture(JSONObject data){Map<String,JSONObject> current=SyncMerge.records(data);for(Row r:rows()){JSONObject next=current.remove(r.key);if(!SyncMerge.same(next,r.value)){r.value=next;r.dirty=true;r.mutation=UUID.randomUUID().toString();put(r);}}for(Map.Entry<String,JSONObject> item:current.entrySet()){Row r=new Row();r.key=item.getKey();r.value=item.getValue();r.dirty=true;r.mutation=UUID.randomUUID().toString();put(r);}}
    void accept(JSONObject server){String key=server.optString("record_key");long revision=server.optLong("revision");Row r=find(key);JSONObject incoming=server.optBoolean("deleted")?null:server.optJSONObject("payload");if(r==null){r=new Row();r.key=key;r.value=incoming;r.base=incoming;r.revision=revision;r.mutation=UUID.randomUUID().toString();put(r);return;}if(revision<=r.revision)return;
        if(r.conflict!=null){r.base=incoming;r.revision=revision;r.conflict=AiProtocol.obj("remote",incoming==null?JSONObject.NULL:incoming,"revision",revision);put(r);return;}
        if(r.mutation.equals(server.optString("mutation_id"))){r.base=incoming;r.revision=revision;r.dirty=!SyncMerge.same(r.value,incoming);if(r.dirty)r.mutation=UUID.randomUUID().toString();r.conflict=null;put(r);return;}
        SyncMerge.Result merged=SyncMerge.merge(r.base,r.value,incoming);if(merged.conflict){r.conflict=AiProtocol.obj("remote",incoming==null?JSONObject.NULL:incoming,"revision",revision);r.revision=revision;r.base=incoming;r.dirty=true;}else{r.value=(JSONObject)merged.value;r.base=incoming;r.revision=revision;r.conflict=null;r.dirty=!SyncMerge.same(r.value,incoming);}r.mutation=UUID.randomUUID().toString();put(r);
    }
    JSONObject project(JSONObject template){Map<String,JSONObject> values=new LinkedHashMap<>();for(Row r:rows())if(r.value!=null)values.put(r.key,r.value);return SyncMerge.project(template,values);}
    void resolve(String key,boolean local){Row r=find(key);if(r==null||r.conflict==null)return;if(!local)r.value=r.base;r.conflict=null;r.dirty=!SyncMerge.same(r.value,r.base);r.mutation=UUID.randomUUID().toString();put(r);}
}
