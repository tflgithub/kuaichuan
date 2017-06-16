package com.cn.tfl.kuaichuan.ui.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.bumptech.glide.Glide;
import com.cn.tfl.kuaichuan.AppContext;
import com.cn.tfl.kuaichuan.R;
import com.cn.tfl.kuaichuan.comm.BaseAdapter;
import com.cn.tfl.kuaichuan.comm.BaseRecyclerHolder;
import com.cn.tfl.kuaichuan.core.entity.FileInfo;
import com.cn.tfl.kuaichuan.core.utils.FileUtils;
import com.cn.tfl.kuaichuan.ui.ChooseFileActivity;
import com.cn.tfl.kuaichuan.utils.AnimationUtils;

import java.util.List;

/**
 * Created by Happiness on 2017/6/9.
 */
@RequiresApi(api = Build.VERSION_CODES.M)
public class FileInfoFragment extends Fragment {

    RecyclerView gv;
    ProgressBar pb;

    private int mType = FileInfo.TYPE_APK;
    private List<FileInfo> mFileInfoList;
    private BaseAdapter mFileInfoAdapter;

    @SuppressLint("ValidFragment")
    public FileInfoFragment() {
        super();
    }

    @SuppressLint("ValidFragment")
    public FileInfoFragment(int type) {
        super();
        this.mType = type;
    }

