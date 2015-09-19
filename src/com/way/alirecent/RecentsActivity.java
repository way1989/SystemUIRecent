package com.way.alirecent;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.android.systemui.recent.Constants;
import com.android.systemui.recent.RecentTasksLoader;
import com.android.systemui.recent.RecentsPanelView;
import com.android.systemui.recent.TaskDescription;

@SuppressLint("NewApi")
public class RecentsActivity extends Activity {
	public static final String TOGGLE_RECENTS_INTENT = "com.android.systemui.recent.action.TOGGLE_RECENTS";
	public static final String PRELOAD_INTENT = "com.android.systemui.recent.action.PRELOAD";
	public static final String CANCEL_PRELOAD_INTENT = "com.android.systemui.recent.CANCEL_PRELOAD";
	public static final String CLOSE_RECENTS_INTENT = "com.android.systemui.recent.action.CLOSE";
	public static final String WINDOW_ANIMATION_START_INTENT = "com.android.systemui.recent.action.WINDOW_ANIMATION_START";
	public static final String PRELOAD_PERMISSION = "com.android.systemui.recent.permission.PRELOAD";
	public static final String WAITING_FOR_WINDOW_ANIMATION_PARAM = "com.android.systemui.recent.WAITING_FOR_WINDOW_ANIMATION";
	private static final String WAS_SHOWING = "was_showing";

	public static final String TAG = "RecentsActivity";
	static final boolean DEBUG = true;
	private RecentsPanelView mRecentsPanel;

	public RecentsPanelView getRecentsPanel() {
		return mRecentsPanel;
	}

