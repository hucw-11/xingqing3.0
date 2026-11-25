package cn.campus.schedule;
import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.*;
import java.time.LocalDate;
import java.io.*;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;

@RunWith(AndroidJUnit4.class)
public class Version12Test {
    final Instrumentation inst=InstrumentationRegistry.getInstrumentation();final Context context=inst.getTargetContext();MainActivity main;
    @Before public void open(){context.getSharedPreferences("MainActivity",0).edit().clear().commit();context.getSharedPreferences("ai_chat",0).edit().clear().commit();main=(MainActivity)inst.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    @After public void close(){if(main!=null)inst.runOnMainSync(main::finish);}
    void screenshot(String name)throws Exception{inst.waitForIdleSync();android.os.SystemClock.sleep(400);File f=new File(context.getExternalFilesDir(null),name+".png");try(FileOutputStream out=new FileOutputStream(f)){inst.getUiAutomation().takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,out);}try(InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.getUiAutomation().executeShellCommand("cp "+f.getAbsolutePath()+" /data/local/tmp/"+name+".png"))){while(in.read()!=-1){}}}
    @Test public void multiDayCourseAndTimetableRoundtrip()throws Exception{
        inst.runOnMainSync(()->{JSONObject data=main.copy();AgendaFeatures.put(data,"configured",true);main.persist(data);main.courseForm(null,2,3);});
        onView(withContentDescription("课程名称")).perform(replaceText("现代导航技术"));onView(withContentDescription("结束节次（1–12）")).perform(replaceText("4"));onView(isRoot()).perform(closeSoftKeyboard());onView(withContentDescription("星期四")).perform(click());screenshot("multi-day-course");onView(withText("保存")).perform(click());
        JSONObject course=main.courses().getJSONObject(0);assertEquals(2,CourseRules.days(course).size());LocalDate mon=ScheduleRules.monday(LocalDate.now());assertTrue(CourseRules.occurs(main.semester(),mon.plusDays(1),course,20));assertTrue(CourseRules.occurs(main.semester(),mon.plusDays(3),course,20));assertFalse(CourseRules.occurs(main.semester(),mon.plusDays(2),course,20));
        JSONObject file=TimetableTransfer.validate(TimetableTransfer.read(new ByteArrayInputStream(TimetableTransfer.export(main.data).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))));JSONObject merged=TimetableTransfer.merge(main.data,file,false);assertEquals(1,merged.getJSONArray("courses").length());assertEquals(main.events().toString(),merged.getJSONArray("events").toString());assertFalse(file.has("moods"));assertFalse(file.has("events"));
        AgendaFeatures.put(file,"totalWeeks",19);try{TimetableTransfer.merge(main.data,file,false);fail("different semester must not silently merge");}catch(IllegalArgumentException expected){}
    }
    @Test public void rolloverKeepsOccurrencesAndChildrenSeparate()throws Exception{
        LocalDate today=LocalDate.now(),first=today.minusDays(2);JSONObject task=AiProtocol.obj("id","roll","title","实验报告","date",first.toString(),"start","14:00","end","","repeat","daily","count",3,"interval",1,"rollover",true,"children",new JSONArray().put(AiProtocol.obj("id","a","title","整理数据","done",false)).put(AiProtocol.obj("id","b","title","撰写结论","done",false)));
        inst.runOnMainSync(()->main.features.saveEvent(null,task,first,false,false));JSONObject carried=TaskRules.visible(main.events().getJSONObject(0),today,today).get(0);JSONArray children=TaskRules.children(carried);AgendaFeatures.put(children.optJSONObject(0),"done",true);AgendaFeatures.put(carried,"children",children);inst.runOnMainSync(()->main.features.saveSingle(carried));
        java.util.List<JSONObject> list=TaskRules.visible(main.events().getJSONObject(0),today,today);assertEquals(3,list.size());assertEquals(1,TaskRules.completed(TaskRules.children(list.get(0))));assertEquals(0,TaskRules.completed(TaskRules.children(list.get(1))));assertEquals(0,TaskRules.completed(TaskRules.children(list.get(2))));
        inst.runOnMainSync(()->{main.selected=today;main.render();});screenshot("rollover-subtasks");JSONObject done=AgendaFeatures.cloneJson(list.get(0));AgendaFeatures.put(done,"done",true);inst.runOnMainSync(()->main.features.saveSingle(done));assertEquals(2,TaskRules.visible(main.events().getJSONObject(0),today.plusDays(1),today.plusDays(1)).size());
    }
    @Test public void aiRequiresConfirmationAndUsesScopedText()throws Exception{
        SecretStore.save(context,"test-key-for-offline-test");assertFalse(context.getSharedPreferences("ai_credentials",0).getString("key","").contains("test-key"));
        AiActivity ai=(AiActivity)inst.startActivitySync(new Intent(context,AiActivity.class).putExtra("date",LocalDate.now().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        final JSONObject[] captured={null};inst.runOnMainSync(()->ai.client=new DeepSeekClient(){@Override JSONObject call(String key,JSONObject request){captured[0]=request;return AiProtocol.obj("reply","我拟了一份学习安排，你可以检查后保存。","actions",new JSONArray().put(AiProtocol.obj("kind","event","title","整理笔记","date",LocalDate.now().toString(),"start","14:00","end","15:00","repeat","daily","count",3,"interval",1,"rollover",true,"tags",new JSONArray().put("学习"),"children",new JSONArray().put("整理知识点").put("完成练习"))));}});
        onView(withContentDescription("给 AI 发消息")).perform(replaceText("安排连续三天下午两点整理笔记，并拆成子任务"));onView(isRoot()).perform(closeSoftKeyboard());onView(withText("发送")).perform(click());boolean[] busy={true};for(int i=0;i<100;i++){inst.runOnMainSync(()->busy[0]=ai.busy);if(!busy[0])break;android.os.SystemClock.sleep(100);}assertFalse(busy[0]);assertNotNull(captured[0]);assertFalse(captured[0].toString().contains("test-key-for-offline-test"));assertEquals(0,ai.events().length());screenshot("ai-confirmation-card");
        onView(withText("编辑并保存")).perform(scrollTo(),click());onView(withText("保存")).perform(click());assertEquals(1,ai.events().length());assertEquals(2,ai.events().getJSONObject(0).getJSONArray("children").length());assertTrue(ai.events().getJSONObject(0).getBoolean("rollover"));assertEquals(3,ai.events().getJSONObject(0).getInt("count"));
        inst.runOnMainSync(()->ai.editProposal(ai.history.optJSONObject(ai.history.length()-1).optJSONArray("actions").optJSONObject(0)));assertEquals(1,ai.events().length());inst.runOnMainSync(ai::finish);SecretStore.save(context,"");
    }
    @Test public void invalidAiActionCannotWriteAnything()throws Exception{
        try{AiProtocol.action(AiProtocol.obj("kind","delete","date",LocalDate.now().toString()));fail();}catch(IllegalArgumentException expected){}
        try{AiProtocol.action(AiProtocol.obj("kind","event","date",LocalDate.now().toString(),"title","未给时间"));fail();}catch(IllegalArgumentException expected){}
        assertEquals(0,main.events().length());
    }
}
