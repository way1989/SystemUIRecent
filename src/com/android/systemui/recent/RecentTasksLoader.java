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
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Process;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;

import com.android.systemui.utils.FrostedGlassUtil;
import com.android.systemui.utils.ImageUtil;
import com.android.systemui.utils.MemoryUtil;
import com.way.alirecent.R;

@SuppressLint("NewApi")
public class RecentTasksLoader implements View.OnTouchListener {
	static final String TAG = "RecentTasksLoader";
	static final boolean DEBUG = true;

	private static final int DISPLAY_TASKS = 20;
	private static final int MAX_TASKS = DISPLAY_TASKS + 1; // allow extra for
															// non-apps
	private static final int PRELOAD_DATA_TIMEOUT = 5000;

	public static final String HOME_PACKAGE_NAME = "com.aliyun.homeshell";
	private static final String HOME_MAIN_CLASS = "com.aliyun.homeshell.Launcher";

	private Context mContext;
	private RecentsPanelView mRecentsPanel;

	private AsyncTask<Void, ArrayList<TaskDescription>, Void> mTaskLoader;
	private Handler mHandler;

	private int mIconDpi;
	private Bitmap mDefaultIconBackground;
	private int mNumTasksInFirstScreenful = Integer.MAX_VALUE;

	private boolean mFirstScreenful;
	private ArrayList<TaskDescription> mLoadedTasks;

	public enum State {
		LOADING, LOADED, CANCELLED
	};

	private State mState = State.CANCELLED;

	public State getState() {
		return mState;
	}

	private Display mDisplay;
	private int mNavSize = 0;
	private DisplayMetrics mDisplayMetrics = new DisplayMetrics();
	private BitmapDrawable mDrawableScreenshot = null;
	private int mMemoryPercent;
	private long mTimeStamp = 0;

	private static RecentTasksLoader sInstance;
	private TaskDescription mFirstTaskDescription;

	public TaskDescription getFirstTaskDescription() {
		return mFirstTaskDescription;
	}

	public static RecentTasksLoader getInstance(Context context) {
		if (sInstance == null) {
			sInstance = new RecentTasksLoader(context);
		}
		return sInstance;
	}

	private RecentTasksLoader(Context context) {
		mContext = context;
		mHandler = new Handler();
		final Resources res = context.getResources();

		// get the icon size we want -- on tablets, we use bigger icons
		boolean isTablet = res
				.getBoolean(R.bool.config_recents_interface_for_tablets);
		if (isTablet) {
			ActivityManager activityManager = (ActivityManager) context
					.getSystemService(Context.ACTIVITY_SERVICE);
			mIconDpi = activityManager.getLauncherLargeIconDensity();
		} else {
			mIconDpi = res.getDisplayMetrics().densityDpi;
		}

		// Render default icon (just a blank image)
		int defaultIconSize = res.getDimensionPixelSize(R.dimen.app_icon_size);
		int iconSize = (int) (defaultIconSize * mIconDpi / res
				.getDisplayMetrics().densityDpi);
		mDefaultIconBackground = Bitmap.createBitmap(iconSize, iconSize,
				Bitmap.Config.ARGB_8888);

		WindowManager wm = (WindowManager) context
				.getSystemService(Context.WINDOW_SERVICE);
		mDisplay = wm.getDefaultDisplay();
		mDisplay.getMetrics(mDisplayMetrics);
	}

	public void setRecentsPanel(RecentsPanelView newRecentsPanel,
			RecentsPanelView caller) {
		// Only allow clearing mRecentsPanel if the caller is the current
		// recentsPanel
		if (newRecentsPanel != null || mRecentsPanel == caller) {
			mRecentsPanel = newRecentsPanel;
			if (mRecentsPanel != null) {
				mNumTasksInFirstScreenful = mRecentsPanel
						.numItemsInOneScreenful();
			}
		}
	}

	public Bitmap getDefaultIcon() {
		return mDefaultIconBackground;
	}

