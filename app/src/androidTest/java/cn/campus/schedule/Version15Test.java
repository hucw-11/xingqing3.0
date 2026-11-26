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
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;

@RunWith(AndroidJUnit4.class)
public class Version15Test {
    final Instrumentation inst=InstrumentationRegistry.getInstrumentation();final Context context=inst.getTargetContext();MainActivity main;
    final LocalDate today=LocalDate.now();
    @Before public void open(){context.getSharedPreferences("MainActivity",0).edit().clear().commit();context.getSharedPreferences("cloud_sync",0).edit().clear().commit();context.getSharedPreferences("reminders",0).edit().putBoolean("summary",false).putBoolean("courses",false).commit();main=(MainActivity)inst.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));inst.waitForIdleSync();onView(isRoot()).check(matches(isDisplayed()));}
    @After public void close(){inst.runOnMainSync(()->{if(main.features.projectDialog!=null)main.features.projectDialog.dismiss();main.finish();});}
    JSONObject project(String id){return AiProtocol.obj("id",id,"title",id,"date",today.minusDays(3).toString(),"start","14:00","end","","allDay",true,"repeat","none","rollover",true,"continuous",true,"done",false,"deadline",today.plusDays(4).toString(),"children",new JSONArray().put(AiProtocol.obj("id","a","title","整理资料","done",false)).put(AiProtocol.obj("id","b","title","完成报告","done",false)));}
    void set(JSONArray tasks){inst.runOnMainSync(()->{JSONObject data=main.copy();AgendaFeatures.put(data,"events",tasks);main.persist(data);main.render();});}
    JSONObject active(String id){for(JSONObject task:TaskRules.visible(main.events().optJSONObject(0),today,today))if(task.optString("id").equals(id))return task;throw new AssertionError();}
    void screenshot(String name)throws Exception{inst.waitForIdleSync();android.os.SystemClock.sleep(250);File file=new File(context.getExternalFilesDir(null),name+".png");try(FileOutputStream out=new FileOutputStream(file)){inst.getUiAutomation().takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,out);}try(InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.getUiAutomation().executeShellCommand("cp "+file.getAbsolutePath()+" /data/local/tmp/"+name+".png"))){while(in.read()!=-1){}}}
    @Test public void finishedCoursesAndTodosAreLastButOverdueTodosStayPending()throws Exception{
        JSONObject data=main.copy();AgendaFeatures.put(data,"configured",true);AgendaFeatures.put(data,"semester",ScheduleRules.monday(today).toString());AgendaFeatures.put(data,"courses",new JSONArray().put(AiProtocol.obj("id","course","title","现代导航技术","room","A301","days",new JSONArray().put(today.getDayOfWeek().getValue()),"from",3,"to",4,"weeks","1-16")));JSONObject done=project("done"),pending=project("pending");AgendaFeatures.put(done,"continuous",false);AgendaFeatures.put(done,"done",true);AgendaFeatures.put(done,"completedDate",today.toString());AgendaFeatures.put(pending,"continuous",false);AgendaFeatures.put(pending,"date",today.toString());AgendaFeatures.put(pending,"allDay",false);AgendaFeatures.put(pending,"start","01:00");AgendaFeatures.put(data,"events",new JSONArray().put(done).put(pending));
        List<MainActivity.Entry> before=AgendaItems.build(data,today,today.atTime(11,24));assertFalse(before.stream().filter(e->e.course).findFirst().get().done);List<MainActivity.Entry> after=AgendaItems.build(data,today,today.atTime(11,25));assertTrue(after.stream().filter(e->e.course).findFirst().get().done);assertFalse(after.stream().filter(e->e.title.equals("pending")).findFirst().get().done);boolean encountered=false;for(MainActivity.Entry e:after){if(e.done)encountered=true;else assertFalse(encountered);}
        inst.runOnMainSync(()->{main.persist(data);main.render();});onView(withText("done")).check(doesNotExist());onView(withContentDescription("展开或收起已完成安排")).perform(scrollTo(),click());onView(withText("done")).perform(scrollTo()).check(matches(isDisplayed()));screenshot("completed-at-bottom");
    }
    @Test public void tenOngoingTasksAreCollapsedAndTodayReferencesTheOriginalChild()throws Exception{
        JSONArray tasks=new JSONArray();for(int i=0;i<10;i++)tasks.put(project("大任务 "+i));set(tasks);onView(withText("大任务 0")).check(doesNotExist());screenshot("daily-focus");onView(withContentDescription("展开或收起持续任务")).perform(click());onView(withContentDescription("持续任务详情：大任务 0")).perform(scrollTo(),click());onView(withContentDescription("加入今天：整理资料")).inRoot(isDialog()).perform(scrollTo(),click());screenshot("continuous-task-detail");pressBack();
        assertEquals(10,main.events().length());assertEquals(1,main.entries().stream().filter(e->e.childId.equals("a")).count());onView(withContentDescription("完成今日子任务：整理资料")).perform(scrollTo(),click());assertTrue(main.events().getJSONObject(0).getJSONArray("children").getJSONObject(0).getBoolean("done"));assertEquals(1,main.entries().stream().filter(e->e.childId.equals("a")&&e.done).count());assertEquals(10,main.events().length());
    }
    @Test public void historicalPagesContainProgressNotRepeatedOpenCards()throws Exception{
        JSONObject original=project("持续任务"),changed=AgendaFeatures.cloneJson(original);changed.getJSONArray("children").getJSONObject(0).put("done",true);TaskProgress.record(original,changed,today.minusDays(1).atTime(15,0));assertTrue(TaskProgress.forDay(changed,today.minusDays(2),today).isEmpty());List<JSONObject> yesterday=TaskProgress.forDay(changed,today.minusDays(1),today);assertEquals(1,yesterday.size());assertTrue(yesterday.get(0).has("progressSummary"));assertTrue(yesterday.get(0).getString("progressSummary").contains("整理资料"));JSONObject undone=AgendaFeatures.cloneJson(changed);undone.getJSONArray("children").getJSONObject(0).put("done",false);TaskProgress.record(changed,undone,today.atTime(9,0));assertTrue(TaskProgress.forDay(undone,today.minusDays(1),today).get(0).getString("progressSummary").contains("完成：整理资料"));set(new JSONArray().put(undone));inst.runOnMainSync(()->{main.selected=today.minusDays(1);main.render();});screenshot("past-progress");
    }
    @Test public void todaySelectionCarriesUntilDoneOrRemoved()throws Exception{
        JSONObject task=project("项目");JSONObject child=task.getJSONArray("children").getJSONObject(0);child.put("focusDate",today.minusDays(1).toString());assertTrue(TaskProgress.focused(child,today));child.put("done",true);child.put("completedDate",today.minusDays(1).toString());assertFalse(TaskProgress.focused(child,today));child.put("completedDate",today.toString());assertTrue(TaskProgress.focused(child,today));child.put("focusDate","");assertFalse(TaskProgress.focused(child,today));
    }
    @Test public void completingParentPlacesItOnlyOnActualCompletionDay()throws Exception{
        JSONObject task=project("跨天任务"),done=AgendaFeatures.cloneJson(task);AgendaFeatures.put(done,"done",true);TaskProgress.record(task,done,today.atTime(14,0));assertTrue(TaskProgress.forDay(done,today.minusDays(3),today).isEmpty());assertEquals(1,TaskProgress.forDay(done,today,today).size());assertTrue(TaskProgress.forDay(done,today.plusDays(1),today.plusDays(1)).isEmpty());
    }
    @Test public void legacyTasksCanBeConvertedWithoutResettingChildrenOrDDL()throws Exception{
        JSONObject old=project("旧的长期任务");old.put("continuous",false);old.getJSONArray("children").getJSONObject(0).put("done",true);set(new JSONArray().put(old));onView(withText("整理")).perform(click());onView(withText("旧的长期任务")).inRoot(isDialog()).perform(click());onView(withText("移入")).inRoot(isDialog()).perform(click());JSONObject updated=main.events().getJSONObject(0);assertTrue(updated.getBoolean("continuous"));assertTrue(updated.getJSONArray("children").getJSONObject(0).getBoolean("done"));assertEquals(old.getString("deadline"),updated.getString("deadline"));assertEquals(old.getString("id"),updated.getString("id"));
    }
    @Test public void editorCanCreateAContinuousTask()throws Exception{
        inst.runOnMainSync(()->main.features.eventForm(null));onView(withContentDescription("日程标题")).inRoot(isDialog()).perform(replaceText("这一周完成实验报告"));onView(isRoot()).inRoot(isDialog()).perform(closeSoftKeyboard());onView(withContentDescription("持续任务")).inRoot(isDialog()).perform(scrollTo(),click());onView(withText("保存")).inRoot(isDialog()).perform(click());JSONObject task=main.events().getJSONObject(0);assertTrue(task.getBoolean("continuous"));assertTrue(task.getBoolean("rollover"));assertTrue(task.getBoolean("allDay"));assertEquals("none",task.getString("repeat"));
    }
    @Test public void nextRepeatedOccurrenceDoesNotInheritDailyFocusOrOldLogs()throws Exception{
        JSONObject task=project("每日练习");task.put("continuous",false);task.put("repeat","daily");task.put("count",4);task.getJSONArray("children").getJSONObject(0).put("focusDate",today.minusDays(3).toString());task.put("progressLog",AiProtocol.obj("log",AiProtocol.obj("date",today.minusDays(3).toString(),"time","10:00","title","旧日期独有的私密文字","done",true)));JSONObject next=TaskRules.scheduled(task,today.minusDays(2)).get(1);assertFalse(next.has("progressLog"));assertFalse(next.getJSONArray("children").getJSONObject(0).has("focusDate"));JSONObject data=main.copy();data.put("events",new JSONArray().put(task));assertFalse(AiProtocol.context(data,today,today).toString().contains("旧日期独有的私密文字"));
    }
}
