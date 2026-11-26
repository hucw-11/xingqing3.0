package cn.campus.schedule;

import android.content.*;
import android.app.job.*;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

final class SyncManager {
    static final Object LOCK=new Object();
    static final String CHANGED="cn.campus.schedule.SYNC_CHANGED";
    static final ScheduledExecutorService NETWORK=Executors.newSingleThreadScheduledExecutor();
    static boolean running;static ScheduledFuture<?> pending;
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("cloud_sync",0);}
    static boolean enabled(Context c){return prefs(c).getBoolean("enabled",false);}
    static String device(Context c){String id=prefs(c).getString("device","");if(id.isEmpty()){id=UUID.randomUUID().toString();prefs(c).edit().putString("device",id).commit();}return id;}
    static JSONObject data(Context c){try{return new JSONObject(c.getSharedPreferences("MainActivity",0).getString("data","{}"));}catch(JSONException e){throw new IllegalStateException("本机数据读取失败");}}
    static boolean persist(Context c,JSONObject expected,JSONObject next){
        synchronized(LOCK){try{
            restore(c);JSONObject latest=data(c);if(!latest.has("courses"))latest=expected;
            SyncMerge.Result result=SyncMerge.merge(expected,next,latest);JSONObject value=(JSONObject)result.value;
            if(result.conflict){backup(c,"edit-conflict",latest);backup(c,"edit-local",next);}
            if(enabled(c))try(SyncLedger ledger=new SyncLedger(c)){
                android.database.sqlite.SQLiteDatabase db=ledger.getWritableDatabase();db.beginTransaction();try{
                    ledger.capture(value);
                    if(result.conflict){Map<String,JSONObject> b=SyncMerge.records(expected),l=SyncMerge.records(next),r=SyncMerge.records(latest);for(SyncLedger.Row row:ledger.rows())if(SyncMerge.merge(b.get(row.key),l.get(row.key),r.get(row.key)).conflict){row.conflict=AiProtocol.obj("remote",r.get(row.key)==null?JSONObject.NULL:r.get(row.key),"revision",row.revision);ledger.put(row);}}
                    ledger.meta("projectPending","true");db.setTransactionSuccessful();
                }finally{db.endTransaction();}
                if(!c.getSharedPreferences("MainActivity",0).edit().putString("data",value.toString()).commit())return false;
                ledger.meta("projectPending","false");
            }else if(!c.getSharedPreferences("MainActivity",0).edit().putString("data",value.toString()).commit())return false;
            request(c);return true;
        }catch(Exception e){return false;}}
    }
    static void restore(Context c){if(!enabled(c))return;synchronized(LOCK){try(SyncLedger ledger=new SyncLedger(c)){if(ledger.meta("projectPending").equals("true"))publish(c,ledger);}catch(Exception e){throw new IllegalStateException("同步内容恢复失败，原数据已保留",e);}}}
    static void backup(Context c,String prefix,JSONObject source)throws IOException{File dir=new File(c.getFilesDir(),"sync-backups");if(!dir.exists()&&!dir.mkdirs())throw new IOException("无法备份原数据");try(Writer out=new OutputStreamWriter(new FileOutputStream(new File(dir,prefix+"-"+System.currentTimeMillis()+"-"+UUID.randomUUID()+".json")),java.nio.charset.StandardCharsets.UTF_8)){out.write(source.toString());}}
    static void status(Context c,String message){prefs(c).edit().putString("status",message).apply();c.sendBroadcast(new Intent(CHANGED).setPackage(c.getPackageName()));}
    static boolean hasContent(JSONObject data){if(data.optBoolean("configured"))return true;for(String k:new String[]{"courses","events","moods"})if(data.optJSONArray(k)!=null&&data.optJSONArray(k).length()>0)return true;JSONArray labels=data.optJSONArray("labels");if(labels!=null&&labels.length()>0){if(labels.length()!=4)return true;Set<String> defaults=new HashSet<>(Arrays.asList("学习","锻炼","娱乐","生活"));for(int i=0;i<labels.length();i++)if(!defaults.remove(labels.optJSONObject(i).optString("name")))return true;}return false;}
    static void bind(Context c,String env,String username,JSONObject tokens)throws Exception{synchronized(LOCK){String owner=tokens.getString("sub"),binding=env+"/"+owner;try(SyncLedger ledger=new SyncLedger(c)){String old=ledger.meta("binding");if(!old.isEmpty()&&!old.equals(binding))throw new IllegalArgumentException("本机已绑定另一个同步账号。请先导出数据；为防止内容串入其他账号，本版本不支持更换账号。");backup(c,"before-sync",data(c));SyncCredentials.save(c,tokens);prefs(c).edit().putString("env",env).putString("username",username).putString("uid",owner).putBoolean("enabled",true).commit();if(old.isEmpty()){ledger.meta("binding",binding);ledger.meta("cursor","0");ledger.meta("firstPull","true");JSONObject initial=data(c);if(hasContent(initial))ledger.capture(initial);}else ledger.capture(data(c));}}schedule(c);request(c);}
    static void request(Context context){Context c=context.getApplicationContext();if(!enabled(c))return;synchronized(LOCK){if(pending!=null)pending.cancel(false);pending=NETWORK.schedule(()->run(c),2,TimeUnit.SECONDS);}schedule(c);}
    static void schedule(Context c){if(!enabled(c))return;JobScheduler scheduler=c.getSystemService(JobScheduler.class);if(scheduler.getPendingJob(320)==null)scheduler.schedule(new JobInfo.Builder(320,new ComponentName(c,SyncJobService.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(15*60*1000L).setPersisted(true).build());scheduler.schedule(new JobInfo.Builder(321,new ComponentName(c,SyncJobService.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(5000).setOverrideDeadline(60000).build());}
    static void pause(Context c){synchronized(LOCK){prefs(c).edit().putBoolean("enabled",false).commit();if(pending!=null)pending.cancel(false);SyncCredentials.clear(c);c.getSystemService(JobScheduler.class).cancel(320);c.getSystemService(JobScheduler.class).cancel(321);}status(c,"已退出同步，本机内容继续保留");}
    static String token(Context c,CloudBaseClient client)throws Exception{JSONObject tokens=SyncCredentials.read(c);if(tokens.optString("access_token").isEmpty())throw new IOException("请先登录同步账号");if(tokens.optLong("expires_at")<System.currentTimeMillis()+60000){JSONObject renewed=client.refresh(tokens.getString("refresh_token"));if(!renewed.has("sub"))AgendaFeatures.put(renewed,"sub",tokens.optString("sub"));if(!renewed.has("refresh_token"))AgendaFeatures.put(renewed,"refresh_token",tokens.optString("refresh_token"));AgendaFeatures.put(renewed,"expires_at",System.currentTimeMillis()+renewed.optLong("expires_in",7200)*1000);synchronized(LOCK){if(!enabled(c))throw new IOException("同步已暂停");SyncCredentials.save(c,renewed);}tokens=renewed;}return tokens.getString("access_token");}
    static void run(Context context){Context c=context.getApplicationContext();synchronized(LOCK){if(running||!enabled(c))return;running=true;}status(c,"正在同步…");try{CloudBaseClient client=new CloudBaseClient(prefs(c).getString("env",""),device(c));synchronize(c,client,token(c,client),prefs(c).getString("uid",""));}catch(Exception e){status(c,e instanceof CloudBaseClient.Failure?e.getMessage():"同步未完成，本机修改已保留，将在联网后重试");}finally{synchronized(LOCK){running=false;}}}
    static void synchronize(Context c,CloudBaseClient client,String token,String uid)throws Exception{
        try(SyncLedger ledger=new SyncLedger(c)){
            pull(c,ledger,client,token);
            downloadPhotos(c,ledger,client,token,uid);
            synchronized(LOCK){if(!enabled(c))return;if(ledger.meta("firstPull").equals("true")){if(ledger.rows().isEmpty())ledger.capture(data(c));ledger.meta("firstPull","false");}else ledger.capture(data(c));}
            for(int pass=0;pass<3;pass++){
                List<SyncLedger.Row> changes;synchronized(LOCK){changes=ledger.rows();}
                boolean sent=false;for(SyncLedger.Row row:changes){if(!enabled(c))return;if(!row.dirty||row.conflict!=null)continue;uploadPhotos(c,client,token,uid,row.value);if(!enabled(c))return;JSONObject response=client.push(token,row),server=response.getJSONObject("record");synchronized(LOCK){if(!enabled(c))return;ledger.meta("projectPending","true");SyncLedger.Row live=ledger.find(row.key);if(response.optBoolean("accepted")&&live!=null){live.base=row.value;live.revision=server.getLong("revision");live.dirty=!SyncMerge.same(live.value,row.value);if(live.dirty)live.mutation=UUID.randomUUID().toString();ledger.put(live);}else ledger.accept(server);publish(c,ledger);}sent=true;}if(!sent)break;
            }
            pull(c,ledger,client,token);downloadPhotos(c,ledger,client,token,uid);
            int conflicts=0,dirty=0;synchronized(LOCK){for(SyncLedger.Row r:ledger.rows()){if(r.conflict!=null)conflicts++;if(r.dirty)dirty++;}prefs(c).edit().putLong("lastSuccess",System.currentTimeMillis()).apply();}
            status(c,conflicts>0?"有 "+conflicts+" 条修改冲突，请选择保留版本":dirty>0?"仍有 "+dirty+" 条修改等待同步":"同步完成 · "+LocalTime.now().withNano(0));
        }ReminderScheduler.schedule(c);
    }
    static void pull(Context c,SyncLedger ledger,CloudBaseClient client,String token)throws Exception{for(int page=0;page<1000;page++){long cursor;synchronized(LOCK){String n=ledger.meta("cursor");cursor=n.isEmpty()?0:Long.parseLong(n);}JSONArray batch=client.pull(token,cursor);synchronized(LOCK){if(!enabled(c))return;android.database.sqlite.SQLiteDatabase db=ledger.getWritableDatabase();db.beginTransaction();try{for(int i=0;i<batch.length();i++){JSONObject row=batch.getJSONObject(i);ledger.accept(row);cursor=Math.max(cursor,row.getLong("revision"));}ledger.meta("cursor",String.valueOf(cursor));if(batch.length()>0)ledger.meta("projectPending","true");db.setTransactionSuccessful();}finally{db.endTransaction();}if(batch.length()>0)publish(c,ledger);}if(batch.length()<100)return;}throw new IOException("同步记录较多，请再次同步继续");}
    static void publish(Context c,SyncLedger ledger)throws IOException{if(!c.getSharedPreferences("MainActivity",0).edit().putString("data",ledger.project(data(c)).toString()).commit())throw new IOException("本机同步内容保存失败");ledger.meta("projectPending","false");c.sendBroadcast(new Intent(CHANGED).setPackage(c.getPackageName()));}
    static String photoReferences(Context c){if(!enabled(c))return "";synchronized(LOCK){try(SyncLedger ledger=new SyncLedger(c)){StringBuilder result=new StringBuilder();for(SyncLedger.Row r:ledger.rows())result.append(r.value).append(r.base);return result.toString();}}}
    static List<String> photos(JSONObject record){List<String> result=new ArrayList<>();if(record!=null){JSONArray names=record.optJSONArray("photos");if(names!=null)for(int i=0;i<names.length();i++)result.add(names.optString(i));}return result;}
    static void uploadPhotos(Context c,CloudBaseClient client,String token,String uid,JSONObject record)throws Exception{for(String name:photos(record)){client.photoPath(uid,name);String key="uploaded:"+uid+":"+name;if(prefs(c).getBoolean(key,false))continue;File file=new File(c.getFilesDir(),"photos/"+name);if(!file.isFile())throw new IOException("照片还未下载完成，请重试同步");byte[] bytes=Files.readAllBytes(file.toPath());if(bytes.length>CloudBaseClient.LIMIT)throw new IOException("照片过大");client.upload(token,uid,name,bytes);prefs(c).edit().putBoolean(key,true).commit();}}
    static void downloadPhotos(Context c,SyncLedger ledger,CloudBaseClient client,String token,String uid)throws Exception{List<SyncLedger.Row> rows;synchronized(LOCK){rows=ledger.rows();}File dir=new File(c.getFilesDir(),"photos");if(!dir.exists()&&!dir.mkdirs())throw new IOException("无法保存照片");for(SyncLedger.Row r:rows)for(String name:photos(r.value)){client.photoPath(uid,name);File target=new File(dir,name);if(target.isFile())continue;byte[] bytes=client.download(token,uid,name);File temp=File.createTempFile("sync-",".tmp",dir);try{Files.write(temp.toPath(),bytes);Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);}finally{temp.delete();}prefs(c).edit().putBoolean("uploaded:"+uid+":"+name,true).commit();c.sendBroadcast(new Intent(CHANGED).setPackage(c.getPackageName()).putExtra("photos",true));}}
}