	public ArrayList<TaskDescription> getLoadedTasks() {
		if (System.currentTimeMillis() - mTimeStamp > PRELOAD_DATA_TIMEOUT) {
			if (DEBUG)
				Log.d(TAG, "getLoadedTasks time out reset ");
			cancelPreloadingRecentTasksList(false);
			return null;
		}
		return mLoadedTasks;
	}

	public void remove(TaskDescription td) {
		if (mLoadedTasks != null)
			mLoadedTasks.remove(td);
	}

	public boolean isFirstScreenful() {
		return mFirstScreenful;
	}

	private boolean isCurrentHomeActivity(ComponentName component,
			ActivityInfo homeInfo) {
		if (homeInfo == null) {
			final PackageManager pm = mContext.getPackageManager();
			homeInfo = new Intent(Intent.ACTION_MAIN).addCategory(
					Intent.CATEGORY_HOME).resolveActivityInfo(pm, 0);
		}
		// fix home task
		if (homeInfo != null) {
			if (!homeInfo.packageName.equals(HOME_PACKAGE_NAME)
					|| !homeInfo.name.equals(HOME_MAIN_CLASS)) {
				homeInfo.packageName = HOME_PACKAGE_NAME;
				homeInfo.name = HOME_MAIN_CLASS;
			}
		}
		if (DEBUG)
			Log.d(TAG, "component : " + component + " homeInfo : " + homeInfo);
		return homeInfo != null
				&& homeInfo.packageName.equals(component.getPackageName())
				&& homeInfo.name.equals(component.getClassName());
	}

