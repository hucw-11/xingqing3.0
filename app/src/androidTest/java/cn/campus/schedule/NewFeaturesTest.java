package cn.campus.schedule;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.*;
import java.io.*;
import java.time.LocalDate;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onData;
import static org.hamcrest.Matchers.is;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;

@RunWith(AndroidJUnit4.class)
public class NewFeaturesTest {
    final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    final Context context=inst.getTargetContext();
    MainActivity main;
    @Before public void open(){context.getSharedPreferences("MainActivity",0).edit().clear().commit();main=(MainActivity)inst.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));inst.waitForIdleSync();onView(isRoot()).check(matches(isDisplayed()));}
    @After public void close(){if(main!=null)inst.runOnMainSync(main::finish);}
    JSONObject event(String title)throws Exception{return new JSONObject().put("id","repeat").put("date",LocalDate.now().toString()).put("title",title).put("start","14:00").put("end","").put("allDay",false).put("repeat","daily").put("interval",1).put("count",3).put("done",false);}
    @Test public void independentCompletionAndFutureDeletion()throws Exception{
        LocalDate today=LocalDate.now();JSONObject task=event("三天的学习任务");
        inst.runOnMainSync(()->main.features.saveEvent(null,task,today,false,false));
        JSONObject first=AgendaFeatures.occurrence(main.events().optJSONObject(0),today);AgendaFeatures.put(first,"done",true);
        inst.runOnMainSync(()->main.features.saveEvent(main.events().optJSONObject(0),first,today,false,false));
        assertTrue(AgendaFeatures.occurrence(main.events().optJSONObject(0),today).optBoolean("done"));
        assertFalse(AgendaFeatures.occurrence(main.events().optJSONObject(0),today.plusDays(1)).optBoolean("done"));
        JSONObject second=AgendaFeatures.occurrence(main.events().optJSONObject(0),today.plusDays(1));AgendaFeatures.put(second,"title","仅改第二天");
        inst.runOnMainSync(()->main.features.saveEvent(main.events().optJSONObject(0),second,today.plusDays(1),false,false));
        assertEquals("三天的学习任务",AgendaFeatures.occurrence(main.events().optJSONObject(0),today.plusDays(2)).optString("title"));
        inst.runOnMainSync(()->main.features.saveEvent(main.events().optJSONObject(0),null,today.plusDays(1),true,true));
        assertNull(AgendaFeatures.occurrence(main.events().optJSONObject(0),today.plusDays(1)));
        assertTrue(AgendaFeatures.occurrence(main.events().optJSONObject(0),today).optBoolean("done"));
    }
    void screenshot(String name)throws Exception{
        android.os.SystemClock.sleep(400);
        inst.waitForIdleSync();File f=new File(context.getExternalFilesDir(null),name+".png");try(FileOutputStream out=new FileOutputStream(f)){inst.getUiAutomation().takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,out);}
        try(InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.getUiAutomation().executeShellCommand("cp "+f.getAbsolutePath()+" /data/local/tmp/"+name+".png"))){while(in.read()!=-1){}}
    }
    MoodActivity journal(){return (MoodActivity)inst.startActivitySync(new Intent(context,MoodActivity.class).putExtra("date",LocalDate.now().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    @Test public void createRepeatingActivityWithTwoLabels()throws Exception{
        inst.runOnMainSync(()->main.features.eventForm(null));
        onView(withContentDescription("日程标题")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(replaceText("每日学习与锻炼"));onView(isRoot()).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(closeSoftKeyboard());
        onView(withText("选择活动标签")).perform(scrollTo(),click());onView(withText("学习")).perform(click());onView(withText("锻炼")).perform(click());onView(withText("确定")).perform(click());
        onView(withContentDescription("重复方式")).perform(scrollTo(),click());onData(is("每天／每隔几天")).inRoot(androidx.test.espresso.matcher.RootMatchers.isPlatformPopup()).perform(click());
        onView(withContentDescription("重复结束方式")).perform(scrollTo(),click());onData(is("共执行若干次")).inRoot(androidx.test.espresso.matcher.RootMatchers.isPlatformPopup()).perform(click());
        onView(withContentDescription("执行次数（不是天数）")).perform(scrollTo(),replaceText("3"));onView(isRoot()).perform(closeSoftKeyboard());screenshot("repeat-editor");onView(withText("保存")).perform(click());
        JSONObject task=main.events().getJSONObject(0);assertEquals(2,task.getJSONArray("tags").length());assertTrue(AgendaFeatures.occurs(task,LocalDate.now().plusDays(2)));assertFalse(AgendaFeatures.occurs(task,LocalDate.now().plusDays(3)));
    }
    @Test public void longDraftAndPhotoSurviveOriginalDeletion()throws Exception{
        MoodActivity first=journal();StringBuilder longText=new StringBuilder();for(int i=0;i<80;i++)longText.append("今天的学习和生活值得认真记录。这是一篇可以持续写下去的长日记。\n");String content=longText.toString();
        onView(withContentDescription("标题（可选）")).perform(replaceText("平凡而充实的一天"));
        onView(withContentDescription("写下此刻的想法，长篇日记也可以……")).perform(replaceText(content));onView(isRoot()).perform(closeSoftKeyboard());inst.runOnMainSync(first::finish);inst.waitForIdleSync();
        MoodActivity reopened=journal();assertEquals(content,reopened.body.getText().toString());
        File original=new File(context.getCacheDir(),"source-photo.png");Bitmap image=Bitmap.createBitmap(400,300,Bitmap.Config.ARGB_8888);image.eraseColor(0xffc4dcfc);try(FileOutputStream out=new FileOutputStream(original)){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
        inst.runOnMainSync(()->reopened.onActivityResult(72,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(original))));
        boolean[] busy={true};for(int i=0;i<100;i++){inst.runOnMainSync(()->busy[0]=reopened.importing);if(!busy[0])break;android.os.SystemClock.sleep(100);}assertFalse(busy[0]);assertEquals(1,reopened.images.size());assertTrue(original.delete());assertTrue(new File(context.getFilesDir(),"photos/"+reopened.images.get(0)).isFile());
        inst.runOnMainSync(()->reopened.body.setText("记录此刻的心情与活力，也记录今天认真生活的自己。"));screenshot("mood-editor");
        inst.runOnMainSync(()->reopened.body.setText(content));onView(withText("保存")).perform(click());inst.waitForIdleSync();
        JSONObject saved=new JSONObject(context.getSharedPreferences("MainActivity",0).getString("data","{}"));assertEquals(content,saved.getJSONArray("moods").getJSONObject(0).getString("body"));assertEquals(1,saved.getJSONArray("moods").getJSONObject(0).getJSONArray("photos").length());
        inst.runOnMainSync(()->{main.features.filter=2;main.render();});screenshot("mood-timeline");
    }
}
