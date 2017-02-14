package com.viciy.downloadmanager.service;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.annotation.Nullable;

import com.viciy.downloadmanager.greendao.DbInstallTaskHelper;
import com.viciy.downloadmanager.greendao.InstallTask;
import com.viciy.downloadmanager.utils.APPUpdateUtil;
import com.viciy.downloadmanager.utils.Constant;
import com.viciy.downloadmanager.utils.SPUtil;
import com.viciy.downloadmanager.utils.SchemeUtils;

import java.io.File;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 * Created by bai-qiang.yang on 2017/2/14.
 */

public class DownloadService extends Service {
    private static final String TAG = DownloadService.class.getSimpleName();

    public static final int HANDLE_DOWNLOAD = 0x001;
    public static final String BUNDLE_KEY_DOWNLOAD_URL = "download_url";
    public static final String BUNDLE_KEY_APK_NAME = "apk_name";
    public static final float UNBIND_SERVICE = 2.0F;

    private Activity activity;
    private DownloadBinder binder;
    private DownloadManager downloadManager;
    private DownloadChangeObserver downloadObserver;
    private BroadcastReceiver downLoadBroadcast;
    private ScheduledExecutorService scheduledExecutorService;

    //下载任务ID
    private long downloadId;
    private String downloadUrl;
    private String downloadApkName;
    private OnProgressListener onProgressListener;