	private IntentFilter mIntentFilter;
	private boolean mShowing;
	private boolean mForeground;
	private Display mDisplay;
	private int mNavSize;
	private DisplayMetrics mDisplayMetrics = new DisplayMetrics();
	private View mBashboard;
	BitmapDrawable mBackgroud;
	LinearLayout mRootLayout;
	FrameLayout mRecentsRootViewLayout;
	Vibrator mVibrator;
	private boolean mCanHidePanelBoard = false;
	private RelativeLayout relLayout;
	private Animator animator;
	private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (CLOSE_RECENTS_INTENT.equals(intent.getAction())) {
				if (mRecentsPanel != null && mRecentsPanel.isShowing()) {
					if (mShowing && !mForeground) {
						// Captures the case right before we transition to
						// another activity
						mRecentsPanel.show(false);
					}
				}
			} else if (WINDOW_ANIMATION_START_INTENT.equals(intent.getAction())) {
				if (mRecentsPanel != null) {
					mRecentsPanel.onWindowAnimationStart();
				}
			}
		}
	};

	@Override
	public void onPause() {
		mShowing = false;
		overridePendingTransition(0, 0);
		mForeground = false;
		releaseAnimator();
		if (mRecentsPanel != null && mRecentsPanel.getProgress() != null) {
			mRecentsPanel.getProgress().clearAnimation();
			mRecentsPanel.setClearAppType(RecentsPanelView.CLEARONEAPP);
		}
		moveToBack();
		if (relLayout != null) {
			relLayout.setVisibility(View.GONE);
		}
		if (DEBUG)
			Log.d(TAG, "onPause");
		super.onPause();
	}

	@Override
	public void onStop() {
		final RecentTasksLoader recentTasksLoader = RecentTasksLoader
				.getInstance(this);
		mShowing = false;
		super.onStop();
	}

	private void moveToBack() {
		if (mRecentsPanel != null) {
			mRecentsPanel.show(false);
		}
		moveTaskToBack(true);
	}

	@Override
	public void onStart() {
		super.onStart();
	}

	public void initData() {
		mShowing = true;
		final RecentTasksLoader recentTasksLoader = RecentTasksLoader
				.getInstance(this);
		if (DEBUG) {
			Log.d("onAnimationEnd", "=======================initData_State:"
					+ recentTasksLoader.getState());
		}
		if (mRecentsPanel != null
				&& recentTasksLoader.getState() == RecentTasksLoader.State.LOADED) {
			if (DEBUG) {
				Log.d("onAnimationEnd",
						"=======================initData_refreshViews");
			}
			mRecentsPanel.refreshViews();
		}
		mBackgroud = recentTasksLoader.getScreenshot(mDisplay);
		// getWindow().getDecorView().setBackground(mBackgroud);
		// getWindow().getDecorView().setBackgroundResource(R.drawable.status_bar_recents_background);
		getWindow().getDecorView().setBackground(
				new LayerDrawable(new Drawable[] {
						mBackgroud,
						getResources().getDrawable(
								R.drawable.status_bar_recents_background) }));
	}

	public boolean performFeedback(boolean always) {
		if (!mVibrator.hasVibrator()) {
			return false;
		}
		final boolean hapticsDisabled = false;
		if (!always && (hapticsDisabled)) {
			return false;
		}
		mVibrator.vibrate(80);
		return true;
	}

	@Override
	public void onResume() {
		// ##BugID:107560:overridePendingTransition instead of background
		// animator
		overridePendingTransition(0, 0);
		/* YUNOS_END */
		super.onResume();
		mForeground = true;
		relLayout.setVisibility(View.INVISIBLE);
		if (DEBUG)
			Log.d(TAG, "onResume");
		initData();
		initAndStartAnimatorSet();
	}

	// ##BugID:107306:use animator to start the animation, reduce invalidate
	// view times
	// and release reources in time
	private void initAndStartAnimatorSet() {
		relLayout.setLayerType(View.LAYER_TYPE_HARDWARE, null);
		animator = ObjectAnimator.ofFloat(
				relLayout,
				"translationY",
				getResources().getDimensionPixelSize(
						R.dimen.ICON_TRASLATE_HEIGHT), 0);
		animator.setDuration(Constants.ANIMATOR_SET_TIME);
		animator.setInterpolator(new DecelerateInterpolator());
		relLayout.getViewTreeObserver().addOnPreDrawListener(
				new OnPreDrawListener() {
					@Override
					public boolean onPreDraw() {
						relLayout.getViewTreeObserver()
								.removeOnPreDrawListener(this);
						if (animator == null) {
							return true;
						}
						animator.addListener(new AnimatorListener() {
							@Override
							public void onAnimationStart(Animator arg0) {
								relLayout.setVisibility(View.VISIBLE);
							}

							@Override
							public void onAnimationRepeat(Animator arg0) {
							}

							@Override
							public void onAnimationEnd(Animator arg0) {
								if (relLayout != null) {
									relLayout.setLayerType(
											View.LAYER_TYPE_NONE, null);
								}
							}

							@Override
							public void onAnimationCancel(Animator arg0) {
							}
						});
						if (!animator.isStarted()) {
							animator.start();
						}
						return true;
					}
				});
	}

	private void releaseAnimator() {
		if (animator != null && (animator.isStarted() || animator.isRunning())) {
			animator.cancel();
		}
		animator = null;
	}

	/* YUNOS_END */
	private boolean isRecentTaskFromApp() {
		TaskDescription firstTaskDescription = RecentTasksLoader.getInstance(
				this).getFirstTaskDescription();
		if (firstTaskDescription != null) {
			String pkgName = firstTaskDescription.getPackageName();
			boolean isTaskLocked = !this.getPackageName().equals(pkgName)
					&& !RecentTasksLoader.HOME_PACKAGE_NAME.equals(pkgName);
			return isTaskLocked;
		}
		return false;
	}

	@Override
	public void onBackPressed() {
		dismissAndGoBack();
	}

	/* YUNOS_BEGIN */
	// ##module(RecentTasks)
	// ##date: 2014/3/26
	// ##author: yulong.hyl@alibaba-inc.com
	// ##BugID:104831:dismissAndGoBack when we touch screen above recentspanel
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		super.onTouchEvent(event);
		if (event.getAction() == MotionEvent.ACTION_DOWN) {
			dismissAndGoBack();
		}
		return true;
	}

	/* YUNOS END */

	public void dismissAndGoHome() {
		if (mRecentsPanel != null) {
			Intent homeIntent = new Intent(Intent.ACTION_MAIN, null);
			homeIntent.addCategory(Intent.CATEGORY_HOME);
			homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
					| Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
			startActivity(homeIntent);
			// startActivityAsUser(homeIntent, new
			// UserHandle(UserHandle.USER_CURRENT));
			mRecentsPanel.show(false);
		}
	}

	public void dismissAndGoBack() {
		if ((mRecentsPanel != null) && (mRecentsPanel.isShowing())) {
			final ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
			/* YUNOS_BEGIN */
			// ##module(RecentTasks)
			// ##date: 2014/3/22
			// ##author: yulong.hyl@alibaba-inc.com
			// ##BugID:102893:onBackPress to return the first task but
			// not homeshell and ourselves
			TaskDescription firstTaskDescription = RecentTasksLoader
					.getInstance(this).getFirstTaskDescription();
			if (firstTaskDescription != null) {
				String pkgName = firstTaskDescription.getPackageName();
				boolean isTaskLocked = !this.getPackageName().equals(pkgName)
						&& !RecentTasksLoader.HOME_PACKAGE_NAME.equals(pkgName);
				if (isTaskLocked) {
					mRecentsPanel.show(false);
					if (firstTaskDescription.taskId >= 0) {
						// SecureManager secure = SecureManager.get(this);
						// if (secure != null) {
						// boolean isBlocked =
						// secure.filterSyncTask(firstTaskDescription.taskId,
						// firstTaskDescription.intent);
						// if (isBlocked) {
						// this.moveTaskToBack(true);
						// } else {
						// am.moveTaskToFront(firstTaskDescription.taskId,
						// ActivityManager.MOVE_TASK_WITH_HOME);
						// }
						// }
					} else {
						Intent intent = firstTaskDescription.intent;
						intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY
								| Intent.FLAG_ACTIVITY_TASK_ON_HOME
								| Intent.FLAG_ACTIVITY_NEW_TASK);
						startActivity(intent);
					}
					return;
				}
			}
			/* YUNOS_END */
		}
		moveToBack();
	}

	public void updateDeleteAppLable(boolean opacity) {
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		mVibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
		performFeedback(false);
		WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
		mDisplay = wm.getDefaultDisplay();
		mDisplay.getMetrics(mDisplayMetrics);
		mNavSize = 0;
		setContentView(R.layout.status_bar_recent_panel);
		mRecentsPanel = (RecentsPanelView) findViewById(R.id.recents_root);
		mRootLayout = (LinearLayout) (this
				.findViewById(R.id.recent_activity_root));
		relLayout = (RelativeLayout) this
				.findViewById(R.id.recent_task_relative);
		mBashboard = findViewById(R.id.recents_bashboard);
		mRecentsRootViewLayout = (FrameLayout) this
				.findViewById(R.id.recents_root_view);
		mBashboard.setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				return true;
			}
		});
		final RecentTasksLoader recentTasksLoader = RecentTasksLoader
				.getInstance(this);
		recentTasksLoader.setRecentsPanel(mRecentsPanel, mRecentsPanel);
		if (savedInstanceState == null
				|| savedInstanceState.getBoolean(WAS_SHOWING)) {
			handleIntent(getIntent(), (savedInstanceState == null));
		}
		mIntentFilter = new IntentFilter();
		mIntentFilter.addAction(CLOSE_RECENTS_INTENT);
		mIntentFilter.addAction(WINDOW_ANIMATION_START_INTENT);
		registerReceiver(mIntentReceiver, mIntentFilter);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putBoolean(WAS_SHOWING, mRecentsPanel.isShowing());
	}

	@Override
	protected void onDestroy() {
		RecentTasksLoader.getInstance(this)
				.setRecentsPanel(null, mRecentsPanel);
		unregisterReceiver(mIntentReceiver);
		super.onDestroy();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		handleIntent(intent, true);
	}

	private void handleIntent(Intent intent,
			boolean checkWaitingForAnimationParam) {
		super.onNewIntent(intent);
		// if (TOGGLE_RECENTS_INTENT.equals(intent.getAction())) {
		if ((mRecentsPanel != null) && (mRecentsPanel.isShowing())) {
			dismissAndGoBack();
		} else {
			performFeedback(false);
			final RecentTasksLoader recentTasksLoader = RecentTasksLoader
					.getInstance(this);
			boolean waitingForWindowAnimation = checkWaitingForAnimationParam
					&& intent.getBooleanExtra(
							WAITING_FOR_WINDOW_ANIMATION_PARAM, false);
			if (DEBUG) {
				int count = (recentTasksLoader.getLoadedTasks() == null) ? 0
						: recentTasksLoader.getLoadedTasks().size();
				if (DEBUG) {
					Log.d(TAG, "handleIntent show task count " + count);
				}
			}
			mRecentsPanel.show(true, recentTasksLoader.getLoadedTasks(),
					recentTasksLoader.isFirstScreenful(),
					waitingForWindowAnimation);
		}
		// }
	}

	public boolean isForeground() {
		return mForeground;
	}

	public boolean isActivityShowing() {
		return mShowing;
	}
}
