package org.omnirom.music.providers;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Song;
import org.omnirom.music.service.IPlaybackService;

/**
 * Created by Guigui on 13/10/2014.
 */
public class PlaybackProxy {
    private static final String TAG = "PlaybackProxy";

    private static HandlerThread mThread;
    private static Handler mHandler;

    private static final int MSG_PLAY       = 1;
    private static final int MSG_PAUSE      = 2;
    private static final int MSG_STOP       = 3;
    private static final int MSG_PLAY_SONG  = 4;

    static {
        mThread = new HandlerThread("PlaybackProxy");
        mThread.start();
        mHandler = new Handler(mThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                try {
                    switch (msg.what) {
                        case MSG_PLAY:
                            getPlayback().play();
                            break;

                        case MSG_PAUSE:
                            getPlayback().pause();
                            break;

                        case MSG_STOP:
                            getPlayback().stop();
                            break;

                        case MSG_PLAY_SONG:
                            getPlayback().playSong((Song) msg.obj);
                            break;
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Cannot run remote method", e);
                }
            }
        };
    }

    private static IPlaybackService getPlayback() {
        return PluginsLookup.getDefault().getPlaybackService();
    }

    public static void play() {
        Message.obtain(mHandler, MSG_PLAY).sendToTarget();
    }

    public static void playSong(Song song) {
        Message.obtain(mHandler, MSG_PLAY_SONG, song).sendToTarget();
    }

    public static void pause() {
        Message.obtain(mHandler, MSG_PAUSE).sendToTarget();
    }

    public static void stop() {
        Message.obtain(mHandler, MSG_STOP).sendToTarget();
    }


}
