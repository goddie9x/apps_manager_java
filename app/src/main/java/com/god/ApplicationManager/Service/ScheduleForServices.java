package com.god.ApplicationManager.Service;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;

import com.god.ApplicationManager.Facade.ServiceHandlerFacade;

public class ScheduleForServices extends JobService {
    private static final String TAG = "ScheduleForServices";
    public static boolean isJobCalled = false;
    @Override
    public boolean onStartJob(JobParameters params) {
        doInBackGround(params);
        isJobCalled = false;
        return false;
    }

    private void doInBackGround(JobParameters params) {
        new Thread(()->{
            int time = 0;
            while (!isJobCalled&& time<21)
            {
                ServiceHandlerFacade.startServices(this);
                try{
                    Thread.sleep(45000);
                    time++;
                }catch (InterruptedException e){
                    Log.e(TAG,e.getMessage());
                }
            }
            jobFinished(params,true);
        }).start();
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.e(TAG,"Job cancel");
        isJobCalled = true;
        return false;
    }
}
