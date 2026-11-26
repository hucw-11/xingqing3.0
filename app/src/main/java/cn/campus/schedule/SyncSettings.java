package cn.campus.schedule;

import android.app.*;
import android.text.InputType;
import android.widget.*;
import org.json.*;
import java.util.*;

final class SyncSettings {
    static void show(MainActivity a){
        LinearLayout f=a.form();android.content.SharedPreferences p=SyncManager.prefs(a);
        TextView state=a.text(p.getString("status","尚未启用云端同步"),15,a.INK,true);f.addView(state);
        f.addView(a.text("同一环境、同一账号的手机共享课程、日程、子任务、心情、照片和标签。断网可保存，联网后自动重试。",14,a.MUTED,false));a.space(f,8);
        if(SyncManager.enabled(a)){
            f.addView(a.text("账号："+p.getString("username","")+"\n环境："+p.getString("env",""),13,a.MUTED,false));
            f.addView(a.action("立即同步",()->{SyncManager.request(a);state.setText("正在安排同步…");}));
            f.addView(a.action("处理修改冲突",()->conflicts(a)));
            f.addView(a.action("退出同步",()->new AlertDialog.Builder(a).setTitle("退出云端同步？").setMessage("本机内容和云端记录继续保留，之后可以用原账号重新登录。").setNegativeButton("取消",null).setPositiveButton("退出",(d,w)->{SyncManager.pause(a);show(a);}).show()));
        }else{
            EditText env=a.field(f,"CloudBase 环境 ID",p.getString("env","")),user=a.field(f,"同步用户名",p.getString("username","")),password=a.field(f,"同步账号密码","");password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
            TextView login=a.action("登录并合并同步",()->{});f.addView(login);
            f.addView(a.text("环境所有者需在 CloudBase 开启用户名密码登录、创建账号并运行初始化 SQL。这里填写 App 同步账号，不是腾讯云密码。首次连接会先备份本机内容，再合并云端记录。",12,a.MUTED,false));
            login.setOnClickListener(v->{String environment=env.getText().toString().trim(),username=user.getText().toString().trim(),secret=password.getText().toString();if(username.isEmpty()||secret.isEmpty()){a.toast("请输入用户名和密码");return;}login.setEnabled(false);state.setText("正在登录…");SyncManager.NETWORK.execute(()->{try{CloudBaseClient client=new CloudBaseClient(environment,SyncManager.device(a));JSONObject tokens=client.login(username,secret);AgendaFeatures.put(tokens,"expires_at",System.currentTimeMillis()+tokens.optLong("expires_in",7200)*1000);SyncManager.bind(a,environment,username,tokens);a.runOnUiThread(()->{password.setText("");state.setText("登录成功，正在合并同步");login.setEnabled(true);});}catch(Exception e){a.runOnUiThread(()->{state.setText(e instanceof IllegalArgumentException||e instanceof CloudBaseClient.Failure?e.getMessage():"登录未完成，请检查网络、账号和云端配置");login.setEnabled(true);});}});});
        }
        f.addView(a.text("草稿、AI 聊天和 API Key 留在本机。系统通知权限与提醒开关由每台手机分别设置。",12,a.MUTED,false));
        ScrollView scroll=new ScrollView(a);scroll.addView(f);AlertDialog dialog=new AlertDialog.Builder(a).setTitle("云端同步").setView(scroll).setPositiveButton("关闭",null).create();dialog.show();android.os.Handler handler=new android.os.Handler(a.getMainLooper());Runnable update=new Runnable(){public void run(){if(!dialog.isShowing())return;if(SyncManager.enabled(a))state.setText(p.getString("status","正在同步…"));handler.postDelayed(this,1000);}};handler.postDelayed(update,1000);dialog.setOnDismissListener(d->handler.removeCallbacks(update));
    }
    static void conflicts(MainActivity a){List<SyncLedger.Row> list;synchronized(SyncManager.LOCK){try(SyncLedger ledger=new SyncLedger(a)){list=ledger.rows();}}list.removeIf(r->r.conflict==null);if(list.isEmpty()){a.toast("没有待处理的修改冲突");return;}String[] names=new String[list.size()];for(int i=0;i<names.length;i++){SyncLedger.Row r=list.get(i);JSONObject value=r.value==null?r.base:r.value;names[i]=value==null?r.key:value.optString("title",value.optString("name",r.key));}new AlertDialog.Builder(a).setTitle("选择冲突记录").setItems(names,(d,w)->choose(a,list.get(w))).setNegativeButton("关闭",null).show();}
    static String description(JSONObject v){if(v==null)return "已删除";return v.optString("title",v.optString("name",""))+"\n"+v.optString("date")+" "+v.optString("start",v.optString("time"))+"\n"+v.optString("body",v.optString("note",v.toString()));}
    static void choose(MainActivity a,SyncLedger.Row row){LinearLayout f=a.form();f.addView(a.text("本机版本\n"+description(row.value),14,a.INK,false));a.space(f,16);f.addView(a.text("云端版本\n"+description(row.base),14,a.INK,false));ScrollView scroll=new ScrollView(a);scroll.addView(f);new AlertDialog.Builder(a).setTitle("选择保留版本").setView(scroll).setNegativeButton("稍后处理",null).setNeutralButton("使用云端",(d,w)->resolve(a,row.key,false)).setPositiveButton("保留本机",(d,w)->resolve(a,row.key,true)).show();}
    static void resolve(MainActivity a,String key,boolean local){synchronized(SyncManager.LOCK){try(SyncLedger ledger=new SyncLedger(a)){SyncLedger.Row row=ledger.find(key);SyncManager.backup(a,"before-conflict-choice",AiProtocol.obj("local",row.value,"remote",row.base));ledger.resolve(key,local);SyncManager.publish(a,ledger);}catch(Exception e){a.toast("处理失败，两个版本仍然保留");return;}}SyncManager.request(a);a.toast("已保留所选版本，正在同步");}
}