    public static FileInfoFragment newInstance(int type) {
        FileInfoFragment fragment = new FileInfoFragment(type);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_file, container, false);
        gv = (RecyclerView) rootView.findViewById(R.id.gv);
        pb = (ProgressBar) rootView.findViewById(R.id.pb);
        int numColumns = 1;
        if (mType == FileInfo.TYPE_APK) { //应用
            numColumns = 4;
        } else if (mType == FileInfo.TYPE_JPG) { //图片
            numColumns = 3;
        }
        gv.setLayoutManager(new GridLayoutManager(getContext(), numColumns));
        //Android6.0 requires android.permission.READ_EXTERNAL_STORAGE
        init();//初始化界面
        return rootView;
    }


    private void init() {
        if (mType == FileInfo.TYPE_APK) {
            new GetFileInfoListTask(getContext(), FileInfo.TYPE_APK).executeOnExecutor(AppContext.MAIN_EXECUTOR);
        } else if (mType == FileInfo.TYPE_JPG) {
            new GetFileInfoListTask(getContext(), FileInfo.TYPE_JPG).executeOnExecutor(AppContext.MAIN_EXECUTOR);
        } else if (mType == FileInfo.TYPE_MP3) {
            new GetFileInfoListTask(getContext(), FileInfo.TYPE_MP3).executeOnExecutor(AppContext.MAIN_EXECUTOR);
        } else if (mType == FileInfo.TYPE_MP4) {
            new GetFileInfoListTask(getContext(), FileInfo.TYPE_MP4).executeOnExecutor(AppContext.MAIN_EXECUTOR);
        }
    }

    @Override
    public void onResume() {
        updateFileInfoAdapter();
        super.onResume();
    }

    /**
     * 更新FileInfoAdapter
     */
    public void updateFileInfoAdapter() {
        if (mFileInfoAdapter != null) {
            mFileInfoAdapter.notifyDataSetChanged();
        }
    }

    /**
     * 更新ChoooseActivity选中View
     */
    private void updateSelectedView() {
        if (getActivity() != null && (getActivity() instanceof ChooseFileActivity)) {
            ChooseFileActivity chooseFileActivity = (ChooseFileActivity) getActivity();
            chooseFileActivity.getSelectedView();
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    /**
     * 显示进度
     */
    public void showProgressBar() {
        if (pb != null) {
            pb.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 隐藏进度
     */
    public void hideProgressBar() {
        if (pb != null && pb.isShown()) {
            pb.setVisibility(View.GONE);
        }
    }


    /**
     * 获取ApkInfo列表任务
     */
    class GetFileInfoListTask extends AsyncTask<String, Integer, List<FileInfo>> {
        Context sContext = null;
        int sType = FileInfo.TYPE_APK;
        List<FileInfo> sFileInfoList = null;

        public GetFileInfoListTask(Context sContext, int type) {
            this.sContext = sContext;
            this.sType = type;
        }

        @Override
        protected void onPreExecute() {
            showProgressBar();
            super.onPreExecute();
        }

        @Override
        protected List doInBackground(String... params) {
            //FileUtils.getSpecificTypeFiles 只获取FileInfo的属性 filePath与size
            if (sType == FileInfo.TYPE_APK) {
                sFileInfoList = FileUtils.getSpecificTypeFiles(sContext, new String[]{FileInfo.EXTEND_APK});
                sFileInfoList = FileUtils.getDetailFileInfos(sContext, sFileInfoList, FileInfo.TYPE_APK);
            } else if (sType == FileInfo.TYPE_JPG) {
                sFileInfoList = FileUtils.getSpecificTypeFiles(sContext, new String[]{FileInfo.EXTEND_JPG, FileInfo.EXTEND_JPEG});
                sFileInfoList = FileUtils.getDetailFileInfos(sContext, sFileInfoList, FileInfo.TYPE_JPG);
            } else if (sType == FileInfo.TYPE_MP3) {
                sFileInfoList = FileUtils.getSpecificTypeFiles(sContext, new String[]{FileInfo.EXTEND_MP3});
                sFileInfoList = FileUtils.getDetailFileInfos(sContext, sFileInfoList, FileInfo.TYPE_MP3);
            } else if (sType == FileInfo.TYPE_MP4) {
                sFileInfoList = FileUtils.getSpecificTypeFiles(sContext, new String[]{FileInfo.EXTEND_MP4});
                sFileInfoList = FileUtils.getDetailFileInfos(sContext, sFileInfoList, FileInfo.TYPE_MP4);
            }

            mFileInfoList = sFileInfoList;
            return sFileInfoList;
        }


        @Override
        protected void onPostExecute(List<FileInfo> list) {
            hideProgressBar();
            switch (mType) {
                case FileInfo.TYPE_APK:
                    mFileInfoAdapter = new BaseAdapter(sContext, sFileInfoList, R.layout.item_apk_file) {
                        @Override
                        public void convert(BaseRecyclerHolder viewHolder, Object item, int position, boolean isScrolling) {
                            FileInfo fileInfo = (FileInfo) item;
                            viewHolder.setImageBitmap(R.id.iv_shortcut, fileInfo.getBitmap());
                            viewHolder.setText(R.id.tv_name, fileInfo.getName() == null ? "" : fileInfo.getName());
                            viewHolder.setText(R.id.tv_size, fileInfo.getSizeDesc() == null ? "" : fileInfo.getSizeDesc());
                            //全局变量是否存在FileInfo
                            if (AppContext.getAppContext().isExist(fileInfo)) {
                                viewHolder.setVisibility(R.id.iv_ok_tick, View.VISIBLE);
                            } else {
                                viewHolder.setVisibility(R.id.iv_ok_tick, View.GONE);
                            }
                        }
                    };
                    break;
                case FileInfo.TYPE_JPG:
                    mFileInfoAdapter = new BaseAdapter(sContext, sFileInfoList, R.layout.item_jpg) {
                        @Override
                        public void convert(BaseRecyclerHolder viewHolder, Object item, int position, boolean isScrolling) {
                            FileInfo fileInfo = (FileInfo) item;
                            Glide.with(getContext())
                                    .load(fileInfo.getFilePath())
                                    .centerCrop()
                                    .placeholder(R.mipmap.icon_jpg)
                                    .crossFade()
                                    .into((ImageView) viewHolder.getView(R.id.iv_shortcut));
                            //全局变量是否存在FileInfo
                            if (AppContext.getAppContext().isExist(fileInfo)) {
                                viewHolder.setVisibility(R.id.iv_ok_tick, View.VISIBLE);
                            } else {
                                viewHolder.setVisibility(R.id.iv_ok_tick, View.GONE);
                            }
                        }
                    };
                    break;
                case FileInfo.TYPE_MP3:
                    mFileInfoAdapter = new BaseAdapter(sContext, sFileInfoList, R.layout.item_file) {
                        @Override
                        public void convert(BaseRecyclerHolder viewHolder, Object item, int position, boolean isScrolling) {
                            FileInfo fileInfo = (FileInfo) item;
                            viewHolder.setText(R.id.tv_name, fileInfo.getName() == null ? "" : fileInfo.getName());
                            viewHolder.setText(R.id.tv_size, fileInfo.getSizeDesc() == null ? "" : fileInfo.getSizeDesc());
                            //全局变量是否存在FileInfo
                            if (AppContext.getAppContext().isExist(fileInfo)) {
                                viewHolder.setVisibility(R.id.iv_ok_tick, View.VISIBLE);
                            } else {
                                viewHolder.setVisibility(R.id.iv_ok_tick, View.GONE);
                            }
                        }
                    };
                    break;
                case FileInfo.TYPE_MP4:
                    mFileInfoAdapter = new BaseAdapter(sContext, sFileInfoList, R.layout.item_file) {
                        @Override
                        public void convert(BaseRecyclerHolder viewHolder, Object item, int position, boolean isScrolling) {
                            FileInfo fileInfo = (FileInfo) item;
                            viewHolder.setImageBitmap(R.id.iv_shortcut, fileInfo.getBitmap());
                            viewHolder.setText(R.id.tv_name, fileInfo.getName() == null ? "" : fileInfo.getName());
                            viewHolder.setText(R.id.tv_size, fileInfo.getSizeDesc() == null ? "" : fileInfo.getSizeDesc());
                            //全局变量是否存在FileInfo
                            if (AppContext.getAppContext().isExist(fileInfo)) {
                                viewHolder.setVisibility(R.id.iv_ok_tick, View.VISIBLE);
                            } else {
                                viewHolder.setVisibility(R.id.iv_ok_tick, View.GONE);
                            }
                        }
                    };
                    break;
            }
            gv.setAdapter(mFileInfoAdapter);
            mFileInfoAdapter.setOnItemClickListener(new BaseAdapter.OnItemClickListener() {
                @Override
                public void onItemClick(RecyclerView parent, View view, int position) {

                    FileInfo fileInfo = mFileInfoList.get(position);
                    if (AppContext.getAppContext().isExist(fileInfo)) {
                        AppContext.getAppContext().delFileInfo(fileInfo);
                        updateSelectedView();
                    } else {
                        //1.添加任务
                        AppContext.getAppContext().addFileInfo(fileInfo);
                        //2.添加任务 动画
                        View targetView = null;
                        View startView = view.findViewById(R.id.iv_shortcut);
                        if (getActivity() != null && (getActivity() instanceof ChooseFileActivity)) {
                            ChooseFileActivity chooseFileActivity = (ChooseFileActivity) getActivity();
                            targetView = chooseFileActivity.getSelectedView();
                        }
                        AnimationUtils.setAddTaskAnimation(getActivity(), startView, targetView, null);
                    }
                    mFileInfoAdapter.notifyDataSetChanged();
                }
            });
        }
    }
}
