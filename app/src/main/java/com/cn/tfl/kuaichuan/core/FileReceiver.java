package com.cn.tfl.kuaichuan.core;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.cn.tfl.kuaichuan.core.entity.FileInfo;
import com.cn.tfl.kuaichuan.core.utils.FileUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Happiness on 2017/6/6.
 */
//文件接收者
public class FileReceiver extends BaseTransfer implements Runnable {

    private static final String TAG = FileReceiver.class.getSimpleName();
    /**
     * 文件接收的监听
     */
    OnReceiveListener mOnReceiveListener;
    /**
     * Socket的输入输出流
     */
    private Socket mSocket;
    private InputStream mInputStream;

    /**
     * 传送文件的信息
     */
    private FileInfo mFileInfo;

    /**
     * 控制线程暂停 恢复
     */
    private final Lock lock = new ReentrantLock();

    public void setOnReceiveListener(OnReceiveListener mOnReceiveListener) {
        this.mOnReceiveListener = mOnReceiveListener;
    }

    boolean mIsPaused = false;


    public FileReceiver(Socket mSocket) {
        this.mSocket = mSocket;
    }

    @Override
    public void init() throws Exception {
        if (this.mSocket != null) {
            this.mInputStream = mSocket.getInputStream();
        }
    }

    @Override
    public void parseHeader() throws Exception {
        Log.i(TAG, "parseHeader######>>>start");

        //Are you sure can read the 1024 byte accurately?
        //读取header部分
        byte[] headerBytes = new byte[BYTE_SIZE_HEADER];
        int headTotal = 0;
        int readByte = -1;
        //开始读取header
        while ((readByte = mInputStream.read()) != -1) {
            headerBytes[headTotal] = (byte) readByte;
            headTotal++;
            if (headTotal == headerBytes.length) {
                break;
            }
        }

        //读取缩略图部分
        byte[] screenshotBytes = new byte[BYTE_SIZE_SCREENSHOT];
        int screenshotTotal = 0;
        int sreadByte = -1;
        //开始读取缩略图
        while((sreadByte = mInputStream.read()) != -1){
            screenshotBytes[screenshotTotal] = (byte) sreadByte;

            screenshotTotal ++;
            if(screenshotTotal == screenshotBytes.length){
                break;
            }
        }

        Bitmap bitmap = BitmapFactory.decodeByteArray(screenshotBytes, 0, screenshotBytes.length);
        if(mOnReceiveListener != null)mOnReceiveListener.onGetScreenshot(bitmap);

        Log.i(TAG, "FileReceiver receive screenshot size------>>>" + screenshotTotal);
        //解析header
        String jsonStr = new String(headerBytes, UTF_8);
        String[] strArray = jsonStr.split(SPERATOR);
        jsonStr = strArray[1].trim();
        mFileInfo = FileInfo.toObject(jsonStr);
        if(mOnReceiveListener != null) mOnReceiveListener.onGetFileInfo(mFileInfo);
        Log.i(TAG, "parseHeader######>>>end");
    }

    @Override
    public void parseBody() throws Exception {
        //写入文件
        long fileSize = mFileInfo.getSize();
        OutputStream bos = new FileOutputStream(FileUtils.generateLocalFile(mFileInfo.getFilePath()));

        //记录文件开始写入时间
        long startTime = System.currentTimeMillis();

        byte[] bytes = new byte[BYTE_SIZE_DATA];
        long total = 0;
        int len = 0;

        long sTime = System.currentTimeMillis();
        long eTime = 0;
        while ((len = mInputStream.read(bytes)) != -1) {
            synchronized (lock) {
                if (mIsPaused) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                bos.write(bytes, 0, len);
                total = total + len;
                eTime = System.currentTimeMillis();
                if (eTime - sTime > 200) { //大于500ms 才进行一次监听
                    sTime = eTime;
                    if (mOnReceiveListener != null) mOnReceiveListener.onProgress(total, fileSize);
                }
            }
        }
        if(mOnReceiveListener != null) mOnReceiveListener.onSuccess(mFileInfo);
    }

    @Override
    public void finish() throws Exception {
        //TODO 实现一些资源的关闭

        if (mInputStream != null) {
            try {
                mInputStream.close();
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

    }

    @Override
    public void run() {

        try {
            if (mOnReceiveListener != null) mOnReceiveListener.onStart();
            init();
        } catch (Exception e) {
            Log.i(TAG, "FileReceiver init() --->>> occur exception" + e.getMessage());
            if (mOnReceiveListener != null) mOnReceiveListener.onFailure(e, mFileInfo);
        }
        try {
            parseHeader();
        } catch (Exception e) {
            Log.i(TAG, "FileReceiver parseHeader() --->>> occur exception" + e.getMessage());
            if (mOnReceiveListener != null) mOnReceiveListener.onFailure(e, mFileInfo);
        }
        try {
            parseBody();
        } catch (Exception e) {
            Log.i(TAG, "FileReceiver parseBody() --->>> occur exception" + e.getMessage());
            if (mOnReceiveListener != null) mOnReceiveListener.onFailure(e, mFileInfo);
        }
        try {
            finish();
        } catch (Exception e) {
            Log.i(TAG, "FileReceiver finish() --->>> occur exception" + e.getMessage());
            if (mOnReceiveListener != null) mOnReceiveListener.onFailure(e, mFileInfo);
        }
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
     * 文件接收的监听
     */
    public interface OnReceiveListener {
        void onStart();

        void onGetFileInfo(FileInfo fileInfo);

        void onGetScreenshot(Bitmap bitmap);

        void onProgress(long progress, long total);

        void onSuccess(FileInfo fileInfo);

        void onFailure(Throwable t, FileInfo fileInfo);
    }


}
