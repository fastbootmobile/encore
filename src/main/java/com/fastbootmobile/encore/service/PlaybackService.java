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

package com.fastbootmobile.encore.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import com.echonest.api.v4.EchoNestException;
import com.fastbootmobile.encore.api.echonest.AutoMixManager;
import com.fastbootmobile.encore.app.OmniMusic;
import com.fastbootmobile.encore.framework.ListenLogger;
import com.fastbootmobile.encore.framework.PluginsLookup;
import com.fastbootmobile.encore.model.Album;
import com.fastbootmobile.encore.model.Artist;
import com.fastbootmobile.encore.model.Playlist;
import com.fastbootmobile.encore.model.SearchResult;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.AbstractProviderConnection;
import com.fastbootmobile.encore.providers.BaseProviderCallback;
import com.fastbootmobile.encore.providers.ILocalCallback;
import com.fastbootmobile.encore.providers.IMusicProvider;
import com.fastbootmobile.encore.providers.ProviderAggregator;
import com.fastbootmobile.encore.providers.ProviderConnection;
import com.fastbootmobile.encore.providers.ProviderIdentifier;
import com.fastbootmobile.encore.receivers.PacManReceiver;
import com.fastbootmobile.encore.receivers.RemoteControlReceiver;
import com.fastbootmobile.encore.utils.SettingsKeys;
import com.fastbootmobile.encore.utils.Utils;
import com.squareup.leakcanary.RefWatcher;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Service handling the playback of the audio and the play notification
 */