	// Create an TaskDescription, returning null if the title or icon is null
	TaskDescription createTaskDescription(int taskId, int persistentTaskId,
			Intent baseIntent, ComponentName origActivity,
			CharSequence description) {
		Intent intent = new Intent(baseIntent);
		if (origActivity != null) {
			intent.setComponent(origActivity);
		}
		final PackageManager pm = mContext.getPackageManager();
		intent.setFlags((intent.getFlags() & ~Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
				| Intent.FLAG_ACTIVITY_NEW_TASK);
		final ResolveInfo resolveInfo = pm.resolveActivity(intent, 0);
		if (resolveInfo != null) {
			final ActivityInfo info = resolveInfo.activityInfo;
			final String title = info.loadLabel(pm).toString();

			if (title != null && title.length() > 0) {
				if (DEBUG)
					Log.v(TAG, "creating activity desc for id="
							+ persistentTaskId + ", label=" + title);

				TaskDescription item = new TaskDescription(taskId,
						persistentTaskId, resolveInfo, baseIntent,
						info.packageName, description);
				item.setLabel(title);

				return item;
			} else {
				if (DEBUG)
					Log.v(TAG, "SKIPPING item " + persistentTaskId);
			}
		}
		return null;
	}

	void loadIcon(TaskDescription td) {
		final ActivityManager am = (ActivityManager) mContext
				.getSystemService(Context.ACTIVITY_SERVICE);
		final PackageManager pm = mContext.getPackageManager();

		Drawable icon = getFullResIcon(td.resolveInfo, pm);

		synchronized (td) {
			if (icon != null) {
				td.setIcon(icon);
			}
			td.setLoaded(true);
		}
	}

	Drawable getFullResDefaultActivityIcon() {
		return getFullResIcon(Resources.getSystem(),
				R.drawable.sym_def_app_icon);
	}

	Drawable getFullResIcon(Resources resources, int iconId) {
		try {
			return resources.getDrawableForDensity(iconId, mIconDpi);
		} catch (Resources.NotFoundException e) {
			return getFullResDefaultActivityIcon();
		}
	}

	private Drawable getFullResIcon(ResolveInfo info,
			PackageManager packageManager) {
		if (DEBUG)
			Log.d(TAG, "----- " + info.activityInfo);
		return info.activityInfo.applicationInfo.loadIcon(packageManager);
	}

	Runnable mPreloadTasksRunnable = new Runnable() {
		public void run() {
			loadTasksInBackground();
		}
	};

	// additional optimization when we have software system buttons - start
	// loading the recent
	// tasks on touch down
	@Override
	public boolean onTouch(View v, MotionEvent ev) {
		int action = ev.getAction() & MotionEvent.ACTION_MASK;
		if (action == MotionEvent.ACTION_DOWN) {
			preloadRecentTasksList();
		} else if (action == MotionEvent.ACTION_CANCEL) {
			cancelPreloadingRecentTasksList();
		} else if (action == MotionEvent.ACTION_UP) {
			// Remove the preloader if we haven't called it yet
			mHandler.removeCallbacks(mPreloadTasksRunnable);
			if (!v.isPressed()) {
				cancelLoadingIcons(true);
			}

		}
		return false;
	}

	private Bitmap screenshotFocusedWindow(int maxWidth, int maxHeight) {
		int rot = mDisplay.getRotation();
		Rect frame = new Rect();
		float degrees = 0f;
		switch (rot) {
		case Surface.ROTATION_90:
			degrees = 360f - 90f;
			break;
		case Surface.ROTATION_180:
			degrees = 360f - 180f;
			break;
		case Surface.ROTATION_270:
			degrees = 360f - 270f;
			break;
		default:
			degrees = 0f;
			break;
		}

		if (mNavSize > 0) {
			if (maxHeight > maxWidth) {
				maxHeight += mNavSize;
			} else {
				maxWidth += mNavSize;
			}
		}

		Matrix matrix = new Matrix();
		int dw = maxWidth;
		int dh = maxHeight;

		frame.set(0, 0, dw, dh);

		float dims[] = { dw, dh };
		boolean requiresRotation = (degrees > 0);
		if (requiresRotation) {
			// Get the dimensions of the device in its native orientation
			matrix.reset();
			matrix.preRotate(-degrees);
			matrix.mapPoints(dims);
			dims[0] = Math.abs(dims[0]);
			dims[1] = Math.abs(dims[1]);
		}

		if (frame.isEmpty()) {
			return null;
		}

		Bitmap bp = ImageUtil.drawableToBitmap(mContext.getResources().getDrawable(R.drawable.bg_thunder_storm));
		// Bitmap bp = SurfaceControl.screenshot((int)dims[0], (int)dims[1]);
		/* YUNOS BEGIN */
		// ##module(systemui)
		// ##date:2013/11/3 ##author:sunchen.sc@alibaba-inc.com##BugId??61381
		// The screenshot() interface may fail. If it fail, it will return null.
		// If the screenshot is null, the statusbar will be transparent.
		if (bp == null) {
			return null;
		}
		/* YUNOS END */
		final int SCALE_RATIO = 8;
		if (requiresRotation) {
			// Rotate the screenshot to the current orientation
			Bitmap ss = Bitmap.createBitmap(dw / SCALE_RATIO, dh / SCALE_RATIO,
					Bitmap.Config.ARGB_8888);

			Canvas c = new Canvas(ss);
			c.clipRect(0, frame.top / SCALE_RATIO, dw / SCALE_RATIO, dh
					/ SCALE_RATIO);
			if (mNavSize > 0) {
				c.clipRect(0, 0, (dw - mNavSize) / SCALE_RATIO, dh
						/ SCALE_RATIO);
			}
			c.translate(ss.getWidth() / 2, ss.getHeight() / 2);
			c.rotate(degrees);
			c.translate(-dims[0] / (2 * SCALE_RATIO), -dims[1]
					/ (2 * SCALE_RATIO));
			Rect dstRect = new Rect(0, 0, ss.getHeight(), ss.getWidth());
			c.drawBitmap(bp, new Rect(0, 0, bp.getWidth(), bp.getHeight()),
					dstRect, null);
			c.setBitmap(null);
			bp.recycle();
			bp = ss;
		} else {
			Bitmap ss = Bitmap.createBitmap(dw / SCALE_RATIO, dh / SCALE_RATIO,
					Bitmap.Config.ARGB_8888);
			Canvas c = new Canvas(ss);
			c.clipRect(0, 0, dw / SCALE_RATIO, dh / SCALE_RATIO);
			if (mNavSize > 0) {
				c.clipRect(0, 0, dw / SCALE_RATIO, (dh - mNavSize)
						/ SCALE_RATIO);
			}
			Rect dstRect = new Rect(0, 0, ss.getWidth(), ss.getHeight());
			Rect srcRect = new Rect(0, frame.top, bp.getWidth(), bp.getHeight());
			c.drawBitmap(bp, srcRect, dstRect, null);
			c.setBitmap(null);

			bp.recycle();
			bp = ss;
		}
		/* YUNOS BEGIN */
		// ##module(SystemUI)
		// ##date:2013/3/29 ##author:sunchen.sc@alibaba-inc.com##BugID:104943
		// Invoke jni with thread safe
		bp = FrostedGlassUtil.getInstance().convertToBlur(bp, 4);
		/* YUNOS END */
		return bp;
	}

	public void takeScreenshot() {
		mDrawableScreenshot = new BitmapDrawable(screenshotFocusedWindow(
				mDisplayMetrics.widthPixels, mDisplayMetrics.heightPixels));
	}

	public void loadMemoryPercent() {
		mMemoryPercent = (int) MemoryUtil.getMemoryPercent(mContext);
	}

	public int getMemoryPercent() {
		loadMemoryPercent();
		return mMemoryPercent;
	}

	public BitmapDrawable getScreenshot(Display display) {
		DisplayMetrics dm = new DisplayMetrics();
		display.getMetrics(dm);
		if (dm.widthPixels != mDisplayMetrics.widthPixels
				|| dm.heightPixels != mDisplayMetrics.heightPixels) {
			// orientation changed, we need force take screen shot.
			mDisplay = display;
			mDisplayMetrics = dm;
		}
		takeScreenshot();
		return mDrawableScreenshot;
	}

	public void preloadRecentTasksList() {
		if (DEBUG)
			Log.d(TAG, "preloadRecentTasksList");
		mHandler.post(mPreloadTasksRunnable);
	}

	public void cancelPreloadingRecentTasksList(boolean refreshView) {
		if (DEBUG)
			Log.d(TAG, "cancelPreloadingRecentTasksList");
		cancelLoadingIcons(refreshView);
		mHandler.removeCallbacks(mPreloadTasksRunnable);
	}

	public void cancelPreloadingRecentTasksList() {
		cancelPreloadingRecentTasksList(true);
	}

	public void cancelLoadingIcons(RecentsPanelView caller) {
		// Only oblige this request if it comes from the current RecentsPanel
		// (eg when you rotate, the old RecentsPanel request should be ignored)
		if (mRecentsPanel == caller) {
			cancelLoadingIcons(true);
		}
	}

	private void cancelLoadingIcons(boolean refreshView) {
		if (mTaskLoader != null) {
			mTaskLoader.cancel(false);
			mTaskLoader = null;
		}
		mLoadedTasks = null;
		mFirstTaskDescription = null;
		mFirstScreenful = false;
		mState = State.CANCELLED;
	}

	public void loadTasksInBackground() {
		loadTasksInBackground(false);
	}

	public void loadTasksInBackground(final boolean zeroeth) {
		if (mState != State.CANCELLED) {
			return;
		}
		mState = State.LOADING;
		mFirstScreenful = true;

		final LinkedBlockingQueue<TaskDescription> tasksWaitingForThumbnails = new LinkedBlockingQueue<TaskDescription>();
		mTaskLoader = new AsyncTask<Void, ArrayList<TaskDescription>, Void>() {
			@Override
			protected void onProgressUpdate(
					ArrayList<TaskDescription>... values) {
				if (!isCancelled()) {
					ArrayList<TaskDescription> newTasks = values[0];
					// do a callback to RecentsPanelView to let it know we have
					// more values
					// how do we let it know we're all done? just always call
					// back twice
					if (mRecentsPanel != null) {
						if (mFirstScreenful == true)
							mRecentsPanel.onTaskLoadingCancelled(true);
						mRecentsPanel.onTasksLoaded(newTasks, mFirstScreenful);
					}
					if (mLoadedTasks == null) {
						mTimeStamp = System.currentTimeMillis();
						mLoadedTasks = new ArrayList<TaskDescription>();
					}
					mLoadedTasks.addAll(newTasks);
					if (DEBUG)
						Log.d(TAG, "loadTasksInBackground 2 tasks ="
								+ mLoadedTasks.size());
					if (mFirstScreenful == false) {
						mState = State.LOADED;
						if (mRecentsPanel != null) {
							mRecentsPanel.onTasksLoadFinished();
						}
						if (DEBUG)
							Log.d(TAG,
									"loadTasksInBackground State.LOADED tasks ="
											+ mLoadedTasks.size());
					}
					mFirstScreenful = false;
				}
			}

			@Override
			protected Void doInBackground(Void... params) {
				// We load in two stages: first, we update progress with just
				// the first screenful
				// of items. Then, we update with the rest of the items
				final int origPri = Process.getThreadPriority(Process.myTid());
				Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
				loadMemoryPercent();
				// takeScreenshot();
				final PackageManager pm = mContext.getPackageManager();
				final ActivityManager am = (ActivityManager) mContext
						.getSystemService(Context.ACTIVITY_SERVICE);

				final List<ActivityManager.RecentTaskInfo> recentTasks = am
						.getRecentTasks(MAX_TASKS,
								ActivityManager.RECENT_IGNORE_UNAVAILABLE);
				int numTasks = recentTasks.size();
				ActivityInfo homeInfo = new Intent(Intent.ACTION_MAIN)
						.addCategory(Intent.CATEGORY_HOME).resolveActivityInfo(
								pm, 0);

				boolean firstScreenful = true;
				ArrayList<TaskDescription> tasks = new ArrayList<TaskDescription>();

				// skip the first task - assume it's either the home screen or
				// the current activity.
				final int first = 0;
				for (int i = first, index = 0; i < numTasks
						&& (index < MAX_TASKS); ++i) {
					if (isCancelled()) {
						break;
					}
					final ActivityManager.RecentTaskInfo recentInfo = recentTasks
							.get(i);

					Intent intent = new Intent(recentInfo.baseIntent);
					if (recentInfo.origActivity != null) {
						intent.setComponent(recentInfo.origActivity);
					}
					/* YUNOS_BEGIN */
					// ##module(RecentTasks)
					// ##date: 2014/3/28
					// ##author: yulong.hyl@alibaba-inc.com
					// ##BugID:105643:keep the first TaskDescription object
					ComponentName origActivity = recentInfo.origActivity;
					if (origActivity == null)
						origActivity = intent.getComponent();

					TaskDescription item = createTaskDescription(recentInfo.id,
							recentInfo.persistentId, recentInfo.baseIntent,
							origActivity, recentInfo.description);
					if (item != null && i == 0) {
						mFirstTaskDescription = item;
					}
					/* YUNOS_END */
					if (DEBUG)
						Log.d("recentInfo", intent.getComponent()
								.getPackageName());

					// Don't load the current home activity.
					if (isCurrentHomeActivity(intent.getComponent(), homeInfo)) {
						if (DEBUG)
							Log.d(TAG, "isCurrentHomeActivity");
						continue;
					}

					// Don't load ourselves
					if (intent.getComponent().getPackageName()
							.equals(mContext.getPackageName())) {
						/* YUNOS_BEGIN */
						// ##modules(Systemui recent): boot on the first time,
						// recenttask contains systemui
						// ##date: 2014.2.11 author: yulong.hyl@alibaba-inc.com
						i++;
						/* YUNOS_END */
						continue;
					}
					/* YUNOS_BEGIN */
					// ##modules(Systemui recent): boot on the first time,
					// recenttask contains systemui
					// ##date: 2014.2.11 author: yulong.hyl@alibaba-inc.com
					if (i == 0) {
						continue;
					}
					/* YUNOS_END */
					if (item != null) {
						loadIcon(item);
						tasks.add(item);
						if (firstScreenful
								&& tasks.size() == mNumTasksInFirstScreenful) {
							publishProgress(tasks);
							tasks = new ArrayList<TaskDescription>();
							firstScreenful = false;
							// break;
						}
						++index;
					}
				}

				if (!isCancelled()) {
					publishProgress(tasks);
					if (firstScreenful) {
						// always should publish two updates
						publishProgress(new ArrayList<TaskDescription>());
					}
				}
				Process.setThreadPriority(origPri);
				return null;
			}
		};
		mTaskLoader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}
}
