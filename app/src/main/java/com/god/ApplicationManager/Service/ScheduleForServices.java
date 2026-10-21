package com.god.ApplicationManager.Service;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;

import com.god.ApplicationManager.Facade.ServiceHandlerFacade;

public class ScheduleForServices extends JobService {
    private static final String TAG = "ScheduleForServices";
    private volatile boolean jobStopped = false;

    @Override
    public boolean onStartJob(JobParameters params) {
        jobStopped = false;
        doInBackGround(params);
        return true;
    }

    private void doInBackGround(JobParameters params) {
        new Thread(() -> {
            int time = 0;
            while (!jobStopped && time < 21) {
                ServiceHandlerFacade.startServices(this);
                try {
                    Thread.sleep(45000);
                    time++;
                } catch (InterruptedException e) {
                    Log.e(TAG, "Service check loop interrupted", e);
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            jobFinished(params, false);
        }).start();
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.i(TAG, "Job stopped by system; asking to reschedule");
        jobStopped = true;
        return true;
    }
}
