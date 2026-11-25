package cn.campus.schedule;
import android.content.*;
import org.json.*;
import java.time.*;
public class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){PendingResult pending=goAsync();new Thread(()->{try{if(ReminderScheduler.TICK.equals(intent.getAction())){SharedPreferences prefs=context.getSharedPreferences("reminders",0);String value=prefs.getString("plan","");if(!value.isEmpty()){JSONObject plan=new JSONObject(value);LocalDateTime now=LocalDateTime.now(),at=LocalDateTime.parse(plan.optString("at"));String key=plan.optString("key");if(!now.isBefore(at.minusSeconds(2))&&now.isBefore(at.plusHours(3))&&!prefs.getString("lastDelivered","").equals(key)){ReminderScheduler.deliver(context,plan);JSONArray siblings=plan.optJSONArray("siblings");if(siblings!=null)for(int i=0;i<siblings.length();i++)ReminderScheduler.deliver(context,siblings.optJSONObject(i));prefs.edit().putString("lastDelivered",key).commit();}}}ReminderScheduler.schedule(context);}catch(Exception ignored){}finally{pending.finish();}},"reminder-delivery").start();}
}
