package com.cn.tfl.kuaichuan.ui.view;

import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.cn.tfl.kuaichuan.AppContext;
import com.cn.tfl.kuaichuan.Constant;
import com.cn.tfl.kuaichuan.R;
import com.cn.tfl.kuaichuan.comm.BaseAdapter;
import com.cn.tfl.kuaichuan.comm.BaseRecyclerHolder;
import com.cn.tfl.kuaichuan.core.entity.FileInfo;
import com.cn.tfl.kuaichuan.core.receiver.SeletedFileListChangedBroadcastReceiver;
import com.cn.tfl.kuaichuan.core.utils.FileUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by Administrator on 2017/6/9.
 */

public class ShowSelectedFileInfoDialog {

    /**
     * UI控件
     */

    Button btn_operation;

    TextView tv_title;

    RecyclerView lv_result;

    Context mContext;
    AlertDialog mAlertDialog;
    BaseAdapter mFileInfoSeletedAdapter;
    private Map<String, FileInfo> mDataHashMap;
    List<Map.Entry<String, FileInfo>> fileInfoMapList;
    BaseAdapter.OnDataListChangedListener mOnDataListChangedListener;

    public ShowSelectedFileInfoDialog(Context context) {
        this.mContext = context;
        View contentView = View.inflate(mContext, R.layout.view_show_selected_file_info_dialog, null);
        btn_operation = (Button) contentView.findViewById(R.id.btn_operation);
        btn_operation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearAllSelectedFiles();
                sendUpdateSeletedFilesBR();//发送更新选中文件的广播
            }
        });
        tv_title = (TextView) contentView.findViewById(R.id.tv_title);
        lv_result = (RecyclerView) contentView.findViewById(R.id.lv_result);
        String title = getAllSelectedFilesDes();
        tv_title.setText(title);
        mDataHashMap = AppContext.getAppContext().getFileInfoMap();
        fileInfoMapList = new ArrayList<>(mDataHashMap.entrySet());
        //排序
        Collections.sort(fileInfoMapList, Constant.DEFAULT_COMPARATOR);
        mFileInfoSeletedAdapter = new BaseAdapter(mContext, fileInfoMapList, R.layout.item_transfer) {
            @Override
            public void convert(BaseRecyclerHolder viewHolder, Object item, int position, boolean isScrolling) {
                final FileInfo fileInfo = (FileInfo) item;
                //初始化
                viewHolder.setVisibility(R.id.pb_file, View.INVISIBLE);
                viewHolder.setVisibility(R.id.btn_operation, View.INVISIBLE);
                viewHolder.setVisibility(R.id.iv_tick, View.VISIBLE);
                viewHolder.setImageBitmap(R.id.iv_tick, BitmapFactory.decodeResource(mContext.getResources(), R.mipmap.icon_del));

                if (FileUtils.isApkFile(fileInfo.getFilePath()) || FileUtils.isMp4File(fileInfo.getFilePath())) { //Apk格式 或者MP4格式需要 缩略图
                    viewHolder.setImageBitmap(R.id.iv_shortcut, fileInfo.getBitmap());
                } else if (FileUtils.isJpgFile(fileInfo.getFilePath())) {//图片格式
                    Glide.with(mContext)
                            .load(fileInfo.getFilePath())
                            .centerCrop()
                            .placeholder(R.mipmap.icon_jpg)
                            .crossFade()
                            .into((ImageView) viewHolder.getView(R.id.iv_shortcut));
                } else if (FileUtils.isMp3File(fileInfo.getFilePath())) {//音乐格式
                    viewHolder.setImageResource(R.id.iv_shortcut, R.mipmap.icon_mp3);
                }

                viewHolder.setText(R.id.tv_name, fileInfo.getFilePath());
                viewHolder.setText(R.id.tv_progress, FileUtils.getFileSize(fileInfo.getSize()));

                viewHolder.getView(R.id.iv_tick).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AppContext.getAppContext().getFileInfoMap().remove(fileInfo.getFilePath());
                        notifyDataSetChanged();
                        if (mOnDataListChangedListener != null)
                            mOnDataListChangedListener.onDataChanged();
                    }
                });
            }
        };
        mFileInfoSeletedAdapter.setOnDataListChangedListener(mOnDataListChangedListener = new BaseAdapter.OnDataListChangedListener() {
            @Override
            public void onDataChanged() {
                if (mFileInfoSeletedAdapter.getItemCount() == 0) {
                    hide();
                }
                tv_title.setText(getAllSelectedFilesDes());
                sendUpdateSeletedFilesBR();//发送更新选中文件的广播
            }
        });

        lv_result.setAdapter(mFileInfoSeletedAdapter);

        this.mAlertDialog = new AlertDialog.Builder(mContext)
                .setView(contentView)
                .create();
    }


    /**
     * //发送更新选中文件列表的广播
     */
    private void sendUpdateSeletedFilesBR() {
        mContext.sendBroadcast(new Intent(SeletedFileListChangedBroadcastReceiver.ACTION_CHOOSE_FILE_LIST_CHANGED));
    }

    /**
     * 获取选中文件对话框的Title
     *
     * @return
     */
    private String getAllSelectedFilesDes() {
        String title = "";

        long totalSize = 0;
        Set<Map.Entry<String, FileInfo>> entrySet = AppContext.getAppContext().getFileInfoMap().entrySet();
        for (Map.Entry<String, FileInfo> entry : entrySet) {
            FileInfo fileInfo = entry.getValue();
            totalSize = totalSize + fileInfo.getSize();
        }

        title = mContext.getResources().getString(R.string.str_selected_file_info_detail)
                .replace("{count}", String.valueOf(entrySet.size()))
                .replace("{size}", String.valueOf(FileUtils.getFileSize(totalSize)));

        return title;
    }

    /**
     * 清除所有选中的文件
     */
    private void clearAllSelectedFiles() {
        AppContext.getAppContext().getFileInfoMap().clear();
        if (mFileInfoSeletedAdapter != null) {
            mFileInfoSeletedAdapter.notifyDataSetChanged();
        }

        this.hide();
    }

    /**
     * 显示
     */
    public void show() {
        if (this.mAlertDialog != null) {
            notifyDataSetChanged();
            tv_title.setText(getAllSelectedFilesDes());
            this.mAlertDialog.show();
        }
    }

    /**
     * 隐藏
     */
    public void hide() {
        if (this.mAlertDialog != null) {
            this.mAlertDialog.hide();
        }
    }


    /**
     * 通知列表发生变化
     */
    public void notifyDataSetChanged() {
        if (mFileInfoSeletedAdapter != null) {
            mFileInfoSeletedAdapter.notifyDataSetChanged();
        }
    }
}