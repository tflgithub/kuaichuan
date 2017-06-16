package com.cn.tfl.kuaichuan.ui;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
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
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.cn.tfl.kuaichuan.AppContext;
import com.cn.tfl.kuaichuan.Constant;
import com.cn.tfl.kuaichuan.R;
import com.cn.tfl.kuaichuan.comm.BaseActivity;
import com.cn.tfl.kuaichuan.comm.BaseAdapter;
import com.cn.tfl.kuaichuan.comm.BaseRecyclerHolder;
import com.cn.tfl.kuaichuan.core.BaseTransfer;
import com.cn.tfl.kuaichuan.core.FileSender;
import com.cn.tfl.kuaichuan.core.entity.FileInfo;
import com.cn.tfl.kuaichuan.core.entity.IpPortInfo;
import com.cn.tfl.kuaichuan.core.utils.FileUtils;
import com.cn.tfl.kuaichuan.core.utils.WifiMgr;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FileSenderActivity extends BaseActivity {

    public static final String TAG = FileSenderActivity.class.getSimpleName();

    List<FileSender> mFileSenderList = new ArrayList<>();
    List<Map.Entry<String, FileInfo>> mFileInfoMapList;
    long mTotalLen = 0;     //所有总文件的进度
    long mCurOffset = 0;    //每次传送的偏移量
    long mLastUpdateLen = 0; //每个文件传送onProgress() 之前的进度
    String[] mStorageArray = null;

    long mTotalTime = 0;
    long mCurTimeOffset = 0;
    long mLastUpdateTime = 0;
    String[] mTimeArray = null;
    int mHasSendedFileCount = 0;

    private TextView tv_value_storage, tv_unit_storage, tv_value_time, tv_unit_time, tv_back;
    private ProgressBar pb_total;
    private RecyclerView senderList;
    public static final int MSG_UPDATE_FILE_INFO = 0X6666;
    public static final int SERVER_IS_BOLCKED = 0x7777;
    private BaseAdapter mFileSenderAdapter;
    private IpPortInfo mIpPortInfo;
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            //TODO 未完成 handler实现细节以及封装
            if (msg.what == MSG_UPDATE_FILE_INFO) {
                updateTotalProgressView();
                if (mFileSenderAdapter != null) mFileSenderAdapter.notifyDataSetChanged();
            } else if (msg.what == SERVER_IS_BOLCKED) {
                Toast.makeText(getContext(), "对方已下线,连接断开！", Toast.LENGTH_SHORT).show();
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_sender);
        senderList = (RecyclerView) findViewById(R.id.sender_list);
        senderList.setLayoutManager(new LinearLayoutManager(getContext()));
        tv_value_storage = (TextView) findViewById(R.id.tv_value_storage);
        tv_unit_storage = (TextView) findViewById(R.id.tv_unit_storage);
        tv_value_time = (TextView) findViewById(R.id.tv_value_time);
        tv_unit_time = (TextView) findViewById(R.id.tv_unit_time);
        pb_total = (ProgressBar) findViewById(R.id.pb_total);
        tv_back = (TextView) findViewById(R.id.tv_back);
        tv_back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showExistDialog();
            }
        });
        init();
    }

    private void init() {
        mIpPortInfo = (IpPortInfo) getIntent().getSerializableExtra(Constant.KEY_IP_PORT_INFO);
        pb_total.setMax(100);
        List<Map.Entry<String, FileInfo>> fileInfoMapList = new ArrayList<>(AppContext.getAppContext().getFileInfoMap().entrySet());
        mFileInfoMapList = fileInfoMapList;
        Collections.sort(fileInfoMapList, Constant.DEFAULT_COMPARATOR);
        mFileSenderAdapter = new BaseAdapter(getContext(), mFileInfoMapList, R.layout.item_transfer) {
            @Override
            public void convert(BaseRecyclerHolder viewHolder, Object item, int position, boolean isScrolling) {
                //初始化
                viewHolder.setVisibility(R.id.pb_file, View.VISIBLE);
                viewHolder.setVisibility(R.id.iv_tick, View.GONE);
                final FileInfo fileInfo = mFileInfoMapList.get(position).getValue();
                if (FileUtils.isApkFile(fileInfo.getFilePath()) || FileUtils.isMp4File(fileInfo.getFilePath())) { //Apk格式 或者MP4格式需要 缩略图
                    viewHolder.setImageBitmap(R.id.iv_shortcut, fileInfo.getBitmap());
                } else if (FileUtils.isJpgFile(fileInfo.getFilePath())) {//图片格式
                    Glide.with(getContext())
                            .load(fileInfo.getFilePath())
                            .centerCrop()
                            .placeholder(R.mipmap.icon_jpg)
                            .crossFade()
                            .into((ImageView) viewHolder.getView(R.id.iv_shortcut));
                } else if (FileUtils.isMp3File(fileInfo.getFilePath())) {//音乐格式
                    viewHolder.setImageResource(R.id.iv_shortcut, R.mipmap.icon_mp3);
                }
                viewHolder.setText(R.id.tv_name, FileUtils.getFileName(fileInfo.getFilePath()));

                if (fileInfo.getResult() == FileInfo.FLAG_SUCCESS) { //文件传输成功
                    long total = fileInfo.getSize();
                    viewHolder.setVisibility(R.id.pb_file, View.GONE);
                    viewHolder.setText(R.id.tv_progress, FileUtils.getFileSize(total) + "/" + FileUtils.getFileSize(total));
                    viewHolder.setVisibility(R.id.btn_operation, View.INVISIBLE);
                    viewHolder.setVisibility(R.id.iv_tick, View.VISIBLE);
                } else if (fileInfo.getResult() == FileInfo.FLAG_FAILURE) { //文件传输失败
                    viewHolder.setVisibility(R.id.pb_file, View.GONE);
                } else {//文件传输中
                    long progress = fileInfo.getProcceed();
                    long total = fileInfo.getSize();
                    viewHolder.setText(R.id.tv_progress, FileUtils.getFileSize(progress) + "/" + FileUtils.getFileSize(total));

                    int percent = (int) (progress * 100 / total);
                    ((ProgressBar) viewHolder.getView(R.id.pb_file)).setMax(100);
                    ((ProgressBar) viewHolder.getView(R.id.pb_file)).setProgress(percent);
                    //TODO 传输过程中取消的问题
                    viewHolder.setText(R.id.btn_operation, getString(R.string.str_cancel));
                    viewHolder.getView(R.id.btn_operation).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            //可否通过广播来实现？
                        }
                    });
                }
            }
        };
        senderList.setAdapter(mFileSenderAdapter);
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE_WRITE_FILE);
        } else {
            initSendServer(fileInfoMapList);//开启传送文件
        }
    }

    private void initSendServer(List<Map.Entry<String, FileInfo>> fileInfoMapList) {

        String serverIp = WifiMgr.getInstance(getContext()).getIpAddressFromHotspot();
        for (Map.Entry<String, FileInfo> entry : fileInfoMapList) {
            final FileInfo fileInfo = entry.getValue();
            FileSender fileSender = new FileSender(getContext(), fileInfo, serverIp, Constant.DEFAULT_SERVER_PORT);
            fileSender.setOnSendListener(new FileSender.OnSendListener() {
                @Override
                public void onStart() {
                    mLastUpdateLen = 0;
                    mLastUpdateTime = System.currentTimeMillis();
                }

                @Override
                public void onProgress(long progress, long total) {
                    //TODO 更新
                    //=====更新进度 流量 时间视图 start ====//
                    mCurOffset = progress - mLastUpdateLen > 0 ? progress - mLastUpdateLen : 0;
                    mTotalLen = mTotalLen + mCurOffset;
                    mLastUpdateLen = progress;
                    mCurTimeOffset = System.currentTimeMillis() - mLastUpdateTime > 0 ? System.currentTimeMillis() - mLastUpdateTime : 0;
                    mTotalTime = mTotalTime + mCurTimeOffset;
                    mLastUpdateTime = System.currentTimeMillis();
                    //=====更新进度 流量 时间视图 end ====//
                    //更新文件传送进度的ＵＩ
                    fileInfo.setProcceed(progress);
                    AppContext.getAppContext().updateFileInfo(fileInfo);
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
                    System.out.println(Thread.currentThread().getName());
                    //TODO 成功
                    fileInfo.setResult(FileInfo.FLAG_SUCCESS);
                    AppContext.getAppContext().updateFileInfo(fileInfo);
                    mHandler.sendEmptyMessage(MSG_UPDATE_FILE_INFO);
                }

                @Override
                public void onFailure(Throwable t, FileInfo fileInfo) {
                    mHasSendedFileCount++;//统计发送文件
                    //TODO 失败
                    fileInfo.setResult(FileInfo.FLAG_FAILURE);
                    AppContext.getAppContext().updateFileInfo(fileInfo);
                    mHandler.sendEmptyMessage(MSG_UPDATE_FILE_INFO);
                }
            });
            mFileSenderList.add(fileSender);
            AppContext.FILE_SENDER_EXECUTOR.execute(fileSender);
        }
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
            if (mHasSendedFileCount == AppContext.getAppContext().getFileInfoMap().size()) {
                pb_total.setProgress(0);
                tv_value_storage.setTextColor(getResources().getColor(R.color.color_yellow));
                tv_value_time.setTextColor(getResources().getColor(R.color.color_yellow));
                return;
            }

            long total = AppContext.getAppContext().getAllSendFileInfoSize();
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

    @Override
    public void onBackPressed() {
        showExistDialog();
    }

    /**
     * 判断是否有文件在传送
     */
    private boolean hasFileSending() {
        for (FileSender fileSender : mFileSenderList) {
            if (fileSender.isRunning()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 显示是否退出 对话框
     */
    private void showExistDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setMessage("是否退出？")
                .setPositiveButton(getResources().getString(R.string.str_yes), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finishNormal();
                    }
                })
                .setNegativeButton(getResources().getString(R.string.str_no), null)
                .create()
                .show();
    }

    /**
     * 正常退出
     */
    private void finishNormal() {
        stopAllFileSendingTask();
        AppContext.getAppContext().getFileInfoMap().clear();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    DatagramSocket datagramSocket = new DatagramSocket();
                    byte[] sendData = Constant.OFF_LINE.getBytes(BaseTransfer.UTF_8);
                    DatagramPacket sendPacket =
                            new DatagramPacket(sendData, sendData.length, mIpPortInfo.getInetAddress(), mIpPortInfo.getPort());
                    datagramSocket.send(sendPacket);
                    Log.i(TAG, "通知对方下线>>>" + Constant.OFF_LINE);
                } catch (SocketException e) {
                    e.printStackTrace();
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
        finish();
    }

    /**
     * 停止所有的文件发送任务
     */
    private void stopAllFileSendingTask() {
        for (FileSender fileSender : mFileSenderList) {
            if (fileSender != null) {
                fileSender.stop();
            }
        }
    }
}
