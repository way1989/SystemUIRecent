/*
 * Copyright (C) 2013 The YunOS Project
 * MemoryUtil.java
 * Created on: 2013-11-18
 * Author: xiaofen.qinxf@aliyun-inc.com
 */

package com.android.systemui.utils;

import java.util.List;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Debug;

@SuppressLint("NewApi")
public class MemoryUtil {
	private static final String MEMORY_SHARED_PREFERENCES = "recentTaskLockStatus";
	private static final String TAG = "MemoryUtil";
	private static SharedPreferences sSharedPreference = null;
	public static final String USAGE_PERCENT = "number_usage_percent";
	public static final String AVAILABLE_MEMORY = "available_memory";
	public static final String TOTAL_MEMORY = "total_memory";

	/**
	 * Get the memory occupy by the package
	 * 
	 * @param context
	 * @param packageName
	 *            , MUST NOT be null
	 * @return kB unit
	 */
	public static long getPackageAvailabelMemory(Context ctx, String packageName) {
		ActivityManager am = (ActivityManager) ctx
				.getSystemService(Context.ACTIVITY_SERVICE);
		int[] pids = new int[1];
		List<RunningAppProcessInfo> apps = am.getRunningAppProcesses();
		for (int index = 0; index < apps.size(); index++) {
			String[] pkgList = apps.get(index).pkgList;
			for (int k = 0; k < pkgList.length; k++) {
				if (packageName.equals(pkgList[k])) {
					pids[0] = apps.get(index).pid;
					Debug.MemoryInfo[] memoryInfos = am
							.getProcessMemoryInfo(pids);
					int privateDirty = memoryInfos[0].getTotalPrivateDirty();
					int sharedDirty = memoryInfos[0].getTotalSharedDirty();
					return privateDirty + sharedDirty;
				}
			}
		}
		return 0;
	}

	/**
	 * Query the lock statu of task
	 * 
	 * @param ctx
	 * @param packageName
	 * @return true if locked, otherwise false
	 */
	public static boolean isTaskLocked(Context ctx, String packageName) {
		// ContentResolver resolver = ctx.getContentResolver();
		// Cursor cursor =
		// resolver.query(Constants.CONTENT_URI_ACC_USER_WHITE_APP, null,
		// Constants.PACKAGE_NAME + "=?", new String[] {
		// packageName
		// }, null);
		// boolean isTaskLocked = (cursor != null && cursor.getCount() > 0);
		// if (cursor != null) {
		// cursor.close();
		// }
		// return isTaskLocked;

		SharedPreferences sp = getSharedPreference(ctx);
		return sp.getBoolean(packageName, false);

	}

	/**
	 * Lock the task so that it can not be killed by accelerating key
	 * 
	 * @param task
	 *            the task info
	 * @return true when success, otherwise false.
	 */
	public static boolean lockTask(Context ctx, String packageName) {
		return changeTaskLockStatus(ctx, packageName, true);
	}

	/**
	 * 
	 * @param ctx
	 * @param packageName
	 * @return
	 */
	public static boolean unlockTask(Context ctx, String packageName) {
		return changeTaskLockStatus(ctx, packageName, false);
	}

	private static boolean changeTaskLockStatus(Context ctx,
			String packageName, boolean lock) {
		// ContentResolver resolver = ctx.getContentResolver();
		// if (lock) {
		// ContentValues values = new ContentValues();
		// values.put(Constants.PACKAGE_NAME, packageName);
		// resolver.insert(Constants.CONTENT_URI_ACC_USER_WHITE_APP, values);
		// } else {
		// resolver.delete(Constants.CONTENT_URI_ACC_USER_WHITE_APP,
		// Constants.PACKAGE_NAME + "=?", new String[]{packageName});
		// }
		SharedPreferences sp = ctx.getSharedPreferences(
				MEMORY_SHARED_PREFERENCES, Context.MODE_PRIVATE);
		Editor edit = sp.edit();
		edit.putBoolean(packageName, lock);
		edit.apply();
		return true;
	}

	public static long getMemoryPercent(final Context ctx) {
		long avaialable = getSystemAvaialbeMemorySize(ctx);
		long total = getTotalMemory(ctx);
		long percent = (avaialable * 100) / total;
		return 100 - percent;
	}

	public static long getSystemAvaialbeMemorySize(Context ctx) {
		MemoryInfo memoryInfo = new MemoryInfo();
		ActivityManager am = null;
		am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
		am.getMemoryInfo(memoryInfo);
		long memSize = memoryInfo.availMem;
		return memSize;
	}

	public static long getTotalMemory(Context ctx) {
		MemoryInfo memoryInfo = new MemoryInfo();
		ActivityManager am = null;
		am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
		am.getMemoryInfo(memoryInfo);
		return memoryInfo.totalMem;
	}

	private static SharedPreferences getSharedPreference(Context ctx) {
		if (null == sSharedPreference) {
			sSharedPreference = ctx.getSharedPreferences(
					MEMORY_SHARED_PREFERENCES, Context.MODE_PRIVATE);
		}
		return sSharedPreference;
	}
}
