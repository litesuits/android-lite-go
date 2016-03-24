package com.litesuits.go.sample;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import com.litesuits.go.OverloadPolicy;
import com.litesuits.go.R;
import com.litesuits.go.SchedulePolicy;
import com.litesuits.go.SmartExecutor;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

public class MainActivity extends Activity {
    protected String TAG = MainActivity.class.getSimpleName();
    protected ListView mListview;
    protected BaseAdapter mAdapter;
    protected Activity activity = null;

    protected SmartExecutor mainExecutor;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.main);
        activity = this;
        initViews();
        initSmartExecutor();

    }

    private void initViews() {
        mListview = (ListView) findViewById(R.id.listview);
        mAdapter = new ArrayAdapter<String>(this, R.layout.list_item, R.id.tv_item,
                getResources().getStringArray(R.array.test_case_list));
        mListview.setAdapter(mAdapter);
        mListview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                clickTestItem(position);
            }
        });
    }

    private void initSmartExecutor() {
        if (mainExecutor == null) {
            // set this temporary parameter, just for test
            // 智能并发调度控制器：设置[最大并发数]，和[等待队列]大小仅供测试，具体根据实际场景设置。
            mainExecutor = new SmartExecutor();

            // 打开调试和日志，发布时建议关闭。
            mainExecutor.setDebug(true);

            // number of concurrent threads at the same time, recommended core size is CPU count
            mainExecutor.setCoreSize(2);

            // adjust maximum number of waiting queue size by yourself or based on phone performance
            mainExecutor.setQueueSize(100);

            // 任务数量超出[最大并发数]后，自动进入[等待队列]，等待当前执行任务完成后按策略进入执行状态：后进先执行。
            mainExecutor.setSchedulePolicy(SchedulePolicy.LastInFirstRun);

            // 后续添加新任务数量超出[等待队列]大小时，执行过载策略：抛弃队列内最旧任务。
            mainExecutor.setOverloadPolicy(OverloadPolicy.DiscardOldTaskInQueue);

            //GoUtil.showTips(activity, "LiteGo", "Let It Go!");
        }
    }

    /**
     * <item>0. Submit Runnable</item>
     * <item>1. Submit FutureTask</item>
     * <item>2. Submit Callable</item>
     * <item>3. Strategy Test</item>
     */
    private void clickTestItem(final int which) {
        switch (which) {
            case 0:
                // 0. Submit Runnable
                mainExecutor.submit(new Runnable() {
                    @Override
                    public void run() {
                        Log.i(TAG, " Runnable start!  thread id: " + Thread.currentThread().getId());
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        Log.i(TAG, " Runnable end!  thread id: " + Thread.currentThread().getId());
                    }
                });
                break;

            case 1:
                // 1. Submit FutureTask
                FutureTask<String> futureTask = new FutureTask<String>(new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        Log.i(TAG, " FutureTask thread id: " + Thread.currentThread().getId());
                        return "FutureTask";
                    }
                });
                mainExecutor.submit(futureTask);
                break;

            case 2:
                // 2. Submit Callable
                mainExecutor.submit(new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        Log.i(TAG, " Callable thread id: " + Thread.currentThread().getId());
                        return "Callable";
                    }
                });

                break;

            case 3:
                // 3. Strategy Test

                // set this temporary parameter, just for test
                SmartExecutor smallExecutor = new SmartExecutor();


                // number of concurrent threads at the same time, recommended core size is CPU count
                smallExecutor.setCoreSize(2);

                // adjust maximum number of waiting queue size by yourself or based on phone performance
                smallExecutor.setQueueSize(2);

                // 任务数量超出[最大并发数]后，自动进入[等待队列]，等待当前执行任务完成后按策略进入执行状态：后进先执行。
                smallExecutor.setSchedulePolicy(SchedulePolicy.LastInFirstRun);

                // 后续添加新任务数量超出[等待队列]大小时，执行过载策略：抛弃队列内最旧任务。
                smallExecutor.setOverloadPolicy(OverloadPolicy.DiscardOldTaskInQueue);

                // 一次投入 4 个任务
                for (int i = 0; i < 4; i++) {
                    final int j = i;
                    smallExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            Log.i(TAG, " TASK " + j + " is running now ----------->");
                            SystemClock.sleep(j * 200);
                        }
                    });
                }

                // 再投入1个需要取消的任务
                Future future = smallExecutor.submit(new Runnable() {
                    @Override
                    public void run() {
                        Log.i(TAG, " TASK 4 will be canceled ... ------------>");
                        SystemClock.sleep(1000);
                    }
                });
                future.cancel(false);
                break;
        }
    }


}
