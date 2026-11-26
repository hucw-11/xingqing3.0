package cn.campus.schedule;

import android.app.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.json.*;

/** The daily timeline and the ongoing task list are two views over the same records. */
final class AgendaView {
    final MainActivity a;final AgendaFeatures f;
    AgendaView(MainActivity a){this.a=a;f=a.features;}
    void draw(){
        if(!a.selected.equals(f.foldDate)){f.foldDate=a.selected;f.continuousExpanded=false;f.completedExpanded=false;}
        f.controls();a.space(a.body,8);String prefix=a.selected.equals(LocalDate.now())?"今天 · ":"";
        a.body.addView(a.text(prefix+a.selected.getMonthValue()+"月"+a.selected.getDayOfMonth()+"日 周"+a.DAYS[a.selected.getDayOfWeek().getValue()-1],20,a.INK,true));a.space(a.body,8);
        List<MainActivity.Entry> list=a.entries(),ongoing=new ArrayList<>(),finished=new ArrayList<>(),daily=new ArrayList<>();
        for(MainActivity.Entry e:list){if(e.continuous)ongoing.add(e);else if(e.done)finished.add(e);else daily.add(e);}
        a.body.addView(a.text(daily.size()+" 个今日安排 · "+finished.size()+" 项已完成／已结束",13,a.MUTED,false));a.space(a.body,10);
        if(a.selected.equals(LocalDate.now())&&f.filter!=2&&(ongoing.size()>0||eligible().size()>0))ongoing(ongoing);
        if(daily.isEmpty()&&ongoing.isEmpty()&&finished.isEmpty()){a.space(a.body,40);TextView empty=a.text("这一天，留一点空白\n\n点击 ＋ 添加安排",18,a.MUTED,false);empty.setGravity(Gravity.CENTER);a.body.addView(empty);if(!a.data.optBoolean("configured"))a.body.addView(a.action("设置学期，让课程自动出现",a::settings));}
        for(int group=0;group<4;group++){List<MainActivity.Entry> items=new ArrayList<>();for(MainActivity.Entry e:daily)if(DaySections.group(e.allDay,e.minute)==group)items.add(e);if(items.isEmpty())continue;a.space(a.body,12);a.body.addView(a.text("◷  "+new String[]{"全天","上午","下午","晚上"}[group]+"  "+items.size(),15,a.MUTED,false));a.space(a.body,12);for(MainActivity.Entry e:items)card(e);}
        if(!finished.isEmpty()){a.space(a.body,18);TextView toggle=a.action((f.completedExpanded?"▾":"▸")+" 已完成／已结束 · "+finished.size()+" 项",()->{f.completedExpanded=!f.completedExpanded;a.render();});toggle.setTextSize(15);toggle.setTextColor(a.MUTED);toggle.setContentDescription("展开或收起已完成安排");a.body.addView(toggle);if(f.completedExpanded)for(MainActivity.Entry e:finished)card(e);}
        a.agendaClockKey=clockKey(a);
    }
    void ongoing(List<MainActivity.Entry> list){
        list.sort(Comparator.comparing(e->e.source.optString("deadline").isEmpty()?"9999-12-31":e.source.optString("deadline")));
        int urgent=0;for(MainActivity.Entry e:list)if(!e.source.optString("deadline").isEmpty()&&ChronoUnit.DAYS.between(LocalDate.now(),LocalDate.parse(e.source.optString("deadline")))<=7)urgent++;
        LinearLayout row=a.row();TextView toggle=a.action((f.continuousExpanded?"▾":"▸")+" 持续任务 · "+list.size()+" 项"+(urgent>0?" · "+urgent+" 项临近 DDL":""),()->{f.continuousExpanded=!f.continuousExpanded;a.render();});toggle.setTextSize(14);toggle.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);toggle.setContentDescription("展开或收起持续任务");row.addView(toggle,new LinearLayout.LayoutParams(0,-2,1));TextView manage=a.action("整理",this::convert);manage.setTextSize(13);row.addView(manage);a.body.addView(row);
        if(f.continuousExpanded)for(MainActivity.Entry e:list){LinearLayout card=a.column();card.setPadding(a.dp(12),a.dp(12),a.dp(12),a.dp(12));card.setBackground(a.shape(Color.WHITE,16));card.addView(a.text(e.title,17,a.INK,true));JSONArray children=TaskRules.children(e.source);card.addView(a.text((children.length()==0?"进行中":"完成 "+TaskRules.completed(children)+"/"+children.length())+(e.source.optString("deadline").isEmpty()?"":" · "+ReminderRules.deadlineText(e.source,LocalDate.now())),12,a.MUTED,false));card.setOnClickListener(v->f.projectDetails(e.source));card.setContentDescription("持续任务详情："+e.title);a.body.addView(card);a.space(a.body,6);}
    }
    List<JSONObject> eligible(){List<JSONObject> list=new ArrayList<>();for(int i=0;i<a.events().length();i++){JSONObject event=a.events().optJSONObject(i);if(!event.optBoolean("done")&&!event.optBoolean("continuous")&&event.optString("repeat","none").equals("none")&&(event.optBoolean("rollover")||TaskRules.children(event).length()>0))list.add(event);}return list;}
    void convert(){List<JSONObject> list=eligible();if(list.isEmpty()){a.toast("可以在活动编辑页开启“持续任务”");return;}String[] names=new String[list.size()];boolean[] chosen=new boolean[list.size()];for(int i=0;i<names.length;i++)names[i]=list.get(i).optString("title");new AlertDialog.Builder(a).setTitle("移入持续任务").setMultiChoiceItems(names,chosen,(d,w,on)->chosen[w]=on).setMessage(null).setNegativeButton("取消",null).setPositiveButton("移入",(d,w)->{JSONObject next=a.copy();Set<String> ids=new HashSet<>();for(int i=0;i<chosen.length;i++)if(chosen[i])ids.add(list.get(i).optString("id"));JSONArray events=next.optJSONArray("events");for(int i=0;i<events.length();i++)if(ids.contains(events.optJSONObject(i).optString("id"))){JSONObject e=events.optJSONObject(i);AgendaFeatures.put(e,"continuous",true);AgendaFeatures.put(e,"rollover",true);AgendaFeatures.put(e,"allDay",true);}if(a.persist(next)){a.render();a.toast("已保留原有 DDL 和子任务进度");}}).show();}
    void card(MainActivity.Entry e){
        if(e.mood){f.moodCard(e);return;}
        LinearLayout line=a.row();line.setGravity(Gravity.TOP);LinearLayout rail=a.new TimelineRail();rail.setPadding(0,a.dp(14),a.dp(7),0);rail.addView(a.text(e.allDay?"":e.time.substring(0,5),15,e.done?a.MUTED:a.INK,true));line.addView(rail,new LinearLayout.LayoutParams(a.dp(66),-1));
        LinearLayout card=a.column();card.setPadding(a.dp(17),a.dp(18),a.dp(17),a.dp(18));card.setBackground(a.shape(e.done?0xffebedf3:Color.WHITE,24));
        LinearLayout top=a.row();TextView name=a.text(e.title,19,e.done?a.MUTED:a.INK,true);if(e.done&&!e.course)name.setPaintFlags(name.getPaintFlags()|android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);top.addView(name,new LinearLayout.LayoutParams(0,-2,1));String badge=e.progress?"当天进展":e.course?(e.done?"已结束":"课程"):e.done?"已完成":!e.childId.isEmpty()?"今天做":"待办";TextView tag=a.text(badge,12,e.done?a.MUTED:a.BLUE,false);top.addView(tag);card.addView(top);a.space(card,7);card.addView(a.text((e.allDay?"全天":e.time)+(e.note.isEmpty()?"":" · "+e.note),13,a.MUTED,false));
        if(e.progress){TextView preview=(TextView)card.getChildAt(card.getChildCount()-1);preview.setMaxLines(2);preview.setEllipsize(android.text.TextUtils.TruncateAt.END);card.setOnClickListener(v->new AlertDialog.Builder(a).setTitle(e.title+" · 当天进展").setMessage(e.note).setPositiveButton("关闭",null).show());}
        else if(!e.childId.isEmpty()){
            CheckBox done=new CheckBox(a);done.setText(e.done?"已完成":"完成这个子任务");done.setChecked(e.done);done.setContentDescription("完成今日子任务："+e.title);done.setOnCheckedChangeListener((v,on)->f.childDone(e.source,e.childId,on));card.addView(done);if(!e.done){TextView remove=a.action("移出今天",()->f.focusChild(e.source,e.childId,false));remove.setTextSize(13);card.addView(remove);}card.setOnClickListener(v->f.projectDetails(e.source));
        }else{f.paintTags(card,e.source,e.course);if(!e.course)f.taskCard(card,e.source);card.setOnClickListener(v->{if(e.course)new AlertDialog.Builder(a).setTitle(e.title).setItems(new String[]{"编辑课程","记录感受"},(d,w)->{if(w==0)a.courseForm(e.source,e.source.optInt("day"),e.source.optInt("from"));else f.mood(null,e,true);}).show();else a.eventForm(e.source);});}
        line.addView(card,new LinearLayout.LayoutParams(0,-2,1));a.body.addView(line);a.space(a.body,12);
    }
    static String clockKey(MainActivity a){if(a.data==null)return "";StringBuilder key=new StringBuilder(LocalDate.now().toString());if(!a.timetable)for(MainActivity.Entry e:AgendaItems.build(a.data,a.selected,LocalDateTime.now()))if(e.course)key.append(e.source.optString("id")).append(e.done);return key.toString();}
    static void tick(MainActivity a){if(a instanceof AiActivity||a.features==null||a.timetable||a.agendaClockKey.isEmpty())return;String key=clockKey(a);if(key.equals(a.agendaClockKey))return;if(!a.getWindow().getDecorView().hasWindowFocus())return;int y=a.pageScroll==null?0:a.pageScroll.getScrollY();a.render();a.pageScroll.post(()->a.pageScroll.scrollTo(0,y));}
}
