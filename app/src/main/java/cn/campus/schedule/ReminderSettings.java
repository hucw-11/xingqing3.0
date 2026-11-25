package cn.campus.schedule;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.*;
final class ReminderSettings {
    static String status(MainActivity a){return "通知："+(ReminderScheduler.permitted(a)?"已允许":"未允许，系统通知不会送达")+"\n准时提醒："+(ReminderScheduler.exact(a)?"已允许":"未允许，系统可能延迟");}
    static void show(MainActivity a){LinearLayout f=a.form();SharedPreferences prefs=a.getSharedPreferences("reminders",0);Switch summary=new Switch(a);summary.setText("08:30、14:30、19:30 任务汇总");summary.setChecked(prefs.getBoolean("summary",true));f.addView(summary);Switch course=new Switch(a);course.setText("上课前 15 分钟提醒");course.setChecked(prefs.getBoolean("courses",true));f.addView(course);f.addView(a.text("汇总仅列未完成母任务，含顺延任务；有 DDL 的任务注明截止日期和倒计时。",14,a.MUTED,false));TextView permissionState=a.text(status(a),14,a.INK,true);f.addView(permissionState);f.addView(a.action("允许系统通知",()->{if(Build.VERSION.SDK_INT>=33&&a.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)a.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},91);else a.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,a.getPackageName()));}));f.addView(a.action("允许准时提醒",()->{if(Build.VERSION.SDK_INT>=31){try{a.startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+a.getPackageName())));}catch(ActivityNotFoundException e){a.toast("请在系统设置中允许闹钟和提醒权限");}}else a.toast("此系统无需单独授权");}));f.addView(a.text("手机重启后会重新安排提醒。部分手机还需在系统中允许后台运行；系统强行停止 App 后，请重新打开以恢复。",12,a.MUTED,false));AlertDialog dialog=a.dialog("提醒设置",f,()->{prefs.edit().putBoolean("summary",summary.isChecked()).putBoolean("courses",course.isChecked()).commit();ReminderScheduler.schedule(a);});dialog.show();dialog.getWindow().getDecorView().getViewTreeObserver().addOnWindowFocusChangeListener(focused->{if(focused)permissionState.setText(status(a));});}
}
