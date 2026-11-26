package cn.campus.schedule;

import android.content.*;
import android.database.sqlite.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CloudSyncTest {
    final Context base=InstrumentationRegistry.getInstrumentation().getTargetContext();
    Phone a,b;Cloud cloud;
    static class Phone extends ContextWrapper {
        final String prefix;Phone(Context c){super(c);prefix="test-"+UUID.randomUUID()+"-";}
        @Override public SharedPreferences getSharedPreferences(String name,int mode){return super.getSharedPreferences(prefix+name,mode);}
        @Override public Context getApplicationContext(){return this;}
        @Override public File getFilesDir(){File dir=new File(super.getFilesDir(),prefix);dir.mkdirs();return dir;}
        @Override public File getDatabasePath(String name){return super.getDatabasePath(prefix+name);}
        @Override public SQLiteDatabase openOrCreateDatabase(String n,int mode,SQLiteDatabase.CursorFactory f){return super.openOrCreateDatabase(prefix+n,mode,f);}
        @Override public SQLiteDatabase openOrCreateDatabase(String n,int mode,SQLiteDatabase.CursorFactory f,android.database.DatabaseErrorHandler e){return super.openOrCreateDatabase(prefix+n,mode,f,e);}
        @Override public boolean deleteDatabase(String name){return super.deleteDatabase(prefix+name);}
    }
    static class Cloud extends CloudBaseClient {
        final Map<String,JSONObject> rows=new LinkedHashMap<>();final Map<String,byte[]> images=new HashMap<>();long revision;boolean loseAck;int uploads;
        Cloud(){super("example-env","test-device");}
        @Override JSONArray pull(String token,long after){JSONArray result=new JSONArray();rows.values().stream().filter(r->r.optLong("revision")>after).sorted(Comparator.comparingLong(r->r.optLong("revision"))).forEach(r->result.put(SyncMerge.copy(r)));return result;}
        @Override JSONObject push(String token,SyncLedger.Row r)throws Exception{JSONObject existing=rows.get(r.key);if(existing!=null&&existing.optString("mutation_id").equals(r.mutation))return AiProtocol.obj("accepted",true,"record",SyncMerge.copy(existing));if(existing!=null&&existing.optLong("revision")!=r.revision)return AiProtocol.obj("accepted",false,"record",SyncMerge.copy(existing));JSONObject saved=AiProtocol.obj("record_key",r.key,"payload",r.value==null?JSONObject.NULL:SyncMerge.copy(r.value),"deleted",r.value==null,"revision",++revision,"mutation_id",r.mutation);rows.put(r.key,saved);if(loseAck){loseAck=false;throw new IOException("response lost after commit");}return AiProtocol.obj("accepted",true,"record",SyncMerge.copy(saved));}
        @Override void upload(String token,String uid,String name,byte[] bytes){images.putIfAbsent(name,bytes);uploads++;}
        @Override byte[] download(String token,String uid,String name)throws IOException{if(!images.containsKey(name))throw new IOException();return images.get(name);}
    }
    JSONObject sample(){return AiProtocol.obj("schema",2,"configured",false,"semester",LocalDate.now().toString(),"courses",new JSONArray(),"events",new JSONArray(),"moods",new JSONArray(),"labels",new JSONArray());}
    @Before public void setup(){a=new Phone(base);b=new Phone(base);cloud=new Cloud();seed(a);seed(b);}
    @After public void finish(){a.deleteDatabase("cloud-sync.db");b.deleteDatabase("cloud-sync.db");}
    void seed(Phone p){p.getSharedPreferences("MainActivity",0).edit().putString("data",sample().toString()).commit();SyncManager.prefs(p).edit().putBoolean("enabled",true).putString("uid","same-user").commit();try(SyncLedger ledger=new SyncLedger(p)){ledger.meta("cursor","0");ledger.meta("firstPull","true");}}
    void save(Phone p,JSONObject data){synchronized(SyncManager.LOCK){p.getSharedPreferences("MainActivity",0).edit().putString("data",data.toString()).commit();try(SyncLedger ledger=new SyncLedger(p)){ledger.capture(data);}}}
    void sync(Phone p)throws Exception{SyncManager.synchronize(p,cloud,"token","same-user");}
    JSONObject event(String id){return AiProtocol.obj("id",id,"title",id,"date",LocalDate.now().toString(),"start","14:00","allDay",false,"done",false,"children",new JSONArray().put(AiProtocol.obj("id","one","title","整理资料","done",false)).put(AiProtocol.obj("id","two","title","撰写报告","done",false)));}
    @Test public void twoPhonesAddEditAndDeleteWithoutResurrection()throws Exception{
        JSONObject data=sample();data.getJSONArray("events").put(event("课后任务"));save(a,data);sync(a);sync(b);assertEquals(1,SyncManager.data(b).getJSONArray("events").length());
        JSONObject left=SyncManager.data(a),right=SyncManager.data(b);left.getJSONArray("events").put(event("手机A新增"));right.getJSONArray("events").put(event("手机B新增"));save(a,left);save(b,right);sync(a);sync(b);sync(a);assertEquals(3,SyncManager.data(a).getJSONArray("events").length());
        left=SyncManager.data(a);JSONArray keep=new JSONArray();for(int i=0;i<left.getJSONArray("events").length();i++){JSONObject row=left.getJSONArray("events").getJSONObject(i);if(!row.getString("id").equals("课后任务"))keep.put(row);}AgendaFeatures.put(left,"events",keep);save(a,left);sync(a);sync(b);sync(a);assertEquals(2,SyncManager.data(b).getJSONArray("events").length());assertTrue(cloud.rows.get("events:课后任务").getBoolean("deleted"));
    }
    @Test public void independentSubtasksAndRepeatOccurrencesMerge()throws Exception{
        JSONObject data=sample(),e=event("repeat");AgendaFeatures.put(e,"overrides",new JSONObject());data.getJSONArray("events").put(e);save(a,data);sync(a);sync(b);
        JSONObject left=SyncManager.data(a),right=SyncManager.data(b);left.getJSONArray("events").getJSONObject(0).getJSONArray("children").getJSONObject(0).put("done",true);right.getJSONArray("events").getJSONObject(0).getJSONArray("children").getJSONObject(1).put("done",true);left.getJSONArray("events").getJSONObject(0).getJSONObject("overrides").put("2026-11-26",AiProtocol.obj("done",true));right.getJSONArray("events").getJSONObject(0).getJSONObject("overrides").put("2026-11-27",AiProtocol.obj("done",true));save(a,left);save(b,right);sync(a);sync(b);sync(a);JSONObject merged=SyncManager.data(a).getJSONArray("events").getJSONObject(0);assertTrue(merged.getJSONArray("children").getJSONObject(0).getBoolean("done"));assertTrue(merged.getJSONArray("children").getJSONObject(1).getBoolean("done"));assertEquals(2,merged.getJSONObject("overrides").length());
    }
    @Test public void conflictingLongTextSurvivesRestartAndNeedsExplicitChoice()throws Exception{
        JSONObject data=sample();data.getJSONArray("moods").put(AiProtocol.obj("id","journal","body","原始日记","title","一天"));save(a,data);sync(a);sync(b);JSONObject left=SyncManager.data(a),right=SyncManager.data(b);left.getJSONArray("moods").getJSONObject(0).put("body","手机A写的长日记");right.getJSONArray("moods").getJSONObject(0).put("body","手机B写的长日记");save(a,left);save(b,right);sync(a);sync(b);
        try(SyncLedger ledger=new SyncLedger(b)){assertNotNull(ledger.find("moods:journal").conflict);assertEquals("手机B写的长日记",ledger.find("moods:journal").value.getString("body"));assertEquals("手机A写的长日记",ledger.find("moods:journal").base.getString("body"));}
        left=SyncManager.data(a);left.getJSONArray("moods").getJSONObject(0).put("title","后来修改的标题");save(a,left);sync(a);sync(b);try(SyncLedger ledger=new SyncLedger(b)){assertNotNull(ledger.find("moods:journal").conflict);ledger.resolve("moods:journal",true);SyncManager.publish(b,ledger);}sync(b);sync(a);assertEquals("手机B写的长日记",SyncManager.data(a).getJSONArray("moods").getJSONObject(0).getString("body"));
    }
    @Test public void photoBytesFollowRecordAndUploadOnce()throws Exception{
        File dir=new File(a.getFilesDir(),"photos");dir.mkdirs();byte[] bytes={1,2,3,4};Files.write(new File(dir,"test-photo.jpg").toPath(),bytes);JSONObject data=sample();data.getJSONArray("moods").put(AiProtocol.obj("id","photo","body","有照片","photos",new JSONArray().put("test-photo.jpg")));save(a,data);sync(a);sync(b);assertArrayEquals(bytes,Files.readAllBytes(new File(b.getFilesDir(),"photos/test-photo.jpg").toPath()));sync(a);assertEquals(1,cloud.uploads);
    }
    @Test public void lostResponseRetriesIdempotentlyAndOutboxPersists()throws Exception{
        JSONObject data=sample();data.getJSONArray("events").put(event("offline"));save(a,data);cloud.loseAck=true;try{sync(a);fail();}catch(IOException expected){}try(SyncLedger ledger=new SyncLedger(a)){assertTrue(ledger.find("events:offline").dirty);}sync(a);sync(b);assertEquals(1,cloud.rows.size());assertEquals(1,SyncManager.data(b).getJSONArray("events").length());try(SyncLedger ledger=new SyncLedger(a)){assertFalse(ledger.find("events:offline").dirty);}
    }
    @Test public void projectionRecoversAfterProcessDeathBeforePreferenceWrite()throws Exception{
        try(SyncLedger ledger=new SyncLedger(a)){ledger.accept(AiProtocol.obj("record_key","events:recover","payload",event("recover"),"revision",1,"mutation_id","other-device"));ledger.meta("projectPending","true");}SyncManager.restore(a);assertEquals("recover",SyncManager.data(a).getJSONArray("events").getJSONObject(0).getString("id"));
    }
    @Test public void staleEditorPreservesBothVersionsAndUnrelatedRemoteChanges()throws Exception{
        JSONObject initial=sample();initial.getJSONArray("moods").put(AiProtocol.obj("id","journal","body","原文"));save(a,initial);sync(a);JSONObject editor=(JSONObject)SyncMerge.copy(initial),remote=SyncManager.data(a);remote.getJSONArray("moods").getJSONObject(0).put("body","其他手机的修改");remote.getJSONArray("events").put(event("来自另一台手机"));save(a,remote);sync(a);editor.getJSONArray("moods").getJSONObject(0).put("body","编辑器内的新长文");
        assertTrue(SyncManager.persist(a,initial,editor));SyncManager.prefs(a).edit().putBoolean("enabled",false).commit();synchronized(SyncManager.LOCK){if(SyncManager.pending!=null)SyncManager.pending.cancel(false);}try(SyncLedger ledger=new SyncLedger(a)){assertNotNull(ledger.find("moods:journal").conflict);assertEquals("其他手机的修改",ledger.find("moods:journal").base.getString("body"));}assertEquals(1,SyncManager.data(a).getJSONArray("events").length());
    }
    @Test public void cloudSettingsScreenAndCredentials()throws Exception{
        JSONObject tokens=AiProtocol.obj("access_token","private-access-token","refresh_token","private-refresh-token","sub","same-user");SyncCredentials.save(a,tokens);assertFalse(a.getSharedPreferences("sync_credentials",0).getString("session","").contains("private-access-token"));assertEquals("private-refresh-token",SyncCredentials.read(a).getString("refresh_token"));
        android.app.Instrumentation inst=InstrumentationRegistry.getInstrumentation();MainActivity activity=(MainActivity)inst.startActivitySync(new Intent(base,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));try{inst.runOnMainSync(()->SyncSettings.show(activity));androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("登录并合并同步")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()));File screenshot=new File(base.getExternalFilesDir(null),"cloud-sync-settings.png");try(FileOutputStream out=new FileOutputStream(screenshot)){inst.getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}try(InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.getUiAutomation().executeShellCommand("cp "+screenshot.getAbsolutePath()+" /data/local/tmp/cloud-sync-settings.png"))){while(in.read()!=-1){}}}finally{inst.runOnMainSync(activity::finish);}
    }
}
