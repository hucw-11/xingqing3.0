package cn.campus.schedule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.*;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class DayPlanningTest {
    @Test public void sectionsShareChildProgressAndParentLink()throws Exception{
        LocalDate today=LocalDate.now();JSONObject task=AiProtocol.obj("id","p","title","母任务","date",today.toString(),"continuous",true,"rollover",true,"allDay",true,"children",new JSONArray().put(AiProtocol.obj("id","c","title","阅读","focusDate",today.toString())));JSONObject data=AiProtocol.obj("events",new JSONArray().put(task),"courses",new JSONArray(),"moods",new JSONArray());MainActivity.Entry child=AgendaItems.build(data,today,LocalDateTime.now()).stream().filter(e->!e.childId.isEmpty()).findFirst().get();DayPlanning.put(data,child,today,AiProtocol.obj("section","afternoon"));child=AgendaItems.build(data,today,LocalDateTime.now()).stream().filter(e->!e.childId.isEmpty()).findFirst().get();assertEquals("下午",child.time);assertEquals(720,child.minute);assertFalse(child.allDay);assertEquals("p",child.source.optString("id"));DayPlanning.put(data,child,today,AiProtocol.obj("section","time","time","19:45","order",0));child=AgendaItems.build(data,today,LocalDateTime.now()).stream().filter(e->!e.childId.isEmpty()).findFirst().get();assertEquals(1185,child.minute);assertEquals(1,data.getJSONArray("events").length());
    }
    @Test public void dailyOrderAndMotherPlanSurviveJsonRoundTrip()throws Exception{LocalDate today=LocalDate.now();JSONObject data=AiProtocol.obj("events",new JSONArray().put(AiProtocol.obj("id","p","title","长期任务","date",today.toString(),"continuous",true,"rollover",true,"allDay",true)),"courses",new JSONArray(),"moods",new JSONArray());MainActivity.Entry parent=AgendaItems.build(data,today,LocalDateTime.now()).get(0);DayPlanning.put(data,parent,today,AiProtocol.obj("section","evening","order",1024));List<MainActivity.Entry> rows=AgendaItems.build(new JSONObject(data.toString()),today,LocalDateTime.now());assertEquals(2,rows.size());assertTrue(rows.stream().anyMatch(e->!e.continuous&&e.minute==1080));}
}
