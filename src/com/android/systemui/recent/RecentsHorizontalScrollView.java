/*YUNOS_PROJECT modified by modules(systemui recent)*/
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
 *
 * Updated on: 2013-11-16
 * Author: haiyan.tan@aliyun-inc.com
 * Updated on: 2013-11-17
 * Author: wenjun.yuanwj
 */

package com.android.systemui.recent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorSet;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.database.DataSetObserver;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.FloatMath;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;

import com.android.systemui.recent.RecentsPanelView.TaskDescriptionAdapter;
import com.android.systemui.recent.RecentsPanelView.ViewHolder;
import com.android.systemui.utils.MemoryUtil;
import com.way.alirecent.R;
import com.way.alirecent.RecentsActivity;
//import android.widget.HorizontalScrollView;
/*YUNOS BEGIN*/
// ##modules(Systemui recent):
// ##date: 2013-11.20 ##author: yun.yangyun@aliyun-inc.com
/*YUNOS END*/
/*YUNOS_BEGIN*/
//##modules(Systemui recent): usertrack
//##date: 2013-12.26 author: yulong.hyl@alibaba-inc.com
/*YUNOS_END*/
@SuppressLint("NewApi")
public class RecentsHorizontalScrollView extends HorizontalSwipeView
        implements RecentsSwipeHelper.Callback, RecentsPanelView.RecentsScrollView {
    private static final String TAG = RecentsPanelView.TAG;
    private static final boolean DEBUG = RecentsPanelView.DEBUG;
    private LinearLayout mLinearLayout;
    private TaskDescriptionAdapter mAdapter;
    private RecentsCallback mCallback;
    protected int mLastScrollPosition;
    private RecentsSwipeHelper mSwipeHelper;
    private RecentsScrollViewPerformanceHelper mPerformanceHelper;
    private HashSet<View> mRecycledViews;
    private int mNumItemsInOneScreenful;
    private Context mContext;

    public RecentsHorizontalScrollView(Context context, AttributeSet attrs) {
        super(context, attrs, 0);
        mContext = context;
        float densityScale = getResources().getDisplayMetrics().density;
        float pagingTouchSlop = ViewConfiguration.get(mContext).getScaledPagingTouchSlop();
        mSwipeHelper = new RecentsSwipeHelper(RecentsSwipeHelper.Y, this, densityScale, pagingTouchSlop);
        mPerformanceHelper = RecentsScrollViewPerformanceHelper.create(context, attrs, this, false);
        mRecycledViews = new HashSet<View>();
        mDisplay = ((WindowManager)context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        mDisplay.getMetrics(mDisplayMetrics);
        /* YUNOS BEGIN */
        // ##module(System recent)
        // ##date:2013-11-20 ##author: haiyan.tan@aliyun-inc.com
        mSwipeHelper.setScreenHeight(mDisplayMetrics.heightPixels);
        /* YUNOS END */
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    public void setMinSwipeAlpha(float minAlpha) {
//        mSwipeHelper.setMinAlpha(minAlpha);
    }

    private int scrollPositionOfMostRecent() {
        return 0;
    }

    private void addToRecycledViews(View v) {
        if (mRecycledViews.size() < mNumItemsInOneScreenful) {
            mRecycledViews.add(v);
        }
    }

    public View findViewForTask(int persistentTaskId) {
        for (int i = 0; i < mLinearLayout.getChildCount(); i++) {
            View v = mLinearLayout.getChildAt(i);
            RecentsPanelView.ViewHolder holder = (RecentsPanelView.ViewHolder) v.getTag();
            if (holder != null && holder.taskDescription != null
                    && holder.taskDescription.persistentTaskId == persistentTaskId) {
                return v;
            }
        }
        return null;
    }
    /*YUNOS BEGIN*/
    // ##modules(Systemui recent):
    // ##date: 2013-11.16 ##author: yun.yangyun@aliyun-inc.com
    public void removeAllViewsInLayout() {
        int count = mLinearLayout.getChildCount();
        View[] refView = new View[count];
        RecentsPanelView.ViewHolder holder;
        // child in mLinearLayout may be dismissed when we use, so we store it before wo clear all
        for (int i = 0; i < count; i++)
            refView[i] = mLinearLayout.getChildAt(i);
        ArrayList<View> animationViews = new ArrayList<View>();
        /* YUNOS_BEGIN */
        // ##module(RecentTasks)
        // ##date: 2014/3/18
        // ##author: yulong.hyl@alibaba-inc.com
        // ##BugID:101792:add null pointer judgement condition
        if (refView.length <= 0) {
            return;
        }
        /* YUNOS_END */
        for (int i = 0; i < count; i++) {
            holder = (RecentsPanelView.ViewHolder) refView[i].getTag();
            /* YUNOS_BEGIN */
            // ##module(RecentTasks)
            // ##date: 2014/3/18
            // ##author: yulong.hyl@alibaba-inc.com
            // ##BugID:101792:add null pointer judgement condition
            if (holder == null || holder.taskDescription == null) {
                return;
            }
            /* YUNOS_END */
            String packageName = holder.taskDescription.packageName;
            if (!MemoryUtil.isTaskLocked(mContext, packageName)) {
                int startIndex = mCurPage * CHILD_COUNT_PER_SCREEN_IN_PORT;
                if (i >= startIndex && (i < startIndex + CHILD_COUNT_PER_SCREEN_IN_PORT)) {
                    animationViews.add(refView[i]);
                }
            }
        }
        if (animationViews.size() < 1) {
            removeAllViewsOnAnimationEnd();
        }

        mSwipeHelper.startDeleteAnimation(animationViews);
    }
    /*YUNOS END*/

    public void removeAllViewsOnAnimationEnd() {
        int count = mLinearLayout.getChildCount();
        View[] refView = new View[count];
        RecentsPanelView.ViewHolder holder;
        // child in mLinearLayout may be dismissed when we use, so we store it before wo clear all
        for (int i = 0; i < count; i++)
            refView[i] = mLinearLayout.getChildAt(i);
        /* YUNOS_BEGIN */
        // ##module(RecentTasks)
        // ##date: 2014/3/18
        // ##author: yulong.hyl@alibaba-inc.com
        // ##BugID:101792:add null pointer judgement condition
        if (refView.length <= 0) {
            return;
        }
        /* YUNOS_END */
        for (int i = 0; i < count; i++) {
            holder = (RecentsPanelView.ViewHolder) refView[i].getTag();
            /* YUNOS_BEGIN */
            // ##module(RecentTasks)
            // ##date: 2014/3/18
            // ##author: yulong.hyl@alibaba-inc.com
            // ##BugID:101792:add null pointer judgement condition
            if (holder == null || holder.taskDescription == null) {
                return;
            }
            /* YUNOS_END */
            String packageName = holder.taskDescription.packageName;
            if (!MemoryUtil.isTaskLocked(mContext, packageName)) {
                final View animView = getChildContentView(refView[i]);
                animView.setLayerType(View.LAYER_TYPE_NONE, null);
                onChildDismissed(refView[i]);
            }
        }
        mCallback.setClearAppType(RecentsPanelView.CLEARONEAPP);
        if(DEBUG){
            Log.d("onAnimationEnd", "=======================removeAllViewsOnAnimationEnd");
        }
        update(true);
    }

    public int getItemWidth(){
        return mDisplayMetrics.widthPixels/CHILD_COUNT_PER_SCREEN_IN_PORT;
    }
    List<Animator> anims = new ArrayList<Animator>();
    AnimatorSet set = new AnimatorSet();
    private void update(boolean isClearAll) {
        for (int i = 0; i < mLinearLayout.getChildCount(); i++) {
            View v = mLinearLayout.getChildAt(i);
            addToRecycledViews(v);
            mAdapter.recycleView(v);
        }
        LayoutTransition transitioner = getLayoutTransition();
        setLayoutTransition(null);
        mLinearLayout.removeAllViews();
        releaseAnimator();
        Iterator<View> recycledViews = mRecycledViews.iterator();
        for (int i = 0; i < mAdapter.getCount(); i++) {
            View old = null;
            if (recycledViews.hasNext()) {
                old = recycledViews.next();
                recycledViews.remove();
                old.setVisibility(VISIBLE);
            }

            final View view = mAdapter.getView(i, old, mLinearLayout);

            if (mPerformanceHelper != null) {
                mPerformanceHelper.addViewCallback(view);
            }

            OnTouchListener noOpListener = new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            };

            // We don't want a click sound when we dimiss recents
            view.setSoundEffectsEnabled(false);

            OnClickListener launchAppListener = new OnClickListener() {
                public void onClick(View v) {
                    mCallback.handleOnClick(view);
                }
            };

            RecentsPanelView.ViewHolder holder = (RecentsPanelView.ViewHolder) view.getTag();
            View lockView = holder.lockView;
            if (holder != null && holder.taskDescription != null) {
                String packageName = holder.taskDescription.packageName;
                boolean isLocked = MemoryUtil.isTaskLocked(mContext, packageName);
                if (isLocked)
                    lockView.setVisibility(View.VISIBLE);
                else
                    lockView.setVisibility(View.INVISIBLE);
            }
            final View iconView = holder.iconView;
            iconView.setClickable(true);
            iconView.setOnClickListener(launchAppListener);

            // We don't want to dismiss recents if a user clicks on the app
            // title
            // (we also don't want to launch the app either, though, because the
            // app title is a small target and doesn't have great click
            // feedback)
            View animView = getChildContentView(view);
            int finalElementIndex = mAdapter.getCount() <= CHILD_COUNT_PER_SCREEN_IN_PORT ? (mAdapter
                    .getCount() - 1) : (CHILD_COUNT_PER_SCREEN_IN_PORT - 1);
            initAndStartAnimatorSet(isClearAll, transitioner, i, animView,finalElementIndex);
            mLinearLayout.addView(view);
        }

        // Scroll to begin item after initial layout.
        final OnGlobalLayoutListener updateScroll = new OnGlobalLayoutListener() {
            public void onGlobalLayout() {
                // mLastScrollPosition = scrollPositionOfMostRecent();
                scrollTo(0, 0);
                mCurPage = 0;
                final ViewTreeObserver observer = getViewTreeObserver();
                if (observer.isAlive()) {
                    observer.removeOnGlobalLayoutListener(this);
                }
            }
        };
        getViewTreeObserver().addOnGlobalLayoutListener(updateScroll);

    }

    /* YUNOS_BEGIN */
    // ##module(RecentTasks)
    // ##date: 2014/3/30
    // ##author: yulong.hyl@alibaba-inc.com
    // ##BugID:106064:use animatorset to start animation
    //realse animator resource
    private void releaseAnimator() {
        if (set != null && (set.isStarted() || set.isRunning())) {
            if (DEBUG)
                Log.d(RecentsActivity.TAG, "cancelAnimation");
            set.cancel();
        }
        set = null;
        if (anims != null) {
            anims.clear();
        }
        anims = null;
    }
    /* YUNOS_END */

    /* YUNOS_BEGIN */
    // ##module(RecentTasks)
    // ##date: 2014/3/30
    // ##author: yulong.hyl@alibaba-inc.com
    // ##BugID:106064:use animatorset to start animation
    private void initAndStartAnimatorSet(boolean isClearAll, final LayoutTransition transitioner,
            int i, final View animView, final int finalElementIndex) {
        if (i < CHILD_COUNT_PER_SCREEN_IN_PORT && !isClearAll) {
            animView.setTranslationY(getResources().getDimensionPixelSize(R.dimen.ICON_TRASLATE_HEIGHT));
            animView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            ObjectAnimator animator = ObjectAnimator.ofFloat(animView, "translationY", 0);
            animator.setDuration(Constants.ANIMATOR_SET_TIME);
            animator.setStartDelay(i * Constants.DELAYTIME);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addListener(new AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animView != null) {
                        animView.setLayerType(View.LAYER_TYPE_NONE, null);
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    // TODO Auto-generated method stub
                }
            });
            if(anims  ==  null){
                anims = new ArrayList<Animator>();
            }
            anims.add(animator);
            if (i == finalElementIndex) {
                animView.getViewTreeObserver().addOnPreDrawListener(new OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        animView.getViewTreeObserver().removeOnPreDrawListener(this);
                        if(set == null){
                            set = new AnimatorSet();
                        }
                        set.addListener(new AnimatorListener() {
                            public void onAnimationStart(Animator animation) {
                            }

                            public void onAnimationRepeat(Animator animation) {
                            }

                            public void onAnimationEnd(Animator animation) {
                                ((RecentsActivity) mContext).getRecentsPanel().updateMemInfo();
                                setLayoutTransition(transitioner);
                            }

                            public void onAnimationCancel(Animator animation) {
                            }
                        });
                        set.playTogether(anims);
                        if(!set.isStarted()){
                            if (DEBUG)
                                Log.d(RecentsActivity.TAG, "startAnimation");
                            set.start();
                        }
                        return true;
                    }
                });
            }
        }
    }
    /* YUNOS_END */
    @Override
    public void removeViewInLayout(final View view) {
        dismissChild(view);
    }

    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (DEBUG) Log.v(TAG, "onInterceptTouchEvent()");
        return mSwipeHelper.onInterceptTouchEvent(ev) ||
            super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        /* YUNOS BEGIN */
        // ##module(Systemui recent):If there is no recent item hit, just
        // return false and transfer the touch event to others.
        // ##data:2013-11-27 ##author:haiyan.tan@aliyun-inc.com
        if (mSwipeHelper.getCurrentTouchRecentItem() == null
            && mSwipeHelper.getActionDownOutListScope()) {
            return false;
        } else {
            return mSwipeHelper.onTouchEvent(ev) ||
                    super.onTouchEvent(ev);
        }
        /* YUNOS END */
    }

    public boolean canChildBeDismissed(View v) {
        return true;
    }

    public void dismissChild(View v) {
        mSwipeHelper.dismissChild(v, 0, false, 0, null);
    }

    public void onChildDismissed(View v) {
        addToRecycledViews(v);
        if (mCallback.getClearAppType() == RecentsPanelView.CLEARONEAPP) {
            /* YUNOS_BEGIN */
            // ##modules(Systemui recent): usertrack
            // ##date: 2013-12.26 author: yulong.hyl@alibaba-inc.com
            ViewHolder viewHolder = (ViewHolder) v.getTag();
            TaskDescription ad = viewHolder.taskDescription;
            if (ad != null) {
                int position = viewHolder.position;
                String packageName = ad.getPackageName();
                String kvs = "packageName= " + packageName + " position= " + position + " action= "
                        + "slideUp";

            }
            /* YUNOS_END */
            mLinearLayout.removeView(v);
        }
        mCallback.handleSwipe(v);
        // Restore the alpha/translation parameters to what they were before
        // swiping
        // (for when these items are recycled)
        View contentView = getChildContentView(v);
        contentView.setAlpha(1f);
        contentView.setTranslationY(0);
        if (mCallback.getClearAppType() == RecentsPanelView.CLEARONEAPP)
            updatePageIfNecessary(v);
    }

    private void updatePageIfNecessary(View v) {
        if (mCurPage > getPageCount() - 1){
            //if last screen is empty, update page
            snapToScreen(getPageCount() - 1);
        }
    }
    public void onBeginDrag(View v) {
        // We do this so the underlying ScrollView knows that it won't get
        // the chance to intercept events anymore
        requestDisallowInterceptTouchEvent(true);
    }

    private int reverseViewVisibility(View view, RecentsPanelView.ViewHolder viewHolder) {
        /* YUNOS_BEGIN */
        // ##modules(Systemui recent): usertrack
        // ##date: 2013-12.26 author: yulong.hyl@alibaba-inc.com
        int position = viewHolder.position;
        if (viewHolder == null || viewHolder.taskDescription == null) {
            return view.getVisibility();
        }
        String packageName = viewHolder.taskDescription.packageName;
        String kvs = "packageName= " + packageName + " position= " + position + " action= "
                + "slideDown";
        /* YUNOS_END */

        int viewVisibility = view.getVisibility();
        if (viewVisibility == View.VISIBLE) {
            viewVisibility = View.INVISIBLE;
            MemoryUtil.unlockTask(mContext, packageName);
        } else if (viewVisibility == View.INVISIBLE) {
            viewVisibility = View.VISIBLE;
            MemoryUtil.lockTask(mContext, packageName);
        }
        return viewVisibility;
    }

    public void onDragCancelled(View view) {
        RecentsPanelView.ViewHolder viewHolder = (RecentsPanelView.ViewHolder) view.getTag();
        View lockView = viewHolder.lockView;
        lockView.setVisibility(reverseViewVisibility(lockView, viewHolder));
    }

    public void updateLockState(View view, boolean isAnimatinEnd) {
        RecentsPanelView.ViewHolder viewHolder = (RecentsPanelView.ViewHolder) view.getTag();

        if (isAnimatinEnd) {
            return;
        }

        View lockView = viewHolder.lockView;
        int lockViewVisibility = lockView.getVisibility();
    }

    public void updateItemLabel(View view, boolean visible) {
       /* RecentsPanelView.ViewHolder viewHolder = (RecentsPanelView.ViewHolder) view.getTag();
        View labelView = viewHolder.labelView;
        if (visible)
            labelView.setVisibility(View.VISIBLE);
        else
            labelView.setVisibility(View.INVISIBLE);*/
    }

    public void updateDeleteAppLable(boolean opacity) {
        mCallback.updateDeleteAppLable(opacity);
    }

    public boolean getIsOutListScope(MotionEvent ev) {
        int count = mLinearLayout.getChildCount();
        if (count == 0)
            return true;
        final float y = ev.getY() + getScrollY();
        View item = mLinearLayout.getChildAt(0);
        RecentsPanelView.ViewHolder viewHolder = (RecentsPanelView.ViewHolder) item.getTag();
        View iconView = viewHolder.iconView;
        View lockView = viewHolder.lockView;
        if (y < iconView.getTop() || y > lockView.getBottom())
            return true;
        return false;
    }

    public View getChildAtPosition(MotionEvent ev) {
        final float x = ev.getX() + getScrollX();
        final float y = ev.getY() + getScrollY();
        for (int i = 0; i < mLinearLayout.getChildCount(); i++) {
            View item = mLinearLayout.getChildAt(i);
            RecentsPanelView.ViewHolder viewHolder = (RecentsPanelView.ViewHolder) item.getTag();
            View iconView = viewHolder.iconView;
            if (x >= item.getLeft() && x < item.getRight()
                && y >= iconView.getTop() && y < iconView.getBottom()) {
                return item;
            }
        }
        return null;
    }

    public View getChildContentView(View v) {
        return v.findViewById(R.id.recent_item);
    }

    @Override
    public int getVerticalFadingEdgeLength() {
        if (mPerformanceHelper != null) {
            return mPerformanceHelper.getVerticalFadingEdgeLengthCallback();
        } else {
            return super.getVerticalFadingEdgeLength();
        }
    }

    @Override
    public int getHorizontalFadingEdgeLength() {
        if (mPerformanceHelper != null) {
            return mPerformanceHelper.getHorizontalFadingEdgeLengthCallback();
        } else {
            return super.getHorizontalFadingEdgeLength();
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setScrollbarFadingEnabled(true);
        mLinearLayout = (LinearLayout) findViewById(R.id.recents_linear_layout);
        final int leftPadding = mContext.getResources()
            .getDimensionPixelOffset(R.dimen.status_bar_recents_thumbnail_left_margin);
        setOverScrollEffectPadding(leftPadding, 0);
    }

    @Override
    public void onAttachedToWindow() {
        if (mPerformanceHelper != null) {
            mPerformanceHelper.onAttachedToWindowCallback(
                    mCallback, mLinearLayout, isHardwareAccelerated());
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        float densityScale = getResources().getDisplayMetrics().density;
        mSwipeHelper.setDensityScale(densityScale);
        float pagingTouchSlop = ViewConfiguration.get(mContext).getScaledPagingTouchSlop();
        mSwipeHelper.setPagingTouchSlop(pagingTouchSlop);
    }

    private void setOverScrollEffectPadding(int leftPadding, int i) {
        // TODO Add to (Vertical)ScrollView
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // Skip this work if a transition is running; it sets the scroll values independently
        // and should not have those animated values clobbered by this logic
        LayoutTransition transition = mLinearLayout.getLayoutTransition();
        if (transition != null && transition.isRunning()) {
            return;
        }
        // Keep track of the last visible item in the list so we can restore it
        // to the bottom when the orientation changes.
        mLastScrollPosition = scrollPositionOfMostRecent();

        // This has to happen post-layout, so run it "in the future"
        post(new Runnable() {
            public void run() {
                // Make sure we're still not clobbering the transition-set values, since this
                // runnable launches asynchronously
                LayoutTransition transition = mLinearLayout.getLayoutTransition();
                if (transition == null || !transition.isRunning()) {
                    scrollTo(mLastScrollPosition, 0);
                }
            }
        });
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        // scroll to bottom after reloading
        if (visibility == View.VISIBLE && changedView == this) {
            post(new Runnable() {
                public void run() {
                    if(DEBUG){
                        Log.d("onAnimationEnd", "=======================onVisibilityChanged");
                    }
                    //update(false);
                }
            });
        }
    }

    public void setAdapter(TaskDescriptionAdapter adapter) {
        mAdapter = adapter;
        mAdapter.registerDataSetObserver(new DataSetObserver() {
            public void onChanged() {
                if(DEBUG){
                    Log.d("onAnimationEnd", "=======================onChanged");
                }
                //update(false);
            }

            public void onInvalidated() {
                if(DEBUG){
                    Log.d("onAnimationEnd", "=======================onInvalidated");
                }
                update(false);
            }
        });
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int childWidthMeasureSpec =
                MeasureSpec.makeMeasureSpec(dm.widthPixels, MeasureSpec.AT_MOST);
        int childheightMeasureSpec =
                MeasureSpec.makeMeasureSpec(dm.heightPixels, MeasureSpec.AT_MOST);
        View child = mAdapter.createView(mLinearLayout);
        child.measure(childWidthMeasureSpec, childheightMeasureSpec);
        mNumItemsInOneScreenful =
                (int) FloatMath.ceil(dm.widthPixels / (float) child.getMeasuredWidth());
        addToRecycledViews(child);

        for (int i = 0; i < mNumItemsInOneScreenful - 1; i++) {
            addToRecycledViews(mAdapter.createView(mLinearLayout));
        }
    }

    public int numItemsInOneScreenful() {
        return mNumItemsInOneScreenful;
    }

    @Override
    public void setLayoutTransition(LayoutTransition transition) {
        // The layout transition applies to our embedded LinearLayout
        mLinearLayout.setLayoutTransition(transition);
    }

    public void setCallback(RecentsCallback callback) {
        mCallback = callback;
    }

    static final int CHILD_COUNT_PER_SCREEN_IN_PORT = 4;
    private int mChildCountPerScreen = CHILD_COUNT_PER_SCREEN_IN_PORT;
    private Display mDisplay;
    private DisplayMetrics mDisplayMetrics = new DisplayMetrics();

    int getPerScreenWidth(){
        return mDisplayMetrics.widthPixels;
    }

    int getPageCount() {
        int count = mLinearLayout.getChildCount();
        if(count == 0) {
            return 0;
        }
        return (count - 1) / mChildCountPerScreen + 1;
    }
}
