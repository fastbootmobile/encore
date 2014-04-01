package org.omnirom.music.service;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import org.omnirom.music.app.BuildConfig;
import org.omnirom.music.framework.AudioSocketHost;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.ProviderConnection;
import org.omnirom.music.providers.ProviderIdentifier;

import java.util.List;

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

    public PlaybackService() {
        mPlaybackQueue = new PlaybackQueue();
    }

    public void resetShutdownTimeout() {
        mHandler.removeCallbacks(mShutdownRunnable);
        mHandler.postDelayed(mShutdownRunnable, SHUTDOWN_TIMEOUT);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mDSPProcessor = new DSPProcessor();
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
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Remove audio hosts from providers

        // Shutdown DSP chain
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service starting");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        mNumberBound++;
        Log.i(TAG, "Client bound service (" + mNumberBound + ")");
        return mBinder;
    }

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

    @Override
    public void onServiceDisconnected(ProviderConnection connection) {

    }

    private void assignProviderAudioSocket(ProviderConnection connection) {
        // Assign the providers an audio socket
        final String socketName = "org.omnirom.music.AUDIO_SOCKET_" + connection.getProviderName()
                + "_" + System.currentTimeMillis();
        AudioSocketHost socket = connection.createAudioSocket(socketName);
        socket.setDSP(mDSPProcessor);

        Log.i(TAG, "Provider connected and socket set: " + connection.getProviderName());
    }

    private void startPlayingQueue() {
        if (mPlaybackQueue.size() > 0) {
            Song first = mPlaybackQueue.get(0);
            ProviderIdentifier providerIdentifier = first.getProvider();
            IMusicProvider provider = PluginsLookup.getDefault().getProvider(providerIdentifier).getBinder();

            try {
                provider.playSong(first.getRef());
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to play song", e);
            }
        }
    }

    IPlaybackService.Stub mBinder = new IPlaybackService.Stub() {
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
    };

}
