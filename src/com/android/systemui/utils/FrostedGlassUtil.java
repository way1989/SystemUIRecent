
package com.android.systemui.utils;

import android.graphics.Bitmap;

public class FrostedGlassUtil {
    private volatile static FrostedGlassUtil mFrostedGlassUtil;
    /*YUNOS BEGIN*/
    //##module(SystemUI)
    //##date:2013/3/29 ##author:sunchen.sc@alibaba-inc.com##BugID:104943
    //Invoke jni with thread safe
    public static FrostedGlassUtil getInstance() {
        if (mFrostedGlassUtil == null) {
            synchronized (FrostedGlassUtil.class) {
                if (mFrostedGlassUtil == null) {
                    mFrostedGlassUtil = new FrostedGlassUtil();
                }
            }
        }
        return mFrostedGlassUtil;
    }
    /*YUNOS END*/

    public native void clearColor(Bitmap image, int color);

    public native void imageBlur(Bitmap srcBitmap, Bitmap dstBitmap);

    public native void boxBlur(Bitmap srcBitmap, int radius);

    public native void stackBlur(Bitmap srcBitmap, int radius);

    public native void oilPaint(Bitmap srcBitmap, int radius);

    public native void colorWaterPaint(Bitmap srcBitmap, int radius);
    /*YUNOS BEGIN*/
    //##module(SystemUI)
    //##date:2013/3/29 ##author:sunchen.sc@alibaba-inc.com##BugID:104943
    //Invoke jni with thread safe
    public synchronized Bitmap convertToBlur(Bitmap bmp, int radius) {
        long beginBlur = System.currentTimeMillis();
        stackBlur(bmp, radius);
        return bmp;
    }

    static {
        // load frosted glass lib
        System.loadLibrary("frostedGlass");
    }
    /*YUNOS END*/
}
