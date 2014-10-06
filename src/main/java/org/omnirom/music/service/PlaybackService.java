package org.omnirom.music.service;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import org.acra.ACRA;
import org.omnirom.music.api.echonest.AutoMixManager;
import org.omnirom.music.app.R;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.SearchResult;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.AbstractProviderConnection;
import org.omnirom.music.providers.AudioHostSocket;
import org.omnirom.music.providers.DSPConnection;
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

    /**
     * Time after which the service will shutdown if nothing happens
     */
    private static final int SHUTDOWN_TIMEOUT = 5000;

    /**
     * Runnable that will shutdown this service after timeout. Each action should cancel the
     * existing mShutdownRunnable in the mHandler
     */
    private Runnable mShutdownRunnable = new Runnable() {
        @Override
        public void run() {
            if (mState == STATE_PAUSED || mState == STATE_STOPPED) {
                Log.w(TAG, "Shutting down because of timeout and nothing bound");
                stopForeground(true);
                stopSelf();
            } else {
                resetShutdownTimeout();
            }
        }
    };

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
                    ACRA.getErrorReporter().handleException(e, false);
                }
            }
        }
    };

    private Handler mHandler;
    private int mNumberBound = 0;
    private DSPProcessor mDSPProcessor;
    private PlaybackQueue mPlaybackQueue;
    private List<IPlaybackCallback> mCallbacks;
    private ServiceNotification mNotification;
    private RemoteControlClient mRemoteControlClient;
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
        mRepeatMode = false;
    }

    /**
     * Resets the shutdown timeout (after which the service would stop)
     */
    public void resetShutdownTimeout() {
        mHandler.removeCallbacks(mShutdownRunnable);
        mHandler.postDelayed(mShutdownRunnable, SHUTDOWN_TIMEOUT);
    }

    /**
     * Called when the service is created
     */
    @Override
    public void onCreate() {
        super.onCreate();
        mDSPProcessor = new DSPProcessor(this);
        mDSPProcessor.setSink(new NativeAudioSink());

        PluginsLookup.getDefault().initialize(getApplicationContext());
        PluginsLookup.getDefault().registerProviderListener(this);
        mHandler = new Handler();
        resetShutdownTimeout();

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
                notification.notify(PlaybackService.this);
                Bitmap albumArt = notification.getAlbumArt();
                if (albumArt == null) {
                    albumArt = ((BitmapDrawable) getResources().getDrawable(R.drawable.album_placeholder)).getBitmap();
                }
                mRemoteControlClient.editMetadata(false)
                        .putBitmap(RemoteControlClient.MetadataEditor.BITMAP_KEY_ARTWORK,
                                albumArt.copy(albumArt.getConfig(), false)).apply();
            }
        });

        // Setup lockscreen remote controls
        setupRemoteControl();
    }

    /**
     * Called when the service is destroyed
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy()");

        ProviderAggregator.getDefault().removeUpdateCallback(this);

        // Remove audio hosts from providers

        // Shutdown DSP chain
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
        Log.i(TAG, "Service starting");
        mIsStopping = false;
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
        ProviderAggregator.getDefault().addUpdateCallback(this);
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

        if (mNumberBound == 0 && (mState == STATE_STOPPED || mState == STATE_PAUSED)) {
            mHandler.removeCallbacks(mShutdownRunnable);
            mShutdownRunnable.run();
        }

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
                ((ProviderConnection) connection).getBinder().registerCallback(mProviderCallback);
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

    private void setupRemoteControl() {
        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        mediaButtonIntent.setComponent(RemoteControlReceiver.getComponentName(this));

        PendingIntent mediaPendingIntent = PendingIntent.getBroadcast(getApplicationContext(),
                0, mediaButtonIntent, 0);
        mRemoteControlClient = new RemoteControlClient(mediaPendingIntent);
        int flags = RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS
                | RemoteControlClient.FLAG_KEY_MEDIA_NEXT
                | RemoteControlClient.FLAG_KEY_MEDIA_PLAY
                | RemoteControlClient.FLAG_KEY_MEDIA_PAUSE
                | RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE;
                // | RemoteControlClient.FLAG_KEY_MEDIA_STOP;
        mRemoteControlClient.setTransportControlFlags(flags);
    }

    private void updateRemoteMetadata() {
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();
        final Song currentSong = mPlaybackQueue.get(mCurrentTrack);
        Artist artist = aggregator.retrieveArtist(currentSong.getArtist(), currentSong.getProvider());
        Album album = aggregator.retrieveAlbum(currentSong.getAlbum(), currentSong.getProvider());

        RemoteControlClient.MetadataEditor edit = mRemoteControlClient.editMetadata(true);
        if (artist != null) {
            edit.putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, artist.getName());
        }
        if (album != null) {
            edit.putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, album.getName());
        }
        edit.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, currentSong.getTitle());
        edit.putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, currentSong.getDuration());
        edit.apply();
    }

    /**
     * Assigns the provided provider an audio client socket
     *
     * @param connection The provider
     */
    public AudioHostSocket assignProviderAudioSocket(AbstractProviderConnection connection) {
        AudioHostSocket socket = connection.getAudioSocket();

        if (socket == null) {
            // Assign the providers an audio socket
            final String socketName = "org.omnirom.music.AUDIO_SOCKET_" + connection.getProviderName()
                    + "_" + System.currentTimeMillis();
            socket = connection.createAudioSocket(socketName);

            if (connection instanceof DSPConnection) {
                socket.setCallback(mDSPProcessor.getDSPCallback());
            } else {
                socket.setCallback(mDSPProcessor.getProviderCallback());
            }

            Log.i(TAG, "Provider connected and socket set: " + connection.getProviderName());
        }

        return socket;
    }

    /**
     * Request the audio focus and registers the remote media controller
     */
    private synchronized void requestAudioFocus() {
        if (!mHasAudioFocus) {
            Log.d(TAG, "Request audio focus...");
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

            // Request audio focus for playback
            int result = am.requestAudioFocus(this,
                    // Use the music stream.
                    AudioManager.STREAM_MUSIC,
                    // Request permanent focus.
                    AudioManager.AUDIOFOCUS_GAIN);

            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.e(TAG, "Request granted");
                mHasAudioFocus = true;
                am.registerMediaButtonEventReceiver(RemoteControlReceiver.getComponentName(this));

                // create and register the remote control client
                am.registerRemoteControlClient(mRemoteControlClient);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    mRemoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING,
                            (System.currentTimeMillis() - mCurrentTrackStartTime), 1.0f);
                } else {
                    mRemoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
                }

                // Register AUDIO_BECOMING_NOISY to stop playback when earbuds are pulled
                registerReceiver(mAudioNoisyReceiver,
                        new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
            } else {
                Log.e(TAG, "Request denied: " + result);
            }
        }
    }

    /**
     * Release the audio focus and unregisters the media controls
     */
    private synchronized void abandonAudioFocus() {
        if (mHasAudioFocus) {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            am.abandonAudioFocus(this);
            unregisterReceiver(mAudioNoisyReceiver);
            mHasAudioFocus = false;
        }
    }

    /**
     * Starts playing the current playback queue
     */
    private void startPlayingQueue() {
        if (mPlaybackQueue.size() > 0) {
            if (mCurrentTrack < 0) {
                mCurrentTrack = 0;
            }

            final Song next = mPlaybackQueue.get(mCurrentTrack);
            final ProviderIdentifier providerId = next.getProvider();

            if (mCurrentPlayingProvider != null && !next.getProvider().equals(mCurrentPlayingProvider)) {
                // Pause the previously playing track to avoid overlap if it's not the same provider
                try {
                    mBinder.pause();
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to pause the previously playing song");
                }
                mCurrentPlayingProvider = null;
            }

            if (providerId != null) {
                IMusicProvider provider = PluginsLookup.getDefault().getProvider(providerId).getBinder();
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
                } else {
                    mCurrentTrackWaitLoading = false;
                    mCurrentPlayingProvider = providerId;

                    requestAudioFocus();

                    try {
                        if (provider != null) {
                            provider.playSong(next.getRef());
                        }
                    } catch (RemoteException e) {
                        Log.e(TAG, "Unable to play song", e);
                    } catch (NullPointerException e) {
                        Log.e(TAG, "No provider attached", e);
                    }

                    // The notification system takes care of calling startForeground
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mNotification.setCurrentSong(next);
                        }
                    });

                    updateRemoteMetadata();
                    mRemoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_BUFFERING);
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
        if (mCurrentTrack >= 0) {
            return mPlaybackQueue.get(mCurrentTrack);
        } else {
            return null;
        }
    }

    /**
     * The binder implementation of the remote methods
     */
    IPlaybackService.Stub mBinder = new IPlaybackService.Stub() {
        @Override
        public void addCallback(IPlaybackCallback cb) throws RemoteException {
            mCallbacks.add(cb);
        }

        @Override
        public void removeCallback(IPlaybackCallback cb) {
            mCallbacks.remove(cb);
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
            Log.i(TAG, "Play album: " + a.getRef());
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
            final Song currentSong = getCurrentSong();
            if (currentSong != null) {
                // TODO: Refactor that with a threaded Handler that handles messages
                final ProviderIdentifier identifier = currentSong.getProvider();
                abandonAudioFocus();
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

        @Override
        public boolean play() throws RemoteException {
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
            mDSPProcessor.setActiveChain(chain);
        }

        @Override
        public void seek(final long timeMs) throws RemoteException {
            final Song currentSong = getCurrentSong();
            if (currentSong != null) {
                ProviderIdentifier id = currentSong.getProvider();
                IMusicProvider provider = PluginsLookup.getDefault().getProvider(id).getBinder();
                provider.seek(timeMs);
                mCurrentTrackStartTime = System.currentTimeMillis() - timeMs;
            }

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

        @Override
        public void next() throws RemoteException {
            boolean hasNext = mCurrentTrack < mPlaybackQueue.size() - 2;
            if (mPlaybackQueue.size() > 0 && hasNext) {
                mCurrentTrack++;
                mHandler.removeCallbacks(mStartPlaybackRunnable);
                mHandler.post(mStartPlaybackRunnable);

                hasNext = mCurrentTrack < mPlaybackQueue.size() - 2;
                mNotification.setHasNext(hasNext);
            }
        }

        @Override
        public void previous() throws RemoteException {
            boolean shouldRestart = (getCurrentTrackPosition() > 4000 || mCurrentTrack == 0);
            if (shouldRestart) {
                // Restart playback
                seek(0);
            } else {
                // Go to the previous track
                mCurrentTrack--;
                mHandler.removeCallbacks(mStartPlaybackRunnable);
                mHandler.post(mStartPlaybackRunnable);
            }
        }

        @Override
        public void playAtQueueIndex(int index) {
            Log.d(TAG, "Playing track " + index + "/" + mPlaybackQueue.size());
            mCurrentTrack = index;
            mHandler.removeCallbacks(mStartPlaybackRunnable);
            mHandler.post(mStartPlaybackRunnable);
        }

        @Override
        public void stop() throws RemoteException {
            if ((mState == STATE_PLAYING || mState == STATE_BUFFERING) && mPlaybackQueue.size() > 0
                    && mCurrentTrack >= 0) {
                pause();
            }

            for (IPlaybackCallback cb : mCallbacks) {
                try {
                    cb.onPlaybackPause();
                } catch (RemoteException e) {
                    Log.e(TAG, "Cannot call playback callback for playback pause event", e);
                }
            }

            mIsStopping = true;
            stopForeground(true);
        }

        @Override
        public void setRepeatMode(boolean repeat) throws RemoteException {
            mRepeatMode = repeat;
        }

        @Override
        public boolean isRepeatMode() throws RemoteException {
            return mRepeatMode;
        }
    };

    @Override
    public void onSongUpdate(List<Song> s) {
        if (mCurrentTrackWaitLoading) {
            final Song currentSong = getCurrentSong();
            if (s.contains(currentSong) && currentSong.isLoaded()) {
                mHandler.removeCallbacks(mStartPlaybackRunnable);
                mHandler.post(mStartPlaybackRunnable);
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
    public void onArtistUpdate(List<Artist> a) {

    }

    @Override
    public void onProviderConnected(IMusicProvider provider) {
        try {
            provider.registerCallback(mProviderCallback);
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to register self as callback of provider " + provider + "!", e);
        }
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
            mRemoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
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
                        // Manually report to ACRA but keep moving
                        ACRA.getErrorReporter().handleException(e, false);
                    }
                }

                Log.d(TAG, "onSongPaused: Paused...");

                mNotification.setPlayPauseAction(true);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    mRemoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED,
                            (System.currentTimeMillis() - mCurrentTrackStartTime), 1.0f);
                } else {
                    mRemoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED);
                }
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
            } else if (mPlaybackQueue.size() > 0 && mCurrentTrack == mPlaybackQueue.size() - 1
                    && mRepeatMode) {
                // We're repeating, go back to the first track and play it
                mCurrentTrack = 0;
                mHandler.removeCallbacks(mStartPlaybackRunnable);
                mHandler.post(mStartPlaybackRunnable);
            }
        }
    };

    @Override
    public void onAudioFocusChange(int focusChange) {
        final Song currentSong = getCurrentSong();
        final PluginsLookup lookup = PluginsLookup.getDefault();

        if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            // Pause playback
            if (currentSong != null) {
                IMusicProvider provider = lookup.getProvider(currentSong.getProvider()).getBinder();
                try {
                    if (provider != null) {
                        provider.pause();
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to pause current track for audio focus change");
                }
            }
        } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            // Resume playback
            if (currentSong != null) {
                IMusicProvider provider = lookup.getProvider(currentSong.getProvider()).getBinder();
                try {
                    if (provider != null) {
                        provider.resume();
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to pause current track for audio focus change");
                }
            }
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            am.unregisterMediaButtonEventReceiver(RemoteControlReceiver.getComponentName(this));
            am.abandonAudioFocus(this);
            if (currentSong != null) {
                IMusicProvider provider = lookup.getProvider(currentSong.getProvider()).getBinder();
                try {
                    if (provider != null) {
                        provider.pause();
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to pause current track for audio focus change");
                }
            }
        }
    }

}
