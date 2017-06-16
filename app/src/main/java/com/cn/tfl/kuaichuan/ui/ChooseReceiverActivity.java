package com.cn.tfl.kuaichuan.ui;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.wifi.ScanResult;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.cn.tfl.kuaichuan.AppContext;
import com.cn.tfl.kuaichuan.Constant;
import com.cn.tfl.kuaichuan.R;
import com.cn.tfl.kuaichuan.comm.BaseActivity;
import com.cn.tfl.kuaichuan.comm.BaseAdapter;
import com.cn.tfl.kuaichuan.comm.BaseRecyclerHolder;
import com.cn.tfl.kuaichuan.core.BaseTransfer;
import com.cn.tfl.kuaichuan.core.entity.FileInfo;
import com.cn.tfl.kuaichuan.core.entity.IpPortInfo;
import com.cn.tfl.kuaichuan.core.utils.WifiMgr;
import com.cn.tfl.kuaichuan.ui.view.RadarScanView;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ChooseReceiverActivity extends BaseActivity {
    private RadarScanView radarScanView;

    public static final String TAG = ChooseReceiverActivity.class.getSimpleName();
    RecyclerView recyclerView;

    List<ScanResult> mScanResultList;

    BaseAdapter mWifiScanResultAdapter;
    /**
     * 与 文件接收方通信的 线程
     */
    Runnable mUdpServerRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_receiver);
        radarScanView = (RadarScanView) findViewById(R.id.scan_view);
        recyclerView = (RecyclerView) findViewById(R.id.scan_list);
        init();
    }


    public static final int MSG_TO_FILE_SENDER_UI = 0X88;   //消息：跳转到文件发送列表UI
    public static final int MSG_TO_SHOW_SCAN_RESULT = 0X99; //消息：更新扫描可连接Wifi网络的列表
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_TO_FILE_SENDER_UI) {
                IpPortInfo ipPortInfo = (IpPortInfo) msg.obj;
                Bundle bundle = new Bundle();
                bundle.putSerializable(Constant.KEY_IP_PORT_INFO, ipPortInfo);
                Intent intent = new Intent(ChooseReceiverActivity.this, FileSenderActivity.class);
                intent.putExtras(bundle);
                startActivity(intent);
                finishNormal();
            } else if (msg.what == MSG_TO_SHOW_SCAN_RESULT) {
                getOrUpdateWifiScanResult();
                mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_TO_SHOW_SCAN_RESULT), 1000);
            }
        }
    };


    /**
     * 成功进入 文件发送列表UI 调用的finishNormal()
     */
    private void finishNormal() {
        closeSocket();
        finish();
    }

    /**
     * 打开GPS的请求码
     */
    public static final int REQUEST_CODE_OPEN_GPS = 205;

    private WifiStateReceiver mWifiStateReceiver;

    private void init() {
        mWifiStateReceiver = new WifiStateReceiver();
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mWifiStateReceiver, filter);
        WifiMgr.getInstance(getContext()).openWifi();
        radarScanView.startScan();
        //Android 6.0 扫描wifi 需要开启定位
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { //Android 6.0 扫描wifi 需要开启定位
            if (ContextCompat.checkSelfPermission(this, Manifest.permission_group.LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // 获取wifi连接需要定位权限,没有获取权限
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_WIFI_STATE,
                }, REQUEST_CODE_OPEN_GPS);
            }
        } else {//Android 6.0 以下的直接开启扫描
            updateUI();
        }
    }

    private void updateUI() {
        getOrUpdateWifiScanResult();
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_TO_SHOW_SCAN_RESULT), 1000);
    }

    class WifiStateReceiver extends BroadcastReceiver {
        public void onReceive(Context c, Intent intent) {
            if (intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false)) {
                Log.i(TAG, "热点网络不可用");
            } else {
                hideProgressDialog();
                String serverIP = WifiMgr.getInstance(getContext()).getIpAddressFromHotspot();
                //只有获取到热点wifi才发送
                if (serverIP.equals(Constant.DEFAULT_SERVER_IP) && scanResult != null) {
                    Log.i(TAG, "热点网络可用，发送UDP数据报..");
                    mUdpServerRunnable = createSendMsgToServerRunnable(serverIP);
                    AppContext.MAIN_EXECUTOR.execute(mUdpServerRunnable);
                }
            }
        }
    }


    ScanResult scanResult = null;

    private void getOrUpdateWifiScanResult() {
        WifiMgr.getInstance(getContext()).startScan();
        mScanResultList = WifiMgr.getInstance(getContext()).getScanResultList();
        if (mScanResultList != null) {
            mWifiScanResultAdapter = new BaseAdapter(this, mScanResultList, R.layout.item_wifi_scan_result) {
                @Override
                public void convert(BaseRecyclerHolder holder, Object item, int position, boolean isScrolling) {
                    holder.setText(R.id.tv_name, mScanResultList.get(position).SSID);
                }
            };
            recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
            recyclerView.setAdapter(mWifiScanResultAdapter);
            mWifiScanResultAdapter.setOnItemClickListener(new BaseAdapter.OnItemClickListener() {
                @Override
                public void onItemClick(RecyclerView parent, View view, int position) {
                    scanResult = mScanResultList.get(position);
                    showProgressDialog("正在连接" + scanResult.SSID);
                    Log.i(TAG, "###select the wifi info ======>>>" + scanResult.toString());
                    WifiMgr.getInstance(getContext()).connectWifi(scanResult.SSID, null, WifiMgr.WifiEncType.OPEN);
                }
            });
        }
    }

    /**
     * 创建发送UDP消息到 文件接收方 的服务线程
     *
     * @param serverIP
     */
    private Runnable createSendMsgToServerRunnable(final String serverIP) {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    startFileSenderServer(serverIP, Constant.DEFAULT_SERVER_COM_PORT);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }


    /**
     * 开启 文件发送方 通信服务 (必须在子线程执行)
     *
     * @param targetIpAddr
     * @param serverPort
     * @throws Exception
     */
    DatagramSocket mDatagramSocket;

    private void startFileSenderServer(String targetIpAddr, int serverPort) throws Exception {
        Log.i(TAG, "receiver serverIp ----->>>" + targetIpAddr);
        InetAddress ipAddress = InetAddress.getByName(targetIpAddr);
        if (mDatagramSocket == null) {
            mDatagramSocket = new DatagramSocket(serverPort);
        }
        byte[] receiveData = new byte[1024];
        //0.发送 即将发送的文件列表 到文件接收方
        sendFileInfoListToFileReceiverWithUdp(serverPort, ipAddress);
        //1.发送 文件接收方 初始化
        byte[] sendData = Constant.MSG_FILE_RECEIVER_INIT.getBytes(BaseTransfer.UTF_8);
        DatagramPacket sendPacket =
                new DatagramPacket(sendData, sendData.length, ipAddress, serverPort);
        mDatagramSocket.send(sendPacket);
        Log.i(TAG, "Send Msg To FileReceiver######>>>" + Constant.MSG_FILE_RECEIVER_INIT);
        //2.接收 文件接收方 初始化 反馈
        while (true) {
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            mDatagramSocket.receive(receivePacket);
            String response = new String(receivePacket.getData(), BaseTransfer.UTF_8).trim();
            Log.i(TAG, "Get the msg from FileReceiver######>>>" + response);
            if (response != null && response.equals(Constant.MSG_FILE_RECEIVER_INIT_SUCCESS)) {
                // 进入文件发送列表界面 （并且通知文件接收方进入文件接收列表界面）
                mHandler.obtainMessage(MSG_TO_FILE_SENDER_UI, new IpPortInfo(ipAddress, serverPort)).sendToTarget();
            }
        }
    }


    /**
     * 发送即将发送的文件列表到文件接收方
     *
     * @param serverPort
     * @param ipAddress
     * @throws IOException
     */
    private void sendFileInfoListToFileReceiverWithUdp(int serverPort, InetAddress ipAddress) throws IOException {
        Map<String, FileInfo> sendFileInfoMap = AppContext.getAppContext().getFileInfoMap();
        List<Map.Entry<String, FileInfo>> fileInfoMapList = new ArrayList<>(sendFileInfoMap.entrySet());
        //排序
        Collections.sort(fileInfoMapList, Constant.DEFAULT_COMPARATOR);
        for (Map.Entry<String, FileInfo> entry : fileInfoMapList) {
            if (entry.getValue() != null) {
                FileInfo fileInfo = entry.getValue();
                String fileInfoStr = FileInfo.toJsonStr(fileInfo);
                DatagramPacket sendFileInfoListPacket =
                        new DatagramPacket(fileInfoStr.getBytes(), fileInfoStr.getBytes().length, ipAddress, serverPort);
                try {
                    mDatagramSocket.send(sendFileInfoListPacket);
                    Log.i(TAG, "sendFileInfoListToFileReceiverWithUdp------>>>" + fileInfoStr + "=== Success!");
                } catch (Exception e) {
                    Log.i(TAG, "sendFileInfoListToFileReceiverWithUdp------>>>" + fileInfoStr + "=== Failure!");
                }
            }
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_OPEN_GPS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 允许
                updateUI();
            } else {
                // Permission Denied
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }


    @Override
    protected void onDestroy() {
        if (mWifiStateReceiver != null) {
            unregisterReceiver(mWifiStateReceiver);
            mWifiStateReceiver = null;
        }
        scanResult = null;
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
        super.onBackPressed();
    }
}
