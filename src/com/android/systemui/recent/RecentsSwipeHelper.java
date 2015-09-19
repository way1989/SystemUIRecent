/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.recent;

import java.util.ArrayList;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.SuppressLint;
import android.graphics.RectF;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.way.alirecent.R;

@SuppressLint("NewApi")
public class RecentsSwipeHelper {
    static final String TAG = "com.android.systemui.SwipeHelper";
    private static final boolean DEBUG = true;
    private static final boolean DEBUG_INVALIDATE = false;
    private static final boolean SLOW_ANIMATIONS = false; // DEBUG;
    private static final boolean CONSTRAIN_SWIPE = true;
    private static final boolean FADE_OUT_DURING_SWIPE = true;
    private static final boolean DISMISS_IF_SWIPED_FAR_ENOUGH = true;

    public static final int X = 0;
    public static final int Y = 1;

    private static LinearInterpolator sLinearInterpolator = new LinearInterpolator();

    private float SWIPE_ESCAPE_VELOCITY = 100f; // dp/sec
    private int DEFAULT_ESCAPE_ANIMATION_DURATION = 200; // ms
    private int MAX_ESCAPE_ANIMATION_DURATION = 400; // ms
    private int CLEAR_ESCAPE_ANIMATION_DURATION = 100;
    private int MAX_DISMISS_VELOCITY = 2000; // dp/sec
    private static final int SNAP_ANIM_LEN = SLOW_ANIMATIONS ? 1000 : 150; // ms

    public static float ALPHA_FADE_START = 0f; // fraction of thumbnail width
                                                 // where fade starts
    static final float ALPHA_FADE_END = 0.5f; // fraction of thumbnail width
                                              // beyond which alpha->0

    private float mPagingTouchSlop;
    private Callback mCallback;
    private int mSwipeDirection;
    private VelocityTracker mVelocityTracker;

    private float mInitialTouchPos;
    private boolean mDragging;
    private View mCurrView;
    private View mCurrAnimView;
    private boolean mCanCurrViewBeDimissed;
    private float mDensityScale;
    private boolean mIsLockUpdated;

    /* YUNOS BEGIN*/
    // ##module(System recent): for swipe strength effects
    // ##date: 2013-11-20 ##author: haiyan.tan@aliyun-inc.com
    private float mSwipeStrengthStartY = 0f;
    private float mSwipeStrengthFactor = 0f;
    private int mScreenHeight = 0;
    private final int mGragBuffer = 50;
    private View mAppIconView;
    private View mAppLockView;
    private float mHistoryDelta = 0;
    private boolean mActionDownOutListScope = false;
    /* YUNOS END*/

    public RecentsSwipeHelper(int swipeDirection, Callback callback, float densityScale,
            float pagingTouchSlop) {
        mCallback = callback;
        mSwipeDirection = swipeDirection;
        mVelocityTracker = VelocityTracker.obtain();
        mDensityScale = densityScale;
        mPagingTouchSlop = pagingTouchSlop;
    }

    public void setDensityScale(float densityScale) {
        mDensityScale = densityScale;
    }

    /* YUNOS BEGIN */
    // ##module(System recent)
    // ##date:2013-11-20 ##author: haiyan.tan@aliyun-inc.com
    public void setScreenHeight(int height) {
        mScreenHeight = height;
    }
    /* YUNOS END */

    public void setPagingTouchSlop(float pagingTouchSlop) {
        mPagingTouchSlop = pagingTouchSlop;
    }

    private float getPos(MotionEvent ev) {
        return mSwipeDirection == X ? ev.getX() : ev.getY();
    }

    private float getTranslation(View v) {
        return mSwipeDirection == X ? v.getTranslationX() : v.getTranslationY();
    }

    private float getVelocity(VelocityTracker vt) {
        return mSwipeDirection == X ? vt.getXVelocity() :
                vt.getYVelocity();
    }

    private ObjectAnimator createTranslationAnimation(View v, float newPos) {
        ObjectAnimator anim = ObjectAnimator.ofFloat(v,
                mSwipeDirection == X ? "translationX" : "translationY", newPos);
        return anim;
    }

    private float getPerpendicularVelocity(VelocityTracker vt) {
        return mSwipeDirection == X ? vt.getYVelocity() :
                vt.getXVelocity();
    }

