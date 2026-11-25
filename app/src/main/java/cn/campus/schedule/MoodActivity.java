package cn.campus.schedule;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import org.json.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Full-page journal editor. Drafts use separate keys and never overwrite a saved entry. */
public class MoodActivity extends Activity {
    public static final String[] MOODS={"😞 很差","🙁 低落","😐 平静","🙂 愉快","😄 很棒"};
    public static final String[] ENERGY={"疲惫","偏低","一般","充沛","精力十足"};
    SharedPreferences prefs;String id,key,pending="";JSONObject record;
    EditText title,body,labels;Spinner mood,energy;LocalDate date;String time;LinearLayout photos;TextView dateButton,timeButton,status;
    final List<String> images=new ArrayList<>();boolean ready=false,closed=false,importing=false;
    final java.util.concurrent.ExecutorService io=java.util.concurrent.Executors.newSingleThreadExecutor();
    int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    TextView text(String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(0xff171c26);return v;}
    LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    TextView button(String value,Runnable action){TextView v=text(value,16);v.setTextColor(0xff0868ce);v.setPadding(dp(8),dp(14),dp(8),dp(14));v.setGravity(Gravity.CENTER);v.setOnClickListener(w->action.run());return v;}
    void error(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    static void put(JSONObject o,String k,Object v){AgendaFeatures.put(o,k,v);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);prefs=getSharedPreferences("MainActivity",0);id=getIntent().getStringExtra("id");key="draft:"+(id==null?"new":id);
        try{
            record=new JSONObject();JSONObject data=new JSONObject(prefs.getString("data","{}"));JSONArray moods=data.optJSONArray("moods");if(id!=null&&moods!=null)for(int i=0;i<moods.length();i++)if(moods.optJSONObject(i).optString("id").equals(id))record=AgendaFeatures.cloneJson(moods.optJSONObject(i));
            if(id==null){put(record,"date",getIntent().getStringExtra("date"));put(record,"time",LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));put(record,"mood",2);put(record,"energy",2);put(record,"link",getIntent().getStringExtra("link"));put(record,"linkDate",getIntent().getStringExtra("linkDate"));put(record,"linkTitle",getIntent().getStringExtra("linkTitle"));}
            String draft=prefs.getString(key,null);if(draft!=null&&(!getIntent().getBooleanExtra("fresh",false)||state!=null))record=new JSONObject(draft);
            date=LocalDate.parse(record.optString("date",LocalDate.now().toString()));time=record.optString("time","12:00");images.addAll(AgendaFeatures.strings(record.optJSONArray("photos")));pending=prefs.getString("pendingCamera","");
        }catch(Exception e){error("记录读取失败，原始数据已保留");finish();return;}
        LinearLayout root=col();root.setPadding(dp(20),dp(8),dp(20),dp(12));root.setBackgroundColor(0xfff3f4f8);setContentView(root);
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);toolbar.addView(button("返回",this::finish));TextView heading=text(id==null?"记录此刻":"心情日记",23);heading.setTypeface(null,Typeface.BOLD);toolbar.addView(heading,new LinearLayout.LayoutParams(0,dp(56),1));toolbar.addView(button("保存",this::save));root.addView(toolbar);
        ScrollView scroll=new ScrollView(this);LinearLayout content=col();scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout when=new LinearLayout(this);dateButton=button(date.toString(),()->new DatePickerDialog(this,(v,y,m,d)->{date=LocalDate.of(y,m+1,d);dateButton.setText(date.toString());draft();},date.getYear(),date.getMonthValue()-1,date.getDayOfMonth()).show());when.addView(dateButton,new LinearLayout.LayoutParams(0,-2,1));timeButton=button(time,()->new TimePickerDialog(this,(v,h,m)->{time=String.format(Locale.ROOT,"%02d:%02d",h,m);timeButton.setText(time);draft();},Integer.parseInt(time.substring(0,2)),Integer.parseInt(time.substring(3)),true).show());when.addView(timeButton);content.addView(when);
        title=edit(content,"标题（可选）",record.optString("title"),false);title.setTextSize(23);
        content.addView(text("此刻心情",13));mood=spinner(content,MOODS,record.optInt("mood",2));content.addView(text("此刻活力",13));energy=spinner(content,ENERGY,record.optInt("energy",2));
        body=edit(content,"写下此刻的想法，长篇日记也可以……",record.optString("body"),true);body.setMinLines(9);body.setGravity(Gravity.TOP);body.setTextSize(17);body.setLineSpacing(dp(5),1);body.setHorizontallyScrolling(false);body.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        labels=edit(content,"心情标签，用逗号分隔，例如 放松,有成就感",record.optString("labels"),false);
        if(!record.optString("linkTitle").isEmpty())content.addView(text("关联活动 · "+record.optString("linkTitle"),14));
        LinearLayout add=new LinearLayout(this);add.addView(button("拍照",()->photo(true)),new LinearLayout.LayoutParams(0,-2,1));add.addView(button("从相册选图",()->photo(false)),new LinearLayout.LayoutParams(0,-2,1));content.addView(add);photos=col();content.addView(photos);refreshPhotos();
        status=text("草稿自动保存在本机",12);content.addView(status);content.addView(button("放弃草稿",()->new AlertDialog.Builder(this).setTitle("放弃未保存的修改？").setNegativeButton("继续写",null).setPositiveButton("放弃",(d,w)->{closed=true;prefs.edit().remove(key).commit();cleanPhotos();finish();}).show()));
        if(id!=null)content.addView(button("删除这篇记录",()->new AlertDialog.Builder(this).setTitle("删除这篇心情日记？").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{try{JSONObject data=new JSONObject(prefs.getString("data","{}"));JSONArray arr=new JSONArray(),old=data.optJSONArray("moods");for(int i=0;i<old.length();i++)if(!old.optJSONObject(i).optString("id").equals(id))arr.put(old.optJSONObject(i));put(data,"moods",arr);if(!prefs.edit().putString("data",data.toString()).remove(key).commit())throw new IOException();closed=true;cleanPhotos();finish();}catch(Exception e){error("删除失败，请重试");}}).show()));
        TextWatcher watcher=new TextWatcher(){public void beforeTextChanged(CharSequence s,int p,int c,int n){}public void onTextChanged(CharSequence s,int p,int b,int c){draft();}public void afterTextChanged(Editable e){}};title.addTextChangedListener(watcher);body.addTextChangedListener(watcher);labels.addTextChangedListener(watcher);
        AdapterView.OnItemSelectedListener listener=new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int i,long l){draft();}};mood.setOnItemSelectedListener(listener);energy.setOnItemSelectedListener(listener);ready=true;
    }
    EditText edit(LinearLayout parent,String hint,String value,boolean multi){EditText e=new EditText(this);e.setHint(hint);e.setContentDescription(hint);e.setText(value);e.setSingleLine(!multi);parent.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    Spinner spinner(LinearLayout parent,String[] values,int selection){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));s.setSelection(selection);parent.addView(s,new LinearLayout.LayoutParams(-1,dp(48)));return s;}
    JSONObject snapshot(){JSONObject r=AgendaFeatures.cloneJson(record);put(r,"title",title.getText().toString());put(r,"body",body.getText().toString());put(r,"labels",labels.getText().toString());put(r,"mood",mood.getSelectedItemPosition());put(r,"energy",energy.getSelectedItemPosition());put(r,"date",date.toString());put(r,"time",time);put(r,"photos",new JSONArray(images));return r;}
    void draft(){if(ready&&!closed){prefs.edit().putString(key,snapshot().toString()).apply();if(status!=null)status.setText("草稿已自动保存");}}
    @Override protected void onPause(){super.onPause();if(ready&&!closed)prefs.edit().putString(key,snapshot().toString()).commit();}
    @Override public void finish(){if(importing){error("照片正在保存，请稍候");return;}super.finish();}
    @Override protected void onDestroy(){super.onDestroy();io.shutdown();}
    void cleanPhotos(){io.execute(()->{File dir=new File(getFilesDir(),"photos");File[] files=dir.listFiles();if(files==null)return;StringBuilder references=new StringBuilder();for(Map.Entry<String,?> entry:prefs.getAll().entrySet())if(entry.getKey().equals("data")||entry.getKey().startsWith("draft:"))references.append(entry.getValue());for(File f:files)if(!references.toString().contains(f.getName()))f.delete();});}
    void save(){if(importing){error("照片正在保存，请稍候");return;}JSONObject r=snapshot();if(r.optString("title").trim().isEmpty()&&r.optString("body").trim().isEmpty()&&images.isEmpty()){error("写一点文字或添加照片再保存吧");return;}try{JSONObject data=new JSONObject(prefs.getString("data","{}"));JSONArray old=data.optJSONArray("moods"),arr=new JSONArray();put(r,"id",id==null?UUID.randomUUID().toString():id);boolean found=false;if(old!=null)for(int i=0;i<old.length();i++){JSONObject item=old.optJSONObject(i);if(item.optString("id").equals(id)){arr.put(r);found=true;}else arr.put(item);}if(!found)arr.put(r);put(data,"moods",arr);if(!prefs.edit().putString("data",data.toString()).remove(key).commit())throw new IOException();closed=true;cleanPhotos();finish();}catch(Exception e){error("保存失败，草稿仍然保留");}}
    void photo(boolean camera){if(images.size()>=9){error("每篇最多 9 张照片");return;}draft();try{if(camera){File dir=new File(getCacheDir(),"camera");if(!dir.exists()&&!dir.mkdirs())throw new IOException();File target=File.createTempFile("capture-",".jpg",dir);pending=target.getAbsolutePath();prefs.edit().putString("pendingCamera",pending).commit();Uri uri=FileProvider.getUriForFile(this,getPackageName()+".photos",target);Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT,uri).addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);i.setClipData(ClipData.newRawUri("photo",uri));startActivityForResult(i,71);}else{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,72);}}catch(Exception e){error(camera?"无法打开相机，请使用相册选图":"无法打开相册");}}
    @Override protected void onActivityResult(int request,int result,Intent intent){super.onActivityResult(request,result,intent);if(result!=RESULT_OK)return;List<Uri> uris=new ArrayList<>();if(request==71){pending=prefs.getString("pendingCamera",pending);if(!pending.isEmpty())uris.add(Uri.fromFile(new File(pending)));}else if(request==72&&intent!=null){if(intent.getClipData()!=null)for(int i=0;i<intent.getClipData().getItemCount();i++)uris.add(intent.getClipData().getItemAt(i).getUri());else if(intent.getData()!=null)uris.add(intent.getData());}if(uris.isEmpty())return;int slots=9-images.size();if(uris.size()>slots)error("仅保留前 "+slots+" 张照片");importing=true;io.execute(()->{List<String> added=new ArrayList<>();for(Uri uri:uris.subList(0,Math.min(slots,uris.size())))try{added.add(importPhoto(uri));}catch(Exception|OutOfMemoryError e){runOnUiThread(()->error("部分照片无法读取，请选择其他图片"));}runOnUiThread(()->{images.addAll(added);importing=false;prefs.edit().remove("pendingCamera").apply();refreshPhotos();draft();});});}
    String importPhoto(Uri uri)throws Exception{
        File dir=new File(getFilesDir(),"photos");if(!dir.exists()&&!dir.mkdirs())throw new IOException();File temp=File.createTempFile("import-",".tmp",getCacheDir());
        try{try(InputStream input=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temp)){if(input==null)throw new IOException();byte[] b=new byte[8192];int n,total=0;while((n=input.read(b))!=-1){total+=n;if(total>40*1024*1024)throw new IOException("too large");out.write(b,0,n);}}
            Bitmap bitmap=thumbnail(temp,2048);if(bitmap==null)throw new IOException();try{android.media.ExifInterface exif=new android.media.ExifInterface(temp.getAbsolutePath());int orientation=exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);Matrix matrix=new Matrix();switch(orientation){case 2:matrix.setScale(-1,1);break;case 3:matrix.setRotate(180);break;case 4:matrix.setScale(1,-1);break;case 5:matrix.setRotate(90);matrix.postScale(-1,1);break;case 6:matrix.setRotate(90);break;case 7:matrix.setRotate(270);matrix.postScale(-1,1);break;case 8:matrix.setRotate(270);break;default:break;}if(!matrix.isIdentity()){Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);if(rotated!=bitmap)bitmap.recycle();bitmap=rotated;}}catch(IOException ignored){}
            String name=UUID.randomUUID()+".jpg";try(OutputStream out=new FileOutputStream(new File(dir,name))){if(!bitmap.compress(Bitmap.CompressFormat.JPEG,90,out))throw new IOException();}finally{bitmap.recycle();}return name;
        }finally{if(!temp.delete())temp.deleteOnExit();}
    }
    public static Bitmap thumbnail(File file,int size){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getAbsolutePath(),o);o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>size)o.inSampleSize*=2;o.inJustDecodeBounds=false;return BitmapFactory.decodeFile(file.getAbsolutePath(),o);}
    void refreshPhotos(){photos.removeAllViews();for(String name:new ArrayList<>(images)){LinearLayout card=col();ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImageBitmap(thumbnail(new File(getFilesDir(),"photos/"+name),600));card.addView(image,new LinearLayout.LayoutParams(-1,dp(180)));image.setOnClickListener(v->{ImageView full=new ImageView(this);full.setAdjustViewBounds(true);full.setImageBitmap(thumbnail(new File(getFilesDir(),"photos/"+name),2048));new AlertDialog.Builder(this).setView(full).setPositiveButton("关闭",null).show();});card.addView(button("移除此照片",()->{images.remove(name);refreshPhotos();draft();}));photos.addView(card);}}
}
