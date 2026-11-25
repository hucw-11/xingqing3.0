package cn.campus.schedule;
import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.*;
import java.io.*;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;

@RunWith(AndroidJUnit4.class)
public class Version13Test {
    final Instrumentation inst=InstrumentationRegistry.getInstrumentation();final Context context=inst.getTargetContext();MainActivity main;AiActivity ai;
    @Before public void open(){context.getSharedPreferences("MainActivity",0).edit().clear().commit();context.getSharedPreferences("ai_chat",0).edit().clear().commit();context.getSharedPreferences("reminders",0).edit().clear().putBoolean("summary",false).putBoolean("courses",false).commit();context.getSystemService(NotificationManager.class).cancelAll();main=(MainActivity)inst.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    void shell(String command)throws Exception{try(InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.getUiAutomation().executeShellCommand(command))){while(in.read()!=-1){}}}
    @After public void close()throws Exception{if(ai!=null)inst.runOnMainSync(ai::finish);if(main!=null)inst.runOnMainSync(main::finish);context.getSystemService(AlarmManager.class).cancel(ReminderScheduler.alarm(context));context.getSystemService(NotificationManager.class).cancelAll();SecretStore.save(context,"");}
    JSONObject task(String id,boolean all,boolean done,String ddl){return AiProtocol.obj("id",id,"title",id,"date",LocalDate.now().toString(),"allDay",all,"done",done,"start","14:00","end","","deadline",ddl);}
    void screenshot(String name)throws Exception{inst.waitForIdleSync();android.os.SystemClock.sleep(400);File f=new File(context.getExternalFilesDir(null),name+".png");try(FileOutputStream out=new FileOutputStream(f)){inst.getUiAutomation().takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,out);}try(InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.getUiAutomation().executeShellCommand("cp "+f.getAbsolutePath()+" /data/local/tmp/"+name+".png"))){while(in.read()!=-1){}}}
    @Test public void plainRepliesAndToolCallsAreIndependent()throws Exception{
        JSONObject reply=DeepSeekClient.decode(AiProtocol.obj("choices",new JSONArray().put(AiProtocol.obj("finish_reason","stop","message",AiProtocol.obj("content","你好，想聊什么？")))));assertEquals("你好，想聊什么？",reply.getString("reply"));
        JSONObject call=AiProtocol.obj("function",AiProtocol.obj("name","propose_records","arguments",AiProtocol.obj("actions",new JSONArray().put(AiProtocol.obj("kind","event","title","测试"))).toString()));
        JSONObject tools=DeepSeekClient.decode(AiProtocol.obj("choices",new JSONArray().put(AiProtocol.obj("finish_reason","tool_calls","message",AiProtocol.obj("content",JSONObject.NULL,"tool_calls",new JSONArray().put(call))))));assertEquals(1,tools.getJSONArray("actions").length());
        try{DeepSeekClient.decode(AiProtocol.obj("choices",new JSONArray().put(AiProtocol.obj("finish_reason","stop","message",AiProtocol.obj("content"," \n")))));fail();}catch(DeepSeekClient.ServiceException e){assertEquals("empty",e.code);assertTrue(e.retryable);}
    }
    @Test public void ddlSummaryDeduplicatesAndNotifiesParents()throws Exception{
        LocalDate today=LocalDate.now();JSONObject data=main.copy();JSONArray tasks=new JSONArray().put(task("全天且有DDL",true,false,today.plusDays(3).toString())).put(task("已经完成",true,true,today.toString())).put(task("定时但今天截止",false,false,today.toString()));AgendaFeatures.put(tasks.optJSONObject(0),"children",new JSONArray().put(AiProtocol.obj("title","不应该通知的子任务","done",false)));AgendaFeatures.put(data,"events",tasks);assertEquals(2,ReminderRules.summary(data,today).size());assertTrue(ReminderRules.deadlineText(tasks.optJSONObject(0),today).contains("紧急"));assertTrue(ReminderRules.deadlineText(tasks.optJSONObject(2),today).contains("今天截止"));
        AgendaFeatures.put(data,"configured",true);AgendaFeatures.put(data,"semester","2026-11-23");JSONArray starts=new JSONArray(data.getJSONArray("starts").toString());starts.put(0,"08:45");AgendaFeatures.put(data,"starts",starts);AgendaFeatures.put(data,"courses",new JSONArray().put(AiProtocol.obj("id","course","title","导航技术","room","A301","day",1,"from",1,"to",2,"weeks","1")));JSONObject next=ReminderRules.next(data,LocalDateTime.of(2026,11,23,8,0),true,true);assertEquals("2026-11-23T08:30",next.getString("at"));assertEquals(1,next.getJSONArray("siblings").length());assertEquals("course",next.getJSONArray("siblings").getJSONObject(0).getString("kind"));
    }
    @Test public void backgroundAlarmDeliversOneSummary()throws Exception{
        if(android.os.Build.VERSION.SDK_INT>=33)shell("pm grant "+context.getPackageName()+" android.permission.POST_NOTIFICATIONS");if(android.os.Build.VERSION.SDK_INT>=31)shell("appops set "+context.getPackageName()+" SCHEDULE_EXACT_ALARM allow");inst.waitForIdleSync();
        JSONObject data=main.copy();AgendaFeatures.put(data,"events",new JSONArray().put(task("写报告",true,false,LocalDate.now().toString())).put(task("已完成不提醒",true,true,"")));inst.runOnMainSync(()->main.persist(data));inst.runOnMainSync(main::finish);main=null;
        LocalDateTime at=LocalDateTime.now().plusSeconds(2);JSONObject plan=AiProtocol.obj("kind","summary","at",at.toString(),"date",at.toLocalDate().toString(),"key","test-summary");context.getSharedPreferences("reminders",0).edit().putString("plan",plan.toString()).commit();context.getSystemService(AlarmManager.class).setExact(AlarmManager.RTC_WAKEUP,at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),ReminderScheduler.alarm(context));android.service.notification.StatusBarNotification[] shown={};for(int i=0;i<100;i++){shown=context.getSystemService(NotificationManager.class).getActiveNotifications();if(shown.length>0)break;android.os.SystemClock.sleep(100);}assertEquals(1,shown.length);assertEquals(200,shown[0].getId());assertTrue(shown[0].getNotification().extras.getString(Notification.EXTRA_TITLE).contains("1 项"));assertTrue(shown[0].getNotification().extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)[0].toString().contains("今天截止"));
    }
    @Test public void longPressLabelOnlyRemovesAssociations()throws Exception{
        inst.runOnMainSync(()->{main.update("labels",null,AiProtocol.obj("id","mistake","name","误建标签","color",main.BLUE));JSONObject data=main.copy();JSONObject e=task("保留活动",true,false,"");AgendaFeatures.put(e,"tags",new JSONArray().put("mistake"));AgendaFeatures.put(e,"overrides",AiProtocol.obj(LocalDate.now().toString(),AgendaFeatures.cloneJson(e)));data.optJSONArray("events").put(e);main.persist(data);main.features.manageLabels();});onView(withText("误建标签")).perform(longClick());screenshot("label-actions");onView(withText("删除标签")).perform(click());onView(withText("删除")).perform(click());assertEquals(1,main.events().length());assertEquals(0,main.events().getJSONObject(0).getJSONArray("tags").length());assertEquals(0,main.events().getJSONObject(0).getJSONObject("overrides").getJSONObject(LocalDate.now().toString()).getJSONArray("tags").length());
    }
    @Test public void floatingButtonAndEveningSection()throws Exception{
        inst.runOnMainSync(()->{JSONObject data=main.copy();JSONArray events=data.optJSONArray("events");for(int i=0;i<8;i++){JSONObject e=task("安排 "+i,false,false,"");AgendaFeatures.put(e,"start",i==7?"19:30":"09:00");events.put(e);}main.persist(data);main.render();main.pageScroll.post(()->main.pageScroll.fullScroll(android.view.View.FOCUS_DOWN));});inst.waitForIdleSync();onView(withContentDescription("创建日程或心情")).check(matches(isDisplayed()));screenshot("evening-floating-button");onView(withContentDescription("创建日程或心情")).perform(click());onView(withText("新增活动")).perform(click());onView(withContentDescription("日程标题")).perform(replaceText("带截止日期的任务"));onView(isRoot()).perform(closeSoftKeyboard());onView(withText("设置 DDL（可选）")).perform(scrollTo(),click());onView(withId(android.R.id.button1)).perform(click());onView(withText("保存")).perform(click());assertEquals(LocalDate.now().toString(),main.events().getJSONObject(8).getString("deadline"));
    }
    void waitForAi(){boolean[] busy={true};for(int i=0;i<150;i++){inst.runOnMainSync(()->busy[0]=ai.busy);if(!busy[0])return;android.os.SystemClock.sleep(100);}fail("AI request did not settle");}
    @Test public void retryUsesOriginalScopeAndSessionsStaySeparate()throws Exception{
        SecretStore.save(context,"offline-test-key");ai=(AiActivity)inst.startActivitySync(new Intent(context,AiActivity.class).putExtra("date",LocalDate.now().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final int[] calls={0};List<String> requests=Collections.synchronizedList(new ArrayList<>());inst.runOnMainSync(()->ai.client=new DeepSeekClient(){@Override JSONObject call(String key,JSONObject request)throws Exception{requests.add(request.toString());calls[0]++;if(calls[0]<=3)throw new ServiceException("empty","空内容",true);return AiProtocol.obj("reply","我在，接着聊吧。","actions",new JSONArray());}});onView(withContentDescription("给 AI 发消息")).perform(replaceText("今天有点累"));onView(isRoot()).perform(closeSoftKeyboard());onView(withText("发送")).perform(click());waitForAi();assertEquals(3,calls[0]);assertTrue(context.getSharedPreferences("ai_chat",0).getString("lastDiagnostic","").contains("attempt=3"));assertEquals(1,ai.history.length());assertEquals("failed",ai.history.getJSONObject(0).getString("status"));assertFalse(requests.get(0).contains("response_format"));
        inst.runOnMainSync(()->{ai.readApp=true;ai.from=ai.from.minusDays(2);ai.storeHistory();});onView(withText("重试这条消息")).perform(scrollTo(),click());waitForAi();assertEquals(4,calls[0]);assertEquals(requests.get(0),requests.get(3));assertEquals(2,ai.history.length());screenshot("chat-retry");String old=ai.sessionId;inst.runOnMainSync(()->{ai.storeHistory();ai.chats.create("学习安排",LocalDate.now());ai.loadSession();ai.render();});assertEquals(0,ai.history.length());assertFalse(ai.readApp);assertEquals(0,ChatStore.messages(ai.history,20).length());inst.runOnMainSync(()->ai.switchSession(old));assertEquals(2,ai.history.length());assertTrue(ai.readApp);
        onView(withText("对话")).perform(click());screenshot("chat-sessions-menu");pressBack();
    }
    @Test public void legacyChatMigrationAndHistorySelection()throws Exception{
        context.getSharedPreferences("ai_chat",0).edit().clear().putString("history",new JSONArray().put(AiProtocol.obj("role","user","text","原来的聊天")).toString()).commit();ChatStore store=new ChatStore(context,LocalDate.now());assertEquals("原来的聊天",store.active().getJSONArray("history").getJSONObject(0).getString("text"));JSONArray messages=new JSONArray().put(AiProtocol.obj("role","user","text","不携带","excluded",true)).put(AiProtocol.obj("role","assistant","text","可以携带")).put(AiProtocol.obj("role","user","text","当前消息"));assertEquals(2,ChatStore.messages(messages,20).length());assertEquals(1,ChatStore.messages(messages,0).length());
    }
}