    private void setTranslation(View v, float translate) {
        if (mSwipeDirection == X) {
            v.setTranslationX(translate);
        } else {
            v.setTranslationY(translate);
        }
    }

    private float getSize(View v) {
        return mSwipeDirection == X ? v.getMeasuredWidth() :
                v.getMeasuredHeight();
    }

    private float getAlphaForOffset(View view) {
        float viewSize = getSize(view);
        final float fadeSize = ALPHA_FADE_END * viewSize;
        float result = 1.0f;
        float pos = getTranslation(view);
        if (pos >= viewSize * ALPHA_FADE_START) {
            result = 1.0f - (pos - viewSize * ALPHA_FADE_START) / fadeSize;
        } else if (pos < viewSize * (1.0f - ALPHA_FADE_START)) {
            result = 1.0f + (viewSize * ALPHA_FADE_START + pos) / fadeSize;
        }
        // Make .03 alpha the minimum so you always see the item a bit-- slightly below
        // .03, the item disappears entirely (as if alpha = 0) and that discontinuity looks
        // a bit jarring
        return Math.max(0.03f, result);
    }

    // invalidate the view's own bounds all the way up the view hierarchy
    public static void invalidateGlobalRegion(View view) {
        invalidateGlobalRegion(
            view,
            new RectF(view.getLeft(), view.getTop(), view.getRight(), view.getBottom()));
    }

    // invalidate a rectangle relative to the view's coordinate system all the way up the view
    // hierarchy
    public static void invalidateGlobalRegion(View view, RectF childBounds) {
        //childBounds.offset(view.getTranslationX(), view.getTranslationY());
        if (DEBUG_INVALIDATE)
            Log.v(TAG, "-------------");
        while (view.getParent() != null && view.getParent() instanceof View) {
            view = (View) view.getParent();
            view.getMatrix().mapRect(childBounds);
            view.invalidate((int) Math.floor(childBounds.left),
                            (int) Math.floor(childBounds.top),
                            (int) Math.ceil(childBounds.right),
                            (int) Math.ceil(childBounds.bottom));
            if (DEBUG_INVALIDATE) {
                Log.v(TAG, "INVALIDATE(" + (int) Math.floor(childBounds.left)
                        + "," + (int) Math.floor(childBounds.top)
                        + "," + (int) Math.ceil(childBounds.right)
                        + "," + (int) Math.ceil(childBounds.bottom));
            }
        }
    }

    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int action = ev.getAction();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mIsLockUpdated = false;
                mDragging = false;
                mCurrView = mCallback.getChildAtPosition(ev);
                mActionDownOutListScope = mCallback.getIsOutListScope(ev);
                mVelocityTracker.clear();
                if (mCurrView != null) {
                    mCurrAnimView = mCallback.getChildContentView(mCurrView);
                    /* YUNOS BEGIN*/
                    // ##module(System recent)
                    // ##date: 2013-11-20 ##author: haiyan.tan@aliyun-inc.com
                    mAppIconView = mCurrAnimView.findViewById(R.id.app_icon);
                    mAppLockView = mCurrAnimView.findViewById(R.id.recent_item_lock);
                    /* YUNOS END */
                    mCanCurrViewBeDimissed = mCallback.canChildBeDismissed(mCurrView);
                    mVelocityTracker.addMovement(ev);
                    mInitialTouchPos = getPos(ev);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mCurrView != null) {
                    mVelocityTracker.addMovement(ev);
                    float pos = getPos(ev);
                    float delta = pos - mInitialTouchPos;
                    if (DEBUG) {
                        Log.d(TAG, "onInterceptTouchEvent pos = " + pos + " delta = " + delta);
                    }
                    if (Math.abs(delta) > mPagingTouchSlop) {
                        mCallback.onBeginDrag(mCurrView);
                        mDragging = true;
                        mInitialTouchPos = getPos(ev) - getTranslation(mCurrAnimView);
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mDragging = false;
                mCurrView = null;
                mCurrAnimView = null;
                break;
        }
        return mDragging;
    }

