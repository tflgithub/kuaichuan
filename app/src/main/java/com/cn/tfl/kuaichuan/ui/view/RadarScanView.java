package com.cn.tfl.kuaichuan.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.view.View;

/**
 * Created by Happiness on 2017/6/6.
 */

public class RadarScanView extends View {

    /**
     * 思路：我们首先初始化画笔，并且获取到控件的宽高，在onMeasure()中设置铺满，然后在onDraw()方法中绘制四个静态圆和一个渐变圆，
     * 我们通过Matrix矩阵来让他不停的旋转就达到我们想要的效果了
     */

    private Paint mPaintLine, mPaintCircle;


    // 旋转角度
    private int degrees;
    private RectF mRectF;
    int centerX, centerY;

    public RadarScanView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    private void initView() {
        mPaintLine = new Paint();
        mPaintLine.setColor(Color.WHITE);
        mPaintLine.setAntiAlias(true);
        mPaintLine.setStrokeWidth(2);
        mPaintLine.setStyle(Paint.Style.STROKE);//设置空心

        mPaintCircle = new Paint();
        mPaintCircle.setStyle(Paint.Style.FILL);//设置实心
        mPaintCircle.setAntiAlias(true);
        mRectF = new RectF();
    }

    /**
     * 测量
     *
     * @author LGL
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mRectF.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
        centerX = getMeasuredWidth() / 2;
        centerY = getMeasuredHeight() / 2;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //画圆
        canvas.drawCircle(centerX, centerY, centerX / 2, mPaintLine);
        canvas.drawCircle(centerX, centerY, centerX, mPaintLine);
        //画线
        // canvas.drawLine(0, centerY, getMeasuredWidth(), centerY, mPaintLine);
        // canvas.drawLine(centerX, 0, centerX, getMeasuredHeight(), mPaintLine);

        // 绘制渐变圆
        Shader mShader = new SweepGradient(centerX, centerY, Color.TRANSPARENT, Color.parseColor("#33FFFFFF"));
        //绘制时渐变
        mPaintCircle.setShader(mShader);
        canvas.save();
        canvas.rotate(degrees, centerX, centerY);
        canvas.drawArc(mRectF, 0, degrees, true, mPaintCircle);
        canvas.restore();
    }

    private static final int MSG_RUN = 1;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_RUN) {
                degrees++;
                postInvalidate();
                sendEmptyMessageDelayed(MSG_RUN, 30);
            }
        }
    };

    /**
     * 对外公开扫描的方法
     */
    public void startScan() {
        if (mHandler != null) {
            mHandler.obtainMessage(MSG_RUN).sendToTarget();
        }
    }
}
