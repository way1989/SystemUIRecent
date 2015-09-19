/*YUNOS_PROJECT added by modules(systemui recent)*/
/**
 * Copyright (C) 2013 The YunOS Project
 *
 * AliProgress.java
 *
 *  Created on: 2013-11-17
 *      Author: yun.yangyun@alibaba-inc.com
 */
package com.android.systemui.recent;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.Toast;

import com.android.systemui.utils.MemoryUtil;
import com.way.alirecent.R;
import com.way.alirecent.RecentsActivity;

public class AliProgress extends View implements Animation.AnimationListener {
    //sizes
    private int mRectWidth;
    private int mArcWidth;

    //colors
    private int mRimColor;

    //paints
    private Paint mArcPaint = new Paint();
    private Paint mRimPaint = new Paint();

    //rects
    private RectF mRectBounds = new RectF();
    private RectF mArcBounds = new RectF();

    private int mDegree = 0;
    private int mProgress = 0;
    private int mEndProgress = 0;
    private int mBeginProgress = 0;

    // animation
    private AliAnimation mProgressAnimation;

    //private Bitmap mLightImage;
    private double mLightRadius;
    //private double mLightImageWidthHalf;
    private int mLightBounds;
    private boolean mIsClearing = false;

    // animation Type
    private static final int CLEARANIMATIONTYPE = 1;
    private static final int UPDATEANIMATIONTYPE = 2;

    // animation duration
    private static final int CLEARDURATION = 1400; // 1400ms
    private static final int UPDATEDURATION = 250; // 250ms

    // is doing animation?
    private boolean mAnimating = false;

    private boolean isClickAccerlateButton;
    private Context context;
    private long memorySize;

    public void setIsClickAccerlateButton(boolean isClick) {
        isClickAccerlateButton = isClick;
    }

    public AliProgress(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        setDefaultAttr(context);
        parseAttributes(context.obtainStyledAttributes(attrs, R.styleable.AliArc));
        mProgressAnimation = new AliAnimation();
        mProgressAnimation.setAnimationListener(this);
        //mLightImage = BitmapFactory.decodeResource(context.getResources(), R.drawable.bashboard_white_light_normal);
        //mLightImageWidthHalf = mLightImage.getWidth() / 2.0;
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setupBounds();
        mLightRadius = (getLayoutParams().width - mArcWidth - mLightBounds * 2) / 2.0;
        setupPaints();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawArc(mArcBounds, 360, 360, false, mRimPaint);
        canvas.drawArc(mArcBounds, -90, mDegree, false, mArcPaint);
       /* if (!mIsClearing) {
            double angle = (mDegree - 90) * Math.PI / 180.0;
            float left = (float)(mLightRadius + mLightRadius * Math.cos(angle) - mLightImageWidthHalf + mLightBounds);
            float top = (float)(mLightRadius + mLightRadius * Math.sin(angle) - mLightImageWidthHalf + mLightBounds);
            canvas.drawBitmap(mLightImage, left + 2, top + 3, null);
        }*/
    }

    private void setDefaultAttr(Context context) {
        mArcWidth = context.getResources().getDimensionPixelSize(R.dimen.default_arc_width);
        mRimColor = context.getResources().getColor(R.color.default_rim_color);
    }

    private void parseAttributes(TypedArray a) {
        mArcWidth = (int)a.getDimension(R.styleable.AliArc_arcWidth, mArcWidth);
        mLightBounds = mArcWidth * 3;
        mRimColor = a.getColor(R.styleable.AliArc_rimColor, mRimColor);
    }

    private void setupBounds() {
        int boundsWidth = (mArcWidth + 1) / 2;
        mArcBounds.set(boundsWidth + mLightBounds, boundsWidth + mLightBounds, getLayoutParams().width - boundsWidth - mLightBounds,
            getLayoutParams().height - boundsWidth - mLightBounds);
    }

