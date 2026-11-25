package cn.campus.schedule;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.*;
import java.time.LocalDate;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;

@RunWith(AndroidJUnit4.class)
public class AppFlowTest {
    private MainActivity launch(Instrumentation inst,Context context) {
        return (MainActivity)inst.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    private void screenshot(Instrumentation inst,Context context,String name) throws Exception {
        inst.waitForIdleSync();
        Bitmap image=inst.getUiAutomation().takeScreenshot();
        try(FileOutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}
    }
    @Test public void courseAgendaEditAndRestart() throws Exception {
        Instrumentation inst=InstrumentationRegistry.getInstrumentation();Context context=inst.getTargetContext();LocalDate today=LocalDate.now();
        JSONObject data=new JSONObject();data.put("configured",true);data.put("semester",ScheduleRules.monday(today).toString());data.put("totalWeeks",20);
        data.put("starts",new JSONArray(Arrays.asList("08:00","08:50","09:50","10:40","11:30","14:00","14:50","15:45","16:35","19:00","19:50","20:40")));
        data.put("ends",new JSONArray(Arrays.asList("08:45","09:35","10:35","11:25","12:15","14:45","15:35","16:30","17:20","19:45","20:35","21:25")));
        data.put("courses",new JSONArray().put(new JSONObject().put("id","course").put("title","现代导航技术").put("room","教学楼 A301").put("day",today.getDayOfWeek().getValue()).put("from",3).put("to",4).put("weeks","1-16")));
        data.put("events",new JSONArray()
            .put(new JSONObject().put("id","all").put("title","完成课程作业").put("date",today.toString()).put("allDay",true).put("start","14:30").put("end","").put("note","记得提交报告").put("done",true))
            .put(new JSONObject().put("id","meeting").put("title","实验室开会").put("date",today.toString()).put("allDay",false).put("start","14:30").put("end","").put("note","实验楼 402").put("done",false)));
        context.getSharedPreferences("MainActivity",Context.MODE_PRIVATE).edit().putString("data",data.toString()).commit();
        MainActivity first=launch(inst,context);
        onView(withText("现代导航技术")).check(matches(isDisplayed()));
        onView(withText("09:50–11:25 · 教学楼 A301")).check(matches(isDisplayed()));
        screenshot(inst,context,"agenda");
        onView(withText("实验室开会")).perform(scrollTo(),click());
        // The form has multiple EditTexts; use the title's current text to select the correct field.
        onView(withText("实验室开会")).perform(replaceText("项目讨论会"),closeSoftKeyboard());
        onView(withText("保存")).perform(click());
        onView(withText("项目讨论会")).perform(scrollTo()).check(matches(isDisplayed()));
        inst.runOnMainSync(first::finish);inst.waitForIdleSync();MainActivity second=launch(inst,context);
        onView(withText("项目讨论会")).perform(scrollTo()).check(matches(isDisplayed()));
        onView(withText("▥  课表")).perform(click());
        onView(withText("周三")).check(matches(isDisplayed()));
        screenshot(inst,context,"timetable");
        inst.runOnMainSync(second::finish);
    }
}
