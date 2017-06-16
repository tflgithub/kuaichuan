package com.cn.tfl.kuaichuan.comm;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.cn.tfl.kuaichuan.AppContext;
import com.cn.tfl.kuaichuan.core.entity.FileInfo;

/**
 * Created by Happiness on 2017/6/9.
 */

public class BaseActivity extends AppCompatActivity {

    /**
     * 写文件的请求码
     */
    public static final int REQUEST_CODE_WRITE_FILE = 200;

    /**
     * 读取文件的请求码
     */
    public static final int REQUEST_CODE_READ_FILE = 201;

    /**
     * 打开GPS的请求码
     */
    public static final int REQUEST_CODE_OPEN_GPS = 205;

    Context mContext;
    ProgressDialog mProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mContext = this;
        //StatusBarUtils.setStatuBarAndBottomBarTranslucent(this);
        super.onCreate(savedInstanceState);

    }


    /**
     * 获取上下文
     *
     * @return
     */
    public Context getContext() {
        return mContext;
    }

    /**
     * 显示对话框
     */
    protected void showProgressDialog(String text) {
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(mContext);
        }
        mProgressDialog.setMessage(text);
        mProgressDialog.show();
    }

    /**
     * 隐藏对话框
     */
    protected void hideProgressDialog() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.hide();
            mProgressDialog = null;
        }
    }

    /**
     * 解析FileInfo
     *
     * @param msg
     */
    protected void parseFileInfo(String msg) {
        FileInfo fileInfo = FileInfo.toObject(msg);
        if (fileInfo != null && fileInfo.getFilePath() != null) {
            AppContext.getAppContext().addReceiverFileInfo(fileInfo);
        }
    }

}
