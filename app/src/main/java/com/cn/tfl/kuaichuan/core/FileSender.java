package com.cn.tfl.kuaichuan.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.cn.tfl.kuaichuan.core.entity.FileInfo;
import com.cn.tfl.kuaichuan.core.utils.ApkUtils;
import com.cn.tfl.kuaichuan.core.utils.FileUtils;
import com.cn.tfl.kuaichuan.core.utils.ScreenshotUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Happiness on 2017/6/6.
 */
//文件发送者
public class FileSender extends BaseTransfer implements Runnable {

    private static final String TAG = FileSender.class.getSimpleName();

    Context mContext;

    Lock lock = new ReentrantLock();
    /**
     * 传送文件目标的地址以及端口
     */
    private String mServerIpAddress;

    private int mPort;
    /**
     * 传送文件的信息
     */
    private FileInfo mFileInfo;

    /**
     * Socket的输入输出流
     */
    private Socket mSocket;
    private OutputStream mOutputStream;
    boolean mIsPaused = false;
    private boolean mIsFinished = false;
    private boolean mIsStop = false;

    /**
     * 文件传送的监听
     */
    OnSendListener mOnSendListener;

    public void setOnSendListener(OnSendListener mOnSendListener) {
        this.mOnSendListener = mOnSendListener;
    }

    public FileSender(Context context, FileInfo mFileInfo, String mServerIpAddress, int mPort) {
        this.mContext = context;
        this.mFileInfo = mFileInfo;
        this.mServerIpAddress = mServerIpAddress;
        this.mPort = mPort;
    }

    @Override
    public void init() throws Exception {
        this.mSocket = new Socket(mServerIpAddress, mPort);
        OutputStream os = this.mSocket.getOutputStream();
        mOutputStream = new BufferedOutputStream(os);
    }

    @Override
    public void parseHeader() throws Exception {
        Log.i(TAG, ">>>>>>>>>>parseHeader start");
        StringBuffer headerSb = new StringBuffer();
        String jsonStr = FileInfo.toJsonStr(mFileInfo);
        jsonStr = TYPE_FILE + SPERATOR + jsonStr;
        headerSb.append(jsonStr);
        int leftLen = BYTE_SIZE_HEADER - jsonStr.getBytes(UTF_8).length; //对于英文是一个字母对应一个字节，中文的情况下对应两个字节。剩余字节数不应该是字节数
        for (int i = 0; i < leftLen; i++) {
            headerSb.append(" ");
        }
        byte[] headbytes = headerSb.toString().getBytes(UTF_8);

        //写入header
        mOutputStream.write(headbytes);


        //拼接缩略图
        StringBuilder screenshotSb = new StringBuilder();

        int ssByteArraySize = 0;

        //缩略图的分类处理
        if (mFileInfo != null) {
            Bitmap screenshot = null;
            byte[] bytes = null;
            if (FileUtils.isApkFile(mFileInfo.getFilePath())) { //apk 缩略图处理
                Bitmap bitmap = ApkUtils.drawableToBitmap(ApkUtils.getApkThumbnail(mContext, mFileInfo.getFilePath()));
                screenshot = ScreenshotUtils.extractThumbnail(bitmap, 96, 96);
            } else if (FileUtils.isJpgFile(mFileInfo.getFilePath())) { //jpg 缩略图处理
                screenshot = FileUtils.getScreenshotBitmap(mContext, mFileInfo.getFilePath(), FileInfo.TYPE_JPG);
                screenshot = ScreenshotUtils.extractThumbnail(screenshot, 96, 96);
            } else if (FileUtils.isMp3File(mFileInfo.getFilePath())) { //mp3 缩略图处理
                //DO NOTHING mp3文件可以没有缩略图 可指定
                screenshot = FileUtils.getScreenshotBitmap(mContext, mFileInfo.getFilePath(), FileInfo.TYPE_MP3);
                screenshot = ScreenshotUtils.extractThumbnail(screenshot, 96, 96);
            } else if (FileUtils.isMp4File(mFileInfo.getFilePath())) { //mp4 缩略图处理
                screenshot = FileUtils.getScreenshotBitmap(mContext, mFileInfo.getFilePath(), FileInfo.TYPE_MP4);
                screenshot = ScreenshotUtils.extractThumbnail(screenshot, 96, 96);
            }
            if (screenshot != null) {
                bytes = FileUtils.bitmapToByteArray(screenshot);
                ssByteArraySize = bytes.length;
                mOutputStream.write(bytes);
            }
        }

        int ssLeftLen = BYTE_SIZE_SCREENSHOT - ssByteArraySize; //缩略图剩余的字节数
        for (int i = 0; i < ssLeftLen; i++) {
            screenshotSb.append(" ");
        }
        byte[] screenshotBytes = screenshotSb.toString().getBytes(UTF_8);

        //写入缩略图
        mOutputStream.write(screenshotBytes);

        Log.i(TAG, ">>>>>>>>>>parseHeader end");
    }