    private void setupPaints() {
        mArcPaint.setAntiAlias(true);
        mArcPaint.setStyle(Style.STROKE);
        mArcPaint.setStrokeWidth(mArcWidth);

        mRimPaint.setColor(mRimColor);
        mRimPaint.setAntiAlias(true);
        mRimPaint.setStyle(Style.STROKE);
        mRimPaint.setStrokeWidth(mArcWidth);
    }

    public void setProgress(int progress) {
        if (progress < 0)
            return;
        mProgress = progress;
        mDegree = Math.round((mProgress / 100.0f) * 360.0f);
        this.post(new Runnable() {
            @Override
            public void run() {
                invalidate();
            }
        });
    }

    public int getProgress() {
        return mProgress;
    }

    public void setPaintColor(int color) {
        mArcPaint.setColor(color);
    }

    public void startClearAnimation(int endProgress) {
        if ( mAnimating == false || mProgressAnimation.getType() != CLEARANIMATIONTYPE) {
            mEndProgress = endProgress;
            mBeginProgress = mProgress;
            mProgressAnimation.setType(CLEARANIMATIONTYPE);
            mProgressAnimation.setDuration(CLEARDURATION);
            this.startAnimation(mProgressAnimation);
        }
    }

    public void startUpdateAnimation(int progress) {
        if (!mAnimating) {
            mBeginProgress = mProgress;
            mProgressAnimation.setProgressChange(mProgress - progress);
            mProgressAnimation.setType(UPDATEANIMATIONTYPE);
            mProgressAnimation.setDuration(UPDATEDURATION);
            this.startAnimation(mProgressAnimation);
        } else {
            // FIXME: it may be cause a joggle;
            mEndProgress = progress;
        }
    }

    @Override
    public void onAnimationEnd(Animation animation) {
        mAnimating = false;
        if (context != null && context instanceof RecentsActivity) {
            if (isClickAccerlateButton) {
                long currentMemorySize = MemoryUtil.getSystemAvaialbeMemorySize(context);
                if(currentMemorySize > memorySize){
                    int releaseMemorySize = (int)((currentMemorySize - memorySize)/1024/1024);
                    if(releaseMemorySize > 0){
                        String textFormat = context.getResources().getString(R.string.recent_task_release_memory);
                        String finalText = String.format(textFormat, releaseMemorySize);
                        Toast.makeText(context, finalText, Toast.LENGTH_SHORT).show();
                    }else{
                        Toast.makeText(context, R.string.recent_task_no_more_released, Toast.LENGTH_SHORT).show();
                    }
                }else{
                    Toast.makeText(context, R.string.recent_task_no_more_released, Toast.LENGTH_SHORT).show();
                }
                ((RecentsActivity) context).dismissAndGoBack();
                isClickAccerlateButton = false;
            }
        }
    }

    public void setMemorySizeBeforeClear(long memorySize) {
        this.memorySize = memorySize;
    }

    @Override
    public void onAnimationRepeat(Animation animation) {
        mAnimating = true;
    }

    @Override
    public void onAnimationStart(Animation animation) {
        mAnimating = true;
    }

    private class AliAnimation extends Animation {
        private int mType;
        private int mProgressChange;

        public AliAnimation() {
        }

        public void setType(int type) {
             mType = type;
        }

        public int getType() {
            return mType;
        }

        public void setProgressChange(int change) {
            mProgressChange = change;
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            if (mType == CLEARANIMATIONTYPE) {
                // half animation to change progress to zero, another half change from zero to mEndProgress
                if (3.0 * interpolatedTime <= 1.0) {
                    mIsClearing = true;
                    setProgress((int)(mBeginProgress - 3 * mBeginProgress * interpolatedTime));
                } else {
                    mIsClearing = false;
                    setProgress((int)((3.0 * interpolatedTime - 1.0) * mEndProgress / 2.0));
                }
            } else if (mType == UPDATEANIMATIONTYPE) {
                mIsClearing = false;
                setProgress(mBeginProgress - (mProgressChange * (int)(interpolatedTime * 100)) / 100);
            }
        }
    }
}
