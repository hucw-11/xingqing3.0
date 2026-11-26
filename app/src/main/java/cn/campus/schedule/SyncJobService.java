package cn.campus.schedule;
import android.app.job.*;
public class SyncJobService extends JobService {
    @Override public boolean onStartJob(JobParameters params){SyncManager.NETWORK.execute(()->{SyncManager.run(getApplicationContext());jobFinished(params,false);});return true;}
    @Override public boolean onStopJob(JobParameters params){return true;}
}