    @Override
    public void parseBody() throws Exception {
        Log.i(TAG, ">>>>>>>>>>parseBody start");
        //写入文件
        long fileSize = mFileInfo.getSize();
        InputStream fis = new FileInputStream(new File(mFileInfo.getFilePath()));

        //记录文件开始写入时间
        long startTime = System.currentTimeMillis();

        byte[] bytes = new byte[BYTE_SIZE_DATA];
        long total = 0;
        int len = 0;

        long sTime = System.currentTimeMillis();
        long eTime = 0;
        while ((len = fis.read(bytes)) != -1) {
            synchronized (lock) {
                if (mIsPaused) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                mOutputStream.write(bytes, 0, len);
                total = total + len;
                eTime = System.currentTimeMillis();
                if (eTime - sTime > 200) { //大于500ms 才进行一次监听
                    sTime = eTime;
                    if (mOnSendListener != null) mOnSendListener.onProgress(total, fileSize);
                }
            }
        }
        //记录文件结束写入时间
        long endTime = System.currentTimeMillis();
        //Log.i(TAG, "FileSender body write######>>>" + (TimeUtils.formatTime(endTime - startTime)));
        Log.i(TAG, "FileSender body write######>>>" + total);

        mOutputStream.flush();
        //每一次socket连接就是一个通信，如果当前OutputStream不关闭的话。FileReceiver端会阻塞在那里
        mOutputStream.close();
        Log.i(TAG, "parseBody######>>>end");

        if (mOnSendListener != null) mOnSendListener.onSuccess(mFileInfo);
        mIsFinished = true;
    }

    @Override
    public void finish() throws Exception {
        if (mOutputStream != null) {
            try {
                mOutputStream.close();
            } catch (IOException e) {

            }
        }

        if (mSocket != null && mSocket.isConnected()) {
            try {
                mSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        Log.i(TAG, "FileSender close socket######>>>");
    }


    /**
     * 停止线程下载
     */
    public void pause() {
        synchronized (lock) {
            mIsPaused = true;
            lock.notifyAll();
        }
    }

    /**
     * 重新开始线程下载
     */
    public void resume() {
        synchronized (lock) {
            mIsPaused = false;
            lock.notifyAll();
        }
    }

    /**
     * 判断是否断开连接，断开返回true,没有返回false
     *
     * @return
     */
    public Boolean isServerClose() {
        try {
            //发送1个字节的紧急数据，默认情况下，服务器端没有开启紧急数据处理，不影响正常通信
            mSocket.sendUrgentData(0xFF);
            return false;
        } catch (Exception se) {
            return true;
        }
    }

    /**
     * 设置当前的发送任务不执行
     */
    public void stop() {
        mIsStop = true;
    }

    /**
     * 文件是否在传送中？
     *
     * @return
     */
    public boolean isRunning() {
        return !mIsFinished;
    }


    @Override
    public void run() {
        if (mIsStop) return; //设置当前的任务不执行， 只能在线程未执行之前有效

        try {
            if (mOnSendListener != null) mOnSendListener.onStart();
            init();
        } catch (Exception e) {
            Log.i(TAG, "FileSender init() --->>> occur exception:" + e.getMessage());
            if (mOnSendListener != null) mOnSendListener.onFailure(e, mFileInfo);
        }
        try {
            parseHeader();
        } catch (Exception e) {
            Log.i(TAG, "FileSender parseHeader() --->>> occur exception:" + e.getMessage());
            if (mOnSendListener != null) mOnSendListener.onFailure(e, mFileInfo);
        }
        try {
            parseBody();
        } catch (Exception e) {
            Log.i(TAG, "FileSender parseBody() --->>> occur exception:" + e.getMessage());
            if (mOnSendListener != null) mOnSendListener.onFailure(e, mFileInfo);
        }
        try {
            finish();
        } catch (Exception e) {
            Log.i(TAG, "FileSender finish() --->>> occur exception:" + e.getMessage());
            if (mOnSendListener != null) mOnSendListener.onFailure(e, mFileInfo);
        }
    }


    /**
     * 文件传送的监听
     */
    public interface OnSendListener {

        void onStart();

        void onProgress(long progress, long total);

        void onSuccess(FileInfo fileInfo);

        void onFailure(Throwable t, FileInfo fileInfo);
    }
}
