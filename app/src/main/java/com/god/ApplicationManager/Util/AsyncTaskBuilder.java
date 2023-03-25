package com.god.ApplicationManager.Util;

import android.os.AsyncTask;

/*
 * T is the type parameter of doInBackground
 * V is the type parameter of publishProgress
 * U is the return value when end Task
 */
public class AsyncTaskBuilder<T,V,U> extends AsyncTask<T,V,U> {
    public interface DoInBackground<U,T>{
         U callback(T...ts);
    }
    private DoInBackground doInBackgroundFunc;
    public interface OnPreExecute{
         void callback();
    }
    private OnPreExecute onPreExecuteFunc;
    public interface OnPostExecute<U>{
         void callback(U u);
    }
    private OnPostExecute onPostExecuteFunc;
    public interface OnProgressUpdate<V>{
        void callback(V... values);
    }
    private OnProgressUpdate onProgressUpdateFunc;
    public interface OnCancelledWithParam<U>{
        public void callback(U u);
    }
    private OnCancelledWithParam onCancelledWithParamFunc;
    public interface OnCancelled{
        public void callback();
    }
    private OnCancelled onCancelledFunc;
    public AsyncTaskBuilder() {
        super();
    }

    @Override
    protected U doInBackground(T... ts) {
        if(doInBackgroundFunc!=null){
            return (U) doInBackgroundFunc.callback(ts);
        }
        else return null;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        if(onPreExecuteFunc!=null){
            onPreExecuteFunc.callback();
        }
    }

    @Override
    protected void onPostExecute(U u) {
        super.onPostExecute(u);
        if(onPostExecuteFunc!=null){
            onPostExecuteFunc.callback(u);
        }
    }

    @Override
    protected void onProgressUpdate(V... values) {
        super.onProgressUpdate(values);
        if(onProgressUpdateFunc!=null){
            onProgressUpdateFunc.callback(values);
        }
    }

    @Override
    protected void onCancelled(U u) {
        super.onCancelled(u);
        if(onCancelledWithParamFunc!=null){
            onCancelledWithParamFunc.callback(u);
        }
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
        if(onCancelledFunc!=null){
            onCancelledFunc.callback();
        }
    }
    public void setDoInBackgroundFunc(DoInBackground doInBackgroundFunc){
        this.doInBackgroundFunc = doInBackgroundFunc;
    }
    public void setOnPreExecuteFunc(OnPreExecute onPreExecuteFunc){
        this.onPreExecuteFunc = onPreExecuteFunc;
    }
    public void setOnPostExecuteFunc(OnPostExecute onPostExecuteFunc){
        this.onPostExecuteFunc = onPostExecuteFunc;
    }
    public void setOnProgressUpdateFunc(OnProgressUpdate onProgressUpdateFunc){
        this.onProgressUpdateFunc = onProgressUpdateFunc;
    }
    public void setOnCancelledWithParamFunc(OnCancelledWithParam onCancelledWithParamFunc){
        this.onCancelledWithParamFunc= onCancelledWithParamFunc;
    }
    public void setOnCancelledFunc(OnCancelled onCancelledFunc){
        this.onCancelledFunc = onCancelledFunc;
    }
}
