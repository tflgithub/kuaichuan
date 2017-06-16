package com.cn.tfl.kuaichuan.ui;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.cn.tfl.kuaichuan.AppContext;
import com.cn.tfl.kuaichuan.Constant;
import com.cn.tfl.kuaichuan.R;
import com.cn.tfl.kuaichuan.comm.BaseActivity;
import com.cn.tfl.kuaichuan.comm.BaseAdapter;
import com.cn.tfl.kuaichuan.comm.BaseRecyclerHolder;
import com.cn.tfl.kuaichuan.core.BaseTransfer;
import com.cn.tfl.kuaichuan.core.FileReceiver;
import com.cn.tfl.kuaichuan.core.entity.FileInfo;
import com.cn.tfl.kuaichuan.core.entity.IpPortInfo;
import com.cn.tfl.kuaichuan.core.utils.ApMgr;
import com.cn.tfl.kuaichuan.core.utils.ApkUtils;
import com.cn.tfl.kuaichuan.core.utils.FileUtils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FileReceiverActivity extends BaseActivity {

    private static final String TAG = FileReceiverActivity.class.getSimpleName();
    public static final int MSG_FILE_RECEIVER_INIT_SUCCESS = 0X4444;
    public static final int MSG_ADD_FILE_INFO = 0X5555;
    public static final int MSG_UPDATE_FILE_INFO = 0X6666;
    public static final int OFF_LINE = 0X7777;
    IpPortInfo mIpPortInfo;
    ServerRunnable mReceiverServer;
    RecyclerView receiverList;
    BaseAdapter adapter;
    private Map<String, FileInfo> mDataHashMap;
    List<Map.Entry<String, FileInfo>> fileInfoMapList;
    private TextView tv_value_storage, tv_unit_storage, tv_value_time, tv_unit_time, tv_back;
    private ProgressBar pb_total;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_receiver);
        receiverList = (RecyclerView) findViewById(R.id.receiver_list);
        receiverList.setLayoutManager(new LinearLayoutManager(getContext()));
        tv_value_storage = (TextView) findViewById(R.id.tv_value_storage);
        tv_unit_storage = (TextView) findViewById(R.id.tv_unit_storage);
        tv_value_time = (TextView) findViewById(R.id.tv_value_time);
        tv_unit_time = (TextView) findViewById(R.id.tv_unit_time);
        pb_total = (ProgressBar) findViewById(R.id.pb_total);
        tv_back = (TextView) findViewById(R.id.tv_back);
        tv_back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmQuiteDialog();
            }
        });
        init();
    }


    private void confirmQuiteDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setMessage("确认退出？");
        builder.setPositiveButton("是", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                quit();
            }
        }).setNegativeButton("否", null).create();
        builder.show();
    }


    private void init() {
        mIpPortInfo = (IpPortInfo) getIntent().getSerializableExtra(Constant.KEY_IP_PORT_INFO);
        mDataHashMap = AppContext.getAppContext().getReceiverFileInfoMap();
        fileInfoMapList = new ArrayList<>(mDataHashMap.entrySet());
        //排序
        Collections.sort(fileInfoMapList, Constant.DEFAULT_COMPARATOR);
        adapter = new BaseAdapter(getContext(), fileInfoMapList, R.layout.item_transfer) {
            @Override
            public void convert(BaseRecyclerHolder holder, Object item, int position, boolean isScrolling) {
                holder.setVisibility(R.id.pb_file, View.VISIBLE);
                holder.setVisibility(R.id.iv_tick, View.GONE);
                final FileInfo fileInfo = fileInfoMapList.get(position).getValue();
                if (FileUtils.isApkFile(fileInfo.getFilePath()) || FileUtils.isMp4File(fileInfo.getFilePath())) { //Apk格式 或者MP4格式需要 缩略图
                    if (fileInfo.getBitmap() != null) {
                        holder.setImageBitmap(R.id.iv_shortcut, fileInfo.getBitmap());
                    } else {
                        if (FileUtils.isApkFile(fileInfo.getFilePath())) {
                            holder.setImageBitmap(R.id.iv_shortcut, BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher));
                        } else if (FileUtils.isMp4File(fileInfo.getFilePath())) {
                            holder.setImageBitmap(R.id.iv_shortcut, BitmapFactory.decodeResource(getResources(), R.mipmap.icon_mp4));
                        }
                    }
                } else if (FileUtils.isJpgFile(fileInfo.getFilePath())) {//图片格式
                    //文件接收时候的图片的缩略图是在FileInfo里面的
                    if (fileInfo.getBitmap() != null) {
                        holder.setImageBitmap(R.id.iv_shortcut, fileInfo.getBitmap());
                    } else {
                        holder.setImageBitmap(R.id.iv_shortcut, BitmapFactory.decodeResource(getResources(), R.mipmap.icon_jpg));
                    }
                } else if (FileUtils.isMp3File(fileInfo.getFilePath())) {//音乐格式
                    holder.setImageBitmap(R.id.iv_shortcut, BitmapFactory.decodeResource(getResources(), R.mipmap.icon_mp3));
                }
                holder.setText(R.id.tv_name, FileUtils.getFileName(fileInfo.getFilePath()));

                if (fileInfo.getResult() == FileInfo.FLAG_SUCCESS) { //文件传输成功
                    long total = fileInfo.getSize();
                    holder.setVisibility(R.id.pb_file, View.GONE);
                    holder.setText(R.id.tv_progress, FileUtils.getFileSize(total) + "/" + FileUtils.getFileSize(total));
                    holder.setVisibility(R.id.btn_operation, View.VISIBLE);
                    holder.setVisibility(R.id.iv_tick, View.INVISIBLE);
                    if (FileUtils.isApkFile(FileUtils.getLocalFilePath(fileInfo.getFilePath()))) { //Apk格式
                        if (!ApkUtils.isInstalled(getContext(), FileUtils.getLocalFilePath(fileInfo.getFilePath()))) { //未装过改应用 需要安装
                            holder.setText(R.id.btn_operation, getResources().getString(R.string.str_install));
                            holder.getView(R.id.btn_operation).setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    ApkUtils.install(getContext(), FileUtils.getLocalFilePath(fileInfo.getFilePath()));
                                }
                            });

                        } else {//装过改应用
                            holder.setText(R.id.btn_operation, getResources().getString(R.string.str_run));
                        }
                        holder.setVisibility(R.id.iv_tick, View.VISIBLE);
                    } else if (FileUtils.isJpgFile(fileInfo.getFilePath()) ||//图片格式
                            FileUtils.isMp3File(fileInfo.getFilePath()) || //音乐格式
                            FileUtils.isMp4File(fileInfo.getFilePath())) {//视屏音乐格式
                        //视屏格式
                        holder.setText(R.id.btn_operation, getResources().getString(R.string.str_open));
                        holder.getView(R.id.btn_operation).setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                FileUtils.openFile(getContext(), FileUtils.getLocalFilePath(fileInfo.getFilePath()));
                            }
                        });
                    }
                } else if (fileInfo.getResult() == FileInfo.FLAG_FAILURE) {
                    holder.setVisibility(R.id.pb_file, View.GONE);
                } else {//文件传输中
                    long progress = fileInfo.getProcceed();
                    long total = fileInfo.getSize();
                    holder.setText(R.id.tv_progress, FileUtils.getFileSize(progress) + "/" + FileUtils.getFileSize(total));
                    int percent = (int) (progress * 100 / total);
                    ((ProgressBar) holder.getView(R.id.pb_file)).setMax(100);
                    ((ProgressBar) holder.getView(R.id.pb_file)).setProgress(percent);
                    //TODO 传输过程中取消的问题
                    holder.setText(R.id.btn_operation, getString(R.string.str_cancel));
                    holder.getView(R.id.btn_operation).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            //可否通过广播来实现？
                        }
                    });
                }
            }
        };
        receiverList.setAdapter(adapter);
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE_WRITE_FILE);
        } else {
            initServer(); //启动接收服务
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_WRITE_FILE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initServer(); //启动接收服务
            } else {
                // Permission Denied
                Toast.makeText(this, getString(R.string.tip_permission_denied_and_not_receive_file), Toast.LENGTH_SHORT).show();
                onBackPressed();
            }
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }


    /**
     * 开启文件接收端服务
     */
    private void initServer() {
        mReceiverServer = new ServerRunnable(Constant.DEFAULT_SERVER_PORT);
        new Thread(mReceiverServer).start();
    }


    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_FILE_RECEIVER_INIT_SUCCESS) {
                Toast.makeText(getContext(), "对方已上线", Toast.LENGTH_SHORT).show();
                sendMsgToFileSender();
            } else if (msg.what == MSG_ADD_FILE_INFO) {
                //ADD FileInfo 到 Adapter
                FileInfo fileInfo = (FileInfo) msg.obj;
                Toast.makeText(getContext(), "收到一个任务：" + (fileInfo != null ? fileInfo.getFilePath() : ""), Toast.LENGTH_SHORT).show();
            } else if (msg.what == MSG_UPDATE_FILE_INFO) {
                //ADD FileInfo 到 Adapter
                updateTotalProgressView();
                if (adapter != null) update();
            } else if (msg.what == OFF_LINE) {
                Toast.makeText(getContext(), "对方已下线", Toast.LENGTH_SHORT).show();
            }
        }
    };


    /**
     * 更新数据
     */
    private void update() {
        fileInfoMapList.clear();
        mDataHashMap = AppContext.getAppContext().getReceiverFileInfoMap();
        List<Map.Entry<String, FileInfo>> list = new ArrayList<>(mDataHashMap.entrySet());
        Collections.sort(list, Constant.DEFAULT_COMPARATOR);
        fileInfoMapList.addAll(list);
        adapter.notifyDataSetChanged();
    }

    /**
     * 更新进度 和 耗时的 View
     */
    private void updateTotalProgressView() {
        try {
            //设置传送的总容量大小
            mStorageArray = FileUtils.getFileSizeArrayStr(mTotalLen);
            tv_value_storage.setText(mStorageArray[0]);
            tv_unit_storage.setText(mStorageArray[1]);

            //设置传送的时间情况
            mTimeArray = FileUtils.getTimeByArrayStr(mTotalTime);
            tv_value_time.setText(mTimeArray[0]);
            tv_unit_time.setText(mTimeArray[1]);


            //设置传送的进度条情况
            if (mHasSendedFileCount == AppContext.getAppContext().getReceiverFileInfoMap().size()) {
                pb_total.setProgress(0);
                tv_value_storage.setTextColor(getResources().getColor(R.color.color_yellow));
                tv_value_time.setTextColor(getResources().getColor(R.color.color_yellow));
                return;
            }

            long total = AppContext.getAppContext().getAllReceiverFileInfoSize();
            int percent = (int) (mTotalLen * 100 / total);
            pb_total.setProgress(percent);

            if (total == mTotalLen) {
                pb_total.setProgress(0);
                tv_value_storage.setTextColor(getResources().getColor(R.color.color_yellow));
                tv_value_time.setTextColor(getResources().getColor(R.color.color_yellow));
            }
        } catch (Exception e) {
            //convert storage array has some problem
        }
    }

    long mTotalLen = 0;     //所有总文件的进度
    long mCurOffset = 0;    //每次传送的偏移量
    long mLastUpdateLen = 0; //每个文件传送onProgress() 之前的进度
    String[] mStorageArray = null;


    long mTotalTime = 0;
    long mCurTimeOffset = 0;
    long mLastUpdateTime = 0;
    String[] mTimeArray = null;

    int mHasSendedFileCount = 0;
    FileInfo mCurFileInfo;
    ServerSocket serverSocket;

    /**
     * ServerSocket启动线程
     */
    class ServerRunnable implements Runnable {

        private int port;

        public ServerRunnable(int port) {
            this.port = port;
        }

        @Override
        public void run() {
            Log.i(TAG, "------>>>Socket已经开启");
            try {
                serverSocket = new ServerSocket(port);
                mHandler.obtainMessage(MSG_FILE_RECEIVER_INIT_SUCCESS).sendToTarget();
                while (!Thread.currentThread().isInterrupted()) {
                    Socket socket = serverSocket.accept();
                    Log.i(TAG, "------>>>socket ip:" + socket.getLocalSocketAddress());
                    //生成缩略图
                    FileReceiver fileReceiver = new FileReceiver(socket);
                    fileReceiver.setOnReceiveListener(new FileReceiver.OnReceiveListener() {
                        @Override
                        public void onStart() {
                            mLastUpdateLen = 0;
                            mLastUpdateTime = System.currentTimeMillis();
                        }

                        @Override
                        public void onGetFileInfo(FileInfo fileInfo) {
                            mHandler.obtainMessage(MSG_ADD_FILE_INFO, fileInfo).sendToTarget();
                            mCurFileInfo = fileInfo;
                            AppContext.getAppContext().addReceiverFileInfo(mCurFileInfo);
                            mHandler.sendEmptyMessage(MSG_UPDATE_FILE_INFO);
                        }

                        @Override
                        public void onGetScreenshot(Bitmap bitmap) {

                        }

                        @Override
                        public void onProgress(long progress, long total) {
                            //=====更新进度 流量 时间视图 start ====//
                            mCurOffset = progress - mLastUpdateLen > 0 ? progress - mLastUpdateLen : 0;
                            mTotalLen = mTotalLen + mCurOffset;
                            mLastUpdateLen = progress;

                            mCurTimeOffset = System.currentTimeMillis() - mLastUpdateTime > 0 ? System.currentTimeMillis() - mLastUpdateTime : 0;
                            mTotalTime = mTotalTime + mCurTimeOffset;
                            mLastUpdateTime = System.currentTimeMillis();
                            //=====更新进度 流量 时间视图 end ====//

                            mCurFileInfo.setProcceed(progress);
                            AppContext.getAppContext().updateReceiverFileInfo(mCurFileInfo);
                            mHandler.sendEmptyMessage(MSG_UPDATE_FILE_INFO);
                        }

                        @Override
                        public void onSuccess(FileInfo fileInfo) {
                            //=====更新进度 流量 时间视图 start ====//
                            mHasSendedFileCount++;

                            mTotalLen = mTotalLen + (fileInfo.getSize() - mLastUpdateLen);
                            mLastUpdateLen = 0;
                            mLastUpdateTime = System.currentTimeMillis();
                            //=====更新进度 流量 时间视图 end ====//

                            fileInfo.setResult(FileInfo.FLAG_SUCCESS);
                            AppContext.getAppContext().updateReceiverFileInfo(fileInfo);
                            mHandler.sendEmptyMessage(MSG_UPDATE_FILE_INFO);
                        }

                        @Override
                        public void onFailure(Throwable t, FileInfo fileInfo) {
                            mHasSendedFileCount++;//统计发送文件

                            fileInfo.setResult(FileInfo.FLAG_FAILURE);
                            AppContext.getAppContext().updateFileInfo(fileInfo);
                            mHandler.sendEmptyMessage(MSG_UPDATE_FILE_INFO);
                        }
                    });
                    AppContext.MAIN_EXECUTOR.execute(fileReceiver);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * 关闭Socket 通信 (避免端口占用)
         */
        public void close() {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                    serverSocket = null;
                } catch (IOException e) {
                }
            }
        }
    }


    public void sendMsgToFileSender() {
        new Thread() {
            @Override
            public void run() {
                try {
                    sendFileReceiverInitSuccessMsgToFileSender();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    DatagramSocket mDatagramSocket = null;

    /**
     * 通知文件发送方 ===>>> 文件接收方初始化完毕
     */
    public void sendFileReceiverInitSuccessMsgToFileSender() throws Exception {
        Log.i(TAG, "sendFileReceiverInitSuccessMsgToFileSender------>>>start");
        if (mDatagramSocket == null) {
            mDatagramSocket = new DatagramSocket(Constant.DEFAULT_SERVER_COM_PORT);
        }
        InetAddress ipAddress = mIpPortInfo.getInetAddress();
        //1.发送 文件接收方 初始化
        byte[] sendData = Constant.MSG_FILE_RECEIVER_INIT_SUCCESS.getBytes(BaseTransfer.UTF_8);
        DatagramPacket sendPacket =
                new DatagramPacket(sendData, sendData.length, ipAddress, mIpPortInfo.getPort());
        mDatagramSocket.send(sendPacket);
        Log.i(TAG, "Send Msg To FileSender######>>>" + Constant.MSG_FILE_RECEIVER_INIT_SUCCESS);
        Log.i(TAG, "sendFileReceiverInitSuccessMsgToFileSender------>>>end");
        byte[] receiveData = new byte[1024];
        while (true) {
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            mDatagramSocket.receive(receivePacket);
            String msg = new String(receivePacket.getData(), BaseTransfer.UTF_8).trim();
            if (msg != null) {
                if (msg.startsWith(Constant.MSG_FILE_RECEIVER_INIT)) {
                    Log.i(TAG, "Get the msg from FileReceiver######>>>" + Constant.MSG_FILE_RECEIVER_INIT);
                    mHandler.sendEmptyMessage(MSG_FILE_RECEIVER_INIT_SUCCESS);
                } else if (msg.startsWith(Constant.OFF_LINE)) {
                    Log.i(TAG, "收到下线通知");
                    mHandler.sendEmptyMessage(OFF_LINE);
                } else {
                    System.out.println("Get the FileInfo from FileReceiver######>>>" + msg);
                    FileInfo fileInfo = FileInfo.toObject(msg);
                    mHandler.obtainMessage(MSG_ADD_FILE_INFO, fileInfo).sendToTarget();
                }
            }
        }
    }


    @Override
    public void onBackPressed() {

        confirmQuiteDialog();
    }

    private void quit() {
        if (mReceiverServer != null) {
            mReceiverServer.close();
            mReceiverServer = null;
        }
        closeSocket();//关闭TCP UDP 资源
        AppContext.getAppContext().getReceiverFileInfoMap().clear();  //清除选中文件的信息
        ApMgr.disableAp(getContext());    //关闭热点
        this.finish();
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
}
