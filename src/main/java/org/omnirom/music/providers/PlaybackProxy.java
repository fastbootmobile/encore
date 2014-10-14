package org.omnirom.music.providers;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Song;
import org.omnirom.music.service.IPlaybackService;
import org.omnirom.music.service.PlaybackService;

/**
 * Created by Guigui on 13/10/2014.
 */
public class PlaybackProxy {
    private static final String TAG = "PlaybackProxy";

    private static HandlerThread mThread;
    private static Handler mHandler;

    private static final int MSG_PLAY           = 1;
    private static final int MSG_PAUSE          = 2;
    private static final int MSG_STOP           = 3;
    private static final int MSG_PLAY_SONG      = 4;
    private static final int MSG_PLAY_AT_INDEX  = 5;
    private static final int MSG_CLEAR_QUEUE    = 6;
    private static final int MSG_QUEUE_SONG     = 7;
    private static final int MSG_QUEUE_ALBUM    = 8;

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

                        case MSG_PLAY_AT_INDEX:
                            getPlayback().playAtQueueIndex(msg.arg1);
                            break;

                        case MSG_CLEAR_QUEUE:
                            getPlayback().getCurrentPlaybackQueue().clear();
                            break;

                        case MSG_QUEUE_SONG:
                            getPlayback().queueSong((Song) msg.obj, msg.arg1 == 1);
                            break;

                        case MSG_QUEUE_ALBUM:
                            getPlayback().queueAlbum((Album) msg.obj, msg.arg1 == 1);
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

    public static void playAtIndex(int index) {
        Message.obtain(mHandler, MSG_PLAY_AT_INDEX, index, 0).sendToTarget();
    }

    public static void pause() {
        Message.obtain(mHandler, MSG_PAUSE).sendToTarget();
    }

    public static void stop() {
        Message.obtain(mHandler, MSG_STOP).sendToTarget();
    }

    public static void clearQueue() {
        Message.obtain(mHandler, MSG_CLEAR_QUEUE).sendToTarget();
    }

    public static void queueSong(Song song, boolean top) {
        Message.obtain(mHandler, MSG_QUEUE_SONG, top ? 1 : 0, 0, song).sendToTarget();
    }

    public static void queueAlbum(Album album, boolean top) {
        Message.obtain(mHandler, MSG_QUEUE_ALBUM, top ? 1 : 0, 0, album).sendToTarget();
    }

    public static int getState() {
        try {
            return getPlayback().getState();
        } catch (RemoteException e) {
            return PlaybackService.STATE_STOPPED;
        }
    }
}
