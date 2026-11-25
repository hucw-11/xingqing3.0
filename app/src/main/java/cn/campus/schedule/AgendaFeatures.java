package cn.campus.schedule;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.time.*;
import java.util.*;

/** Labels and recurring event editing. All mutations go through MainActivity's atomic save. */
final class AgendaFeatures {
    final MainActivity a;
    int filter=0;
    String tagFilter="";
    AgendaFeatures(MainActivity activity){a=activity;}
    static JSONObject cloneJson(JSONObject o){try{return new JSONObject(o.toString());}catch(JSONException e){throw new IllegalStateException(e);}}
    static void put(JSONObject o,String k,Object v){try{o.put(k,v);}catch(JSONException e){throw new IllegalStateException(e);}}
    static List<String> strings(JSONArray array){List<String> r=new ArrayList<>();if(array!=null)for(int i=0;i<array.length();i++)r.add(array.optString(i));return r;}
    void migrate(){
        JSONObject next=a.copy();
        if(!next.has("moods"))put(next,"moods",new JSONArray());
        if(!next.has("labels")){JSONArray labels=new JSONArray();int[] colors={0xff0868ce,0xff31956a,0xff9d6cc7,0xffd38a38};String[] names={"学习","锻炼","娱乐","生活"};for(int i=0;i<names.length;i++)labels.put(a.obj("id",UUID.randomUUID().toString(),"name",names[i],"color",colors[i]));put(next,"labels",labels);}
        if(!next.has("studyLabel")){JSONArray labels=next.optJSONArray("labels");for(int i=0;i<labels.length();i++)if(labels.optJSONObject(i).optString("name").equals("学习"))put(next,"studyLabel",labels.optJSONObject(i).optString("id"));}
        JSONArray courses=next.optJSONArray("courses");for(int i=0;i<courses.length();i++){JSONObject c=courses.optJSONObject(i);if(!c.has("tags")){JSONArray tags=new JSONArray();if(!next.optString("studyLabel").isEmpty())tags.put(next.optString("studyLabel"));put(c,"tags",tags);}}
        put(next,"schema",2);if(!next.toString().equals(a.data.toString()))a.persist(next);
    }
    String studyId(){return a.data.optString("studyLabel");}
    JSONArray tags(JSONObject source,boolean course){if(source.has("tags"))return source.optJSONArray("tags");JSONArray r=new JSONArray();if(course&&!studyId().isEmpty())r.put(studyId());return r;}
    String labelText(JSONObject source,boolean course){List<String> ids=strings(tags(source,course)),names=new ArrayList<>();JSONArray labels=a.data.optJSONArray("labels");for(int i=0;i<labels.length();i++){JSONObject l=labels.optJSONObject(i);if(ids.contains(l.optString("id")))names.add(l.optString("name"));}return String.join(" · ",names);}
    void paintTags(LinearLayout card,JSONObject source,boolean course){
        List<String> ids=strings(tags(source,course));JSONArray labels=a.data.optJSONArray("labels");
        LinearLayout chips=a.row();for(int i=0;i<labels.length();i++){JSONObject l=labels.optJSONObject(i);if(!ids.contains(l.optString("id")))continue;TextView chip=a.text(l.optString("name"),11,l.optInt("color"),true);chip.setPadding(a.dp(6),a.dp(4),a.dp(6),a.dp(4));chips.addView(chip);}if(chips.getChildCount()>0){HorizontalScrollView scroll=new HorizontalScrollView(a);scroll.addView(chips);card.addView(scroll);}
    }
    void controls(){
        LinearLayout modes=a.row();for(int i=0;i<3;i++){final int f=i;TextView b=a.action(new String[]{"全部","活动","心情"}[i],()->{filter=f;a.render();});b.setTextSize(15);if(i==filter)b.setBackground(a.shape(0xffdeebfc,18));modes.addView(b,new LinearLayout.LayoutParams(0,a.dp(42),1));}a.body.addView(modes);
        LinearLayout options=a.row();TextView tag=a.action(tagFilter.isEmpty()?"筛选标签":"标签筛选中",this::chooseFilter);tag.setTextSize(13);options.addView(tag,new LinearLayout.LayoutParams(0,a.dp(40),1));TextView manage=a.action("管理标签",this::manageLabels);manage.setTextSize(13);options.addView(manage,new LinearLayout.LayoutParams(0,a.dp(40),1));a.body.addView(options);
        String draft=a.getPreferences(0).getString("draft:new",null);if(draft!=null)a.body.addView(a.action("继续未完成的心情草稿",()->mood(null,null,false)));
    }
    boolean visible(MainActivity.Entry e){return (filter!=1||!e.mood)&&(filter!=2||e.mood)&&(e.mood||tagFilter.isEmpty()||strings(tags(e.source,e.course)).contains(tagFilter));}
    void plus(){new AlertDialog.Builder(a).setTitle("记录与安排").setItems(new String[]{"新增活动","记录心情"},(d,w)->{if(w==0)eventForm(null);else mood(null,null,false);}).show();}
    void mood(JSONObject existing,MainActivity.Entry activity,boolean fresh){
        if(existing==null&&fresh&&a.getPreferences(0).contains("draft:new")){new AlertDialog.Builder(a).setTitle("还有一篇心情草稿").setItems(new String[]{"继续原草稿","放弃草稿，另写一篇"},(d,w)->{if(w==1)a.getPreferences(0).edit().remove("draft:new").commit();mood(null,w==0?null:activity,false);}).show();return;}
        Intent i=new Intent(a,MoodActivity.class).putExtra("date",a.selected.toString());
        if(existing!=null)i.putExtra("id",existing.optString("id"));
        if(activity!=null)i.putExtra("link",activity.source.optString("id")).putExtra("linkDate",a.selected.toString()).putExtra("linkTitle",activity.title);
        i.putExtra("fresh",fresh);a.startActivity(i);
    }
    void openEvent(MainActivity.Entry e){eventForm(e.source);}
    void moodCard(MainActivity.Entry e){
        LinearLayout line=a.row();line.setGravity(android.view.Gravity.TOP);LinearLayout rail=a.new TimelineRail();rail.setPadding(0,a.dp(14),0,0);rail.addView(a.text(e.time,15,a.INK,true));line.addView(rail,new LinearLayout.LayoutParams(a.dp(66),-1));
        LinearLayout card=a.column();card.setPadding(a.dp(17),a.dp(18),a.dp(17),a.dp(18));card.setBackground(a.shape(Color.WHITE,24));card.addView(a.text(MoodActivity.MOODS[e.source.optInt("mood",2)]+" · "+MoodActivity.ENERGY[e.source.optInt("energy",2)],14,a.BLUE,true));a.space(card,7);if(!e.source.optString("title").isEmpty())card.addView(a.text(e.title,19,a.INK,true));TextView excerpt=a.text(e.note,15,a.INK,false);excerpt.setMaxLines(3);excerpt.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(excerpt);if(!e.source.optString("labels").isEmpty())card.addView(a.text(e.source.optString("labels"),12,a.MUTED,false));JSONArray photos=e.source.optJSONArray("photos");if(photos!=null&&photos.length()>0){ImageView picture=new ImageView(a);picture.setScaleType(ImageView.ScaleType.CENTER_CROP);picture.setImageBitmap(MoodActivity.thumbnail(new java.io.File(a.getFilesDir(),"photos/"+photos.optString(0)),600));card.addView(picture,new LinearLayout.LayoutParams(-1,a.dp(110)));}if(!e.source.optString("linkTitle").isEmpty())card.addView(a.text("关联 · "+e.source.optString("linkTitle"),12,a.MUTED,false));card.setOnClickListener(v->mood(e.source,null,false));line.addView(card,new LinearLayout.LayoutParams(0,-2,1));a.body.addView(line);a.space(a.body,12);
    }
    static Set<Integer> days(JSONObject source){Set<Integer> r=new HashSet<>();JSONArray days=source.optJSONArray("weekdays");if(days!=null)for(int i=0;i<days.length();i++)r.add(days.optInt(i));return r;}
    static boolean occurs(JSONObject e,LocalDate date){
        Set<LocalDate> dates=new HashSet<>();for(String s:strings(e.optJSONArray("dates")))dates.add(LocalDate.parse(s));
        String until=e.optString("until");return RepeatRule.matches(LocalDate.parse(e.optString("date")),date,e.optString("repeat","none"),e.optInt("interval",1),days(e),dates,until.isEmpty()?null:LocalDate.parse(until),e.optInt("count",0));
    }
    static JSONObject occurrence(JSONObject event,LocalDate date){
        if(!occurs(event,date))return null;JSONObject overrides=event.optJSONObject("overrides");JSONObject o=overrides==null?null:overrides.optJSONObject(date.toString());if(o!=null&&o.optBoolean("deleted"))return null;
        JSONObject r=cloneJson(o==null?event:o);put(r,"id",event.optString("id"));put(r,"occurrence",date.toString());put(r,"repeat",event.optString("repeat","none"));if(o==null&&!event.optString("repeat","none").equals("none"))put(r,"done",false);return r;
    }
    void chooseFilter(){JSONArray labels=a.data.optJSONArray("labels");String[] names=new String[labels.length()+1];names[0]="全部标签";for(int i=0;i<labels.length();i++)names[i+1]=labels.optJSONObject(i).optString("name");new AlertDialog.Builder(a).setTitle("筛选活动标签").setItems(names,(d,w)->{tagFilter=w==0?"":labels.optJSONObject(w-1).optString("id");a.render();}).show();}
    List<String> tagPicker(LinearLayout f,JSONObject original,boolean course){
        List<String> chosen=new ArrayList<>(strings(tags(original==null?new JSONObject():original,course)));TextView button=a.action("选择活动标签",()->{});button.setTextSize(16);f.addView(button);
        Runnable refresh=()->{List<String> names=new ArrayList<>();JSONArray labels=a.data.optJSONArray("labels");for(int i=0;i<labels.length();i++)if(chosen.contains(labels.optJSONObject(i).optString("id")))names.add(labels.optJSONObject(i).optString("name"));button.setText(names.isEmpty()?"选择活动标签":String.join(" · ",names));};refresh.run();
        button.setOnClickListener(v->{JSONArray labels=a.data.optJSONArray("labels");String[] names=new String[labels.length()];boolean[] checked=new boolean[names.length];for(int i=0;i<names.length;i++){names[i]=labels.optJSONObject(i).optString("name");checked[i]=chosen.contains(labels.optJSONObject(i).optString("id"));}new AlertDialog.Builder(a).setTitle("活动标签（可多选）").setMultiChoiceItems(names,checked,(d,w,on)->{String id=labels.optJSONObject(w).optString("id");if(on&&!chosen.contains(id))chosen.add(id);else if(!on)chosen.remove(id);}).setPositiveButton("确定",(d,w)->refresh.run()).show();});return chosen;
    }
    void manageLabels(){JSONArray labels=a.data.optJSONArray("labels");String[] names=new String[labels.length()+1];names[0]="＋ 新建标签";for(int i=0;i<labels.length();i++)names[i+1]=labels.optJSONObject(i).optString("name");new AlertDialog.Builder(a).setTitle("标签管理").setItems(names,(d,w)->editLabel(w==0?null:labels.optJSONObject(w-1))).show();}
    void editLabel(JSONObject label){LinearLayout f=a.form();EditText name=a.field(f,"标签名称",label==null?"":label.optString("name"));Spinner color=new Spinner(a);color.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,new String[]{"蓝色","绿色","紫色","橙色","粉色","青色"}));int[] colors={0xff0868ce,0xff31956a,0xff9d6cc7,0xffd38a38,0xffca668c,0xff378e9c};if(label!=null)for(int i=0;i<colors.length;i++)if(colors[i]==label.optInt("color"))color.setSelection(i);f.addView(color);AlertDialog d=a.dialog(label==null?"新建标签":"编辑标签",f,()->{String value=name.getText().toString().trim();if(value.isEmpty())throw new IllegalArgumentException("请填写标签名称");JSONArray labels=a.data.optJSONArray("labels");for(int i=0;i<labels.length();i++){JSONObject l=labels.optJSONObject(i);if(l.optString("name").equals(value)&&(label==null||!l.optString("id").equals(label.optString("id"))))throw new IllegalArgumentException("这个标签名称已存在");}a.update("labels",label,a.obj("id",label==null?UUID.randomUUID().toString():label.optString("id"),"name",value,"color",colors[color.getSelectedItemPosition()]));});d.show();}
    void eventForm(JSONObject occurrence){
        JSONObject original=null;if(occurrence!=null)for(int i=0;i<a.events().length();i++)if(a.events().optJSONObject(i).optString("id").equals(occurrence.optString("id")))original=a.events().optJSONObject(i);
        final JSONObject series=original;
        if(series!=null&&!series.optString("repeat","none").equals("none")){new AlertDialog.Builder(a).setTitle("编辑重复活动").setItems(new String[]{"仅本次","本次及以后"},(d,w)->editor(occurrence,series,w==1)).show();}else editor(occurrence,series,false);
    }
    void editor(JSONObject e,JSONObject series,boolean future){
        LinearLayout f=a.form();EditText title=a.field(f,"日程标题",e==null?"":e.optString("title"));
        LocalDate occurrenceDate=e==null?a.selected:LocalDate.parse(e.optString("occurrence",e.optString("date")));LocalDate[] date={occurrenceDate};
        TextView dateButton=a.action(date[0].toString(),()->{});dateButton.setTextSize(16);dateButton.setOnClickListener(v->a.pickDate(date[0],d->{date[0]=d;dateButton.setText(d.toString());}));f.addView(dateButton);
        boolean repeating=series!=null&&!series.optString("repeat","none").equals("none");if(repeating) {dateButton.setEnabled(false);f.addView(a.text("本次日期固定；修改时间不会影响其他次的完成状态",12,a.MUTED,false));}
        Switch all=new Switch(a);all.setText("全天日程");all.setChecked(e!=null&&e.optBoolean("allDay"));f.addView(all);
        String[] times={e==null?"14:00":e.optString("start","14:00"),e==null?"":e.optString("end")};LinearLayout timeArea=a.column();f.addView(timeArea);
        timeArea.addView(a.text("开始时间",13,a.MUTED,false));TextView start=a.action(times[0],()->{});start.setOnClickListener(v->a.timePicker(start,times,0));timeArea.addView(start);
        timeArea.addView(a.text("结束时间（可选）",13,a.MUTED,false));LinearLayout endRow=a.row();TextView end=a.action(times[1].isEmpty()?"选择时间":times[1],()->{});end.setOnClickListener(v->a.timePicker(end,times,1));endRow.addView(end,new LinearLayout.LayoutParams(0,a.dp(48),1));endRow.addView(a.action("清除",()->{times[1]="";end.setText("选择时间");}));timeArea.addView(endRow);timeArea.setVisibility(all.isChecked()?View.GONE:View.VISIBLE);all.setOnCheckedChangeListener((b,on)->timeArea.setVisibility(on?View.GONE:View.VISIBLE));
        EditText note=a.field(f,"备注（可选）",e==null?"":e.optString("note"));List<String> tags=tagPicker(f,e,false);
        CheckBox done=new CheckBox(a);done.setText("已完成");done.setChecked(e!=null&&e.optBoolean("done"));f.addView(done);
        boolean allowRule=!repeating||future;LinearLayout rules=a.column();f.addView(rules);
        Spinner mode=new Spinner(a);mode.setContentDescription("重复方式");mode.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,new String[]{"不重复","每天／每隔几天","每周指定星期","指定多个日期"}));String[] modes={"none","daily","weekly","dates"};JSONObject base=series==null?new JSONObject():series;for(int i=0;i<4;i++)if(modes[i].equals(base.optString("repeat","none")))mode.setSelection(i);rules.addView(a.text("重复方式",13,a.MUTED,false));rules.addView(mode);
        EditText interval=a.field(rules,"间隔（天或周，1–365）",""+base.optInt("interval",1));interval.setInputType(2);
        LinearLayout weekdays=a.row();CheckBox[] days=new CheckBox[7];Set<Integer> selectedDays=days(base);for(int i=0;i<7;i++){days[i]=new CheckBox(a);days[i].setText(a.DAYS[i]);days[i].setTextSize(12);days[i].setChecked(selectedDays.isEmpty()?i==date[0].getDayOfWeek().getValue()-1:selectedDays.contains(i+1));weekdays.addView(days[i]);}HorizontalScrollView hs=new HorizontalScrollView(a);hs.addView(weekdays);rules.addView(hs);
        TreeSet<String> dates=new TreeSet<>(strings(base.optJSONArray("dates")));if(future)dates.removeIf(s->LocalDate.parse(s).isBefore(occurrenceDate));TextView datesButton=a.action("选择具体日期",()->{});datesButton.setTextSize(15);rules.addView(datesButton);TextView dateList=a.text(String.join("、",dates),13,a.MUTED,false);rules.addView(dateList);datesButton.setOnClickListener(v->new AlertDialog.Builder(a).setTitle("指定日期").setItems(new String[]{"添加日期","清空已选日期"},(dialog,w)->{if(w==0)a.pickDate(date[0],d->{dates.add(d.toString());dateList.setText(String.join("、",dates));});else {dates.clear();dateList.setText("");}}).show());
        Spinner ending=new Spinner(a);ending.setContentDescription("重复结束方式");ending.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,new String[]{"不设结束日期","截止某天（含当天）","共执行若干次"}));ending.setSelection(!base.optString("until").isEmpty()?1:base.optInt("count")>0?2:0);rules.addView(ending);
        LocalDate[] until={base.optString("until").isEmpty()?date[0].plusMonths(1):LocalDate.parse(base.optString("until"))};TextView untilButton=a.action(until[0].toString(),()->{});untilButton.setTextSize(16);untilButton.setOnClickListener(v->a.pickDate(until[0],d->{until[0]=d;untilButton.setText(d.toString());}));rules.addView(untilButton);EditText count=a.field(rules,"执行次数（不是天数）",""+base.optInt("count",3));if(count.getText().toString().equals("0"))count.setText("3");if(future&&base.optInt("count")>0){JSONObject unbounded=cloneJson(base);unbounded.remove("count");int passed=0;for(LocalDate d=LocalDate.parse(base.optString("date"));d.isBefore(occurrenceDate);d=d.plusDays(1))if(occurs(unbounded,d))passed++;count.setText(""+Math.max(1,base.optInt("count")-passed));}count.setInputType(2);
        Runnable show=()->{int m=mode.getSelectedItemPosition(),endMode=ending.getSelectedItemPosition();interval.setVisibility(m==1||m==2?View.VISIBLE:View.GONE);hs.setVisibility(m==2?View.VISIBLE:View.GONE);datesButton.setVisibility(m==3?View.VISIBLE:View.GONE);dateList.setVisibility(m==3?View.VISIBLE:View.GONE);ending.setVisibility(m>0?View.VISIBLE:View.GONE);untilButton.setVisibility(m>0&&endMode==1?View.VISIBLE:View.GONE);count.setVisibility(m>0&&endMode==2?View.VISIBLE:View.GONE);};
        AdapterView.OnItemSelectedListener listener=new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int i,long id){show.run();}};mode.setOnItemSelectedListener(listener);ending.setOnItemSelectedListener(listener);show.run();if(!allowRule)rules.setVisibility(View.GONE);
        AlertDialog dialog=a.dialog(e==null?"新增日程":"编辑日程",f,()->{
            String name=title.getText().toString().trim();if(name.isEmpty())throw new IllegalArgumentException("请填写日程标题");if(!all.isChecked()&&!times[1].isEmpty()&&ScheduleRules.minutes(times[1])<=ScheduleRules.minutes(times[0]))throw new IllegalArgumentException("结束时间须晚于开始时间");
            JSONObject value=a.obj("id",series==null?UUID.randomUUID().toString():series.optString("id"),"title",name,"date",date[0].toString(),"allDay",all.isChecked(),"start",times[0],"end",times[1],"note",note.getText().toString().trim(),"tags",new JSONArray(tags),"done",done.isChecked());
            if(allowRule){int m=mode.getSelectedItemPosition();put(value,"repeat",modes[m]);int step=1;try{if(m==1||m==2)step=Integer.parseInt(interval.getText().toString());}catch(Exception ex){throw new IllegalArgumentException("间隔须为数字");}if(step<1||step>365)throw new IllegalArgumentException("间隔须为 1–365");put(value,"interval",step);JSONArray week=new JSONArray();for(int i=0;i<7;i++)if(days[i].isChecked())week.put(i+1);if(m==2&&week.length()==0)throw new IllegalArgumentException("请选择至少一个星期");put(value,"weekdays",week);if(m==3){if(dates.isEmpty())throw new IllegalArgumentException("请选择具体日期");if(LocalDate.parse(dates.first()).isBefore(date[0]))throw new IllegalArgumentException("指定日期不能早于开始日期");}put(value,"dates",new JSONArray(dates));int endMode=ending.getSelectedItemPosition();if(m>0&&endMode==1){if(until[0].isBefore(date[0]))throw new IllegalArgumentException("截止日期不能早于开始日期");put(value,"until",until[0].toString());}if(m>0&&endMode==2){int n;try{n=Integer.parseInt(count.getText().toString());}catch(Exception ex){throw new IllegalArgumentException("请输入执行次数");}if(n<1||n>10000)throw new IllegalArgumentException("执行次数须为 1–10000");put(value,"count",n);}}
            saveEvent(series,value,occurrenceDate,future,false);a.selected=date[0];
        });
        if(e!=null){f.addView(a.action("记录感受",()->{dialog.dismiss();MainActivity.Entry activity=new MainActivity.Entry();activity.source=e;activity.title=e.optString("title");mood(null,activity,true);}));TextView delete=a.action("删除",()->new AlertDialog.Builder(a).setTitle(future?"删除本次及以后的活动？":"删除这次活动？").setMessage("关联的心情记录将保留。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{saveEvent(series,null,occurrenceDate,future,true);dialog.dismiss();a.render();}).show());f.addView(delete);}
        dialog.show();
    }
    void saveEvent(JSONObject series,JSONObject value,LocalDate date,boolean future,boolean delete){
        if(value!=null&&(series==null||future||series.optString("repeat","none").equals("none"))&&value.optBoolean("done")&&!value.optString("repeat","none").equals("none"))put(value,"overrides",a.obj(date.toString(),cloneJson(value)));
        if(series==null||series.optString("repeat","none").equals("none")){a.update("events",series,value);return;}
        JSONObject next=a.copy();JSONArray arr=next.optJSONArray("events");JSONObject old=null;for(int i=0;i<arr.length();i++)if(arr.optJSONObject(i).optString("id").equals(series.optString("id")))old=arr.optJSONObject(i);
        if(old==null)throw new IllegalArgumentException("原活动已不存在");JSONObject overrides=old.optJSONObject("overrides");if(overrides==null)overrides=new JSONObject();
        if(!future){put(overrides,date.toString(),delete?a.obj("deleted",true):value);put(old,"overrides",overrides);}
        else {
            // Completed occurrences survive shortening/deleting a series, even when in the future.
            Iterator<String> keys=overrides.keys();while(keys.hasNext()){String key=keys.next();JSONObject o=overrides.optJSONObject(key);if(!LocalDate.parse(key).isBefore(date)&&o.optBoolean("done")&&!o.optBoolean("deleted")){JSONObject history=cloneJson(o);put(history,"id",UUID.randomUUID().toString());put(history,"repeat","none");put(history,"date",key);arr.put(history);if(value!=null){JSONObject skipped=value.optJSONObject("overrides");if(skipped==null)skipped=new JSONObject();put(skipped,key,a.obj("deleted",true));put(value,"overrides",skipped);}}}
            put(old,"until",date.minusDays(1).toString());if(value!=null){put(value,"id",UUID.randomUUID().toString());arr.put(value);}
        }
        if(!a.persist(next))throw new IllegalArgumentException("保存失败，请重试");
    }
}
