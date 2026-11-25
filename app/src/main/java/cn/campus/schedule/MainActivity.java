package cn.campus.schedule;

import android.app.*;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainActivity extends Activity {
    private final int BG=0xfff3f4f8, BLUE=0xff0868ce, INK=0xff171c26, MUTED=0xff858a96;
    private final String[] DAYS={"一","二","三","四","五","六","日"};
    private final String[] DEFAULT_START={"08:00","08:50","09:50","10:40","11:30","14:00","14:50","15:45","16:35","19:00","19:50","20:40"};
    private final String[] DEFAULT_END={"08:45","09:35","10:35","11:25","12:15","14:45","15:35","16:30","17:20","19:45","20:35","21:25"};
    private JSONObject data;
    private LocalDate selected=LocalDate.now();
    private boolean timetable=false;
    private LinearLayout root,body;
    private int dp(float n){ return (int)(n*getResources().getDisplayMetrics().density+.5f); }
    private JSONObject obj(Object... pairs) {
        JSONObject o=new JSONObject();
        try { for(int i=0;i<pairs.length;i+=2)o.put((String)pairs[i],pairs[i+1]); } catch(JSONException e){throw new IllegalStateException(e);}
        return o;
    }
    private JSONArray courses(){return data.optJSONArray("courses");}
    private JSONArray events(){return data.optJSONArray("events");}
    private LocalDate semester(){return LocalDate.parse(data.optString("semester"));}
    private int totalWeeks(){return data.optInt("totalWeeks",20);}
    private String start(int n){return data.optJSONArray("starts").optString(n-1);}
    private String end(int n){return data.optJSONArray("ends").optString(n-1);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        String saved=getPreferences(MODE_PRIVATE).getString("data",null);
        try { data=saved==null?obj("semester",ScheduleRules.monday(LocalDate.now()).toString(),"configured",false,"totalWeeks",20,"starts",new JSONArray(Arrays.asList(DEFAULT_START)),"ends",new JSONArray(Arrays.asList(DEFAULT_END)),"courses",new JSONArray(),"events",new JSONArray()):new JSONObject(saved); }
        catch(JSONException e){ new AlertDialog.Builder(this).setTitle("数据读取失败").setMessage("原始数据已保留，请重新启动或联系开发者。").setPositiveButton("关闭",(d,w)->finish()).setCancelable(false).show(); return; }
        if(state!=null){selected=LocalDate.parse(state.getString("selected",selected.toString()));timetable=state.getBoolean("timetable");}
        render();
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("selected",selected.toString());out.putBoolean("timetable",timetable);}
    private boolean persist(JSONObject next){
        if(!getPreferences(MODE_PRIVATE).edit().putString("data",next.toString()).commit()){toast("保存失败，请重试");return false;}
        data=next;return true;
    }
    private JSONObject copy(){try{return new JSONObject(data.toString());}catch(JSONException e){throw new IllegalStateException(e);}}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    private TextView action(String s,Runnable run){TextView t=text(s,21,BLUE,false);t.setGravity(Gravity.CENTER);t.setMinWidth(dp(44));t.setMinHeight(dp(44));t.setOnClickListener(v->run.run());return t;}
    private void space(LinearLayout l,int h){l.addView(new android.view.View(this),new LinearLayout.LayoutParams(1,dp(h)));}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void render(){
        root=column();root.setBackgroundColor(BG);root.setPadding(dp(20),dp(12),dp(20),dp(12));setContentView(root);
        LinearLayout header=row();TextView title=text(timetable?"课表":"日程",30,INK,true);header.addView(title,new LinearLayout.LayoutParams(0,dp(58),1));title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(action("今天",()->{selected=LocalDate.now();render();}));
        header.addView(action("⚙",this::settings));root.addView(header);
        LinearLayout controls=row();
        String label=timetable?(data.optBoolean("configured")?"第 "+ScheduleRules.week(semester(),selected)+" 周":"设置开学日期"):(selected.getYear()+" 年 "+selected.getMonthValue()+" 月");
        TextView month=text(label,18,INK,true);controls.addView(month,new LinearLayout.LayoutParams(0,dp(48),1));month.setGravity(Gravity.CENTER_VERTICAL);
        month.setOnClickListener(v->{if(timetable)settings();else pickDate(selected,d->{selected=d;render();});});
        LinearLayout arrows=row();arrows.setBackground(shape(0xffe6e8ee,28));arrows.addView(action("‹",()->{selected=selected.minusWeeks(1);render();}));arrows.addView(action("›",()->{selected=selected.plusWeeks(1);render();}));controls.addView(arrows);root.addView(controls);space(root,12);
        if(!timetable) weekStrip();
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);body=column();body.setPadding(0,dp(12),0,dp(20));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(timetable)drawTable();else drawAgenda();
        LinearLayout nav=row();nav.setPadding(dp(5),dp(5),dp(5),dp(5));nav.setBackground(shape(Color.WHITE,40));nav.setElevation(dp(4));
        for(int i=0;i<2;i++){final boolean page=i==0;TextView tab=text((i==0?"▥  课表":"▣  日程"),17,timetable==page?BLUE:MUTED,true);tab.setGravity(Gravity.CENTER);if(timetable==page)tab.setBackground(shape(0xffe2edfc,32));tab.setOnClickListener(v->{timetable=page;render();});nav.addView(tab,new LinearLayout.LayoutParams(0,dp(52),1));}root.addView(nav);
    }
    private void weekStrip(){
        LinearLayout strip=row();LocalDate monday=ScheduleRules.monday(selected);
        for(int i=0;i<7;i++){
            LocalDate date=monday.plusDays(i);boolean active=date.equals(selected);LinearLayout item=column();item.setGravity(Gravity.CENTER);item.setPadding(0,dp(10),0,dp(8));if(active)item.setBackground(shape(BLUE,18));
            TextView day=text(DAYS[i],14,active?Color.WHITE:MUTED,false);day.setGravity(Gravity.CENTER);item.addView(day);space(item,9);
            TextView number=text(""+date.getDayOfMonth(),22,active?Color.WHITE:INK,true);number.setGravity(Gravity.CENTER);item.addView(number);space(item,5);
            TextView marker=text(date.equals(LocalDate.now())?"•":" ",12,active?Color.WHITE:BLUE,true);item.addView(marker);item.setOnClickListener(v->{selected=date;render();});strip.addView(item,new LinearLayout.LayoutParams(0,dp(100),1));
        }root.addView(strip);space(root,12);
    }
    private static class Entry {JSONObject source;boolean course;String title,note,time;int minute;boolean allDay,done;}
    private List<Entry> entries(){
        List<Entry> list=new ArrayList<>();
        if(data.optBoolean("configured"))for(int i=0;i<courses().length();i++){
            JSONObject c=courses().optJSONObject(i);
            if(ScheduleRules.occurs(semester(),selected,c.optInt("day"),ScheduleRules.weeks(c.optString("weeks"),totalWeeks()))){
                Entry e=new Entry();e.source=c;e.course=true;e.title=c.optString("title");e.note=c.optString("room");e.time=start(c.optInt("from"))+"–"+end(c.optInt("to"));e.minute=ScheduleRules.minutes(start(c.optInt("from")));list.add(e);
            }
        }
        for(int i=0;i<events().length();i++){
            JSONObject s=events().optJSONObject(i);if(!s.optString("date").equals(selected.toString()))continue;
            Entry e=new Entry();e.source=s;e.title=s.optString("title");e.note=s.optString("note");e.allDay=s.optBoolean("allDay");e.done=s.optBoolean("done");e.time=e.allDay?"全天":s.optString("start")+(s.optString("end").isEmpty()?"":"–"+s.optString("end"));e.minute=e.allDay?-1:ScheduleRules.minutes(s.optString("start"));list.add(e);
        }
        list.sort(Comparator.comparingInt(e->e.minute));return list;
    }
    private void drawAgenda(){
        LinearLayout heading=row();String prefix=selected.equals(LocalDate.now())?"今天 · ":"";heading.addView(text(prefix+selected.getMonthValue()+"月"+selected.getDayOfMonth()+"日 周"+DAYS[selected.getDayOfWeek().getValue()-1],20,INK,true),new LinearLayout.LayoutParams(0,-2,1));TextView plus=action("＋",()->eventForm(null));plus.setBackground(shape(0xffdeebfc,30));heading.addView(plus);body.addView(heading);space(body,8);
        List<Entry> list=entries();body.addView(text(list.size()+" 个安排 · 把今天过得从容一点",13,MUTED,false));space(body,14);
        if(list.isEmpty()){space(body,65);TextView empty=text("今天，留一点空白\n\n点击 ＋ 添加安排",18,MUTED,false);empty.setGravity(Gravity.CENTER);body.addView(empty);if(!data.optBoolean("configured")){space(body,20);body.addView(action("设置学期，让课程自动出现",this::settings));}return;}
        for(int group=0;group<3;group++){
            List<Entry> items=new ArrayList<>();for(Entry e:list)if((e.allDay?0:e.minute<720?1:2)==group)items.add(e);if(items.isEmpty())continue;
            space(body,12);body.addView(text("◷  "+new String[]{"全天","上午","下午"}[group]+"  "+items.size(),15,MUTED,false));space(body,12);
            for(Entry e:items){
                LinearLayout line=row();line.setGravity(Gravity.TOP);LinearLayout clock=column();clock.setPadding(0,dp(14),dp(7),0);TextView time=text(e.allDay?"•":e.time.substring(0,5),15,e.allDay?BLUE:INK,true);clock.addView(time);TextView dot=text("      •",18,BLUE,true);clock.addView(dot);line.addView(clock,new LinearLayout.LayoutParams(dp(66),-1));
                LinearLayout card=column();card.setPadding(dp(17),dp(18),dp(17),dp(18));card.setBackground(shape(e.done?0xffebedf3:Color.WHITE,24));
                LinearLayout top=row();TextView name=text(e.title,19,e.done?MUTED:INK,true);if(e.done)name.setPaintFlags(name.getPaintFlags()|android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);top.addView(name,new LinearLayout.LayoutParams(0,-2,1));TextView tag=text(e.course?"课程":e.done?"已完成":"待办",12,BLUE,false);tag.setPadding(dp(8),dp(4),dp(8),dp(4));tag.setBackground(shape(0xffedf4ff,9));top.addView(tag);card.addView(top);space(card,7);
                card.addView(text((e.allDay?"全天":e.time)+(e.note.isEmpty()?"":" · "+e.note),13,MUTED,false));
                card.setOnClickListener(v->{if(e.course)courseForm(e.source,e.source.optInt("day"),e.source.optInt("from"));else eventForm(e.source);});line.addView(card,new LinearLayout.LayoutParams(0,-2,1));body.addView(line);space(body,12);
            }
        }
    }
    private void drawTable(){
        if(!data.optBoolean("configured")){body.addView(action("先设置开学日期与上课时间",this::settings));return;}
        LocalDate mon=ScheduleRules.monday(selected);body.addView(text(mon.format(DateTimeFormatter.ofPattern("MM.dd"))+" — "+mon.plusDays(6).format(DateTimeFormatter.ofPattern("MM.dd"))+"  · 点击空格添加课程",13,MUTED,false));space(body,12);
        HorizontalScrollView horizontal=new HorizontalScrollView(this);LinearLayout grid=row();grid.setGravity(Gravity.TOP);horizontal.addView(grid);body.addView(horizontal);
        LinearLayout times=column();times.addView(text("节次",12,MUTED,false),new LinearLayout.LayoutParams(dp(50),dp(54)));
        for(int n=1;n<=12;n++){LinearLayout slot=column();slot.setGravity(Gravity.CENTER);TextView num=text(""+n,17,INK,true);slot.addView(num);slot.addView(text(start(n),10,MUTED,false));slot.addView(text(end(n),10,MUTED,false));times.addView(slot,new LinearLayout.LayoutParams(dp(50),dp(83)));}grid.addView(times);
        int width=Math.max(dp(67),(getResources().getDisplayMetrics().widthPixels-dp(90))/5);
        for(int day=1;day<=7;day++){
            final int weekday=day;LocalDate date=mon.plusDays(day-1);LinearLayout col=column();LinearLayout head=column();head.setGravity(Gravity.CENTER);boolean today=date.equals(LocalDate.now());if(today)head.setBackground(shape(0xffdeebfc,12));head.addView(text("周"+DAYS[day-1],14,today?BLUE:INK,true));head.addView(text(date.format(DateTimeFormatter.ofPattern("MM/dd")),11,MUTED,false));col.addView(head,new LinearLayout.LayoutParams(width,dp(54)));
            FrameLayout frame=new FrameLayout(this);col.addView(frame,new LinearLayout.LayoutParams(width,dp(83*12)));
            for(int n=1;n<=12;n++){final int period=n;TextView cell=text("",12,MUTED,false);GradientDrawable bg=shape(0xfff7f8fb,0);bg.setStroke(dp(.5f),0xffe4e7ee);cell.setBackground(bg);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(width,dp(83));p.topMargin=dp((n-1)*83);frame.addView(cell,p);cell.setOnClickListener(v->courseForm(null,weekday,period));}
            for(int i=0;i<courses().length();i++){
                JSONObject c=courses().optJSONObject(i);if(!ScheduleRules.occurs(semester(),date,c.optInt("day"),ScheduleRules.weeks(c.optString("weeks"),totalWeeks())))continue;
                TextView block=text(c.optString("title")+"\n\n"+c.optString("room"),12,BLUE,true);block.setPadding(dp(7),dp(10),dp(7),dp(6));block.setBackground(shape(0xffdeebfc,12));block.setGravity(Gravity.TOP);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(width-dp(4),dp((c.optInt("to")-c.optInt("from")+1)*83)-dp(5));p.topMargin=dp((c.optInt("from")-1)*83)+dp(2);p.leftMargin=dp(2);frame.addView(block,p);block.setOnClickListener(v->courseForm(c,weekday,c.optInt("from")));
            }grid.addView(col);
        }
    }
    private interface DateResult {void accept(LocalDate date);}
    private void pickDate(LocalDate initial,DateResult callback){new DatePickerDialog(this,(p,y,m,d)->callback.accept(LocalDate.of(y,m+1,d)),initial.getYear(),initial.getMonthValue()-1,initial.getDayOfMonth()).show();}
    private EditText field(LinearLayout form,String label,String value){form.addView(text(label,13,MUTED,false));EditText input=new EditText(this);input.setSingleLine(true);input.setText(value);input.setTextSize(16);form.addView(input,new LinearLayout.LayoutParams(-1,dp(52)));space(form,10);return input;}
    private LinearLayout form(){LinearLayout f=column();f.setPadding(dp(24),dp(12),dp(24),dp(18));return f;}
    private TextView picker(LinearLayout form,String label,String value,Runnable click){form.addView(text(label,13,MUTED,false));TextView t=text(value+"  ›",16,BLUE,true);t.setGravity(Gravity.CENTER_VERTICAL);form.addView(t,new LinearLayout.LayoutParams(-1,dp(48)));t.setOnClickListener(v->click.run());return t;}
    private AlertDialog dialog(String title,LinearLayout f,Runnable save){ScrollView scroll=new ScrollView(this);scroll.addView(f);AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存",null).create();d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{try{save.run();d.dismiss();render();}catch(IllegalArgumentException e){toast(e.getMessage()==null?"请检查填写内容":e.getMessage());}}));return d;}
    private void update(String collection,JSONObject original,JSONObject value){
        JSONObject next=copy();JSONArray old=next.optJSONArray(collection),arr=new JSONArray();boolean found=false;
        for(int i=0;i<old.length();i++){JSONObject item=old.optJSONObject(i);if(original!=null&&item.optString("id").equals(original.optString("id"))){found=true;if(value!=null)arr.put(value);}else arr.put(item);}
        if(!found&&value!=null)arr.put(value);try{next.put(collection,arr);}catch(JSONException e){throw new IllegalStateException(e);}if(!persist(next))throw new IllegalArgumentException("未能保存，请重试");
    }
    private void removeButton(LinearLayout f,String collection,JSONObject original,AlertDialog parent){TextView del=action("删除",()->new AlertDialog.Builder(this).setTitle(collection.equals("courses")?"删除这门课程？":"删除这个日程？").setMessage(collection.equals("courses")?"该课程在所有上课周次的安排都会移除。":"删除后无法恢复。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{update(collection,original,null);parent.dismiss();render();}).show());del.setTextColor(0xffc64e57);f.addView(del);}
    private void courseForm(JSONObject c,int day,int period){
        LinearLayout f=form();EditText title=field(f,"课程名称",c==null?"":c.optString("title")),room=field(f,"教室",c==null?"":c.optString("room"));
        f.addView(text("星期",13,MUTED,false));Spinner weekday=new Spinner(this);weekday.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"星期一","星期二","星期三","星期四","星期五","星期六","星期日"}));weekday.setSelection(day-1);f.addView(weekday,new LinearLayout.LayoutParams(-1,dp(48)));
        EditText from=field(f,"开始节次（1–12）",""+(c==null?period:c.optInt("from"))),to=field(f,"结束节次（1–12）",""+(c==null?period:c.optInt("to")));from.setInputType(2);to.setInputType(2);
        EditText weeks=field(f,"上课周次，如 1-16 或 1,3,5-8",c==null?"1-"+totalWeeks():c.optString("weeks"));LinearLayout shortcuts=row();
        for(int parity=0;parity<2;parity++){final int p=parity;TextView b=action(parity==0?"单周":"双周",()->{StringJoiner join=new StringJoiner(",");for(int n=p==0?1:2;n<=totalWeeks();n+=2)join.add(""+n);weeks.setText(join.toString());});shortcuts.addView(b,new LinearLayout.LayoutParams(0,dp(42),1));}f.addView(shortcuts);
        AlertDialog d=dialog(c==null?"添加课程":"编辑课程",f,()->{
            String name=title.getText().toString().trim();if(name.isEmpty())throw new IllegalArgumentException("请填写课程名称");int a,b;try{a=Integer.parseInt(from.getText().toString());b=Integer.parseInt(to.getText().toString());}catch(NumberFormatException e){throw new IllegalArgumentException("请填写有效节次");}if(a<1||b>12||a>b)throw new IllegalArgumentException("节次范围须在 1–12，结束节次不能早于开始");
            String range=weeks.getText().toString().trim();Set<Integer> chosen;try{chosen=ScheduleRules.weeks(range,totalWeeks());}catch(Exception e){throw new IllegalArgumentException("请检查上课周次，如 1-16 或 1,3,5-8");}
            for(int i=0;i<courses().length();i++){JSONObject other=courses().optJSONObject(i);if(c!=null&&other.optString("id").equals(c.optString("id")))continue;if(other.optInt("day")==weekday.getSelectedItemPosition()+1&&a<=other.optInt("to")&&b>=other.optInt("from")){Set<Integer> overlap=new HashSet<>(chosen);overlap.retainAll(ScheduleRules.weeks(other.optString("weeks"),totalWeeks()));if(!overlap.isEmpty())throw new IllegalArgumentException("与「"+other.optString("title")+"」的上课时间重叠");}}
            update("courses",c,obj("id",c==null?UUID.randomUUID().toString():c.optString("id"),"title",name,"room",room.getText().toString().trim(),"day",weekday.getSelectedItemPosition()+1,"from",a,"to",b,"weeks",range));
        });if(c!=null)removeButton(f,"courses",c,d);d.show();
    }
    private void timePicker(TextView button,String[] values,int index){String value=values[index].isEmpty()?"15:00":values[index];new TimePickerDialog(this,(p,h,m)->{values[index]=String.format(Locale.ROOT,"%02d:%02d",h,m);button.setText(values[index]);},Integer.parseInt(value.substring(0,2)),Integer.parseInt(value.substring(3)),true).show();}
    private void eventForm(JSONObject e){
        LinearLayout f=form();EditText title=field(f,"日程标题",e==null?"":e.optString("title"));LocalDate[] date={e==null?selected:LocalDate.parse(e.optString("date"))};TextView dateButton=text(date[0].toString(),16,BLUE,true);f.addView(dateButton,new LinearLayout.LayoutParams(-1,dp(48)));dateButton.setOnClickListener(v->pickDate(date[0],d->{date[0]=d;dateButton.setText(d.toString());}));
        Switch all=new Switch(this);all.setText("全天日程");all.setChecked(e!=null&&e.optBoolean("allDay"));f.addView(all,new LinearLayout.LayoutParams(-1,dp(48)));
        String[] times={e==null?"14:30":e.optString("start","14:30"),e==null?"":e.optString("end")};LinearLayout timeArea=column();f.addView(timeArea);timeArea.addView(text("开始时间",13,MUTED,false));TextView startButton=action(times[0],()->{});startButton.setOnClickListener(v->timePicker(startButton,times,0));timeArea.addView(startButton);timeArea.addView(text("结束时间（可选）",13,MUTED,false));LinearLayout endRow=row();TextView endButton=action(times[1].isEmpty()?"选择时间":times[1],()->{});endButton.setOnClickListener(v->timePicker(endButton,times,1));endRow.addView(endButton,new LinearLayout.LayoutParams(0,dp(48),1));endRow.addView(action("清除",()->{times[1]="";endButton.setText("选择时间");}));timeArea.addView(endRow);timeArea.setVisibility(all.isChecked()?View.GONE:View.VISIBLE);all.setOnCheckedChangeListener((b,on)->timeArea.setVisibility(on?View.GONE:View.VISIBLE));
        EditText note=field(f,"备注（可选）",e==null?"":e.optString("note"));CheckBox done=new CheckBox(this);done.setText("已完成");done.setChecked(e!=null&&e.optBoolean("done"));f.addView(done);
        AlertDialog d=dialog(e==null?"新增日程":"编辑日程",f,()->{String name=title.getText().toString().trim();if(name.isEmpty())throw new IllegalArgumentException("请填写日程标题");if(!all.isChecked()){int a=ScheduleRules.minutes(times[0]);if(!times[1].isEmpty()&&ScheduleRules.minutes(times[1])<=a)throw new IllegalArgumentException("结束时间须晚于开始时间");}update("events",e,obj("id",e==null?UUID.randomUUID().toString():e.optString("id"),"title",name,"date",date[0].toString(),"allDay",all.isChecked(),"start",times[0],"end",times[1],"note",note.getText().toString().trim(),"done",done.isChecked()));selected=date[0];});if(e!=null)removeButton(f,"events",e,d);d.show();
    }
    private void settings(){
        LinearLayout f=form();f.addView(text("开学日期所在的周记为第 1 周，周一开始。",13,MUTED,false));LocalDate[] date={semester()};TextView dateButton=text(date[0].toString()+"  ›",18,BLUE,true);f.addView(dateButton,new LinearLayout.LayoutParams(-1,dp(56)));dateButton.setGravity(Gravity.CENTER_VERTICAL);dateButton.setOnClickListener(v->pickDate(date[0],d->{date[0]=d;dateButton.setText(d.toString()+"  ›");}));EditText count=field(f,"学期周数（1–52）",""+totalWeeks());count.setInputType(2);f.addView(text("每节课时间 · 点击修改",16,INK,true));space(f,10);
        String[] times=new String[24];for(int i=0;i<12;i++){times[i*2]=start(i+1);times[i*2+1]=end(i+1);LinearLayout r=row();r.addView(text("第 "+(i+1)+" 节",14,INK,true),new LinearLayout.LayoutParams(dp(60),dp(48)));for(int j=0;j<2;j++){final int index=i*2+j;TextView button=action(times[index],()->{});button.setTextSize(16);button.setOnClickListener(v->timePicker(button,times,index));r.addView(button,new LinearLayout.LayoutParams(0,dp(48),1));}f.addView(r);}
        AlertDialog dialog=dialog("学期与节次设置",f,()->{
            int weeks;try{weeks=Integer.parseInt(count.getText().toString());}catch(NumberFormatException e){throw new IllegalArgumentException("请输入有效周数");}if(weeks<1||weeks>52)throw new IllegalArgumentException("学期周数须为 1–52");
            for(int i=0;i<courses().length();i++)try{ScheduleRules.weeks(courses().optJSONObject(i).optString("weeks"),weeks);}catch(Exception e){throw new IllegalArgumentException("已有课程超出新学期周数，请先调整课程周次");}
            JSONArray starts=new JSONArray(),ends=new JSONArray();int previous=-1;
            for(int i=0;i<12;i++){int a=ScheduleRules.minutes(times[i*2]),b=ScheduleRules.minutes(times[i*2+1]);if(a>=b||a<previous)throw new IllegalArgumentException("第 "+(i+1)+" 节的时间无效或与上一节重叠");previous=b;starts.put(times[i*2]);ends.put(times[i*2+1]);}
            JSONObject next=copy();try{next.put("semester",date[0].toString());next.put("configured",true);next.put("totalWeeks",weeks);next.put("starts",starts);next.put("ends",ends);}catch(JSONException e){throw new IllegalStateException(e);}if(!persist(next))throw new IllegalArgumentException("保存失败");
        });dialog.show();
    }
}
