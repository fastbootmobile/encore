/*
 * Copyright (C) 2014 Fastboot Mobile, LLC.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program;
 * if not, see <http://www.gnu.org/licenses>.
 */

package com.fastbootmobile.encore.framework;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import com.fastbootmobile.encore.model.Album;
import com.fastbootmobile.encore.model.Playlist;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.ProviderIdentifier;
import com.fastbootmobile.encore.service.IPlaybackCallback;
import com.fastbootmobile.encore.service.IPlaybackService;
import com.fastbootmobile.encore.service.PlaybackService;

import java.util.ArrayList;
import java.util.List;

/**
 * Proxy class for Playback service calls
 */
public class PlaybackProxy {
    private static final String TAG = "PlaybackProxy";

    private static Handler sHandler;
    private static final List<IPlaybackCallback> sPendingCallbacks = new ArrayList<>();

    private static final int MSG_PLAY               = 1;
    private static final int MSG_PAUSE              = 2;
    private static final int MSG_STOP               = 3;
    private static final int MSG_PLAY_SONG          = 4;
    private static final int MSG_PLAY_AT_INDEX      = 5;
    private static final int MSG_CLEAR_QUEUE        = 6;
    private static final int MSG_QUEUE_SONG         = 7;
    private static final int MSG_QUEUE_ALBUM        = 8;
    private static final int MSG_ADD_CALLBACK       = 9;
    private static final int MSG_REMOVE_CALLBACK    = 10;
    private static final int MSG_PLAY_ALBUM         = 11;
    private static final int MSG_SEEK               = 12;
    private static final int MSG_SET_DSP_CHAIN      = 13;
    private static final int MSG_NEXT               = 14;
    private static final int MSG_PREVIOUS           = 15;
    private static final int MSG_SET_REPEAT_MODE    = 16;
    private static final int MSG_PLAY_PLAYLIST      = 17;
    private static final int MSG_QUEUE_PLAYLIST     = 18;
    private static final int MSG_SET_SHUFFLE_MODE   = 19;
    private static final int MSG_PLAY_NEXT          = 20;
    private static final int MSG_SLEEP_TIMER        = 21;

    private static class PlaybackProxyHandler extends Handler {
        public PlaybackProxyHandler(Looper looper) {
            super(looper);
        }

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
                        getPlayback().clearPlaybackQueue();
                        break;

                    case MSG_QUEUE_SONG:
                        getPlayback().queueSong((Song) msg.obj, msg.arg1 == 1);
                        break;

                    case MSG_QUEUE_ALBUM:
                        getPlayback().queueAlbum((Album) msg.obj, msg.arg1 == 1);
                        break;

                    case MSG_PLAY_ALBUM:
                        getPlayback().playAlbum((Album) msg.obj);
                        break;

                    case MSG_PLAY_NEXT:
                        getPlayback().playNext((Song) msg.obj);
                        break;

                    case MSG_SEEK:
                        getPlayback().seek((Long) msg.obj);
                        break;

                    case MSG_SET_DSP_CHAIN:
                        getPlayback().setDSPChain((List<ProviderIdentifier>) msg.obj);
                        break;

                    case MSG_NEXT:
                        getPlayback().next();
                        break;

                    case MSG_PREVIOUS:
                        getPlayback().previous();
                        break;

                    case MSG_SET_REPEAT_MODE:
                        getPlayback().setRepeatMode((Boolean) msg.obj);
                        break;

                    case MSG_SET_SHUFFLE_MODE:
                        getPlayback().setShuffleMode((Boolean) msg.obj);
                        break;

                    case MSG_PLAY_PLAYLIST:
                        getPlayback().playPlaylist((Playlist) msg.obj);
                        break;

                    case MSG_QUEUE_PLAYLIST:
                        getPlayback().queuePlaylist((Playlist) msg.obj, msg.arg1 == 1);
                        break;

                    case MSG_ADD_CALLBACK:
                        try {
                            getPlayback().addCallback((IPlaybackCallback) msg.obj);
                        } catch (RemoteException e) {
                            // Service likely isn't bound: queue callback so that it's added when
                            // the service is ready.
                            synchronized (sPendingCallbacks) {
                                sPendingCallbacks.add((IPlaybackCallback) msg.obj);
                            }
                        }
                        break;

                    case MSG_REMOVE_CALLBACK:
                        IPlaybackService service = getPlayback(false);
                        if (service != null) {
                            service.removeCallback((IPlaybackCallback) msg.obj);
                        }
                        break;

