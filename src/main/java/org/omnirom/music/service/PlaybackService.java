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

package org.omnirom.music.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import com.echonest.api.v4.EchoNestException;

import org.omnirom.music.api.echonest.AutoMixManager;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.framework.RefCountedBitmap;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.SearchResult;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.AbstractProviderConnection;
import org.omnirom.music.providers.ILocalCallback;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.IProviderCallback;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderConnection;
import org.omnirom.music.providers.ProviderIdentifier;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Service handling the playback of the audio and the play notification
 */
public class PlaybackService extends Service
        implements PluginsLookup.ConnectionListener, ILocalCallback,
        AudioManager.OnAudioFocusChangeListener {

    private static final String TAG = "PlaybackService";

    public static final int STATE_STOPPED   = 0;
    public static final int STATE_PLAYING   = 1;
    public static final int STATE_PAUSED    = 2;
    public static final int STATE_BUFFERING = 3;
    public static final int STATE_PAUSING   = 4;

    private static final String SERVICE_SHARED_PREFS = "PlaybackServicePrefs";
    private static final String PREF_KEY_REPEAT = "repeatMode";

    public static final String ACTION_COMMAND = "command";
    public static final String EXTRA_COMMAND_NAME = "command_name";
    public static final int COMMAND_NEXT = 1;
    public static final int COMMAND_PREVIOUS = 2;
    public static final int COMMAND_PAUSE = 3;
    public static final int COMMAND_STOP = 4;

    private Runnable mNotifyQueueChangedRunnable = new Runnable() {
        @Override
        public void run() {
            mNotification.setHasNext(mPlaybackQueue.size() > 1);

            for (IPlaybackCallback cb : mCallbacks) {
                try {
                    cb.onPlaybackQueueChanged();
                } catch (RemoteException e) {
                    Log.e(TAG, "Cannot notify playback queue changed", e);
                }
            }
        }
    };

    private BroadcastReceiver mAudioNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                try {
                    mBinder.stop();
                } catch (RemoteException e) {
                    Log.e(TAG, "Cannot stop playback during AUDIO_BECOMING_NOISY", e);
                }
            }
        }
    };

    private Handler mHandler;
    private int mNumberBound = 0;
    private NativeAudioSink mNativeSink;
    private NativeHub mNativeHub;
    private DSPProcessor mDSPProcessor;
    private PlaybackQueue mPlaybackQueue;
    private List<IPlaybackCallback> mCallbacks;
    private ServiceNotification mNotification;
    private int mCurrentTrack = -1;
    private long mCurrentTrackStartTime;
    private long mPauseLastTick;
    private int mState = STATE_STOPPED;
    private boolean mIsResuming;
    private boolean mIsStopping;
    private boolean mCurrentTrackWaitLoading;
    private ProviderIdentifier mCurrentPlayingProvider;
    private boolean mHasAudioFocus;
    private boolean mRepeatMode;
    private Prefetcher mPrefetcher;
    private RemoteMetadataManager mRemoteMetadata;
    private PowerManager.WakeLock mWakeLock;
    private boolean mIsForeground;

    private Runnable mStartPlaybackImplRunnable = new Runnable() {
        @Override
        public void run() {
            startPlayingQueue();
        }
    };

    private Runnable mStartPlaybackRunnable = new Runnable() {
        @Override
        public void run() {
            new Thread(mStartPlaybackImplRunnable).start();
        }
    };

    public PlaybackService() {
        mPlaybackQueue = new PlaybackQueue();
        mCallbacks = new ArrayList<IPlaybackCallback>();
        mPrefetcher = new Prefetcher(this);
        mRemoteMetadata = new RemoteMetadataManager(this);
    }

    /**
     * Called when the service is created
     */
    @Override
    public void onCreate() {
        super.onCreate();
        mHandler = new Handler();

        ProviderAggregator.getDefault().addUpdateCallback(this);

        // Native playback initialization
        mNativeHub = new NativeHub();
        mNativeSink = new NativeAudioSink();
        mNativeHub.setSinkPointer(mNativeSink.getPlayer().getHandle());

        mDSPProcessor = new DSPProcessor(this);
        mDSPProcessor.restoreChain(this);

        // Plugins initialization
        PluginsLookup.getDefault().initialize(getApplicationContext());
        PluginsLookup.getDefault().registerProviderListener(this);

        List<ProviderConnection> connections = PluginsLookup.getDefault().getAvailableProviders();
        for (ProviderConnection conn : connections) {
            if (conn.getBinder() != null) {
                assignProviderAudioSocket(conn);
            } else {
                Log.w(TAG, "Cannot assign audio socket to " + conn.getIdentifier() + ", binder is null");
            }
        }

        // Setup
        mIsStopping = false;

        // Bind to all provider
        List<ProviderConnection> providers = PluginsLookup.getDefault().getAvailableProviders();
        for (ProviderConnection pc : providers) {
            try {
                if (pc.getBinder() != null) {
                    Log.e(TAG, "RegisterCallback: Setup");
                    pc.getBinder().registerCallback(mProviderCallback);
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Cannot register callback", e);
            }
        }

        // Register AutoMix manager
        mCallbacks.add(AutoMixManager.getDefault());

        // Setup notification system
        mNotification = new ServiceNotification(this);
        mNotification.setOnNotificationChangedListener(new ServiceNotification.NotificationChangedListener() {
            @Override
            public void onNotificationChanged(ServiceNotification notification) {
                NotificationManagerCompat nmc = NotificationManagerCompat.from(PlaybackService.this);
                if (mIsForeground) {
                    notification.notify(nmc);
                } else {
                    notification.notify(PlaybackService.this);
                }

                RefCountedBitmap albumArt = notification.getAlbumArt();
                albumArt.acquire();
                mRemoteMetadata.setAlbumArt(albumArt.get());
                albumArt.release();
            }
        });

        // Setup lockscreen remote controls
        mRemoteMetadata.setup();

        // Setup playback wakelock (but don't acquire it yet)
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OmniMusicPlayback");

        // Restore preferences
        SharedPreferences prefs = getSharedPreferences(SERVICE_SHARED_PREFS, MODE_PRIVATE);
        mRepeatMode = prefs.getBoolean(PREF_KEY_REPEAT, false);
    }

    /**
     * Called when the service is destroyed
     */
    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy()");

        PluginsLookup.getDefault().removeProviderListener(this);
        ProviderAggregator.getDefault().removeUpdateCallback(this);
        mRemoteMetadata.release();

        if (mHasAudioFocus) {
            abandonAudioFocus();
        }

        mIsForeground = false;

        // Remove audio hosts from providers
        Log.e(TAG, "DESTROY -- UNREGISTERING CALLBACKS");
        List<ProviderConnection> connections = PluginsLookup.getDefault().getAvailableProviders();
        for (ProviderConnection pc : connections) {
            IMusicProvider provider = pc.getBinder();
            try {
                if (provider != null) {
                    provider.unregisterCallback(mProviderCallback);
                } else {
                    Log.e(TAG, "Cannot unregister callback: provider binder is null");
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Cannot unregister callback", e);
            }
        }

        PluginsLookup.getDefault().tearDown(mNativeHub);

        // Release all currently playing songs
        mPlaybackQueue.clear();
        mCurrentTrack = -1;

        // Shutdown DSP chain

        super.onDestroy();
    }

    /**
     * Called when the main app is calling startService on this service.
     *
     * @param intent  The intent attached, not used
     * @param flags   The flags, not used
     * @param startId The start id, not used
     * @return a status integer
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mIsStopping = false;
        if (intent != null && intent.getAction() != null
                && intent.getAction().equals(ACTION_COMMAND)) {
            switch (intent.getIntExtra(EXTRA_COMMAND_NAME, -1)) {
                case COMMAND_NEXT:
                    nextImpl();
                    break;

                case COMMAND_PREVIOUS:
                    previousImpl();
                    break;

                case COMMAND_PAUSE:
                    if (mState == STATE_STOPPED || mState == STATE_PAUSED || mState == STATE_PAUSING) {
                        playImpl();
                    } else {
                        pauseImpl();
                    }
                    break;

                case COMMAND_STOP:
                    stopImpl();
                    break;
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * Called when the main app binds on this service
     *
     * @param intent The intent attached, not used
     * @return The binder, in our case an IPlaybackService
     */
    @Override
    public IBinder onBind(Intent intent) {
        mNumberBound++;
        Log.i(TAG, "Client bound service (" + mNumberBound + ")");
        return mBinder;
    }

    /**
     * Called when an app unbind from this service
     *
     * @param intent The intent attached, not used
     * @return true
     */
    @Override
    public boolean onUnbind(Intent intent) {
        mNumberBound--;
        Log.i(TAG, "Client unbound service (" + mNumberBound + " left)");
        return super.onUnbind(intent);
    }

    /**
     * ProviderConnection listener: Called when a provider is bound
     *
     * @param connection The provider connection
     */
    @Override
    public void onServiceConnected(AbstractProviderConnection connection) {
        Log.i(TAG, "Service connected: " + connection.getIdentifier());
        assignProviderAudioSocket(connection);

        if (connection instanceof ProviderConnection) {
            try {
                Log.e(TAG, "RegisterCallback: service connected");
                IMusicProvider binder = ((ProviderConnection) connection).getBinder();

                if (binder != null) {
                    binder.registerCallback(mProviderCallback);
                } else {
                    Log.e(TAG, "Cannot register callback in onServiceConnected, binder is null");
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Cannot register callback on connected service");
            }
        }
    }

    /**
     * ProviderConnection listener: Called when a provider has disconnected
     *
     * @param connection The provider connected
     */
    @Override
    public void onServiceDisconnected(AbstractProviderConnection connection) {
        // TODO: Release the audio socket, update the playback status if we were playing from
        // this provider.
        Log.w(TAG, "Provider disconnected, rebinding before it's too late");
        connection.bindService();
    }

    public NativeHub getNativeHub() {
        return mNativeHub;
    }

    /**
     * Assigns the provided provider an audio client socket
     *
     * @param connection The provider
     */
    public String assignProviderAudioSocket(AbstractProviderConnection connection) {
        String socket = connection.getAudioSocketName();

        if (socket == null) {
            // Assign the providers an audio socket
            socket = "org.omnirom.music.AUDIO_SOCKET_" + connection.getProviderName()
                    + "_" + System.currentTimeMillis();
            if (connection.createAudioSocket(mNativeHub, socket)) {
                Log.i(TAG, "Provider connected and socket set: " + connection.getProviderName());
            } else {
                Log.w(TAG, "Error while creating audio socket for " + connection.getProviderName());
            }
        }

        return socket;
    }

    /**
     * Request the audio focus and registers the remote media controller
     */
    private synchronized void requestAudioFocus() {
        if (!mHasAudioFocus) {
            final AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

            // Request audio focus for music playback
            int result = am.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);

            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                mHasAudioFocus = true;
                am.registerMediaButtonEventReceiver(RemoteControlReceiver.getComponentName(this));

                // Notify the remote metadata that we're getting active
                mRemoteMetadata.setActive(true);

                // Register AUDIO_BECOMING_NOISY to stop playback when earbuds are pulled
                registerReceiver(mAudioNoisyReceiver,
                        new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));

                // Add a WakeLock to avoid CPU going to sleep while music is playing
                mWakeLock.acquire();

                // Request a global effects session ID
                final Intent intent = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
                intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);
                intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
                sendBroadcast(intent);
            } else {
                Log.e(TAG, "Audio focus request denied: " + result);
            }
        }
    }

    /**
     * Release the audio focus and unregisters the media controls
     */
    private synchronized void abandonAudioFocus() {
        if (mHasAudioFocus) {
            final AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            am.abandonAudioFocus(this);
            unregisterReceiver(mAudioNoisyReceiver);
            mHasAudioFocus = false;
            mWakeLock.release();

            final Intent audioEffectsIntent = new Intent(
                    AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
            audioEffectsIntent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);
            audioEffectsIntent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
            sendBroadcast(audioEffectsIntent);
        }
    }

    /**
     * Starts playing the current playback queue
     */
    private void startPlayingQueue() {
        if (mPlaybackQueue.size() > 0) {
            // mCurrentTrack in this context is the track that is going to be played
            if (mCurrentTrack < 0) {
                mCurrentTrack = 0;
            }

            final Song next = mPlaybackQueue.get(mCurrentTrack);
            final ProviderIdentifier providerId = next.getProvider();

            if (mCurrentPlayingProvider != null && !next.getProvider().equals(mCurrentPlayingProvider)) {
                // Pause the previously playing track to avoid overlap if it's not the same provider
                ProviderConnection prevConn = PluginsLookup.getDefault().getProvider(mCurrentPlayingProvider);
                if (prevConn != null) {
                    IMusicProvider prevProv = prevConn.getBinder();
                    if (prevProv != null) {
                        try {
                            prevProv.pause();
                        } catch (RemoteException e) {
                            Log.e(TAG, "Unable to pause previously playing provider", e);
                        }
                    }
                }
                mCurrentPlayingProvider = null;
            }

            if (providerId != null) {
                ProviderConnection connection = PluginsLookup.getDefault().getProvider(providerId);
                if (connection != null) {
                    IMusicProvider provider = connection.getBinder();
                    if (provider != null) {
                        mState = STATE_BUFFERING;

                        for (IPlaybackCallback cb : mCallbacks) {
                            try {
                                cb.onSongStarted(true, next);
                            } catch (RemoteException e) {
                                Log.e(TAG, "Cannot call playback callback for song start event", e);
                            }
                        }

                        Log.d(TAG, "onSongStarted: Buffering...");

                        if (!next.isLoaded()) {
                            // Track not loaded yet, delay until track info arrived
                            mCurrentTrackWaitLoading = true;
                            Log.w(TAG, "Track not yet loaded: " + next.getRef() + ", delaying");
                        } else if (!next.isAvailable()) {
                            // Track is not available, skip to the next one
                            nextImpl();
                        } else {
                            mCurrentTrackWaitLoading = false;
                            mCurrentPlayingProvider = providerId;

                            requestAudioFocus();

                            try {
                                provider.playSong(next.getRef());
                            } catch (RemoteException e) {
                                Log.e(TAG, "Unable to play song", e);
                            } catch (NullPointerException e) {
                                Log.e(TAG, "No provider attached", e);
                            } catch (IllegalStateException e) {
                                Log.e(TAG, "Illegal State from provider", e);
                            }

                            // The notification system takes care of calling startForeground
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    mNotification.setCurrentSong(next);
                                }
                            });

                            mRemoteMetadata.setCurrentSong(next);
                            mRemoteMetadata.notifyBuffering();
                        }
                    }
                }
            } else {
                Log.e(TAG, "Cannot play the first song of the queue because the Song's " +
                        "ProviderIdentifier is null!");
            }
        }
    }

    /**
     * Notifies the listeners that the playback queue contents changed. Note that we don't call
     * this when the first item of the queue is removed because of playback moving on to the next
     * track of the queue, but only when a manual/non-logical operation is done.
     */
    private void notifyQueueChanged() {
        mHandler.removeCallbacks(mNotifyQueueChangedRunnable);
        mHandler.post(mNotifyQueueChangedRunnable);
    }

    /**
     * If a song is currently playing, returns the Song in the playback queue at the index
     * corresponding to mCurrentTrack
     * @return A Song if a song is playing, null otherwise
     */
    private Song getCurrentSong() {
        if (mCurrentTrack >= 0 && mPlaybackQueue.size() > mCurrentTrack) {
            return mPlaybackQueue.get(mCurrentTrack);
        } else {
            return null;
        }
    }

    /**
     * Moves to the next track
     */
    void nextImpl() {
        boolean hasNext = mCurrentTrack < mPlaybackQueue.size() - 1;
        if (mPlaybackQueue.size() > 0 && hasNext) {
            mCurrentTrack++;
            mHandler.removeCallbacks(mStartPlaybackRunnable);
            mHandler.post(mStartPlaybackRunnable);

            hasNext = mCurrentTrack < mPlaybackQueue.size() - 1;
            mNotification.setHasNext(hasNext);
        }

        final AutoMixManager mixManager = AutoMixManager.getDefault();
        if (mixManager.getCurrentPlayingBucket() != null) {
            try {
                mixManager.getCurrentPlayingBucket().notifySkip();
            } catch (EchoNestException e) {
                Log.e(TAG, "Cannot notify EchoNest of skip event", e);
            }
        }
    }

    /**
     * Restarts the current song or goes to the previous one
     */
    void previousImpl() {
        boolean shouldRestart = (getCurrentTrackPositionImpl() > 4000 || mCurrentTrack == 0);
        if (shouldRestart) {
            // Restart playback
            seekImpl(0);
        } else {
            // Go to the previous track
            mCurrentTrack--;
            mHandler.removeCallbacks(mStartPlaybackRunnable);
            mHandler.post(mStartPlaybackRunnable);
        }
    }

    /**
     * Pauses the playback
     */
    void pauseImpl() {
        final Song currentSong = getCurrentSong();
        if (currentSong != null) {
            // TODO: Refactor that with a threaded Handler that handles messages
            final ProviderIdentifier identifier = currentSong.getProvider();
            mState = STATE_PAUSING;
            Log.d(TAG, "onSongPaused: Pausing...");

            new Thread() {
                public void run() {
                    IMusicProvider provider = PluginsLookup.getDefault().getProvider(identifier).getBinder();
                    try {
                        if (provider != null) {
                            provider.pause();
                        } else {
                            Log.e(TAG, "Provider is null! Has it crashed?");
                            // TODO: Should we notify pause?
                        }
                    } catch (RemoteException e) {
                        Log.e(TAG, "Cannot pause the track!", e);
                    }
                }
            }.start();
        }
    }

    /**
     * Stops the playback and the service, release the audio focus
     */
    void stopImpl() {
        if ((mState == STATE_PLAYING || mState == STATE_BUFFERING) && mPlaybackQueue.size() > 0
                && mCurrentTrack >= 0) {
            pauseImpl();
        }

        mRemoteMetadata.notifyStopped();
        abandonAudioFocus();

        for (IPlaybackCallback cb : mCallbacks) {
            try {
                cb.onPlaybackPause();
            } catch (RemoteException e) {
                Log.e(TAG, "Cannot call playback callback for playback pause event", e);
            }
        }

        mState = STATE_STOPPED;
        mIsStopping = true;
        stopForeground(true);
        mIsForeground = false;
        stopSelf();
    }

    void playImpl() {
        new Thread() {
            public void run() {
                final Song currentSong = getCurrentSong();
                if (currentSong != null) {
                    IMusicProvider provider = PluginsLookup.getDefault().getProvider(currentSong.getProvider()).getBinder();
                    if (provider != null) {
                        try {
                            provider.resume();
                        } catch (RemoteException e) {
                            Log.e(TAG, "Cannot resume", e);
                        }
                        mIsResuming = true;
                        mState = STATE_BUFFERING;

                        for (IPlaybackCallback cb : mCallbacks) {
                            try {
                                cb.onSongStarted(true, currentSong);
                            } catch (RemoteException e) {
                                Log.e(TAG, "Cannot call playback callback for song start event", e);
                            }
                        }

                        requestAudioFocus();
                        mNotification.setPlayPauseAction(false);
                    } else {
                        Log.e(TAG, "Provider is null! Can't resume.");
                    }
                } else if (mPlaybackQueue.size() > 0) {
                    mHandler.removeCallbacks(mStartPlaybackRunnable);
                    mHandler.post(mStartPlaybackRunnable);
                }
            }
        }.start();
    }

    /**
     * Plays the song in the queue at the specified index
     * @param index The index to play, 0-based
     */
    private void playAtIndexImpl(int index) {
        Log.d(TAG, "Playing track " + (index + 1) + "/" + mPlaybackQueue.size() + " (this=" + this + ")");
        mCurrentTrack = index;
        mHandler.removeCallbacks(mStartPlaybackRunnable);
        mHandler.post(mStartPlaybackRunnable);
    }

    /**
     * @return The reference to the next track in the queue
     */
    public Song getNextTrack() {
        if (mCurrentTrack < mPlaybackQueue.size() - 1) {
            return mPlaybackQueue.get(mCurrentTrack + 1);
        } else {
            // No more tracks
            return null;
        }
    }

    public int getCurrentTrackPositionImpl() {
        if (mState == STATE_PAUSED) {
            // When we are paused, we delay the track start time for as long as we are paused
            // so that the song position actually pauses too. This is more a hack (hey, we
            // mimic the official Spotify app bug!), and ideally we should calculate the elapsed
            // time by counting the frames that are fed to the AudioSink.
            mCurrentTrackStartTime += (System.currentTimeMillis() - mPauseLastTick);
            mPauseLastTick = System.currentTimeMillis();
        }

        return (int) (System.currentTimeMillis() - mCurrentTrackStartTime);
    }

    void seekImpl(final long timeMs) {
        final Song currentSong = getCurrentSong();
        boolean success = false;
        if (currentSong != null) {
            ProviderIdentifier id = currentSong.getProvider();
            ProviderConnection conn = PluginsLookup.getDefault().getProvider(id);
            if (conn != null) {
                final IMusicProvider provider = conn.getBinder();
                if (provider != null) {
                    try {
                        provider.seek(timeMs);
                        success = true;
                        mCurrentTrackStartTime = System.currentTimeMillis() - timeMs;
                    } catch (RemoteException e) {
                        Log.e(TAG, "Cannot seek to time", e);
                    }
                }
            }
        }

        if (success) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (IPlaybackCallback cb : mCallbacks) {
                        try {
                            cb.onSongScrobble((int) timeMs);
                        } catch (RemoteException e) {
                            Log.e(TAG, "Cannot notify scrobbling", e);
                        }
                    }
                }
            });
        }
    }


    IPlaybackService.Stub mBinder = new IPlaybackService.Stub() {
        @Override
        public void addCallback(IPlaybackCallback cb) throws RemoteException {
            mCallbacks.add(cb);
        }

        @Override
        public void removeCallback(IPlaybackCallback cb) throws RemoteException {
            for (IPlaybackCallback callback : mCallbacks) {
                if (cb.getIdentifier() == callback.getIdentifier()) {
                    mCallbacks.remove(cb);
                    break;
                }
            }
        }

        @Override
        public void playPlaylist(Playlist p) throws RemoteException {
            Log.i(TAG, "Play playlist: " + p.getRef());
            mCurrentTrack = 0;
            mPlaybackQueue.clear();
            queuePlaylist(p, false);
            mHandler.removeCallbacks(mStartPlaybackRunnable);
            mHandler.post(mStartPlaybackRunnable);
        }

        @Override
        public void playSong(Song s) throws RemoteException {
            Log.i(TAG, "Play song: " + s.getRef());
            mCurrentTrack = 0;
            mPlaybackQueue.clear();
            queueSong(s, true);
            mHandler.removeCallbacks(mStartPlaybackRunnable);
            mHandler.post(mStartPlaybackRunnable);
        }

        @Override
        public void playAlbum(Album a) throws RemoteException {
            Log.i(TAG, "Play album: " + a.getRef() + " (this=" + this + ")");
            mCurrentTrack = 0;
            mPlaybackQueue.clear();
            queueAlbum(a, false);
            mHandler.removeCallbacks(mStartPlaybackRunnable);
            mHandler.post(mStartPlaybackRunnable);
        }

        @Override
        public void queuePlaylist(Playlist p, boolean top) throws RemoteException {
            Iterator<String> songsIt = p.songs();

            final ProviderAggregator aggregator = ProviderAggregator.getDefault();
            while (songsIt.hasNext()) {
                String ref = songsIt.next();
                mPlaybackQueue.addSong(aggregator.retrieveSong(ref, p.getProvider()), top);
            }

            notifyQueueChanged();
        }

        @Override
        public void queueSong(Song s, boolean top) throws RemoteException {
            final ProviderAggregator aggregator = ProviderAggregator.getDefault();
            mPlaybackQueue.addSong(aggregator.retrieveSong(s.getRef(), null), top);
            notifyQueueChanged();
        }

        @Override
        public void queueAlbum(Album p, boolean top) throws RemoteException {
            Iterator<String> songsIt = p.songs();

            final ProviderAggregator aggregator = ProviderAggregator.getDefault();
            while (songsIt.hasNext()) {
                String ref = songsIt.next();
                mPlaybackQueue.addSong(aggregator.retrieveSong(ref, p.getProvider()), top);
            }

            notifyQueueChanged();
        }

        @Override
        public void pause() throws RemoteException {
            pauseImpl();
        }

        @Override
        public boolean play() throws RemoteException {
            playImpl();
            return true;
        }

        @Override
        public int getState() { return mState; }

        @Override
        public int getCurrentTrackLength() throws RemoteException {
            final Song currentSong = getCurrentSong();
            if (currentSong != null) {
                return currentSong.getDuration();
            } else {
                return -1;
            }
        }

        @Override
        public int getCurrentTrackPosition() throws RemoteException {
            return getCurrentTrackPositionImpl();
        }

        @Override
        public Song getCurrentTrack() throws RemoteException {
            return getCurrentSong();
        }

        @Override
        public int getCurrentTrackIndex() { return mCurrentTrack; }

        @Override
        public List<Song> getCurrentPlaybackQueue() {
            return mPlaybackQueue;
        }

        @Override
        public int getCurrentRms() throws RemoteException {
            return mDSPProcessor.getRms();
        }

        @Override
        public List<ProviderIdentifier> getDSPChain() throws RemoteException {
            return mDSPProcessor.getActiveChain();
        }

        @Override
        public void setDSPChain(List<ProviderIdentifier> chain) throws RemoteException {
            mDSPProcessor.setActiveChain(PlaybackService.this, chain);
        }

        @Override
        public void seek(final long timeMs) throws RemoteException {
            seekImpl(timeMs);
        }

        @Override
        public void next() throws RemoteException {
            nextImpl();
        }

        @Override
        public void previous() throws RemoteException {
            previousImpl();
        }

        @Override
        public void playAtQueueIndex(int index) {
            playAtIndexImpl(index);
        }

        @Override
        public void stop() throws RemoteException {
            stopImpl();
        }

        @Override
        public void setRepeatMode(boolean repeat) throws RemoteException {
            mRepeatMode = repeat;
            SharedPreferences prefs = getSharedPreferences(SERVICE_SHARED_PREFS, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(PREF_KEY_REPEAT, repeat);
            editor.apply();
        }

        @Override
        public boolean isRepeatMode() throws RemoteException {
            return mRepeatMode;
        }

        @Override
        public void clearPlaybackQueue() throws RemoteException {
            mPlaybackQueue.clear();
        }
    };

    @Override
    public void onSongUpdate(List<Song> s) {
        final Song currentSong = getCurrentSong();

        if (s.contains(currentSong) && currentSong.isLoaded()) {
            if (mCurrentTrackWaitLoading) {
                mHandler.removeCallbacks(mStartPlaybackRunnable);
                mHandler.post(mStartPlaybackRunnable);
            }

            mRemoteMetadata.setCurrentSong(currentSong);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mNotification.setCurrentSong(getCurrentSong());
                }
            });
        }
    }

    @Override
    public void onAlbumUpdate(List<Album> a) {
        // ignore
    }

    @Override
    public void onPlaylistUpdate(List<Playlist> p) {
        // TODO: Update playback queue if it's the playlist we're playing
    }

    @Override
    public void onArtistUpdate(List<Artist> a) {

    }

    @Override
    public void onProviderConnected(IMusicProvider provider) {
    }

    @Override
    public void onSearchResult(SearchResult searchResult) {

    }

    private IProviderCallback.Stub mProviderCallback = new BaseProviderCallback() {
        @Override
        public void onSongPlaying(ProviderIdentifier provider) {
            boolean wasPaused = mIsResuming;
            if (!wasPaused) {
                mCurrentTrackStartTime = System.currentTimeMillis();
            } else {
                mIsResuming = false;
            }

            mState = STATE_PLAYING;
            final Song currentSong = getCurrentSong();

            if (currentSong == null) {
                throw new IllegalStateException("Current song is null on callback! Queue size=" + mPlaybackQueue.size() +
                        " and index=" + mCurrentTrack + " and this=" + this);
            }

            for (IPlaybackCallback cb : mCallbacks) {
                try {
                    if (!wasPaused) {
                        cb.onSongStarted(false, currentSong);
                    } else {
                        cb.onPlaybackResume();
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Cannot call playback callback for song start event", e);
                }
            }

            Log.d(TAG, "onSongStarted: Playing...");

            mNotification.setPlayPauseAction(false);
            mRemoteMetadata.notifyPlaying(0);

            // Prepare pre-fetching the next song
            // Note: We don't take care of the delay being too early when it's paused, as long
            // as it matches the next track.
            ProviderConnection conn = PluginsLookup.getDefault().getProvider(provider);
            if (conn != null) {
                IMusicProvider binder = conn.getBinder();
                if (binder != null) {
                    try {
                        mHandler.removeCallbacks(mPrefetcher);
                        mHandler.postDelayed(mPrefetcher,
                                currentSong.getDuration() - binder.getPrefetchDelay());
                    } catch (RemoteException e) {
                        Log.e(TAG, "Cannot get prefetch delay from provider", e);
                    }
                }
            }
        }

        @Override
        public void onSongPaused(ProviderIdentifier provider) throws RemoteException {
            final Song currentSong = getCurrentSong();
            if (currentSong.getProvider().equals(provider) && !mIsStopping) {
                mState = STATE_PAUSED;
                mPauseLastTick = System.currentTimeMillis();

                for (IPlaybackCallback cb : mCallbacks) {
                    try {
                        cb.onPlaybackPause();
                    } catch (RemoteException e) {
                        Log.e(TAG, "Cannot call playback callback for playback pause event", e);
                    } catch (Exception e) {
                        Log.e(TAG, "BIG EXCEPTION DURING REMOTE PLAYBACK PAUSE: ", e);
                        Log.e(TAG, "Callback: " + cb);
                    }
                }

                Log.d(TAG, "onSongPaused: Paused...");

                mNotification.setPlayPauseAction(true);
                mRemoteMetadata.notifyPaused((System.currentTimeMillis() - mCurrentTrackStartTime));
            }
        }

        @Override
        public void onTrackEnded(ProviderIdentifier provider) throws RemoteException {
            if (mPlaybackQueue.size() > 0 && mCurrentTrack < mPlaybackQueue.size() - 1) {
                // Move to the next track
                mCurrentTrack++;

                // We restart the queue in an handler. In the case of the Spotify provider, the
                // endOfTrack callback locks the main API thread, leading to a dead lock if we
                // try to play a track here while still being in the callstack of the endOfTrack
                // callback.
                mHandler.removeCallbacks(mStartPlaybackRunnable);
                mHandler.post(mStartPlaybackRunnable);
            } else if (mPlaybackQueue.size() > 0 && mCurrentTrack == mPlaybackQueue.size() - 1) {
                if (mRepeatMode) {
                    // We're repeating, go back to the first track and play it
                    mCurrentTrack = 0;
                    mHandler.removeCallbacks(mStartPlaybackRunnable);
                    mHandler.post(mStartPlaybackRunnable);
                } else {
                    // Not repeating and at the end of the playlist, stop
                    mBinder.stop();
                }
            }
        }
    };

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                // You have gained the audio focus.
                if (mState == STATE_PLAYING) {
                    mNativeHub.setDucking(false);
                } else {
                    playImpl();
                }
                break;

            case AudioManager.AUDIOFOCUS_LOSS:
                // You have lost the audio focus for a presumably long time. You must stop all audio
                // playback.
                pauseImpl();
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // You have temporarily lost audio focus, but should receive it back shortly. You
                // must stop all audio playback, but you can keep your resources because you will
                // probably get focus back shortly.
                pauseImpl();
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // You have temporarily lost audio focus, but you are allowed to continue to play
                // audio quietly (at a low volume) instead of killing audio completely.
                mNativeHub.setDucking(true);
                break;
        }
    }

}
