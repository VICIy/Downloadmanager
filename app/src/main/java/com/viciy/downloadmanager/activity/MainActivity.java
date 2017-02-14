package com.viciy.downloadmanager.activity;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.viciy.downloadmanager.R;
import com.viciy.downloadmanager.presenter.VersionUpdateImpl;
import com.viciy.downloadmanager.service.DownloadService;
import com.viciy.downloadmanager.utils.VersionUpdate;

import java.text.DecimalFormat;

import static com.viciy.downloadmanager.service.DownloadService.BUNDLE_KEY_APK_NAME;
import static com.viciy.downloadmanager.service.DownloadService.BUNDLE_KEY_DOWNLOAD_URL;

public class MainActivity extends AppCompatActivity implements VersionUpdateImpl {

    private boolean isBindService;

    private Button mButton;

    private ServiceConnection conn = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            DownloadService.DownloadBinder binder = (DownloadService.DownloadBinder) service;
            DownloadService downloadService = binder.getService();

            //接口回调，下载进度
            downloadService.setOnProgressListener(new DownloadService.OnProgressListener() {
                @Override
                public void onProgress(float fraction) {

                    //判断是否真的下载完成进行安装了，以及是否注册绑定过服务
                    if (fraction == DownloadService.UNBIND_SERVICE && isBindService) {
                        unbindService(conn);
                        isBindService = false;
                        Toast.makeText(MainActivity.this, "下载完成", Toast.LENGTH_SHORT).show();
                        mButton.setText(100+"%");
                        mButton.setBackgroundResource(R.drawable.round_conner_bg);
                    } else {

                        String s = new DecimalFormat("#.#").format((int)(fraction * 100));
                        mButton.setText( s + "%");

                        BitmapDrawable d1 = createDrawable(Color.parseColor("#B6DA53"), 1.0f, mButton.getWidth(), mButton.getHeight());
                        Drawable d2 = createDrawable(Color.parseColor("#FF4081"), fraction,mButton.getWidth(),mButton.getHeight());
                        Drawable[] array = new Drawable[2];
                        array[0] = d1;
                        array[1] = d2;
                        LayerDrawable la = new LayerDrawable(array);
                        // 其中第一个参数为层的索引号，后面的四个参数分别为left、top、right和bottom
                        la.setLayerInset(0, 0, 0, 0, 0);
                        la.setLayerInset(1, 0, 0, 0, 0);
                        mButton.setBackgroundDrawable(la);
                    }
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();

    }

    /**
     * 初始化View
     */
    private void initView() {
        mButton = (Button) findViewById(R.id.bt_download);
        mButton.setText("开始下载");
        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VersionUpdate.checkVersion(MainActivity.this,"http://cdn1.utouu.com/apps/apk/cn.bestkeep.2291.apk");
            }
        });
    }

    public BitmapDrawable createDrawable(int color, float length,int w,int h) {
        Bitmap b = null;
        b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_4444);
        Canvas c = new Canvas(b);
        RectF rect = new RectF(0, 0, w * length, h);
        Paint p = new Paint();
        p.setColor(color);
        c.drawRoundRect(rect, 5, 5, p);

        BitmapDrawable bd = new BitmapDrawable(b);
        return bd;
    }

    @Override
    public void bindService(String apkUrl) {
        Intent intent = new Intent(this, DownloadService.class);
        intent.putExtra(BUNDLE_KEY_DOWNLOAD_URL, apkUrl);
        intent.putExtra(BUNDLE_KEY_APK_NAME, "bestkeep");
        isBindService = bindService(intent, conn, BIND_AUTO_CREATE);
    }
}
