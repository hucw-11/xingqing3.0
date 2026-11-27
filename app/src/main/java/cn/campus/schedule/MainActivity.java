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
    @Override public android.content.SharedPreferences getPreferences(int mode){return getSharedPreferences("MainActivity",mode);}
    final int BG=0xfff3f4f8, BLUE=0xff0868ce, INK=0xff171c26, MUTED=0xff858a96;
    final String[] DAYS={"一","二","三","四","五","六","日"};
    final String[] DEFAULT_START={"08:00","08:50","09:50","10:40","11:30","14:00","14:50","15:45","16:35","19:00","19:50","20:40"};
    final String[] DEFAULT_END={"08:45","09:35","10:35","11:25","12:15","14:45","15:35","16:30","17:20","19:45","20:35","21:25"};
    JSONObject data;
    LocalDate selected=LocalDate.now();
    boolean timetable=false;
    LinearLayout root,body;
    ScrollView pageScroll;
    AgendaFeatures features;
    final android.os.Handler syncHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    final Runnable syncPoll=new Runnable(){public void run(){SyncManager.request(MainActivity.this);AgendaView.tick(MainActivity.this);syncHandler.postDelayed(this,30000);}};
    String agendaClockKey="";
    boolean syncRegistered;
    final android.content.BroadcastReceiver syncReceiver=new android.content.BroadcastReceiver(){public void onReceive(android.content.Context c,android.content.Intent i){if(data==null)return;JSONObject latest=SyncManager.data(MainActivity.this);if((!SyncMerge.same(latest,data)||i.getBooleanExtra("photos",false))&&getWindow().getDecorView().hasWindowFocus()){data=latest;if(!(MainActivity.this instanceof AiActivity))render();}}};
    int dp(float n){ return (int)(n*getResources().getDisplayMetrics().density+.5f); }
    JSONObject obj(Object... pairs) {
        JSONObject o=new JSONObject();
        try { for(int i=0;i<pairs.length;i+=2)o.put((String)pairs[i],pairs[i+1]); } catch(JSONException e){throw new IllegalStateException(e);}
        return o;
    }
    JSONArray courses(){return data.optJSONArray("courses");}
    JSONArray events(){return data.optJSONArray("events");}
    LocalDate semester(){return LocalDate.parse(data.optString("semester"));}
    int totalWeeks(){return data.optInt("totalWeeks",20);}
    String start(int n){return data.optJSONArray("starts").optString(n-1);}
    String end(int n){return data.optJSONArray("ends").optString(n-1);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        SyncManager.restore(this);
        String saved=getPreferences(MODE_PRIVATE).getString("data",null);
        try { data=saved==null?obj("semester",ScheduleRules.monday(LocalDate.now()).toString(),"configured",false,"totalWeeks",20,"starts",new JSONArray(Arrays.asList(DEFAULT_START)),"ends",new JSONArray(Arrays.asList(DEFAULT_END)),"courses",new JSONArray(),"events",new JSONArray()):new JSONObject(saved); }
        catch(JSONException e){ new AlertDialog.Builder(this).setTitle("数据读取失败").setMessage("原始数据已保留，请重新启动或联系开发者。").setPositiveButton("关闭",(d,w)->finish()).setCancelable(false).show(); return; }
        if(state!=null){selected=LocalDate.parse(state.getString("selected",selected.toString()));timetable=state.getBoolean("timetable");}
        if(getIntent().hasExtra("date"))selected=LocalDate.parse(getIntent().getStringExtra("date"));if(getIntent().hasExtra("page"))timetable=getIntent().getStringExtra("page").equals("table");features=new AgendaFeatures(this);features.migrate();render();
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("selected",selected.toString());out.putBoolean("timetable",timetable);}
    @Override protected void onActivityResult(int request,int result,android.content.Intent intent){super.onActivityResult(request,result,intent);new TimetableTransfer(this).result(request,result,intent);}
    @Override protected void onNewIntent(android.content.Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.hasExtra("date"))selected=LocalDate.parse(intent.getStringExtra("date"));if(intent.hasExtra("page"))timetable="table".equals(intent.getStringExtra("page"));render();}
    @Override protected void onResume(){super.onResume();if(!syncRegistered){androidx.core.content.ContextCompat.registerReceiver(this,syncReceiver,new android.content.IntentFilter(SyncManager.CHANGED),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);syncRegistered=true;}syncHandler.removeCallbacks(syncPoll);syncPoll.run();if(features!=null){try{data=new JSONObject(getPreferences(0).getString("data",data.toString()));}catch(JSONException ignored){}ReminderScheduler.schedule(this);render();}}
    @Override protected void onPause(){syncHandler.removeCallbacks(syncPoll);if(syncRegistered){unregisterReceiver(syncReceiver);syncRegistered=false;}super.onPause();}
    boolean persist(JSONObject next){
        if(!SyncManager.persist(this,data,next)){toast("保存失败，请重试");return false;}
        data=SyncManager.data(this);ReminderScheduler.schedule(this);return true;
    }
    JSONObject copy(){try{return new JSONObject(data.toString());}catch(JSONException e){throw new IllegalStateException(e);}}
    GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    TextView action(String s,Runnable run){TextView t=text(s,21,BLUE,false);t.setGravity(Gravity.CENTER);t.setMinWidth(dp(44));t.setMinHeight(dp(44));t.setOnClickListener(v->run.run());return t;}
    void space(LinearLayout l,int h){l.addView(new android.view.View(this),new LinearLayout.LayoutParams(1,dp(h)));}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    LocalDate renderedDate;boolean renderedTable;
    void render(){int restoreY=pageScroll!=null&&selected.equals(renderedDate)&&timetable==renderedTable?pageScroll.getScrollY():0;renderedDate=selected;renderedTable=timetable;
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
        ScrollView scroll=new ScrollView(this);pageScroll=scroll;scroll.setFillViewport(true);scroll.setClipToPadding(false);body=column();body.setPadding(0,dp(12),0,dp(20));scroll.addView(body);FrameLayout contentFrame=new FrameLayout(this);contentFrame.addView(scroll,new FrameLayout.LayoutParams(-1,-1));root.addView(contentFrame,new LinearLayout.LayoutParams(-1,0,1));if(!timetable){body.setPadding(0,dp(12),0,dp(96));TextView fab=action("＋",features::plus);fab.setContentDescription("创建日程或心情");fab.setTextSize(30);fab.setTextColor(Color.WHITE);fab.setBackground(shape(BLUE,30));fab.setElevation(dp(6));FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(dp(56),dp(56),Gravity.BOTTOM|Gravity.END);fp.setMargins(dp(12),dp(12),dp(12),dp(12));contentFrame.addView(fab,fp);}
        if(timetable)drawTable();else drawAgenda();
        LinearLayout nav=row();nav.setPadding(dp(5),dp(5),dp(5),dp(5));nav.setBackground(shape(Color.WHITE,40));nav.setElevation(dp(4));
        for(int i=0;i<3;i++){if(i==2){TextView ai=action("✧  AI",()->startActivity(new android.content.Intent(this,AiActivity.class).putExtra("date",selected.toString())));ai.setTextSize(17);nav.addView(ai,new LinearLayout.LayoutParams(0,dp(52),1));continue;}final boolean page=i==0;TextView tab=text((i==0?"▥  课表":"▣  日程"),17,timetable==page?BLUE:MUTED,true);tab.setGravity(Gravity.CENTER);if(timetable==page)tab.setBackground(shape(0xffe2edfc,32));tab.setOnClickListener(v->{timetable=page;render();});nav.addView(tab,new LinearLayout.LayoutParams(0,dp(52),1));}root.addView(nav);pageScroll.setFocusableInTouchMode(true);pageScroll.requestFocus();pageScroll.post(()->pageScroll.scrollTo(0,restoreY));
    }
    void weekStrip(){
        LinearLayout strip=row();LocalDate monday=ScheduleRules.monday(selected);
        for(int i=0;i<7;i++){
            LocalDate date=monday.plusDays(i);boolean active=date.equals(selected);LinearLayout item=column();item.setGravity(Gravity.CENTER);item.setPadding(0,dp(10),0,dp(8));if(active)item.setBackground(shape(BLUE,18));
            TextView day=text(DAYS[i],14,active?Color.WHITE:MUTED,false);day.setGravity(Gravity.CENTER);item.addView(day);space(item,9);
            TextView number=text(""+date.getDayOfMonth(),22,active?Color.WHITE:INK,true);number.setGravity(Gravity.CENTER);item.addView(number);space(item,5);
            TextView marker=text(date.equals(LocalDate.now())?"•":" ",12,active?Color.WHITE:BLUE,true);item.addView(marker);item.setOnClickListener(v->{selected=date;render();});strip.addView(item,new LinearLayout.LayoutParams(0,dp(100),1));
        }root.addView(strip);space(root,12);
    }
    static class Entry {JSONObject source;boolean course,mood,continuous,progress;String title,note,time,childId="";int minute;boolean allDay,done;}
    class TimelineRail extends LinearLayout {
        final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        TimelineRail(){super(MainActivity.this);setOrientation(VERTICAL);setWillNotDraw(false);}
        @Override protected void onDraw(android.graphics.Canvas canvas){
            super.onDraw(canvas);paint.setColor(0xffd8dce5);paint.setStrokeWidth(dp(1.5f));canvas.drawLine(dp(58),dp(5),dp(58),getHeight()-dp(4),paint);paint.setColor(BLUE);canvas.drawCircle(dp(58),dp(24),dp(3),paint);
        }
    }
    List<Entry> entries(){
        List<Entry> list=AgendaItems.build(data,selected,LocalDateTime.now());list.removeIf(e->!features.visible(e));return list;
    }
    void drawAgenda(){new AgendaView(this).draw();}
    void drawTable(){
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
                JSONObject c=courses().optJSONObject(i);if(!CourseRules.occurs(semester(),date,c,totalWeeks()))continue;
                TextView block=text(c.optString("title")+"\n\n"+c.optString("room"),12,BLUE,true);block.setPadding(dp(7),dp(10),dp(7),dp(6));block.setBackground(shape(0xffdeebfc,12));block.setGravity(Gravity.TOP);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(width-dp(4),dp((c.optInt("to")-c.optInt("from")+1)*83)-dp(5));p.topMargin=dp((c.optInt("from")-1)*83)+dp(2);p.leftMargin=dp(2);frame.addView(block,p);block.setOnClickListener(v->courseForm(c,weekday,c.optInt("from")));
            }grid.addView(col);
        }
    }
    interface DateResult {void accept(LocalDate date);}
    void pickDate(LocalDate initial,DateResult callback){new DatePickerDialog(this,(p,y,m,d)->callback.accept(LocalDate.of(y,m+1,d)),initial.getYear(),initial.getMonthValue()-1,initial.getDayOfMonth()).show();}
    EditText field(LinearLayout form,String label,String value){form.addView(text(label,13,MUTED,false));EditText input=new EditText(this);input.setContentDescription(label);input.setSingleLine(true);input.setText(value);input.setTextSize(16);form.addView(input,new LinearLayout.LayoutParams(-1,dp(52)));space(form,10);return input;}
    LinearLayout form(){LinearLayout f=column();f.setPadding(dp(24),dp(12),dp(24),dp(18));return f;}
    TextView picker(LinearLayout form,String label,String value,Runnable click){form.addView(text(label,13,MUTED,false));TextView t=text(value+"  ›",16,BLUE,true);t.setGravity(Gravity.CENTER_VERTICAL);form.addView(t,new LinearLayout.LayoutParams(-1,dp(48)));t.setOnClickListener(v->click.run());return t;}
    AlertDialog dialog(String title,LinearLayout f,Runnable save){ScrollView scroll=new ScrollView(this);scroll.addView(f);AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存",null).create();d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{try{save.run();d.dismiss();render();}catch(IllegalArgumentException e){toast(e.getMessage()==null?"请检查填写内容":e.getMessage());}}));return d;}
    void update(String collection,JSONObject original,JSONObject value){
        JSONObject next=copy();JSONArray old=next.optJSONArray(collection),arr=new JSONArray();boolean found=false;
        for(int i=0;i<old.length();i++){JSONObject item=old.optJSONObject(i);if(original!=null&&item.optString("id").equals(original.optString("id"))){found=true;if(value!=null)arr.put(value);}else arr.put(item);}
        if(!found&&value!=null)arr.put(value);try{next.put(collection,arr);}catch(JSONException e){throw new IllegalStateException(e);}if(!persist(next))throw new IllegalArgumentException("未能保存，请重试");
    }
    void removeButton(LinearLayout f,String collection,JSONObject original,AlertDialog parent){TextView del=action("删除",()->new AlertDialog.Builder(this).setTitle(collection.equals("courses")?"删除这门课程？":"删除这个日程？").setMessage(collection.equals("courses")?"该课程在所有上课周次的安排都会移除。":"删除后无法恢复。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{update(collection,original,null);parent.dismiss();render();}).show());del.setTextColor(0xffc64e57);f.addView(del);}
    void courseForm(JSONObject c,int day,int period){
        LinearLayout f=form();EditText title=field(f,"课程名称",c==null?"":c.optString("title")),room=field(f,"教室",c==null?"":c.optString("room"));
        f.addView(text("上课星期（可多选）",13,MUTED,false));Set<Integer> courseDays=c==null?new TreeSet<>(Arrays.asList(day)):CourseRules.days(c);CheckBox[] weekday=new CheckBox[7];LinearLayout weekdays=row();for(int i=0;i<7;i++){final int n=i+1;weekday[i]=new CheckBox(this);weekday[i].setText(DAYS[i]);weekday[i].setContentDescription("星期"+DAYS[i]);weekday[i].setChecked(courseDays.contains(n));weekday[i].setOnCheckedChangeListener((v,on)->{if(on)courseDays.add(n);else courseDays.remove(n);});weekdays.addView(weekday[i]);}HorizontalScrollView daysScroll=new HorizontalScrollView(this);daysScroll.addView(weekdays);f.addView(daysScroll);
        EditText from=field(f,"开始节次（1–12）",""+(c==null?period:c.optInt("from"))),to=field(f,"结束节次（1–12）",""+(c==null?period:c.optInt("to")));from.setInputType(2);to.setInputType(2);
        List<String> courseTags=features.tagPicker(f,c,true);EditText weeks=field(f,"上课周次，如 1-16 或 1,3,5-8",c==null?"1-"+totalWeeks():c.optString("weeks"));LinearLayout shortcuts=row();
        for(int parity=0;parity<2;parity++){final int p=parity;TextView b=action(parity==0?"单周":"双周",()->{StringJoiner join=new StringJoiner(",");for(int n=p==0?1:2;n<=totalWeeks();n+=2)join.add(""+n);weeks.setText(join.toString());});shortcuts.addView(b,new LinearLayout.LayoutParams(0,dp(42),1));}f.addView(shortcuts);
        AlertDialog d=dialog(c==null?"添加课程":"编辑课程",f,()->{
            String name=title.getText().toString().trim();if(name.isEmpty())throw new IllegalArgumentException("请填写课程名称");int a,b;try{a=Integer.parseInt(from.getText().toString());b=Integer.parseInt(to.getText().toString());}catch(NumberFormatException e){throw new IllegalArgumentException("请填写有效节次");}if(a<1||b>12||a>b)throw new IllegalArgumentException("节次范围须在 1–12，结束节次不能早于开始");
            if(courseDays.isEmpty())throw new IllegalArgumentException("请选择至少一个上课星期");String range=weeks.getText().toString().trim();Set<Integer> chosen;try{chosen=ScheduleRules.weeks(range,totalWeeks());}catch(Exception e){throw new IllegalArgumentException("请检查上课周次，如 1-16 或 1,3,5-8");}
            for(int i=0;i<courses().length();i++){JSONObject other=courses().optJSONObject(i);if(c!=null&&other.optString("id").equals(c.optString("id")))continue;if(!Collections.disjoint(courseDays,CourseRules.days(other))&&a<=other.optInt("to")&&b>=other.optInt("from")){Set<Integer> overlap=new HashSet<>(chosen);overlap.retainAll(ScheduleRules.weeks(other.optString("weeks"),totalWeeks()));if(!overlap.isEmpty())throw new IllegalArgumentException("与「"+other.optString("title")+"」的上课时间重叠");}}
            update("courses",c,obj("id",c==null?UUID.randomUUID().toString():c.optString("id"),"title",name,"room",room.getText().toString().trim(),"day",courseDays.iterator().next(),"days",new JSONArray(courseDays),"from",a,"to",b,"weeks",range,"tags",new JSONArray(courseTags)));
        });if(c!=null)removeButton(f,"courses",c,d);d.show();
    }
    void timePicker(TextView button,String[] values,int index){String value=values[index].isEmpty()?"15:00":values[index];new TimePickerDialog(this,(p,h,m)->{values[index]=String.format(Locale.ROOT,"%02d:%02d",h,m);button.setText(values[index]);},Integer.parseInt(value.substring(0,2)),Integer.parseInt(value.substring(3)),true).show();}
    void eventForm(JSONObject e){ features.eventForm(e); }
    void settings(){
        LinearLayout f=form();f.addView(action("云端同步",()->SyncSettings.show(this)));f.addView(action("提醒设置",()->ReminderSettings.show(this)));f.addView(action("课表导入／导出",()->new TimetableTransfer(this).menu()));f.addView(text("开学日期所在的周记为第 1 周，周一开始。",13,MUTED,false));LocalDate[] date={semester()};TextView dateButton=text(date[0].toString()+"  ›",18,BLUE,true);f.addView(dateButton,new LinearLayout.LayoutParams(-1,dp(56)));dateButton.setGravity(Gravity.CENTER_VERTICAL);dateButton.setOnClickListener(v->pickDate(date[0],d->{date[0]=d;dateButton.setText(d.toString()+"  ›");}));EditText count=field(f,"学期周数（1–52）",""+totalWeeks());count.setInputType(2);f.addView(text("每节课时间 · 点击修改",16,INK,true));space(f,10);
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
