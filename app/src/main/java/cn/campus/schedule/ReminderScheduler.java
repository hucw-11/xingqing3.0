package cn.campus.schedule;
import android.app.*;
import android.content.*;
import android.os.Build;
import org.json.*;
import java.time.*;
import java.util.*;

final class ReminderScheduler {
    static final String TICK="cn.campus.schedule.REMINDER";
    static JSONObject data(Context c)throws JSONException{return new JSONObject(c.getSharedPreferences("MainActivity",0).getString("data","{}"));}
    static PendingIntent alarm(Context c){return PendingIntent.getBroadcast(c,44,new Intent(c,ReminderReceiver.class).setAction(TICK),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static boolean exact(Context c){return Build.VERSION.SDK_INT<31||c.getSystemService(AlarmManager.class).canScheduleExactAlarms();}
    static synchronized void schedule(Context context){Context c=context.getApplicationContext();SharedPreferences prefs=c.getSharedPreferences("reminders",0);AlarmManager manager=c.getSystemService(AlarmManager.class);try{JSONObject source=data(c);if(!source.has("events"))return;JSONObject next=ReminderRules.next(source,LocalDateTime.now(),prefs.getBoolean("summary",true),prefs.getBoolean("courses",true));manager.cancel(alarm(c));if(next==null){prefs.edit().remove("plan").apply();return;}prefs.edit().putString("plan",next.toString()).apply();long timestamp=LocalDateTime.parse(next.optString("at")).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();try{if(exact(c))manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,timestamp,alarm(c));else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,timestamp,alarm(c));}catch(SecurityException e){manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,timestamp,alarm(c));}}catch(Exception e){prefs.edit().putString("schedulerStatus","提醒安排失败，请重新打开提醒设置").apply();}}
    static boolean permitted(Context c){return c.getSystemService(NotificationManager.class).areNotificationsEnabled();}
    static boolean deliver(Context c,JSONObject plan){if(!permitted(c))return false;NotificationManager nm=c.getSystemService(NotificationManager.class);boolean course=plan.optString("kind").equals("course");String channel=course?"course-reminders":"task-summary";nm.createNotificationChannel(new NotificationChannel(channel,course?"课前提醒":"全天任务与 DDL 汇总",course?NotificationManager.IMPORTANCE_HIGH:NotificationManager.IMPORTANCE_DEFAULT));List<String> lines=new ArrayList<>();String title;
        if(course){title="15 分钟后上课 · "+plan.optString("title");lines.add(plan.optString("start")+" · "+plan.optString("room"));}else{try{LocalDate today=LocalDate.now();List<JSONObject> tasks=ReminderRules.summary(data(c),today);if(tasks.isEmpty())return false;title="心晴3.0 · "+tasks.size()+" 项未完成任务";for(JSONObject task:tasks){String ddl=ReminderRules.deadlineText(task,today);lines.add(task.optString("title")+(task.optBoolean("carried")?"（顺延）":"")+(ddl.isEmpty()?"":" · "+ddl));}}catch(Exception e){return false;}}
        Intent open=new Intent(c,MainActivity.class).putExtra("page","agenda").putExtra("date",plan.optString("date")).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);PendingIntent content=PendingIntent.getActivity(c,course?45:46,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification.InboxStyle style=new Notification.InboxStyle();for(int i=0;i<Math.min(7,lines.size());i++)style.addLine(lines.get(i));if(lines.size()>7)style.setSummaryText("另有 "+(lines.size()-7)+" 项，点击查看全部");Notification notification=new Notification.Builder(c,channel).setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(lines.get(0)).setStyle(style).setContentIntent(content).setAutoCancel(true).setCategory(Notification.CATEGORY_REMINDER).build();try{nm.notify(course?plan.optString("key").hashCode():200,notification);return true;}catch(SecurityException e){return false;}
    }
}
