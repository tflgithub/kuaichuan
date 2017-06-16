package com.cn.tfl.kuaichuan.ui;

import android.Manifest;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.cn.tfl.kuaichuan.AppContext;
import com.cn.tfl.kuaichuan.Constant;
import com.cn.tfl.kuaichuan.R;
import com.cn.tfl.kuaichuan.comm.BaseActivity;
import com.cn.tfl.kuaichuan.core.entity.IpPortInfo;
import com.cn.tfl.kuaichuan.core.receiver.WifiAPBroadcastReceiver;
import com.cn.tfl.kuaichuan.core.utils.ApMgr;
import com.cn.tfl.kuaichuan.ui.view.RadarLayout;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class ReceiverWaitingActivity extends BaseActivity {

    public static final String TAG = ReceiverWaitingActivity.class.getSimpleName();
    RadarLayout radarLayout;
    TextView tv_device_name, tv_desc, tv_back;
    WifiAPBroadcastReceiver mWifiAPBroadcastReceiver;
    boolean mIsInitialized = false;
    /**
     * 与 文件发送方 通信的 线程
     */
    Runnable mUdpClientRunnable;
    public static final int MSG_TO_FILE_RECEIVER_UI = 0X88;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receiver_waiting);
        radarLayout = (RadarLayout) findViewById(R.id.radar_layout);
        tv_device_name = (TextView) findViewById(R.id.tv_device_name);
        tv_desc = (TextView) findViewById(R.id.tv_desc);
        tv_back = (TextView) findViewById(R.id.tv_back);
        tv_back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        initWithGetPermission();
    }

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_TO_FILE_RECEIVER_UI) {
                Log.i(TAG, "Send Msg To FileSender######>>>" + Constant.MSG_FILE_RECEIVER_INIT_SUCCESS);
                Log.i(TAG, "sendFileReceiverInitSuccessMsgToFileSender------>>>end");
                IpPortInfo ipPortInfo = (IpPortInfo) msg.obj;
                Bundle bundle = new Bundle();
                bundle.putSerializable(Constant.KEY_IP_PORT_INFO, ipPortInfo);
                Intent intent = new Intent(ReceiverWaitingActivity.this, FileReceiverActivity.class);
                intent.putExtras(bundle);
                startActivity(intent);
                finishNormal();
            }
        }
    };

    /**
     * 成功进入 文件接收列表UI 调用的finishNormal()
     */
    private void finishNormal() {
        if (mWifiAPBroadcastReceiver != null) {
            unregisterReceiver(mWifiAPBroadcastReceiver);
            mWifiAPBroadcastReceiver = null;
        }
        closeSocket();
        finish();
    }

    /**
     * Android 6.0 modify wifi status need this permission: android.permission.WRITE_SETTINGS
     * 这个权限在debug下是无法获取的，必须签名打包。
     */
    public static final int REQUEST_CODE_WRITE_SETTINGS = 7879;

    public void initWithGetPermission() {
        boolean permission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            permission = Settings.System.canWrite(this);
        } else {
            permission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
        }
        if (permission) {
            init();
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_SETTINGS}, REQUEST_CODE_WRITE_SETTINGS);
            } else {
                Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_CODE_WRITE_SETTINGS);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        init();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_WRITE_SETTINGS && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            init();
        } else {
            // Permission Denied
            Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    WifiConfiguration wifiConfiguration = null;

    private void init() {
        radarLayout.setUseRing(true);
        radarLayout.setColor(getResources().getColor(R.color.white));
        radarLayout.setCount(4);
        radarLayout.start();
        final String ssid = TextUtils.isEmpty(android.os.Build.DEVICE) ? Constant.DEFAULT_SSID : android.os.Build.DEVICE;
        //tv_device_name.setText(ssid);
        tv_desc.setText("正在初始化...");
        mWifiAPBroadcastReceiver = new WifiAPBroadcastReceiver() {
            @Override
            public void onWifiApEnabled() {
                if (!mIsInitialized) {
                    mUdpClientRunnable = createSendMsgToFileSenderRunnable();
                    AppContext.MAIN_EXECUTOR.execute(mUdpClientRunnable);
                    tv_desc.setText("初始化完成");
                    tv_desc.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            tv_desc.setText("当前网络名称：" + ssid);
                        }
                    }, 2 * 1000);
                    mIsInitialized = true;
                }
            }
        };
        IntentFilter filter = new IntentFilter(WifiAPBroadcastReceiver.ACTION_WIFI_AP_STATE_CHANGED);
        registerReceiver(mWifiAPBroadcastReceiver, filter);
        wifiConfiguration = ApMgr.configApState(getContext(), ssid);
        if (wifiConfiguration != null) {
            Log.i(TAG, "创建热点成功！");
        } else {
            Log.i(TAG, "创建热点失败！");
        }
    }


    /**
     * 创建发送UDP消息到 文件发送方 的服务线程
     */
    private Runnable createSendMsgToFileSenderRunnable() {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    startFileReceiverServer(Constant.DEFAULT_SERVER_COM_PORT);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }

    /**
     * 开启 文件接收方 通信服务 (必须在子线程执行)
     *
     * @param serverPort
     * @throws Exception
     */
    DatagramSocket mDatagramSocket;

    private void startFileReceiverServer(int serverPort) throws Exception {

        mDatagramSocket = new DatagramSocket(serverPort);
        byte[] receiveData = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        while (true) {
            //1.接收 文件发送方的消息
            try {
                mDatagramSocket.receive(receivePacket);
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
            InetAddress inetAddress = receivePacket.getAddress();
            int port = receivePacket.getPort();
            String msg = new String(receivePacket.getData()).trim();
            if (!TextUtils.isEmpty(msg) && msg.startsWith(Constant.MSG_FILE_RECEIVER_INIT)) {
                Log.i(TAG, "Get the msg from FileReceiver######>>>" + Constant.MSG_FILE_RECEIVER_INIT);
                // 进入文件接收列表界面 (文件接收列表界面需要 通知 文件发送方发送 文件开始传输UDP通知)
                mHandler.obtainMessage(MSG_TO_FILE_RECEIVER_UI, new IpPortInfo(inetAddress, port)).sendToTarget();
            }
        }
    }


    @Override
    protected void onDestroy() {
        if (mWifiAPBroadcastReceiver != null) {
            unregisterReceiver(mWifiAPBroadcastReceiver);
            mWifiAPBroadcastReceiver = null;
        }
        super.onDestroy();
    }

    /**
     * 关闭UDP Socket 流
     */
    private void closeSocket() {
        if (mDatagramSocket != null) {
            mDatagramSocket.disconnect();
            mDatagramSocket.close();
            mDatagramSocket = null;
        }
    }


    @Override
    public void onBackPressed() {
        closeSocket();
        //关闭热点
        ApMgr.disableAp(getContext());
        super.onBackPressed();
    }
}