    public Handler downLoadHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (onProgressListener != null && HANDLE_DOWNLOAD == msg.what) {
                //被除数可以为0，除数必须大于0
                if (msg.arg1 >= 0 && msg.arg2 > 0) {
                    onProgressListener.onProgress(msg.arg1 / (float) msg.arg2);
                }

                int downloadState = (int) msg.obj;
                System.out.println(downloadState+"-------downnl---------------");
                switch (downloadState) {
                    case DownloadManager.STATUS_FAILED:
                        //删除下载的文件
                        deleteFileDownload();
                        //删除保存的downloadUrl和downloadId
                        deleteLoadUrlAndId();
                        break;
                }


            }
        }
    };

    //删除保存的downloadUrl和downloadId
    private void deleteLoadUrlAndId() {
        DbInstallTaskHelper dbInstallTaskHelper = new DbInstallTaskHelper(this);
        dbInstallTaskHelper.delete(downloadUrl,downloadId+"");
        dbInstallTaskHelper.close();
    }

    //删除下载的文件
    private void deleteFileDownload() {
        File fileName = new File(SPUtil.getString(Constant.SP_DOWNLOAD_PATH, ""));

        if (fileName != null && fileName.exists() && fileName.isFile()) {
            fileName.delete();
        }
    }

    private Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            updateProgress();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        binder = new DownloadBinder();
    }

    @Override
    public IBinder onBind(Intent intent) {
        downloadUrl = intent.getStringExtra(BUNDLE_KEY_DOWNLOAD_URL);
        downloadApkName = intent.getStringExtra(BUNDLE_KEY_APK_NAME);
        downloadApk(downloadUrl,downloadApkName);
        return binder;
    }

    /**
     * 下载最新APK
     */
    private void downloadApk( String url,String downloadApkName) {
        downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        downloadObserver = new DownloadChangeObserver();

        registerContentObserver();

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        /**设置用于下载时的网络状态*/
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
        /**设置通知栏是否可见*/
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);
        /**设置漫游状态下是否可以下载*/
        request.setAllowedOverRoaming(false);
        /**如果我们希望下载的文件可以被系统的Downloads应用扫描到并管理，
         我们需要调用Request对象的setVisibleInDownloadsUi方法，传递参数true.*/
        request.setVisibleInDownloadsUi(true);
        /**设置文件保存路径*/
        request.setDestinationInExternalFilesDir(getApplicationContext(), downloadApkName, downloadApkName+".apk");
        /**将下载请求放入队列， return下载任务的ID*/
        downloadId = downloadManager.enqueue(request);
        System.out.println(downloadId);
        //将downloadUrl和downloadId保存到数据库中
        saveIdWithUrl(downloadUrl,downloadId);

        registerBroadcast();
    }

    private void saveIdWithUrl(String downloadUrl, long downloadId) {
        DbInstallTaskHelper dbInstallTaskHelper = new DbInstallTaskHelper(this);
        List<InstallTask> query = dbInstallTaskHelper.query(downloadUrl);

        if (query != null && query.size() > 0) {
            dbInstallTaskHelper.update(downloadUrl, downloadId + "");
        } else {
            dbInstallTaskHelper.add(downloadUrl, downloadId + "");
        }
        dbInstallTaskHelper.close();
    }

    public long getDownloadIdByUrl(String apkUrl) {
        int downLoadId ;
        DbInstallTaskHelper dbInstallTaskHelper = new DbInstallTaskHelper(this);
        List<InstallTask> query = dbInstallTaskHelper.query(apkUrl);

        if (query != null && query.size() > 0) {
            downLoadId = Integer.parseInt(query.get(0).getDownloadId());
        } else {//查询数据库返回为空
            downLoadId = -1;
        }
        dbInstallTaskHelper.close();

        return downLoadId;
    }

    /**
     * 注册广播
     */
    private void registerBroadcast() {
        /**注册service 广播 1.任务完成时 2.进行中的任务被点击*/
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        intentFilter.addAction(DownloadManager.ACTION_NOTIFICATION_CLICKED);
        registerReceiver(downLoadBroadcast = new DownLoadBroadcast(), intentFilter);
    }

    /**
     * 注销广播
     */
    private void unregisterBroadcast() {
        if (downLoadBroadcast != null) {
            unregisterReceiver(downLoadBroadcast);
            downLoadBroadcast = null;
        }
    }

    /**
     * 注册ContentObserver
     */
    private void registerContentObserver() {
        /** observer download change **/
        if (downloadObserver != null) {
            getContentResolver().registerContentObserver(Uri.parse("content://downloads/my_downloads"), false, downloadObserver);
        }
    }

    /**
     * 注销ContentObserver
     */
    private void unregisterContentObserver() {
        if (downloadObserver != null) {
            getContentResolver().unregisterContentObserver(downloadObserver);
        }
    }

    /**
     * 关闭定时器，线程等操作
     */
    private void close() {
        if (scheduledExecutorService != null && !scheduledExecutorService.isShutdown()) {
            scheduledExecutorService.shutdown();
        }

        if (downLoadHandler != null) {
            downLoadHandler.removeCallbacksAndMessages(null);
        }
    }

    /**
     * 发送Handler消息更新进度和状态
     */
    private void updateProgress() {
        int[] bytesAndStatus = getBytesAndStatus(downloadId);
        downLoadHandler.sendMessage(downLoadHandler.obtainMessage(HANDLE_DOWNLOAD, bytesAndStatus[0], bytesAndStatus[1], bytesAndStatus[2]));
    }

    /**
     * 通过query查询下载状态，包括已下载数据大小，总大小，下载状态
     *
     * @param downloadId
     * @return
     */
    private int[] getBytesAndStatus(long downloadId) {
        int[] bytesAndStatus = new int[]{
                -1, -1, 0
        };
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        Cursor cursor = null;
        try {
            cursor = downloadManager.query(query);
            if (cursor != null && cursor.moveToFirst()) {
                //已经下载文件大小
                bytesAndStatus[0] = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                //下载文件的总大小
                bytesAndStatus[1] = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                //下载状态
                bytesAndStatus[2] = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                System.out.println(bytesAndStatus[2]+"下载状态");
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return bytesAndStatus;
    }


    /**
     * 绑定此DownloadService的Activity实例
     *
     * @param activity
     */
    public void setTargetActivity(Activity activity) {
        this.activity = activity;
    }

    /**
     * 接受下载完成广播
     */
    private class DownLoadBroadcast extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            long downId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            switch (intent.getAction()) {
                case DownloadManager.ACTION_DOWNLOAD_COMPLETE:
                    if (downloadId == downId && downId != -1 && downloadManager != null) {
                        Uri downIdUri = downloadManager.getUriForDownloadedFile(downloadId);

                        close();

                        if (downIdUri != null) {
                            String path = SchemeUtils.getPath(context,downIdUri);
                            SPUtil.put(Constant.SP_DOWNLOAD_PATH, path);
                            APPUpdateUtil.installApk(context, path);
                        }

                        if (onProgressListener != null) {
                            onProgressListener.onProgress(UNBIND_SERVICE);
                        }
                    }
                    break;
                default:
                    //点击推送栏的情况 TODO:
                    break;
            }
        }
    }



    /**
     * 监听下载进度
     */
    private class DownloadChangeObserver extends ContentObserver {

        public DownloadChangeObserver() {
            super(downLoadHandler);
            scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        }

        /**
         * 当所监听的Uri发生改变时，就会回调此方法
         *
         * @param selfChange 此值意义不大, 一般情况下该回调值false
         */
        @Override
        public void onChange(boolean selfChange) {
            scheduledExecutorService.scheduleAtFixedRate(progressRunnable, 0, 2, TimeUnit.SECONDS);
        }
    }

    public class DownloadBinder extends Binder {
        /**
         * 返回当前服务的实例
         *
         * @return
         */
        public DownloadService getService() {
            return DownloadService.this;
        }

    }

    public interface OnProgressListener {
        /**
         * 下载进度
         *
         * @param fraction 已下载/总大小
         */
        void onProgress(float fraction);
    }

    /**
     * 对外开发的方法
     *
     * @param onProgressListener
     */
    public void setOnProgressListener(OnProgressListener onProgressListener) {
        this.onProgressListener = onProgressListener;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterBroadcast();
        unregisterContentObserver();
        System.out.println("下载任务服务销毁");
    }

    /**
     * 获取下载状态
     *
     * @param apkUrl to get the ID for the download, unique across the system.
     *                   This ID is used to make future calls related to this download.
     * @return int
     * @see DownloadManager#STATUS_PENDING
     * @see DownloadManager#STATUS_PAUSED
     * @see DownloadManager#STATUS_RUNNING
     * @see DownloadManager#STATUS_SUCCESSFUL
     * @see DownloadManager#STATUS_FAILED
     */
    public int getDownloadStatus(String apkUrl) {
        long downloadId = getDownloadIdByUrl(apkUrl);
        switch ((int) downloadId) {
            case -1:
                return -1;
            default:
                DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
                Cursor c = downloadManager.query(query);

                if (c != null) {
                    try {
                        if (c.moveToFirst()) {
                            return c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                        }
                    } finally {
                        c.close();
                    }
                }
                return -1;
        }

    }
}
