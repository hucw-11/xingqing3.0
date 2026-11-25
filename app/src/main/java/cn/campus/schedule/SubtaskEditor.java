package cn.campus.schedule;
import android.app.AlertDialog;
import android.widget.*;
import org.json.*;
import java.util.*;

final class SubtaskEditor {
    final MainActivity a;final LinearLayout area;final CheckBox parent;
    final List<JSONObject> rows=new ArrayList<>();boolean syncing;
    SubtaskEditor(MainActivity a,LinearLayout form,JSONObject source,CheckBox parent){this.a=a;this.parent=parent;JSONArray items=TaskRules.children(source);for(int i=0;i<items.length();i++)rows.add(items.optJSONObject(i));form.addView(a.text("子任务",14,a.INK,true));area=a.column();form.addView(area);form.addView(a.action("＋ 添加子任务",this::add));draw();parent.setOnCheckedChangeListener((b,on)->{if(syncing)return;if(on&&rows.stream().anyMatch(r->!r.optBoolean("done"))){syncing=true;parent.setChecked(false);syncing=false;new AlertDialog.Builder(a).setTitle("一并完成剩余子任务？").setNegativeButton("取消",null).setPositiveButton("全部完成",(d,w)->{for(JSONObject r:rows)AgendaFeatures.put(r,"done",true);syncing=true;parent.setChecked(true);syncing=false;draw();}).show();}});}
    void add(){LinearLayout f=a.form();EditText title=a.field(f,"子任务名称","");AlertDialog d=new AlertDialog.Builder(a).setTitle("添加子任务").setView(f).setNegativeButton("取消",null).setPositiveButton("添加",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{String name=title.getText().toString().trim();if(name.isEmpty()){a.toast("请填写子任务名称");return;}rows.add(a.obj("id",UUID.randomUUID().toString(),"title",name,"done",false));syncing=true;parent.setChecked(false);syncing=false;draw();d.dismiss();}));d.show();}
    void draw(){area.removeAllViews();for(JSONObject r:new ArrayList<>(rows)){LinearLayout line=a.row();CheckBox done=new CheckBox(a);done.setChecked(r.optBoolean("done"));done.setOnCheckedChangeListener((b,on)->{AgendaFeatures.put(r,"done",on);syncing=true;parent.setChecked(!rows.isEmpty()&&rows.stream().allMatch(v->v.optBoolean("done")));syncing=false;});line.addView(done);EditText title=new EditText(a);title.setSingleLine(true);title.setText(r.optString("title"));title.setContentDescription("子任务名称");title.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int p,int c,int n){}public void onTextChanged(CharSequence s,int p,int b,int c){AgendaFeatures.put(r,"title",s.toString());}public void afterTextChanged(android.text.Editable e){}});line.addView(title,new LinearLayout.LayoutParams(0,-2,1));line.addView(a.action("×",()->{rows.remove(r);draw();}));area.addView(line);}}
    JSONArray value(){JSONArray result=new JSONArray();for(JSONObject row:rows){if(row.optString("title").trim().isEmpty())throw new IllegalArgumentException("子任务名称不能为空");result.put(AgendaFeatures.cloneJson(row));}return result;}
}