    /**
     * @param view The view to be dismissed
     * @param velocity The desired pixels/second speed at which the view should move
     */
    public void dismissChild(final View view, float velocity, final boolean isClearAll, final int index, final ArrayList<View> views) {
        final View animView = mCallback.getChildContentView(view);
        final boolean canAnimViewBeDismissed = mCallback.canChildBeDismissed(view);
        float newPos;

        if (velocity < 0
                || (velocity == 0 && getTranslation(animView) < 0)
                // if we use the Menu to dismiss an item in landscape, animate up
                || (velocity == 0 && getTranslation(animView) == 0 && mSwipeDirection == Y)) {
            newPos = -getSize(animView);
        } else {
            newPos = getSize(animView);
        }
        int duration = MAX_ESCAPE_ANIMATION_DURATION;
        if (velocity != 0) {
            duration = Math.min(duration,
                                (int) (Math.abs(newPos - getTranslation(animView)) * 1000f / Math
                                        .abs(velocity)));
        } else {
            duration = DEFAULT_ESCAPE_ANIMATION_DURATION;
        }
        if (isClearAll)
            duration = CLEAR_ESCAPE_ANIMATION_DURATION;
        animView.clearAnimation();
        animView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        ObjectAnimator anim = createTranslationAnimation(animView, newPos);
        anim.setInterpolator(sLinearInterpolator);
        anim.setDuration(duration);
        anim.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator animation) {
                mCallback.updateItemLabel(view, true);
                if (isClearAll) {
                    int next = index + 1;
                    if (next < views.size())
                        dismissChild(views.get(next), 0, true, next, views);
                    else
                        mCallback.removeAllViewsOnAnimationEnd();
                } else {
                    mCallback.onChildDismissed(view);
                }
                animView.setLayerType(View.LAYER_TYPE_NONE, null);
            }
        });
        anim.addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                if (FADE_OUT_DURING_SWIPE && canAnimViewBeDismissed) {
                    animView.setAlpha(getAlphaForOffset(animView));
                }
                invalidateGlobalRegion(animView);
            }
        });
        anim.start();
    }

    public void snapChild(final View view, float velocity, final float delta) {
        final View animView = mCallback.getChildContentView(view);
        final float size = getSize(animView.findViewById(R.id.app_icon));
        final boolean canAnimViewBeDismissed = mCallback.canChildBeDismissed(animView);
        ObjectAnimator anim = createTranslationAnimation(animView, 0);
        int duration = SNAP_ANIM_LEN;
        anim.setDuration(duration);
        anim.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator animation){
                mCallback.updateItemLabel(view, true);
                if (delta > (0.5 * size))
                    mCallback.updateLockState(view, true);
            }
        });
        anim.start();
    }

    public void startDeleteAnimation(ArrayList<View> views) {
        if(views.size() < 1)
            return;

        dismissChild(views.get(0), 0, true, 0, views);
    }

    public boolean getActionDownOutListScope() {
        return mActionDownOutListScope;
    }

    public View getCurrentTouchRecentItem() {
        return mCurrView;
    }

    public boolean onTouchEvent(MotionEvent ev) {
        if (!mDragging) {
            return false;
        }

        mVelocityTracker.addMovement(ev);
        final int action = ev.getAction();
        switch (action) {
            case MotionEvent.ACTION_OUTSIDE:
            case MotionEvent.ACTION_MOVE:
                if (mCurrView != null) {
                    float delta = getPos(ev) - mInitialTouchPos;
                    if (DEBUG) {
                        Log.d(TAG, "onTouchEvent pos = " + getPos(ev) + " delta = " + delta);
                    }
                    // don't let items that can't be dismissed be dragged more than
                    // maxScrollDistance
                    if (CONSTRAIN_SWIPE && !mCallback.canChildBeDismissed(mCurrView)) {
                        float size = getSize(mCurrAnimView);
                        float maxScrollDistance = 0.15f * size;
                        if (Math.abs(delta) >= size) {
                            delta = delta > 0 ? maxScrollDistance : -maxScrollDistance;
                        } else {
                            delta = maxScrollDistance * (float) Math.sin((delta/size)*(Math.PI/2));
                        }
                    }

                    /* YUNOS BEGIN*/
                    // ##module(System recent): add swipe strength feature.
                    // ##date:2013-11-20 ##author:haiyan.tan@aliyun-inc.om
                    //
                    // The height of NavigationBar is not included in the mScreenHeight.
                    // So just make sure the touch points in valid area.
                    if (ev.getRawY() <= mScreenHeight) {
                        if (Math.abs(delta) >= (mAppIconView.getHeight() + mGragBuffer)) {
                            // If the mHistoryDelta * delta less than 0 if means the direction has changed.
                            // Just reset mSwipeStrengthFactor.
                            if (mHistoryDelta * delta <= 0) {
                                mSwipeStrengthFactor = 0;
                            }
                            mHistoryDelta = delta;
                            if (mSwipeStrengthFactor == 0) {
                                mSwipeStrengthStartY = delta;
                                if (delta <= 0) {
                                    mSwipeStrengthFactor = (mAppIconView.getTop() - (mAppLockView.getHeight() / 2) - Math.abs(delta)) / ev.getRawY();
                                } else {
                                    mSwipeStrengthFactor = (((View)mAppLockView.getParent()).getHeight() - mAppLockView.getBottom() - delta) / (mScreenHeight -  ev.getRawY());
                                }

                            }
                            delta = mSwipeStrengthStartY + (delta - mSwipeStrengthStartY) * mSwipeStrengthFactor;
                        }
                        setTranslation(mCurrAnimView, delta);
                        mCallback.updateItemLabel(mCurrView, false);
                        if (delta < 0)
                            mCallback.updateDeleteAppLable(true);
                        float sizeIcon = getSize(mCurrAnimView.findViewById(R.id.app_icon));
                        if ((delta > (0.5 * sizeIcon)) && !mIsLockUpdated){
                            mCallback.updateLockState(mCurrView, false);
                            mIsLockUpdated = true;
                        }
                        invalidateGlobalRegion(mCurrView);
                    }
                    /* YUNOS END*/
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mSwipeStrengthFactor = 0;
                if (mCurrView != null) {
                    float maxVelocity = MAX_DISMISS_VELOCITY * mDensityScale;
                    mVelocityTracker.computeCurrentVelocity(1000 /* px/sec */, maxVelocity);
                    float escapeVelocity = SWIPE_ESCAPE_VELOCITY * mDensityScale;
                    float velocity = getVelocity(mVelocityTracker);
                    float perpendicularVelocity = getPerpendicularVelocity(mVelocityTracker);

                    // Decide whether to dismiss the current view
                    boolean childSwipedFarEnough = DISMISS_IF_SWIPED_FAR_ENOUGH &&
                            Math.abs(getTranslation(mCurrAnimView)) > 0.26 * getSize(mCurrAnimView);
                    boolean childSwipedFastEnough = (Math.abs(velocity) > escapeVelocity) &&
                            (Math.abs(velocity) > Math.abs(perpendicularVelocity)) &&
                            (velocity > 0) == (getTranslation(mCurrAnimView) > 0);

                    float delta = getPos(ev) - mInitialTouchPos;
                    boolean dismissChild = mCallback.canChildBeDismissed(mCurrView) &&
                            (childSwipedFastEnough || childSwipedFarEnough) && (delta < 0);
                    mCallback.updateDeleteAppLable(false);
                    if (dismissChild) {
                        // flingadingy
                        dismissChild(mCurrView, childSwipedFastEnough ? velocity : 0f, false, 0, null);
                    } else {
                        // snappity
                        float sizeIcon = getSize(mCurrAnimView.findViewById(R.id.app_icon));
                        if (delta > 0.5 * sizeIcon)
                            mCallback.onDragCancelled(mCurrView);
                        snapChild(mCurrView, velocity, delta);
                    }
                }
                break;
        }
        return true;
    }

    public interface Callback {
        View getChildAtPosition(MotionEvent ev);

        View getChildContentView(View v);

        boolean canChildBeDismissed(View v);

        void onBeginDrag(View v);

        void onChildDismissed(View v);

        void onDragCancelled(View v);

        void updateLockState(View v, boolean isAnimatinEnd);

        void updateItemLabel(View v, boolean visible);

        void updateDeleteAppLable(boolean opacity);

        void removeAllViewsOnAnimationEnd();

        boolean getIsOutListScope(MotionEvent ev);
    }
}