public class PlaybackService extends Service
        implements PluginsLookup.ConnectionListener, ILocalCallback,
        AudioManager.OnAudioFocusChangeListener, NativeHub.OnSampleWrittenListener {

    private static final String TAG = "PlaybackService";

    public static final int STATE_STOPPED   = 0;
    public static final int STATE_PLAYING   = 1;
    public static final int STATE_PAUSED    = 2;
    public static final int STATE_BUFFERING = 3;
    public static final int STATE_PAUSING   = 4;

    private static final String SERVICE_SHARED_PREFS = "PlaybackServicePrefs";
    private static final String QUEUE_SHARED_PREFS = "PlaybackQueueMemory";
    private static final String PREF_KEY_REPEAT = "repeatMode";
    private static final String PREF_KEY_SHUFFLE = "shuffleMode";

    public static final String ACTION_COMMAND = "command";
    public static final String EXTRA_COMMAND_NAME = "command_name";
    public static final int COMMAND_NEXT = 1;
    public static final int COMMAND_PREVIOUS = 2;
    public static final int COMMAND_PAUSE = 3;
    public static final int COMMAND_STOP = 4;

    private Runnable mNotifyQueueChangedRunnable = new Runnable() {
        @Override
        public void run() {
            mNotification.setHasNext(mPlaybackQueue.size() > 1 || (mPlaybackQueue.size() > 0 && mRepeatMode));

            for (IPlaybackCallback cb : mCallbacks) {
                try {
                    cb.onPlaybackQueueChanged();
                } catch (RemoteException e) {
                    Log.e(TAG, "Cannot notify playback queue changed", e);
                }
            }

            // Save the queue as well
            savePlaybackQueue();
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
    private NativeAudioSink mNativeSink;
    private NativeHub mNativeHub;
    private DSPProcessor mDSPProcessor;
    private final PlaybackQueue mPlaybackQueue;
    private List<IPlaybackCallback> mCallbacks;
    private ServiceNotification mNotification;
    private int mCurrentTrack = -1;
    private long mCurrentTrackElapsedMs;
    private int mState = STATE_STOPPED;
    private boolean mIsResuming;
    private boolean mIsStopping;
    private boolean mCurrentTrackWaitLoading;
    private ProviderIdentifier mCurrentPlayingProvider;
    private boolean mHasAudioFocus;
    private boolean mRepeatMode;
    private boolean mShuffleMode;
    private Prefetcher mPrefetcher;
    private IRemoteMetadataManager mRemoteMetadata;
    private PowerManager.WakeLock mWakeLock;
    private boolean mIsForeground;
    private ListenLogger mListenLogger;
    private PacManReceiver mPacManReceiver;
    private boolean mCurrentTrackLoaded;
    private HandlerThread mCommandsHandlerThread;
    private CommandHandler mCommandsHandler;
    private long mSleepTimerUptime = -1;
    private boolean mPausedByFocusLoss = false;
    private PlaybackServiceBinder mBinder = new PlaybackServiceBinder(new WeakReference<>(this));
    private PlaybackProviderCallback mProviderCallback = new PlaybackProviderCallback(new WeakReference<>(this));
    private boolean mShouldFlushBuffers = false;

    private static class CommandHandler extends Handler {
        private WeakReference<PlaybackService> mService;
        private static final int MSG_START_PLAYBACK = 1;
        private static final int MSG_PAUSE_PROVIDER = 2;
        private static final int MSG_RESUME_PLAYBACK = 3;
        private static final int MSG_FLUSH_BUFFERS = 4;
        private static final int MSG_STOP_SERVICE = 5;

        public CommandHandler(PlaybackService service, HandlerThread looper) {
            super(looper.getLooper());
            mService = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            final PlaybackService service = mService.get();

            if (service == null) {
                Log.w(TAG, "Service reference is null, dropping handler message");
                return;
            }

            final PluginsLookup plugins = PluginsLookup.getDefault();
            IMusicProvider provider;

            switch (msg.what) {
                case MSG_START_PLAYBACK:
                    service.startPlayingQueue();
                    break;

                case MSG_PAUSE_PROVIDER:
                    provider = plugins.getProvider((ProviderIdentifier.fromSerialized((String) msg.obj))).getBinder();
                    try {
                        if (provider != null) {
                            provider.pause();
                        } else {
                            Log.e(TAG, "Provider is null! Has it crashed?");
                        }
                    } catch (RemoteException e) {
                        Log.e(TAG, "Cannot pause the track!", e);
                    }
                    break;

                case MSG_RESUME_PLAYBACK:
                    service.playImplLocked();
                    break;

                case MSG_FLUSH_BUFFERS:
                    service.mNativeSink.flushSamples();
                    break;

                case MSG_STOP_SERVICE:
                    service.stopImpl();
                    break;
            }
        }
    }

    public PlaybackService() {
        mPlaybackQueue = new PlaybackQueue();
        mCallbacks = new ArrayList<>();
        mHandler = new Handler();
    }

    /**
     * Called when the service is created
     */
    @Override
    public void onCreate() {
        super.onCreate();
        mListenLogger = new ListenLogger(this);
        mPrefetcher = new Prefetcher(this);

        mCommandsHandlerThread = new HandlerThread("PlaybackServiceCommandsHandler");
        mCommandsHandlerThread.start();

        mCommandsHandler = new CommandHandler(this, mCommandsHandlerThread);

        // Register package manager to receive updates
        mPacManReceiver = new PacManReceiver();
        IntentFilter pacManFilter = new IntentFilter();
        pacManFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        pacManFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pacManFilter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        pacManFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pacManFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pacManFilter.addDataScheme("package");
        registerReceiver(mPacManReceiver, pacManFilter);

        // Really Google, I'd love to use your new APIs... But they're not working. If you use
        // the new Lollipop metadata system, you lose Bluetooth AVRCP since the Bluetooth
        // package still use the old RemoteController system.
        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mRemoteMetadata = new RemoteMetadataManagerv21(this);
        } else*/ {
            mRemoteMetadata = new RemoteMetadataManager(this);
        }

        ProviderAggregator.getDefault().addUpdateCallback(this);

        // Native playback initialization
        mNativeHub = new NativeHub(getApplicationContext());
        mNativeSink = new NativeAudioSink();
        mNativeHub.setSinkPointer(mNativeSink.getPlayer().getHandle());
        mNativeHub.setOnAudioWrittenListener(this);
        mNativeHub.onStart();

        mDSPProcessor = new DSPProcessor(this);
        mDSPProcessor.restoreChain(this);

        // Plugins initialization
        PluginsLookup.getDefault().initialize(getApplicationContext());
        PluginsLookup.getDefault().registerProviderListener(this);

        List<ProviderConnection> connections = PluginsLookup.getDefault().getAvailableProviders();
        for (ProviderConnection conn : connections) {
            if (conn.getBinder(false) != null) {
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
                IMusicProvider binder = pc.getBinder(false);
                if (binder != null) {
                    binder.registerCallback(mProviderCallback);
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
                    mIsForeground = true;
                } else {
                    notification.notify(PlaybackService.this);
                }

                BitmapDrawable albumArt = notification.getAlbumArt();
                mRemoteMetadata.setAlbumArt(albumArt);
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
        mShuffleMode = prefs.getBoolean(PREF_KEY_SHUFFLE, false);

        // TODO: Use callbacks
        // Restore playback queue after one second - we have multiple things to wait here:
        //  - The callbacks of the main app's UI
        //  - The providers connecting
        //  - The providers ready to send us data
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                SharedPreferences queuePrefs = getSharedPreferences(QUEUE_SHARED_PREFS, MODE_PRIVATE);
                mPlaybackQueue.restore(queuePrefs);
                mCurrentTrack = queuePrefs.getInt("current", -1);
                mCurrentTrackLoaded = false;
                mNotification.setHasNext(mPlaybackQueue.size() > 1 || (mPlaybackQueue.size() > 0 && mRepeatMode));
            }
        }, 1000);
    }

    /**
     * Called when the service is destroyed
     */
    @Override
    public void onDestroy() {
        unregisterReceiver(mPacManReceiver);
        PluginsLookup.getDefault().removeProviderListener(this);
        ProviderAggregator.getDefault().removeUpdateCallback(this);
        mRemoteMetadata.release();

        // Cancel prefetching
        mHandler.removeCallbacks(mPrefetcher);
        mPrefetcher.cancel();
        mPrefetcher = null;

        if (mHasAudioFocus) {
            abandonAudioFocus();
        }

        mIsForeground = false;

        // Remove audio hosts from providers
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

        // Store the playback queue
        savePlaybackQueue();

        // Shutdown DSP chain
        mNativeHub.onStop();
        mNativeHub.setOnAudioWrittenListener(null);
        mNativeSink.release();

        mCommandsHandlerThread.interrupt();

        RefWatcher watcher = OmniMusic.getRefWatcher(this);
        watcher.watch(this);

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
        Log.i(TAG, "Client bound");
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        Log.i(TAG, "Client rebound");
    }

    /**
     * Called when all clients unbound from this service
     *
     * @param intent The intent attached, not used
     * @return true
     */
    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "Clients unbound");
        return true;
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
                final IMusicProvider binder = ((ProviderConnection) connection).getBinder(false);

                if (binder != null) {
                    binder.registerCallback(mProviderCallback);
                } else {
                    Log.e(TAG, "Cannot register callback in onServiceConnected, binder is null");
                }

                // If we were playing from this provider and it just connected, it means it crashed.
                // We try to restore its state.
                if (binder != null && mState == STATE_PLAYING) {
                    final Song currentSong = getCurrentSong();
                    if (currentSong != null
                            && connection.getIdentifier().equals(currentSong.getProvider())) {
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                int restorePosition = getCurrentTrackPositionImpl();
                                if (restorePosition < 0 || restorePosition > currentSong.getDuration()) {
                                    restorePosition = 0;
                                }
                                Log.d(TAG, "Provider crashed, restoring playback of "
                                        + currentSong.getRef()
                                        + " to " + restorePosition + "ms");

                                try {
                                    binder.playSong(currentSong.getRef());
                                    if (restorePosition > 0) {
                                        binder.seek(getCurrentTrackPositionImpl());
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Cannot restore state", e);
                                }
                            }
                        }, 2000);
                    }
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
        Log.w(TAG, "Provider disconnected, rebinding before it's too late");
        connection.bindService();
    }

    public NativeHub getNativeHub() {
        return mNativeHub;
    }

    public PlaybackQueue getQueue() {
        return mPlaybackQueue;
    }

    /**
     * Saves the playback queue in the local storage
     */
    private void savePlaybackQueue() {
        SharedPreferences queuePrefs = getSharedPreferences(QUEUE_SHARED_PREFS, MODE_PRIVATE);
        mPlaybackQueue.save(queuePrefs.edit());
        queuePrefs.edit().putInt("current", mCurrentTrack).apply();
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
            socket = "com.fastbootmobile.encore.AUDIO_SOCKET_" + connection.getProviderName()
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
                am.registerMediaButtonEventReceiver(RemoteControlReceiver.getComponentName(this));

                // Notify the remote metadata that we're getting active
                mRemoteMetadata.setActive(true);

                // Register AUDIO_BECOMING_NOISY to stop playback when earbuds are pulled
                registerReceiver(mAudioNoisyReceiver,
                        new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));

                // Add a WakeLock to avoid CPU going to sleep while music is playing
                mWakeLock.acquire();

                mHasAudioFocus = true;
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
            // Release the audio focus
            final AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            am.abandonAudioFocus(this);
            unregisterReceiver(mAudioNoisyReceiver);

            // Release our CPU wakelock
            mWakeLock.release();

            // Notify the remote metadata that we're getting down
            mRemoteMetadata.setActive(false);

            mHasAudioFocus = false;
        }
    }

    /**
     * Starts playing the current playback queue
     */
    private void startPlayingQueue() {
        // Check sleep timer
        if (mSleepTimerUptime > 0 && SystemClock.uptimeMillis() >= mSleepTimerUptime) {
            Log.d(TAG, "Stopping playback because of sleep timer");
            mSleepTimerUptime = -1;
            stopImpl();
            return;
        }

        if (mPlaybackQueue.size() > 0) {
            // mCurrentTrack in this context is the track that is going to be played
            if (mCurrentTrack < 0) {
                mCurrentTrack = 0;
            }

            final Song next = mPlaybackQueue.get(mCurrentTrack);
            if (next == null) {
                // Song got unavailable, retry the next one
                boolean shouldFlush = mShouldFlushBuffers;
                nextImpl();

                // Keep the flush status
                mShouldFlushBuffers = shouldFlush;
                return;
            }

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

            // Clear up the sink buffers if we were paused (otherwise we'll cut the end of the
            // previous song)
            if (mState == STATE_PAUSED || mState == STATE_PAUSING || mState == STATE_STOPPED) {
                mCommandsHandler.sendEmptyMessage(CommandHandler.MSG_FLUSH_BUFFERS);
            }

            if (providerId != null) {
                ProviderConnection connection = PluginsLookup.getDefault().getProvider(providerId);
                if (connection != null) {
                    // Reset playbar status
                    getSharedPreferences(SettingsKeys.PREF_SETTINGS, 0)
                            .edit().putBoolean(SettingsKeys.KEY_PLAYBAR_HIDDEN, false).apply();

                    // Start playback
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
                            mCurrentTrackLoaded = false;
                            Log.w(TAG, "Track not yet loaded: " + next.getRef() + ", delaying");
                        } else if (!next.isAvailable()) {
                            // Track is not available, skip to the next one
                            boolean shouldFlush = mShouldFlushBuffers;
                            nextImpl();

                            // Keep the flush status
                            mShouldFlushBuffers = shouldFlush;
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

                            mCurrentTrackLoaded = true;

                            mListenLogger.addEntry(next);

                            // The notification system takes care of calling startForeground
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    mNotification.setCurrentSong(next);
                                }
                            });

                            mRemoteMetadata.setCurrentSong(next, getNextTrack() != null);
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
        synchronized (mPlaybackQueue) {
            try {
                if (mCurrentTrack >= 0 && mPlaybackQueue.size() > mCurrentTrack) {
                    return mPlaybackQueue.get(mCurrentTrack);
                } else {
                    return null;
                }
            } catch (Exception e) {
                return null;
            }
        }
    }

    /**
     * Requests the playback to start, if a request hasn't been posted already
     */
    private void requestStartPlayback() {
        if (!mCommandsHandler.hasMessages(CommandHandler.MSG_START_PLAYBACK)) {
            mCommandsHandler.sendEmptyMessage(CommandHandler.MSG_START_PLAYBACK);
        }
    }

    /**
     * Moves to the next track
     */
    void nextImpl() {
        boolean hasNext = mCurrentTrack < mPlaybackQueue.size() - 1;
        if (mPlaybackQueue.size() > 1 && mShuffleMode) {
            // Shuffle mode is enabled, play any track but not the one we just played
            int previousTrack = mCurrentTrack;
            while (previousTrack == mCurrentTrack) {
                mCurrentTrack = Utils.getRandom(mPlaybackQueue.size());
            }

            mNativeSink.setPaused(true);
            mShouldFlushBuffers = true;

            requestStartPlayback();
            mNotification.setHasNext(true);
        } else if (mPlaybackQueue.size() > 0 && hasNext) {
            mCurrentTrack++;
            mNativeSink.flushSamples();
            requestStartPlayback();

            hasNext = mCurrentTrack < mPlaybackQueue.size() - 1;
            hasNext = hasNext || (mPlaybackQueue.size() > 0 && mRepeatMode);
            mNotification.setHasNext(hasNext);
        } else if (mRepeatMode && mPlaybackQueue.size() > 0) {
            mCurrentTrack = 0;

            mNativeSink.setPaused(true);
            mShouldFlushBuffers = true;

            requestStartPlayback();
            mNotification.setHasNext(true);
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
        boolean shouldRestart = (getCurrentTrackPositionImpl() > 4000 || (!mRepeatMode && mCurrentTrack == 0))
                && mCurrentTrackLoaded;
        if (shouldRestart) {
            // Restart playback
            mNativeSink.setPaused(true);
            mShouldFlushBuffers = true;

            seekImpl(0);
        } else {
            boolean retry = true;

            while (retry) {
                // Go to the previous track
                mCurrentTrack--;
                if (mCurrentTrack < 0) {
                    if (mRepeatMode) {
                        mCurrentTrack = mPlaybackQueue.size() - 1;
                    } else {
                        mCurrentTrack = 0;
                    }
                }

                if (mCurrentTrack < 0 || mCurrentTrack >= mPlaybackQueue.size()) {
                    retry = true;
                    continue;
                }

                Song songToPlay = mPlaybackQueue.get(mCurrentTrack);
                // If song is unavailable, try the next one
                retry = songToPlay == null || (songToPlay.isLoaded() && !songToPlay.isAvailable());
            }

            mNativeSink.setPaused(true);
            mShouldFlushBuffers = true;

            requestStartPlayback();
        }
    }

    /**
     * Pauses the playback
     */
    void pauseImpl() {
        final Song currentSong = getCurrentSong();
        if (currentSong != null) {
            Log.d(TAG, "onSongPaused: Pausing...");

            boolean wasBuffering = (mState == STATE_BUFFERING);

            mState = STATE_PAUSING;

            final ProviderIdentifier identifier = currentSong.getProvider();
            mCommandsHandler.obtainMessage(CommandHandler.MSG_PAUSE_PROVIDER, identifier.serialize()).sendToTarget();

            mNativeSink.setPaused(true);

            if (wasBuffering) {
                // We were buffering, which means the provider might not be playing the song,
                // and might not send the playback paused callback. We assume we're paused.
                mState = STATE_PAUSED;

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

                Log.d(TAG, "onSongPaused: Paused (was buffering)...");

                mNotification.setPlayPauseAction(true);
                mRemoteMetadata.notifyPaused(getCurrentTrackPositionImpl());
            }
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

    synchronized void playImpl() {
        if (mState == STATE_PLAYING || mState == STATE_BUFFERING) {
            // We are already playing, don't do anything
            return;
        }

        mCommandsHandler.sendEmptyMessage(CommandHandler.MSG_RESUME_PLAYBACK);
    }

    private void playImplLocked() {
        final Song currentSong = getCurrentSong();
        if (currentSong != null && mCurrentTrackLoaded) {
            ProviderConnection conn = PluginsLookup.getDefault().getProvider(currentSong.getProvider());
            mNativeSink.setPaused(false);

            if (conn != null) {
                IMusicProvider provider = conn.getBinder();
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
            }
        } else if (mPlaybackQueue.size() > 0) {
            requestStartPlayback();
            mIsResuming = false;
        }
    }

    /**
     * Plays the song in the queue at the specified index
     * @param index The index to play, 0-based
     */
    private void playAtIndexImpl(int index) {
        Log.d(TAG, "Playing track " + (index + 1) + "/" + mPlaybackQueue.size());
        mCurrentTrack = index;

        mNativeSink.setPaused(true);
        mShouldFlushBuffers = true;

        requestStartPlayback();
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
        return (int) mCurrentTrackElapsedMs;
    }

    void seekImpl(final long timeMs) {
        // First, unpause if paused
        mNativeSink.setPaused(false);

        // Then seek on the provider
        final Song currentSong = getCurrentSong();
        boolean success = false;
        if (currentSong != null && mCurrentTrackLoaded) {
            ProviderIdentifier id = currentSong.getProvider();
            ProviderConnection conn = PluginsLookup.getDefault().getProvider(id);
            if (conn != null) {
                final IMusicProvider provider = conn.getBinder();
                if (provider != null) {
                    try {
                        provider.seek(timeMs);
                        success = true;
                        mCurrentTrackElapsedMs = timeMs;
                    } catch (RemoteException e) {
                        Log.e(TAG, "Cannot seek to time", e);
                    } catch (Exception e) {
                        Log.e(TAG, "Provider thrown exception while seeking", e);
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


    private static class PlaybackServiceBinder extends IPlaybackService.Stub {
        private WeakReference<PlaybackService> mParent;

        PlaybackServiceBinder(WeakReference<PlaybackService> parent) {
            mParent = parent;
        }

        @Override
        public void addCallback(IPlaybackCallback cb) throws RemoteException {
            PlaybackService service = mParent.get();

            if (service != null) {
                // Check if we don't have it already
                for (IPlaybackCallback callback : service.mCallbacks) {
                    if (cb.getIdentifier() == callback.getIdentifier()) {
                        return;
                    }
                }

                // Then we add it
                service.mCallbacks.add(cb);
            }
        }

        @Override
        public void removeCallback(IPlaybackCallback cb) throws RemoteException {
            PlaybackService service = mParent.get();

            if (service != null) {
                for (IPlaybackCallback callback : service.mCallbacks) {
                    if (cb.getIdentifier() == callback.getIdentifier()) {
                        service.mCallbacks.remove(cb);
                        break;
                    }
                }
            }
        }

        @Override
        public void playPlaylist(Playlist p) throws RemoteException {
            PlaybackService service = mParent.get();

            if (service != null) {
                Log.i(TAG, "Play playlist: " + p.getRef());
                service.mCurrentTrack = 0;
                synchronized (service.mPlaybackQueue) {
                    service.mPlaybackQueue.clear();
                    queuePlaylist(p, false);
                }
                service. requestStartPlayback();
            }
        }

        @Override
        public void playSong(Song s) throws RemoteException {
            PlaybackService service = mParent.get();

            if (service != null) {
                Log.i(TAG, "Play song: " + s.getRef());
                service.mCurrentTrack = 0;
                synchronized (service.mPlaybackQueue) {
                    service.mPlaybackQueue.clear();
                    queueSong(s, true);
                }
                service.requestStartPlayback();
            }
        }

        @Override
        public void playAlbum(Album a) throws RemoteException {
            PlaybackService service = mParent.get();

            if (a != null) {
                if (service != null) {
                    Log.i(TAG, "Play album: " + a.getRef() + " (this=" + this + ")");
                    service.mCurrentTrack = 0;
                    synchronized (service.mPlaybackQueue) {
                        service.mPlaybackQueue.clear();
                        queueAlbum(a, false);
                    }
                    service.requestStartPlayback();
                }
            } else {
                Log.e(TAG, "Cannot play album: Null");
            }
        }

        @Override
        public void queuePlaylist(Playlist p, boolean top) throws RemoteException {
            PlaybackService service = mParent.get();

            if (service != null) {
                Iterator<String> songsIt = p.songs();

                final ProviderAggregator aggregator = ProviderAggregator.getDefault();
                while (songsIt.hasNext()) {
                    String ref = songsIt.next();
                    service.mPlaybackQueue.addSong(aggregator.retrieveSong(ref, p.getProvider()), top);
                }

                service.notifyQueueChanged();
            }
        }

        @Override
        public void queueSong(Song s, boolean top) throws RemoteException {
            PlaybackService service = mParent.get();

            if (service != null) {
                final ProviderAggregator aggregator = ProviderAggregator.getDefault();
                service.mPlaybackQueue.addSong(aggregator.retrieveSong(s.getRef(), null), top);
                service.notifyQueueChanged();
            }
        }

        @Override
        public void queueAlbum(Album p, boolean top) throws RemoteException {
            PlaybackService service = mParent.get();

            if (service != null) {
                Iterator<String> songsIt = p.songs();

                final ProviderAggregator aggregator = ProviderAggregator.getDefault();
                while (songsIt.hasNext()) {
                    String ref = songsIt.next();
                    service.mPlaybackQueue.addSong(aggregator.retrieveSong(ref, p.getProvider()), top);
                }

                service.notifyQueueChanged();
            }
        }

        @Override
        public void playNext(Song s) throws RemoteException {
            PlaybackService service = mParent.get();

            if (service != null) {
                if (service.mCurrentTrack >= 0) {
                    service.mPlaybackQueue.add(service.mCurrentTrack + 1, s);
                } else {
                    service.mPlaybackQueue.add(0, s);
                }
            }
        }

        @Override
        public void pause() throws RemoteException {
            PlaybackService service = mParent.get();

            if (service != null) {
                // User-requested pause, so abandon audio focus to avoid resume if something else
                // grabs and release playback while we were just paused.
                service.pauseImpl();
                service.abandonAudioFocus();
            }
        }

        @Override
        public boolean play() throws RemoteException {
            PlaybackService service = mParent.get();

            if (service != null) {
                service.playImpl();
                return true;
            } else {
                return false;
            }
        }

        @Override
        public int getState() {
            PlaybackService service = mParent.get();

            if (service != null) {
                return service.mState;
            } else {
                return STATE_STOPPED;
            }
        }

        @Override
        public int getCurrentTrackLength() throws RemoteException {
            PlaybackService service = mParent.get();

            if (service != null) {
                final Song currentSong = service.getCurrentSong();
                if (currentSong != null) {
                    return currentSong.getDuration();
                }
            }
            return -1;
        }

        @Override
        public int getCurrentTrackPosition() throws RemoteException {
            PlaybackService service = mParent.get();

            if (service != null) {
                return service.getCurrentTrackPositionImpl();
            } else {
                return -1;
            }
        }

        @Override
        public Song getCurrentTrack() throws RemoteException {
            PlaybackService service = mParent.get();

            if (service != null) {
                return service.getCurrentSong();
            } else {
                return null;
            }
        }

        @Override
        public int getCurrentTrackIndex() {
            PlaybackService service = mParent.get();

            if (service != null) {
                return service.mCurrentTrack;
            } else {
                return -1;
            }
        }

        @Override
        public List<Song> getCurrentPlaybackQueue() {
            PlaybackService service = mParent.get();

            if (service != null) {
                return service.mPlaybackQueue;
            } else {
                return new ArrayList<Song>();
            }
        }

        @Override
        public int getCurrentRms() throws RemoteException {
            PlaybackService service = mParent.get();

            if (service != null) {
                return service.mDSPProcessor.getRms();
            } else {
                return 0;
            }
        }

        @Override
        public List<ProviderIdentifier> getDSPChain() throws RemoteException {
            PlaybackService service = mParent.get();

            if (service != null) {
                return service.mDSPProcessor.getActiveChain();
            } else {
                return new ArrayList<>();
            }
        }

        @Override
        public void setDSPChain(List<ProviderIdentifier> chain) throws RemoteException {
            PlaybackService service = mParent.get();

            if (service != null) {
                service.mDSPProcessor.setActiveChain(service, chain, service.mNativeHub);
            }
        }

        @Override
        public void seek(final long timeMs) throws RemoteException {
            PlaybackService service = mParent.get();

            if (service != null) {
                service.seekImpl(timeMs);
            }
        }

        @Override
        public void next() throws RemoteException {
            PlaybackService service = mParent.get();

            if (service != null) {
                service.nextImpl();
            }
        }

        @Override
        public void previous() throws RemoteException {
            PlaybackService service = mParent.get();

            if (service != null) {
                service.previousImpl();
            }
        }

        @Override
        public void playAtQueueIndex(int index) {
            PlaybackService service = mParent.get();

            if (service != null) {
                service.playAtIndexImpl(index);
            }
        }

        @Override
        public void stop() throws RemoteException {
            PlaybackService service = mParent.get();

            if (service != null) {
                service.stopImpl();
            }
        }

        @Override
        public void setRepeatMode(boolean repeat) throws RemoteException {
            PlaybackService service = mParent.get();

            if (service != null) {
                service.mRepeatMode = repeat;
                SharedPreferences prefs = service.getSharedPreferences(SERVICE_SHARED_PREFS, MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean(PREF_KEY_REPEAT, repeat);
                editor.apply();
            }
        }

        @Override
        public boolean isRepeatMode() throws RemoteException {
            PlaybackService service = mParent.get();

            if (service != null) {
                return service.mRepeatMode;
            } else {
                return false;
            }
        }

        @Override
        public void setShuffleMode(boolean shuffle) throws RemoteException {
            PlaybackService service = mParent.get();

            if (service != null) {
                service.mShuffleMode = shuffle;
                SharedPreferences prefs = service.getSharedPreferences(SERVICE_SHARED_PREFS, MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean(PREF_KEY_SHUFFLE, shuffle);
                editor.apply();
            }
        }

        @Override
        public boolean isShuffleMode() throws RemoteException {
            PlaybackService service = mParent.get();

            if (service != null) {
                return service.mShuffleMode;
            } else {
                return false;
            }
        }

        @Override
        public void clearPlaybackQueue() throws RemoteException {
            PlaybackService service = mParent.get();

            if (service != null) {
                synchronized (service.mPlaybackQueue) {
                    service.mPlaybackQueue.clear();
                }
            }
        }

        @Override
        public void setSleepTimer(long uptime) throws RemoteException {
            PlaybackService service = mParent.get();

            if (service != null) {
                service.mSleepTimerUptime = uptime;
            }
        }

        @Override
        public long getSleepTimerEndTime() throws RemoteException {
            PlaybackService service = mParent.get();

            if (service != null) {
                return service.mSleepTimerUptime;
            } else {
                return 0;
            }
        }
    };

    @Override
    public void onSongUpdate(List<Song> s) {
        final Song currentSong = getCurrentSong();

        if (currentSong != null && s.contains(currentSong) && currentSong.isLoaded()) {
            if (mCurrentTrackWaitLoading) {
                requestStartPlayback();
            }

            if (mCurrentTrackLoaded) {
                mRemoteMetadata.setCurrentSong(currentSong, getNextTrack() != null);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mNotification.setCurrentSong(getCurrentSong());
                    }
                });
            }
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
    public void onPlaylistRemoved(String ref) {
    }

    @Override
    public void onArtistUpdate(List<Artist> a) {

    }

    @Override
    public void onProviderConnected(IMusicProvider provider) {
    }

    @Override
    public void onSearchResult(List<SearchResult> searchResult) {

    }

    private static class PlaybackProviderCallback extends BaseProviderCallback {
        private WeakReference<PlaybackService> mParent;

        PlaybackProviderCallback(WeakReference<PlaybackService> parent) {
            mParent = parent;
        }

        @Override
        public void onSongPlaying(ProviderIdentifier provider) {
            PlaybackService service = mParent.get();

            if (service != null) {
                boolean wasPaused = service.mIsResuming;
                if (wasPaused) {
                    service.mIsResuming = false;
                } else {
                    service.mCurrentTrackElapsedMs = 0;

                    // Flush and unpause the sink to clear previous track data (if from user action)
                    if (service.mShouldFlushBuffers) {
                        service.mNativeSink.flushSamples();
                    }
                    service.mNativeSink.setPaused(false);
                }

                service.mState = STATE_PLAYING;
                final Song currentSong = service.getCurrentSong();

                if (currentSong == null) {
                    throw new IllegalStateException("Current song is null on callback! Queue size=" + service.mPlaybackQueue.size() +
                            " and index=" + service.mCurrentTrack + " and this=" + this);
                }

                for (IPlaybackCallback cb : service.mCallbacks) {
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

                Log.d(TAG, "onSongPlaying: Playing...");

                service.mNotification.setPlayPauseAction(false);
                service.mRemoteMetadata.notifyPlaying(0);

                // Prepare pre-fetching the next song
                // Note: We don't take care of the delay being too early when it's paused, as long
                // as it matches the next track.
                ProviderConnection conn = PluginsLookup.getDefault().getProvider(provider);
                if (conn != null) {
                    IMusicProvider binder = conn.getBinder();
                    if (binder != null) {
                        try {
                            service.mHandler.removeCallbacks(service.mPrefetcher);
                            service.mHandler.postDelayed(service.mPrefetcher,
                                    currentSong.getDuration() - binder.getPrefetchDelay());
                        } catch (RemoteException e) {
                            Log.e(TAG, "Cannot get prefetch delay from provider", e);
                        }
                    }
                }

                // Save the queue as we started playing a new song (maybe)
                service.savePlaybackQueue();
            }
        }

        @Override
        public void onSongPaused(ProviderIdentifier provider) throws RemoteException {
            PlaybackService service = mParent.get();

            if (service != null) {
                final Song currentSong = service.getCurrentSong();
                if (currentSong != null && currentSong.getProvider().equals(provider) && !service.mIsStopping) {
                    service.mState = STATE_PAUSED;

                    for (IPlaybackCallback cb : service.mCallbacks) {
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

                    // stopImpl() calls pauseImpl(), which may cause the notification to come back
                    // after the current notification went away due to this callback
                    if (!service.mIsStopping) {
                        service.mNotification.setPlayPauseAction(true);
                        service.mRemoteMetadata.notifyPaused(service.getCurrentTrackPositionImpl());
                    }
                }
            }
        }

        @Override
        public void onTrackEnded(ProviderIdentifier provider) throws RemoteException {
            PlaybackService service = mParent.get();

            if (service != null) {
                // We restart the queue in an handler. In the case of the Spotify provider, the
                // endOfTrack callback locks the main API thread, leading to a dead lock if we
                // try to play a track here while still being in the callstack of the endOfTrack
                // callback.

                if (service.mPlaybackQueue.size() > 1 && service.mShuffleMode) {
                    // Shuffle mode is enabled, play any track but not the one we just played
                    int previousTrack = service.mCurrentTrack;
                    while (previousTrack == service.mCurrentTrack) {
                        service.mCurrentTrack = Utils.getRandom(service.mPlaybackQueue.size());
                    }

                    service.mShouldFlushBuffers = false;
                    service.requestStartPlayback();
                } else if (service.mPlaybackQueue.size() > 0 && service.mCurrentTrack < service.mPlaybackQueue.size() - 1) {
                    // Regular sequential mode, not at the end, move to the next track
                    service.mCurrentTrack++;

                    service.mShouldFlushBuffers = false;
                    service.requestStartPlayback();
                } else if (service.mPlaybackQueue.size() > 0 && service.mCurrentTrack == service.mPlaybackQueue.size() - 1) {
                    // Regular sequential mode, at the end of the queue
                    if (service.mRepeatMode) {
                        // We're repeating, go back to the first track and play it
                        service.mCurrentTrack = 0;
                        service.mShouldFlushBuffers = false;
                        service.requestStartPlayback();
                    } else {
                        // Not repeating and at the end of the playlist, stop after a little while
                        // to allow the buffers to empty
                        service.mHandler.sendEmptyMessageDelayed(CommandHandler.MSG_STOP_SERVICE,
                                2000);
                    }
                }
            }
        }
    };

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                // You have gained the audio focus.
                mNativeHub.setDucking(false);
                if (mState != STATE_PLAYING && mPausedByFocusLoss) {
                    playImpl();
                    mPausedByFocusLoss = false;
                }
                break;

            case AudioManager.AUDIOFOCUS_LOSS:
                // You have lost the audio focus for a presumably long time. You must stop all audio
                // playback.
                pauseImpl();
                mPausedByFocusLoss = true;
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // You have temporarily lost audio focus, but should receive it back shortly. You
                // must stop all audio playback, but you can keep your resources because you will
                // probably get focus back shortly.
                pauseImpl();
                mPausedByFocusLoss = true;
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // You have temporarily lost audio focus, but you are allowed to continue to play
                // audio quietly (at a low volume) instead of killing audio completely.
                mNativeHub.setDucking(true);
                break;
        }
    }


    @Override
    public void onSampleWritten(byte[] bytes, int len, int sampleRate, int channels) {
        len = len / 2; // first, we want the number of samples, and we assume 16 bits audio
        len = len / channels; // then, we count "mono"
        mCurrentTrackElapsedMs += len * 1000 / sampleRate;
    }
}
