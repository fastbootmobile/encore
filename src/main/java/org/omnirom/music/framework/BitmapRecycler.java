package org.omnirom.music.framework;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import java.util.List;

public class BitmapRecycler {

    private static BitmapRecycler INSTANCE = new BitmapRecycler();
    private static final int MSG_RECYCLE = 1;

    private List<Bitmap> mBitmaps;
    private Handler mHandler;
    private HandlerThread mHandlerThread;

    public static BitmapRecycler getInstance() {
        return INSTANCE;
    }

    private BitmapRecycler() {
        mHandlerThread = new HandlerThread("BitmapRecycler");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MSG_RECYCLE) {
                    Bitmap bmp = ((Bitmap) msg.obj);
                    if (bmp.getByteCount() > 0) {
                        ((Bitmap) msg.obj).recycle();
                    }
                }
            }
        };
    }

    public void recycle(Bitmap bitmap) {
        if (bitmap != null) {
            Message msg = mHandler.obtainMessage(MSG_RECYCLE, bitmap);
            mHandler.sendMessageDelayed(msg, 25000);
        }
    }
}