                    case MSG_SLEEP_TIMER:
                        getPlayback().setSleepTimer((Long) msg.obj);
                        break;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Cannot run remote method", e);
            }
        }
    }

    static {
        HandlerThread thread = new HandlerThread("PlaybackProxy");
        thread.start();
        sHandler = new PlaybackProxyHandler(thread.getLooper());
    }

    private static IPlaybackService getPlayback(boolean connectIfNull) throws RemoteException {
        IPlaybackService service = PluginsLookup.getDefault().getPlaybackService(connectIfNull);
        if (service == null && connectIfNull) {
            throw new RemoteException("Playback service is null");
        }

        return service;
    }

    private static IPlaybackService getPlayback() throws RemoteException {
        return getPlayback(true);
    }

    static void notifyPlaybackConnected() {
        synchronized (sPendingCallbacks) {
            for (IPlaybackCallback callback : sPendingCallbacks) {
                addCallback(callback);
            }
            sPendingCallbacks.clear();
        }
    }

    public static boolean isServiceConnected() {
        try {
            return (getPlayback(false) != null);
        } catch (RemoteException e) {
            return false;
        }
    }

    public static void play() {
        Message.obtain(sHandler, MSG_PLAY).sendToTarget();
    }

    public static void playSong(Song song) {
        Message.obtain(sHandler, MSG_PLAY_SONG, song).sendToTarget();
    }

    public static void playAlbum(Album album) {
        Message.obtain(sHandler, MSG_PLAY_ALBUM, album).sendToTarget();
    }

    public static void playAtIndex(int index) {
        Message.obtain(sHandler, MSG_PLAY_AT_INDEX, index, 0).sendToTarget();
    }

    public static void pause() {
        Message.obtain(sHandler, MSG_PAUSE).sendToTarget();
    }

    public static void stop() {
        Message.obtain(sHandler, MSG_STOP).sendToTarget();
    }

    public static void clearQueue() {
        Message.obtain(sHandler, MSG_CLEAR_QUEUE).sendToTarget();
    }

    public static void queueSong(Song song, boolean top) {
        Message.obtain(sHandler, MSG_QUEUE_SONG, top ? 1 : 0, 0, song).sendToTarget();
    }

    public static void queueAlbum(Album album, boolean top) {
        Message.obtain(sHandler, MSG_QUEUE_ALBUM, top ? 1 : 0, 0, album).sendToTarget();
    }

    public static void playNext(Song song) {
        Message.obtain(sHandler, MSG_PLAY_NEXT, song).sendToTarget();
    }

    public static int getState() {
        try {
            return getPlayback().getState();
        } catch (RemoteException e) {
            return PlaybackService.STATE_STOPPED;
        }
    }

    public static Song getCurrentTrack() {
        try {
            return getPlayback().getCurrentTrack();
        } catch (RemoteException e) {
            return null;
        }
    }

    public static void addCallback(IPlaybackCallback callback) {
        Message.obtain(sHandler, MSG_ADD_CALLBACK, callback).sendToTarget();
    }

    public static void removeCallback(IPlaybackCallback callback) {
        Message.obtain(sHandler, MSG_REMOVE_CALLBACK, callback).sendToTarget();
    }

    public static List<Song> getCurrentPlaybackQueue() {
        try {
            return getPlayback().getCurrentPlaybackQueue();
        } catch (RemoteException e) {
            return new ArrayList<>();
        }
    }

    public static int getCurrentTrackPosition() {
        try {
            return getPlayback().getCurrentTrackPosition();
        } catch (RemoteException e) {
            return 0;
        }
    }

    public static int getCurrentTrackLength() {
        try {
            return getPlayback().getCurrentTrackLength();
        } catch (RemoteException e) {
            return 0;
        }
    }

    public static int getCurrentTrackIndex() {
        try {
            return getPlayback().getCurrentTrackIndex();
        } catch (RemoteException e) {
            return 0;
        }
    }

    public static void seek(long timeMs) {
        Message.obtain(sHandler, MSG_SEEK, timeMs).sendToTarget();
    }

    public static void next() {
        Message.obtain(sHandler, MSG_NEXT).sendToTarget();
    }

    public static void previous() {
        Message.obtain(sHandler, MSG_PREVIOUS).sendToTarget();
    }

    public static List<ProviderIdentifier> getDSPChain() {
        try {
            return getPlayback().getDSPChain();
        } catch (RemoteException e) {
            return new ArrayList<>();
        }
    }

    public static void setDSPChain(List<ProviderIdentifier> chain) {
        Message.obtain(sHandler, MSG_SET_DSP_CHAIN, chain).sendToTarget();
    }

    public static boolean isRepeatMode() {
        try {
            return getPlayback().isRepeatMode();
        } catch (RemoteException e) {
            return false;
        }
    }

    public static void setRepeatMode(boolean repeat) {
        Message.obtain(sHandler, MSG_SET_REPEAT_MODE, repeat).sendToTarget();
    }

    public static boolean isShuffleMode() {
        try {
            return getPlayback().isShuffleMode();
        } catch (RemoteException e) {
            return false;
        }
    }

    public static void setShuffleMode(boolean shuffle) {
        Message.obtain(sHandler, MSG_SET_SHUFFLE_MODE, shuffle).sendToTarget();
    }

    public static void playPlaylist(Playlist p) {
        Message.obtain(sHandler, MSG_PLAY_PLAYLIST, p).sendToTarget();
    }

    public static void queuePlaylist(Playlist p, boolean top) {
        Message.obtain(sHandler, MSG_QUEUE_PLAYLIST, top ? 1 : 0, 0, p).sendToTarget();
    }

    public static void setSleepTimer(long uptime) {
        Message.obtain(sHandler, MSG_SLEEP_TIMER, uptime).sendToTarget();
    }

    public static long getSleepTimerEndTime() {
        try {
            return getPlayback().getSleepTimerEndTime();
        } catch (RemoteException e) {
            return -1;
        }
    }
}
