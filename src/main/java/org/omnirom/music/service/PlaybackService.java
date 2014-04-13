package org.omnirom.music.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import org.omnirom.music.app.BuildConfig;
import org.omnirom.music.app.MainActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.framework.AudioSocketHost;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.IProviderCallback;
import org.omnirom.music.providers.ProviderConnection;
import org.omnirom.music.providers.ProviderIdentifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Service handling the playback of the audio and the play notification
 */
public class PlaybackService extends Service implements PluginsLookup.ConnectionListener {

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
    }

    /**
     * Called when the service is destroyed
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy()");

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
                }

                // TODO: Do on provider's songStarted callback
                for (IPlaybackCallback cb : mCallbacks) {
                    try {
                        cb.onSongStarted(first);
                        cb.onPlaybackResume();
                    } catch (RemoteException e) {
                        Log.e(TAG, "Cannot call playback callback for song start event", e);
                    }
                }
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
            mPlaybackQueue.clear();
            mPlaybackQueue.addSong(s, true);
            startPlayingQueue();
        }

        @Override
        public void queuePlaylist(Playlist p, boolean top) throws RemoteException {

        }

        @Override
        public void queueSong(Song s, boolean top) throws RemoteException {

        }

        @Override
        public void pause() throws RemoteException {

        }

        @Override
        public boolean play() throws RemoteException {
            return false;
        }

        @Override
        public boolean isPlaying() throws RemoteException {
            return false;
        }

        @Override
        public int getCurrentRms() throws RemoteException {
            return mDSPProcessor.getRms();
        }
    };

}
