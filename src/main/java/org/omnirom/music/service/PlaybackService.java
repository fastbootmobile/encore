package org.omnirom.music.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import org.omnirom.music.app.BuildConfig;
import org.omnirom.music.app.MainActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.framework.AudioSocketHost;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Genre;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ILocalCallback;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.IProviderCallback;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderCache;
import org.omnirom.music.providers.ProviderConnection;
import org.omnirom.music.providers.ProviderIdentifier;

import java.security.Provider;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Service handling the playback of the audio and the play notification
 */
public class PlaybackService extends Service
        implements PluginsLookup.ConnectionListener, ILocalCallback {

    private static final String TAG = "PlaybackService";
    private static final boolean DEBUG = BuildConfig.DEBUG;

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
            if (mNumberBound == 0) {
                Log.w(TAG, "Shutting down because of timeout and nothing bound");
                stopSelf();
            } else {
                resetShutdownTimeout();
            }
        }
    };

    private Handler mHandler;
    private int mNumberBound = 0;
    private DSPProcessor mDSPProcessor;
    private PlaybackQueue mPlaybackQueue;
    private List<IPlaybackCallback> mCallbacks;
    private Notification mNotification;
    private Song mCurrentTrack;
    private long mCurrentTrackStartTime;
    private long mPauseLastTick;
    private boolean mIsPlaying;
    private boolean mIsPaused;

    public PlaybackService() {
        mPlaybackQueue = new PlaybackQueue();
        mCallbacks = new ArrayList<IPlaybackCallback>();
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
        mDSPProcessor.setSink(new DeviceAudioSink());

        PluginsLookup.getDefault().initialize(getApplicationContext());
        PluginsLookup.getDefault().registerProviderListener(this);
        mHandler = new Handler();
        resetShutdownTimeout();

        List<ProviderConnection> connections = PluginsLookup.getDefault().getAvailableProviders();
        for (ProviderConnection conn : connections) {
            if (conn.getBinder() != null) {
                assignProviderAudioSocket(conn);
            }
        }

        mNotification = new Notification(R.drawable.ic_launcher, "Playing music!",
                System.currentTimeMillis());
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        mNotification.setLatestEventInfo(this, "OmniMusic",
                "This is an ugly notification. Beautify me.", pendingIntent);

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
     * @param intent The intent attached, not used
     * @param flags The flags, not used
     * @param startId The start id, not used
     * @return a status integer
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service starting");
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * Called when the main app binds on this service
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
     * @param intent The intent attached, not used
     * @return true
     */
    @Override
    public boolean onUnbind(Intent intent) {
        mNumberBound--;
        Log.i(TAG, "Client unbound service (" + mNumberBound + " left)");

        if (mNumberBound == 0 && ! mIsPlaying) {
            mHandler.removeCallbacks(mShutdownRunnable);
            mShutdownRunnable.run();
        }

        return super.onUnbind(intent);
    }

    /**
     * ProviderConnection listener: Called when a provider is bound
     * @param connection The provider connection
     */
    @Override
    public void onServiceConnected(ProviderConnection connection) {
        assignProviderAudioSocket(connection);
    }

    /**
     * ProviderConnection listener: Called when a provider has disconnected
     * @param connection The provider connected
     */
    @Override
    public void onServiceDisconnected(ProviderConnection connection) {
        // TODO: Release the audio socket, update the playback status if we were playing from
        // this provider.
    }

    /**
     * Assigns the provided provider an audio client socket
     * @param connection The provider
     */
    private void assignProviderAudioSocket(ProviderConnection connection) {
        // Assign the providers an audio socket
        final String socketName = "org.omnirom.music.AUDIO_SOCKET_" + connection.getProviderName()
                + "_" + System.currentTimeMillis();
        AudioSocketHost socket = connection.createAudioSocket(socketName);
        socket.setDSP(mDSPProcessor);

        Log.i(TAG, "Provider connected and socket set: " + connection.getProviderName());
    }

    /**
     * Starts playing the current playback queue
     */
    private void startPlayingQueue() {
        if (mPlaybackQueue.size() > 0) {
            Song first = mPlaybackQueue.get(0);
            ProviderIdentifier providerIdentifier = first.getProvider();
            if (providerIdentifier != null) {
                IMusicProvider provider = PluginsLookup.getDefault().getProvider(providerIdentifier).getBinder();

                try {
                    provider.playSong(first.getRef());
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to play song", e);
                } catch (NullPointerException e)  {
                    Log.e(TAG,"No provider attached",e);
                }

                mCurrentTrack = first;
            } else {
                Log.e(TAG, "Cannot play the first song of the queue because the Song's " +
                        "ProviderIdentifier is null!");
            }
        }

        // We're playing something, so make sure we stay on front
        startForeground(1, mNotification);
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
        public void removeCallback(IPlaybackCallback cb) { mCallbacks.remove(cb); }

        @Override
        public void playPlaylist(Playlist p) throws RemoteException {

        }

        @Override
        public void playSong(Song s) throws RemoteException {
            Log.e(TAG, "Play song: " + s.getRef());
            mPlaybackQueue.clear();
            mPlaybackQueue.addSong(s, true);
            startPlayingQueue();
        }

        @Override
        public void playAlbum(Album a) throws RemoteException {
            Log.e(TAG, "Play album: " + a.getRef());
            mPlaybackQueue.clear();
            Iterator<String> songsIt = a.songs();

            ProviderCache cache = ProviderAggregator.getDefault().getCache();
            while (songsIt.hasNext()) {
                String ref = songsIt.next();
                mPlaybackQueue.addSong(cache.getSong(ref), false);
            }

            startPlayingQueue();
        }

        @Override
        public void queuePlaylist(Playlist p, boolean top) throws RemoteException {

        }

        @Override
        public void queueSong(Song s, boolean top) throws RemoteException {

        }

        @Override
        public void queueAlbum(Album p, boolean top) throws RemoteException {

        }

        @Override
        public void pause() throws RemoteException {
            if (mCurrentTrack != null) {
                // TODO: Refactor that with a threaded Handler that handles messages
                new Thread() {
                    public void run() {
                        IMusicProvider provider = PluginsLookup.getDefault().getProvider(mCurrentTrack.getProvider()).getBinder();
                        try {
                            provider.pause();
                        } catch (RemoteException e) {
                            Log.e(TAG, "Cannot pause the track!", e);
                        }
                    }
                }.start();
            }
        }

        @Override
        public boolean play() throws RemoteException {
            if (mCurrentTrack != null) {
                IMusicProvider provider = PluginsLookup.getDefault().getProvider(mCurrentTrack.getProvider()).getBinder();
                provider.resume();
                mIsPaused = false;

                for (IPlaybackCallback cb : mCallbacks) {
                    try {
                        cb.onPlaybackResume();
                    } catch (RemoteException e) {
                        Log.e(TAG, "Cannot call playback callback for playback resume event", e);
                    }
                }

                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean isPlaying() throws RemoteException {
            return mIsPlaying;
        }

        @Override
        public int getCurrentTrackLength() throws RemoteException {
            return mCurrentTrack.getDuration();
        }

        @Override
        public int getCurrentTrackPosition() throws RemoteException {
            if (mIsPaused) {
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
            return mCurrentTrack;
        }

        @Override
        public List<Song> getCurrentPlaybackQueue() {
            return mPlaybackQueue;
        }

        @Override
        public int getCurrentRms() throws RemoteException {
            return mDSPProcessor.getRms();
        }
    };

    @Override
    public void onSongUpdate(List<Song> s) {
        // ignore
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
        Log.e(TAG, "PROVIDER CONNECTED IN PLAYBACK SERVICE");
        try {
            provider.registerCallback(mProviderCallback);
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to register self as callback of provider " + provider + "!", e);
        }
    }

    private IProviderCallback.Stub mProviderCallback = new IProviderCallback.Stub() {
        @Override
        public void onLoggedIn(ProviderIdentifier provider, boolean success) throws RemoteException {

        }

        @Override
        public void onLoggedOut(ProviderIdentifier provider) throws RemoteException {

        }

        @Override
        public void onPlaylistAddedOrUpdated(ProviderIdentifier provider, Playlist p) throws RemoteException {

        }

        @Override
        public void onSongUpdate(ProviderIdentifier provider, Song s) throws RemoteException {

        }

        @Override
        public void onAlbumUpdate(ProviderIdentifier provider, Album a) throws RemoteException {

        }

        @Override
        public void onArtistUpdate(ProviderIdentifier provider, Artist a) throws RemoteException {

        }

        @Override
        public void onGenreUpdate(ProviderIdentifier provider, Genre g) throws RemoteException {

        }

        @Override
        public void onSongPlaying(ProviderIdentifier provider) throws RemoteException {
            mCurrentTrackStartTime = System.currentTimeMillis();
            mIsPlaying = true;

            for (IPlaybackCallback cb : mCallbacks) {
                try {
                    cb.onSongStarted(mCurrentTrack);
                    cb.onPlaybackResume();
                } catch (RemoteException e) {
                    Log.e(TAG, "Cannot call playback callback for song start event", e);
                }
            }
        }

        @Override
        public void onSongPaused(ProviderIdentifier provider) throws RemoteException {
            mIsPaused = true;
            mPauseLastTick = System.currentTimeMillis();

            Log.e(TAG, "onSongPaused");
            for (IPlaybackCallback cb : mCallbacks) {
                try {
                    cb.onPlaybackPause();
                } catch (RemoteException e) {
                    Log.e(TAG, "Cannot call playback callback for playback pause event", e);
                }
            }
        }

        @Override
        public void onTrackEnded(ProviderIdentifier provider) throws RemoteException {

        }

    };

}
