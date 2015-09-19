package com.android.systemui.recent;

import java.util.ArrayList;

import android.animation.Animator;
import android.animation.LayoutTransition;
import android.animation.TimeInterpolator;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.TaskStackBuilder;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.android.systemui.utils.MemoryUtil;
import com.way.alirecent.R;
import com.way.alirecent.RecentsActivity;

@SuppressLint("NewApi")
public class RecentsPanelView extends FrameLayout implements
		OnItemClickListener, RecentsCallback, Animator.AnimatorListener {
	static final String TAG = "RecentsPanelView";
	static final boolean DEBUG = true;
	private PopupMenu mPopup;
	private View mRecentsScrim;

	private View mRecentsNoApps;
	private ViewGroup mRecentsContainer;
	// private StatusBarTouchProxy mStatusBarTouchProxy;

	private boolean mShowing = false;
	private boolean mWaitingToShow;
	private ViewHolder mItemToAnimateInWhenWindowAnimationIsFinished;
	private boolean mAnimateIconOfFirstTask;
	private boolean mWaitingForWindowAnimation;
	private long mWindowAnimationStartTime;

	private RecentTasksLoader mRecentTasksLoader;
	private ArrayList<TaskDescription> mRecentTaskDescriptions;
	private TaskDescriptionAdapter mListAdapter;
	private int mThumbnailWidth;
	private boolean mFitThumbnailToXY;
	private int mRecentItemLayoutId;
	private boolean mHighEndGfx;
	private ImageView mClearRecents;
	private AliProgress mProgress;
	private int mClearAppType = CLEARONEAPP;

	private static final int CLEARALLAPPS = 1;
	public static final int CLEARONEAPP = 2;

	private static final int WARNINGLIMIT = 75;
	private static final int DANGEROUTLIMIT = 90;

	// color
	private int mHealthColor;
	private int mWarningColor;
	private int mDangerousColor;
	private int mCurrentColor;
	private Context mContext;

	/* YUNOS_END */

	public AliProgress getProgress() {
		return mProgress;
	}

	public static interface RecentsScrollView {
		public int numItemsInOneScreenful();

		public void setAdapter(TaskDescriptionAdapter adapter);

		public void setCallback(RecentsCallback callback);

		public void setMinSwipeAlpha(float minAlpha);

		public View findViewForTask(int persistentTaskId);

		public int getItemWidth();

		public View getChildContentView(View v);
	}

	private final class OnLongClickDelegate implements View.OnLongClickListener {
		View mOtherView;

		OnLongClickDelegate(View other) {
			mOtherView = other;
		}

		public boolean onLongClick(View v) {
			return mOtherView.performLongClick();
		}
	}

	/* package */final static class ViewHolder {
		ImageView iconView;
		TaskDescription taskDescription;
		boolean loadedThumbnailAndIcon;
		ImageView lockView;
		int position;
	}

	/* package */final class TaskDescriptionAdapter extends BaseAdapter {
		private LayoutInflater mInflater;
		private int mItemWidth;

		public TaskDescriptionAdapter(Context context) {
			mInflater = LayoutInflater.from(context);
		}

		public void setItemWidth(int width) {
			mItemWidth = width;
		}

		public int getCount() {
			return mRecentTaskDescriptions != null ? mRecentTaskDescriptions
					.size() : 0;
		}

		public Object getItem(int position) {
			return position; // we only need the index
		}

		public long getItemId(int position) {
			return position; // we just need something unique for this position
		}

		public View createView(ViewGroup parent) {
			View convertView = mInflater.inflate(mRecentItemLayoutId, parent,
					false);
			ViewHolder holder = new ViewHolder();
			holder.iconView = (ImageView) convertView
					.findViewById(R.id.app_icon);
			holder.iconView.setImageBitmap(mRecentTasksLoader.getDefaultIcon());
			holder.lockView = (ImageView) convertView
					.findViewById(R.id.recent_item_lock);

			convertView.setTag(holder);
			if (mItemWidth > 0) {
				convertView.setLayoutParams(new LinearLayout.LayoutParams(
						mItemWidth, LinearLayout.LayoutParams.WRAP_CONTENT));
			}
			return convertView;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				convertView = createView(parent);
			}
			ViewHolder holder = (ViewHolder) convertView.getTag();

			// index is reverse since most recent appears at the bottom...
			final int index = position;

			final TaskDescription td = mRecentTaskDescriptions.get(index);

			// holder.thumbnailView.setContentDescription(td.getLabel());
			holder.loadedThumbnailAndIcon = td.isLoaded();
			if (td.isLoaded()) {
				updateIcon(holder, td.getIcon(), true, false);
			}
			if (index == 0) {
				if (mAnimateIconOfFirstTask) {
					if (mItemToAnimateInWhenWindowAnimationIsFinished != null) {
						holder.iconView.setAlpha(1f);
						holder.iconView.setTranslationX(0f);
						holder.iconView.setTranslationY(0f);
					}
					mItemToAnimateInWhenWindowAnimationIsFinished = holder;
					final int translation = -getResources()
							.getDimensionPixelSize(
									R.dimen.status_bar_recents_app_icon_translate_distance);
					final Configuration config = getResources()
							.getConfiguration();
					if (config.orientation == Configuration.ORIENTATION_PORTRAIT) {
						holder.iconView.setAlpha(0f);
						holder.iconView.setTranslationX(translation);
					} else {
						holder.iconView.setAlpha(0f);
						holder.iconView.setTranslationY(translation);
					}
					if (!mWaitingForWindowAnimation) {
						animateInIconOfFirstTask();
					}
				}
			}

			holder.taskDescription = td;
			holder.position = index;
			RecentsScrollView scrollView = (RecentsScrollView) mRecentsContainer;
			final View animView = scrollView.getChildContentView(convertView);
			animView.setAlpha(1f);
			animView.setTranslationY(0f);
			return convertView;
		}

		public void recycleView(View v) {
			ViewHolder holder = (ViewHolder) v.getTag();
			holder.iconView.setImageBitmap(mRecentTasksLoader.getDefaultIcon());
			holder.iconView.setVisibility(INVISIBLE);
			holder.iconView.animate().cancel();
			holder.iconView.setAlpha(1f);
			holder.iconView.setTranslationX(0f);
			holder.iconView.setTranslationY(0f);
			holder.taskDescription = null;
			holder.loadedThumbnailAndIcon = false;
		}
	}

	public RecentsPanelView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
		mContext = context;
		getColor(context);
	}

	public RecentsPanelView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		mContext = context;
		updateValuesFromResources();

		TypedArray a = context.obtainStyledAttributes(attrs,
				R.styleable.RecentsPanelView, defStyle, 0);

		mRecentItemLayoutId = a.getResourceId(
				R.styleable.RecentsPanelView_recentItemLayout, 0);
		mRecentTasksLoader = RecentTasksLoader.getInstance(context);
		getColor(context);
		a.recycle();
	}

	private void getColor(Context context) {
		mHealthColor = context.getResources().getColor(R.color.health_color);
		mWarningColor = context.getResources().getColor(R.color.warning_Color);
		mDangerousColor = context.getResources().getColor(
				R.color.dangerous_Color);
	}

	// update color , memory used percent, the progress of aliprogress bar
	public void updateMemInfo() {
		// int progress = (int)MemoryUtil.getMemoryPercent(getContext());
		int progress = mRecentTasksLoader.getMemoryPercent();
		setCurrentColor(progress);
		updateRecentTaskCurrentMemPercent(progress);
		mProgress.startUpdateAnimation(progress);
	}

	private void setCurrentColor(int progress) {
		if (progress < WARNINGLIMIT)
			mCurrentColor = mHealthColor;
		else if (progress > DANGEROUTLIMIT)
			mCurrentColor = mDangerousColor;
		else
			mCurrentColor = mWarningColor;

		if (mProgress != null)
			mProgress.setPaintColor(mCurrentColor);
	}

	public void updateRecentTaskCount() {
		TextView view = (TextView) findViewById(R.id.recent_task_num);
		if (view != null) {
			view.setText(String.valueOf(mListAdapter.getCount()));
		}
	}

	// WARNING: MemoryUtil.getMemroyPercent() will take a little more time
	// to get memory usage information.It's unwise to invoke
	// updateRecentTaskCurrentMemPercent frequently.
	public void updateRecentTaskCurrentMemPercent(final int percent) {
		TextView view = (TextView) findViewById(R.id.recent_task_mem_percent);
		if (view != null) {
			view.setText(String.valueOf(percent));
			view.setTextColor(mCurrentColor);
		}
		TextView symbolView = (TextView) findViewById(R.id.recent_task_percent_sign);
		if (symbolView != null) {
			symbolView.setTextColor(mCurrentColor);
		}
	}

	public int numItemsInOneScreenful() {
		if (mRecentsContainer instanceof RecentsScrollView) {
			RecentsScrollView scrollView = (RecentsScrollView) mRecentsContainer;
			return scrollView.numItemsInOneScreenful();
		} else {
			throw new IllegalArgumentException(
					"missing Recents[Horizontal]ScrollView");
		}
	}

	private boolean pointInside(int x, int y, View v) {
		final int l = v.getLeft();
		final int r = v.getRight();
		final int t = v.getTop();
		final int b = v.getBottom();
		return x >= l && x < r && y >= t && y < b;
	}

	public boolean isInContentArea(int x, int y) {
		if (pointInside(x, y, mRecentsContainer)) {
			return true;
			// } else if (mStatusBarTouchProxy != null
			// && pointInside(x, y, mStatusBarTouchProxy)) {
			// return true;
		} else {
			return false;
		}
	}

	public void show(boolean show) {
		show(show, null, false, false);
	}

	public void show(boolean show,
			ArrayList<TaskDescription> recentTaskDescriptions,
			boolean firstScreenful, boolean animateIconOfFirstTask) {
		mAnimateIconOfFirstTask = animateIconOfFirstTask;
		mWaitingForWindowAnimation = animateIconOfFirstTask;
		if (DEBUG)
			Log.d(TAG, "show show=" + show);
		if (show) {
			mWaitingToShow = true;
			refreshRecentTasksList(recentTaskDescriptions, firstScreenful);
			showIfReady();
		} else {
			showImpl(false);
		}
	}

	private void showIfReady() {
		// mWaitingToShow => there was a touch up on the recents button
		// mRecentTaskDescriptions != null => we've created views for the first
		// screenful of items
		if (mWaitingToShow && mRecentTaskDescriptions != null) {
			showImpl(true);
		}
	}

	static void sendCloseSystemWindows(Context context, String reason) {
		// if (ActivityManagerNative.isSystemReady()) {
		// try {
		// ActivityManagerNative.getDefault().closeSystemDialogs(reason);
		// } catch (RemoteException e) {
		// }
		// }
	}

	private void showImpl(boolean show) {
		sendCloseSystemWindows(mContext, "recentapps");

		mShowing = show;

		if (show) {
			// if there are no apps, bring up a "No recent apps" message
			// #YUNOS_BEGIN
			// ##modules(Systemui recent): Delete the default layout when no
			// recent apps.
			// ##date: 2013-11.16 author: haiyan.tan@aliyun-inc.com
			boolean noApps = mRecentTaskDescriptions != null
					&& (mRecentTaskDescriptions.size() == 0);
			// mRecentsNoApps.setAlpha(1f);
			mRecentsNoApps
					.setVisibility(noApps ? View.VISIBLE : View.INVISIBLE);
			// #YUNOS_END
			/* YUNOS_BEGIN */
			// ##modules(Systemui recent):
			// ##date: 2013-11.16 ##author: yun.yangyun@aliyun-inc.com
			/*
			 * mClearRecents.setVisibility(noApps ? View.GONE : View.VISIBLE);
			 * mProgress.setVisibility(noApps ? View.GONE : View.VISIBLE);
			 */
			if (noApps) {
				if (DEBUG) {
					Log.d("onAnimationEnd", "==================noApps");
				}
				updateMemInfo();
			}
			/* YUNOS_END */
			onAnimationEnd(null);
			setFocusable(true);
			setFocusableInTouchMode(true);
			requestFocus();
		} else {
			mWaitingToShow = false;
			/* YUNOS_BEGIN */
			// ##modules(Systemui recent): when dismiss ,call to clear the task
			// list .
			// ##date: 2013-11.23 ##author: xinzheng.lixz
			if (mPopup != null) {
				mPopup.dismiss();
			}
			if (mRecentsNoApps != null) {
				mRecentsNoApps.setVisibility(View.INVISIBLE);
			}
			onUiHidden();
			/* YUNOS_END */
		}
	}

	public void onUiHidden() {
		if (!mShowing && mRecentTaskDescriptions != null) {
			onAnimationEnd(null);
			clearRecentTasksList();
		}
	}

	public void dismiss() {
		((RecentsActivity) mContext).dismissAndGoHome();
	}

	public void dismissAndGoBack() {
		((RecentsActivity) mContext).dismissAndGoBack();
	}

	public void updateDeleteAppLable(boolean opacity) {
		((RecentsActivity) mContext).updateDeleteAppLable(opacity);
	}

	public void onAnimationCancel(Animator animation) {
	}

	public void onAnimationEnd(Animator animation) {
		if (mShowing) {
			final LayoutTransition transitioner = new LayoutTransition();
			((ViewGroup) mRecentsContainer).setLayoutTransition(transitioner);
			createCustomAnimations(transitioner);
		} else {
			((ViewGroup) mRecentsContainer).setLayoutTransition(null);
		}
	}

	public void onAnimationRepeat(Animator animation) {
	}

	public void onAnimationStart(Animator animation) {
	}

	@Override
	public boolean dispatchHoverEvent(MotionEvent event) {
		// Ignore hover events outside of this panel bounds since such events
		// generate spurious accessibility events with the panel content when
		// tapping outside of it, thus confusing the user.
		final int x = (int) event.getX();
		final int y = (int) event.getY();
		if (x >= 0 && x < getWidth() && y >= 0 && y < getHeight()) {
			return super.dispatchHoverEvent(event);
		}
		return true;
	}

	/**
	 * Whether the panel is showing, or, if it's animating, whether it will be
	 * when the animation is done.
	 */
	public boolean isShowing() {
		return mShowing;
	}

	public void setRecentTasksLoader(RecentTasksLoader loader) {
		mRecentTasksLoader = loader;
	}

	public void updateValuesFromResources() {
		final Resources res = mContext.getResources();
		mThumbnailWidth = Math.round(res
				.getDimension(R.dimen.status_bar_recents_thumbnail_width));
		mFitThumbnailToXY = res
				.getBoolean(R.bool.config_recents_thumbnail_image_fits_to_xy);
	}

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();

		mRecentsContainer = (ViewGroup) findViewById(R.id.recents_container);

		mListAdapter = new TaskDescriptionAdapter(mContext);
		if (mRecentsContainer instanceof RecentsScrollView) {
			RecentsScrollView scrollView = (RecentsScrollView) mRecentsContainer;
			mListAdapter.setItemWidth(scrollView.getItemWidth());
			scrollView.setAdapter(mListAdapter);
			scrollView.setCallback(this);
		} else {
			throw new IllegalArgumentException(
					"missing Recents[Horizontal]ScrollView");
		}

		mRecentsScrim = findViewById(R.id.recents_bg_protect);
		mRecentsNoApps = findViewById(R.id.recents_no_apps);
		mClearRecents = (ImageView) findViewById(R.id.recents_rock);
		mProgress = (AliProgress) findViewById(R.id.progress);
		if (mClearRecents != null) {
			mClearRecents.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					long avilableSize = MemoryUtil
							.getSystemAvaialbeMemorySize(mContext);
					Intent intent = new Intent();
					TaskDescription firstTaskDescription = RecentTasksLoader
							.getInstance(mContext).getFirstTaskDescription();

					int numRecentApps = mRecentTaskDescriptions != null ? mRecentTaskDescriptions
							.size() : 0;
					long memRelease = 0;
					if (firstTaskDescription != null) {
						String pkgName = firstTaskDescription.getPackageName();
						boolean isTaskLocked = !mContext.getPackageName()
								.equals(pkgName)
								&& !RecentTasksLoader.HOME_PACKAGE_NAME
										.equals(pkgName);
						if (isTaskLocked) {
							intent.putExtra("package", pkgName);
						}
					}
					// mContext.sendBroadcast(intent);
					ComponentName serviceComponent = new ComponentName(
							"com.aliyun.SecurityCenter",
							"com.aliyun.SecurityCenter.applications.ManageApplicationsService");
					intent.setComponent(serviceComponent);
					// getContext().startService(intent);

					for (int i = 0; i < numRecentApps; i++) {
						String packageName = mRecentTaskDescriptions.get(i).packageName
								+ mRecentTaskDescriptions.get(i).resolveInfo.activityInfo.name;
						if (!MemoryUtil.isTaskLocked(mContext,
								mRecentTaskDescriptions.get(i).packageName))
							memRelease += MemoryUtil.getPackageAvailabelMemory(
									mContext,
									mRecentTaskDescriptions.get(i).packageName);
					}
					long total = MemoryUtil.getTotalMemory(mContext);
					long willAvailable = MemoryUtil
							.getSystemAvaialbeMemorySize(mContext)
							+ memRelease
							* 1024;
					long percent = 100 - (willAvailable * 100) / total;
					mClearAppType = CLEARALLAPPS;
					mProgress.setMemorySizeBeforeClear(avilableSize);
					mProgress.setIsClickAccerlateButton(true);
					mRecentsContainer.removeAllViewsInLayout();
					mProgress.startClearAnimation((int) percent);
					setCurrentColor((int) percent);
					updateRecentTaskCurrentMemPercent((int) percent);
				}
			});
		}
		/* YUNOS_END */
		if (mRecentsScrim != null) {
			mHighEndGfx = true;/* ActivityManager.isHighEndGfx() */
			;
			if (!mHighEndGfx) {
				mRecentsScrim.setBackground(null);
			} else if (mRecentsScrim.getBackground() instanceof BitmapDrawable) {
				// In order to save space, we make the background texture repeat
				// in the Y direction
				((BitmapDrawable) mRecentsScrim.getBackground())
						.setTileModeY(TileMode.REPEAT);
			}
		}
	}

	public void setMinSwipeAlpha(float minAlpha) {
		if (mRecentsContainer instanceof RecentsScrollView) {
			RecentsScrollView scrollView = (RecentsScrollView) mRecentsContainer;
			scrollView.setMinSwipeAlpha(minAlpha);
		}
	}

	private void createCustomAnimations(LayoutTransition transitioner) {
		transitioner.setDuration(200);
		transitioner.setStartDelay(LayoutTransition.CHANGE_DISAPPEARING, 0);
		transitioner.setAnimator(LayoutTransition.DISAPPEARING, null);
	}

	private void updateIcon(ViewHolder h, Drawable icon, boolean show,
			boolean anim) {
		if (icon != null) {
			h.iconView.setImageDrawable(icon);
			if (show && h.iconView.getVisibility() != View.VISIBLE) {
				if (anim) {
					h.iconView.setAnimation(AnimationUtils.loadAnimation(
							mContext, R.anim.recent_appear));
				}
				h.iconView.setVisibility(View.VISIBLE);
			}
		}
	}

	private void animateInIconOfFirstTask() {
		if (mItemToAnimateInWhenWindowAnimationIsFinished != null
				&& !mRecentTasksLoader.isFirstScreenful()) {
			int timeSinceWindowAnimation = (int) (System.currentTimeMillis() - mWindowAnimationStartTime);
			final int minStartDelay = 150;
			final int startDelay = Math.max(0, Math.min(minStartDelay
					- timeSinceWindowAnimation, minStartDelay));
			final int duration = 250;
			final ViewHolder holder = mItemToAnimateInWhenWindowAnimationIsFinished;
			final TimeInterpolator cubic = new DecelerateInterpolator(1.5f);
			for (View v : new View[] { holder.iconView, /* holder.labelView *//*
																			 * ,
																			 * holder
																			 * .
																			 * calloutLine
																			 */}) {
				if (v != null) {
					v.animate().translationX(0).translationY(0).alpha(1f)
							.setStartDelay(startDelay).setDuration(duration)
							.setInterpolator(cubic);
				}
			}
			mItemToAnimateInWhenWindowAnimationIsFinished = null;
			mAnimateIconOfFirstTask = false;
		}
	}

	public void onWindowAnimationStart() {
		mWaitingForWindowAnimation = false;
		mWindowAnimationStartTime = System.currentTimeMillis();
		animateInIconOfFirstTask();
	}

	public void clearRecentTasksList() {
		// Clear memory used by screenshots
		if (DEBUG)
			Log.d(TAG, "clearRecentTasksList  mRecentTaskDescriptions isNull ="
					+ (mRecentTaskDescriptions == null));
		if (mRecentTaskDescriptions != null) {
			mRecentTasksLoader.cancelLoadingIcons(this);
			onTaskLoadingCancelled();
		}
	}

	public void onTaskLoadingCancelled() {
		// Gets called by RecentTasksLoader when it's cancelled
		onTaskLoadingCancelled(true);
	}

	public void onTaskLoadingCancelled(boolean refreshView) {
		// Gets called by RecentTasksLoader when it's cancelled
		if (mRecentTaskDescriptions != null) {
			mRecentTaskDescriptions = null;
			if (refreshView)
				mListAdapter.notifyDataSetInvalidated();
		}
	}

	public void refreshViews() {
		if (DEBUG)
			Log.d(TAG, "refreshViews count =" + mListAdapter.getCount());
		mListAdapter.notifyDataSetInvalidated();
		updateUiElements();
		/* YUNOS BEGIN */
		// ##modules(systemui recents)
		// ##date:2013-11-19 ##author:haiyan.tan@aliyun-inc.com
		updateRecentTaskCount();
		// updateMemInfo();
		/* YUNOS END */
		showIfReady();
	}

	public void refreshRecentTasksList() {
		refreshRecentTasksList(null, false);
	}

	private void refreshRecentTasksList(
			ArrayList<TaskDescription> recentTasksList, boolean firstScreenful) {
		/* YUNOS_BEGIN */
		// ##modules(Systemui recent): bug id 86137(recentsPanelView does not
		// refreshViews when rerecentTasksList has no element)
		// ##date: 2014.1.15 author: yulong.hyl@alibaba-inc.com
		if (mRecentTaskDescriptions == null && recentTasksList != null) {
			/* YUNOS_END */
			onTasksLoaded(recentTasksList, firstScreenful);
		} else {
			mRecentTasksLoader.loadTasksInBackground();
		}
	}

	public void onTasksLoaded(ArrayList<TaskDescription> tasks,
			boolean firstScreenful) {
		if (mRecentTaskDescriptions == null) {
			mRecentTaskDescriptions = new ArrayList<TaskDescription>(tasks);
		} else {
			mRecentTaskDescriptions.addAll(tasks);
		}
		if (DEBUG) {
			Log.d("onAnimationEnd", "=======================onTasksLoaded:"
					+ RecentTasksLoader.getInstance(mContext).getState());
			Log.d("onAnimationEnd",
					"=======================onTasksLoaded_mShowing"
							+ ((RecentsActivity) mContext).isActivityShowing());
		}
		if (((RecentsActivity) mContext).isActivityShowing()
				&& RecentTasksLoader.getInstance(mContext).getState() == RecentTasksLoader.State.LOADED) {
			if (DEBUG) {
				Log.d("onAnimationEnd", "=======================onTasksLoaded");
			}
			refreshViews();
		}
	}

	/* YUNOS_BEGIN */
	// ##module(RecentTasks)
	// ##date: 2014/4/11
	// ##author: yulong.hyl@alibaba-inc.com
	// ##BugID:107051:recent task starts animation twice sometimes
	// RootCause:asynctask callback UI thread to refresh view has a problem
	// Solution:UI thread refresh view when acynctask load finished
	public void onTasksLoadFinished() {
		if (DEBUG) {
			Log.d("onAnimationEnd", "=======================onTasksLoaded:"
					+ RecentTasksLoader.getInstance(mContext).getState());
			Log.d("onAnimationEnd",
					"=======================onTasksLoaded_mShowing"
							+ ((RecentsActivity) mContext).isActivityShowing());
		}
		if (((RecentsActivity) mContext).isActivityShowing()
				&& RecentTasksLoader.getInstance(mContext).getState() == RecentTasksLoader.State.LOADED) {
			if (DEBUG) {
				Log.d("onAnimationEnd", "=======================onTasksLoaded");
			}
			refreshViews();
		}
	}

	/* YUNOS_END */
	private void updateUiElements() {
		final int items = mRecentTaskDescriptions != null ? mRecentTaskDescriptions
				.size() : 0;

		mRecentsContainer.setVisibility(items > 0 ? View.VISIBLE : View.GONE);

		// Set description for accessibility
		int numRecentApps = mRecentTaskDescriptions != null ? mRecentTaskDescriptions
				.size() : 0;
		String recentAppsAccessibilityDescription;
		if (numRecentApps == 0) {
			recentAppsAccessibilityDescription = getResources().getString(
					R.string.status_bar_no_recent_apps);
		} else {
			recentAppsAccessibilityDescription = getResources()
					.getQuantityString(
							R.plurals.status_bar_accessibility_recent_apps,
							numRecentApps, numRecentApps);
		}
		setContentDescription(recentAppsAccessibilityDescription);
	}

	public boolean simulateClick(int persistentTaskId) {
		if (mRecentsContainer instanceof RecentsScrollView) {
			RecentsScrollView scrollView = (RecentsScrollView) mRecentsContainer;
			View v = scrollView.findViewForTask(persistentTaskId);
			if (v != null) {
				handleOnClick(v);
				return true;
			}
		}
		return false;
	}

	public void handleOnClick(View view) {
		ViewHolder holder = (ViewHolder) view.getTag();
		TaskDescription ad = holder.taskDescription;
		if (ad == null) {
			return;
		}
		final Context context = view.getContext();
		final ActivityManager am = (ActivityManager) context
				.getSystemService(Context.ACTIVITY_SERVICE);
		/* YUNOS_BEGIN */
		// ##module(RecentTasks)
		// ##date: 2014/3/27
		// ##author: yulong.hyl@alibaba-inc.com
		// ##BugID:105383:add scaleup animation when click recent task app
		Bundle opts = (view == null) ? null : ActivityOptions
				.makeScaleUpAnimation(view, 0, 0, view.getMeasuredWidth(),
						view.getMeasuredHeight()).toBundle();
		/* YUNOS_END */
		show(false);
		/* YUNOS_BEGIN */
		// ##modules(Systemui recent): usertrack
		// ##date: 2013-12.26 author: yulong.hyl@alibaba-inc.com
		int position = holder.position;
		String packageName = ad.getPackageName();
		String kvs = "packageName= " + packageName + " position= " + position
				+ " action= " + "click";
		/* YUNOS_END */
		if (ad.taskId >= 0) {
			// This is an active task; it should just go to the foreground.
			/* YUNOS BEGIN */
			// ##module(SecurityCenter) bug id 80981(secutitycenter lock app)
			// ##date:2014/1/2 ##author:yulong.hyl@alibaba-inc.com
			// SecureManager secure = SecureManager.get(mContext);
			// if (secure != null) {
			// boolean isBlocked = secure.filterSyncTask(ad.taskId, ad.intent);
			// if (isBlocked) {
			// if (context instanceof RecentsActivity) {
			// RecentsActivity recentsActivity = ((RecentsActivity) context);
			// recentsActivity.moveTaskToBack(true);
			// }
			// } else {
			// am.moveTaskToFront(ad.taskId,
			// ActivityManager.MOVE_TASK_WITH_HOME,opts);
			// }
			// }
			/* YUNOS END */
		} else {
			Intent intent = ad.intent;
			intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY
					| Intent.FLAG_ACTIVITY_TASK_ON_HOME
					| Intent.FLAG_ACTIVITY_NEW_TASK);
			if (DEBUG)
				Log.v(TAG, "Starting activity " + intent);
			try {
				// context.startActivityAsUser(intent, opts, new UserHandle(
				// UserHandle.USER_CURRENT));
				context.startActivity(intent, opts);
			} catch (SecurityException e) {
				Log.e(TAG, "Recents does not have the permission to launch "
						+ intent, e);
			} catch (ActivityNotFoundException e) {
				Log.e(TAG, "Error launching activity " + intent, e);
			}
		}
	}

	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		handleOnClick(view);
	}

	public void setClearAppType(int clearAppType) {
		mClearAppType = clearAppType;
	}

	public int getClearAppType() {
		return mClearAppType;
	}

	public void handleSwipe(View view) {
		TaskDescription ad = ((ViewHolder) view.getTag()).taskDescription;
		if (ad == null || mRecentTaskDescriptions == null) {
			if (DEBUG)
				Log.v(TAG,
						"Not able to find activity description for swiped task; view="
								+ view + " tag=" + view.getTag());
			return;
		}
		if (DEBUG)
			Log.v(TAG, "Jettison " + ad.getLabel());
		mRecentTaskDescriptions.remove(ad);
		mRecentTasksLoader.remove(ad);
		/* YUNOS BEGIN */
		// ##modules(systemui recents)
		// ##date:2013-11-19
		// ##author:yun.yangyun@alibaba-inc.com/haiyan.tan@aliyun-inc.com
		updateRecentTaskCount();
		/* YUNOS END */

		// Handled by widget containers to enable LayoutTransitions properly
		// mListAdapter.notifyDataSetChanged();

		if (mRecentTaskDescriptions.size() == 0) {
			/* YUNOS BEGIN */
			// ##modules(Systemui recent):
			// ##date: 2013-11.18 ##author: yun.yangyun@aliyun-inc.com
			// dismissAndGoBack();
			/* YUNOS END */
			mRecentsNoApps.setVisibility(View.VISIBLE);
		}

		// Currently, either direction means the same thing, so ignore direction
		// and remove
		// the task.
		final ActivityManager am = (ActivityManager) mContext
				.getSystemService(Context.ACTIVITY_SERVICE);
		if (am != null) {
			// am.removeTask(ad.persistentTaskId,
			// ActivityManager.REMOVE_TASK_KILL_PROCESS);
			/* YUNOS_BEGIN */
			// ##modules(Systemui recent): RecentTasks consistent with
			// SecurityCenter experience
			// ##date: 2014.1.11 author: yulong.hyl@alibaba-inc.com
			if (!"com.aliyun.SecurityCenter".equals(ad.packageName)
					&& !"com.android.deskclock".equals(ad.packageName)) {
				// am.forceStopPackage(ad.packageName);
			}
			/* YUNOS_END */
			// Accessibility feedback
			setContentDescription(String.format(
					getResources().getString(
							R.string.accessibility_recents_item_dismissed),
					ad.getLabel()));
			sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
			setContentDescription(null);
		}

		if (mClearAppType == CLEARONEAPP)
			updateMemInfo();
	}

	private void startApplicationDetailsActivity(String packageName) {
		Intent intent = new Intent(
				Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts(
						"package", packageName, null));
		intent.setComponent(intent.resolveActivity(mContext.getPackageManager()));
		TaskStackBuilder.create(getContext())
				.addNextIntentWithParentStack(intent).startActivities();
	}

	public boolean onInterceptTouchEvent(MotionEvent ev) {
		if (mPopup != null) {
			return true;
		} else {
			return super.onInterceptTouchEvent(ev);
		}
	}

	public void handleLongPress(final View selectedView, final View anchorView,
			final View thumbnailView) {
		thumbnailView.setSelected(true);
		final PopupMenu popup = new PopupMenu(mContext,
				anchorView == null ? selectedView : anchorView);
		mPopup = popup;
		popup.getMenuInflater().inflate(R.menu.recent_popup_menu,
				popup.getMenu());
		popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				if (item.getItemId() == R.id.recent_remove_item) {
					mRecentsContainer.removeViewInLayout(selectedView);
				} else if (item.getItemId() == R.id.recent_inspect_item) {
					ViewHolder viewHolder = (ViewHolder) selectedView.getTag();
					if (viewHolder != null
							&& viewHolder.taskDescription != null) {
						final TaskDescription ad = viewHolder.taskDescription;
						startApplicationDetailsActivity(ad.packageName);
						show(false);
					} else {
						throw new IllegalStateException("Oops, no tag on view "
								+ selectedView);
					}
				} else {
					return false;
				}
				return true;
			}
		});
		popup.setOnDismissListener(new PopupMenu.OnDismissListener() {
			public void onDismiss(PopupMenu menu) {
				thumbnailView.setSelected(false);
				mPopup = null;
			}
		});
		popup.show();
	}
}
