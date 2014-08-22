package org.omnirom.music.providers;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import org.omnirom.music.app.BuildConfig;
import org.omnirom.music.framework.AudioSocketHost;
import org.omnirom.music.framework.PluginsLookup;

import java.security.Provider;

/**
 * Represents a connection to an audio provider (music source or DSP) service
 */
public class ProviderConnection extends AbstractProviderConnection {
    private static final String TAG = "ProviderConnection";

    private IMusicProvider mBinder;
    private Handler mHandler;

    /**
     * Constructor
     *
     * @param ctx            The context to which this connection should be bound
     * @param providerName   The name of the provider (example: 'Spotify')
     * @param pkg            The package in which the service can be found (example: org.example.music)
     * @param serviceName    The name of the service (example: .MusicService)
     * @param configActivity The name of the configuration activity in the aforementioned package
     */
    public ProviderConnection(Context ctx, String providerName, String authorName, String pkg,
                              String serviceName, String configActivity) {
        super(ctx, providerName, authorName, pkg, serviceName, configActivity);
    }

    @Override
    public void unbindService() {
        if (mIsBound) {
            ProviderAggregator.getDefault().unregisterProvider(this);
            mBinder = null;
        }

        super.unbindService();
    }

    public IMusicProvider getBinder() {
        return mBinder;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        super.onServiceConnected(name, service);

        mHandler = new Handler();
        mBinder = IMusicProvider.Stub.asInterface(service);

        try {
            // Tell the provider its identifier
            mBinder.setIdentifier(mIdentifier);

            // Register the provider
            ProviderAggregator.getDefault().registerProvider(this);
            mIsBound = true;
            if (DEBUG) Log.d(TAG, "Connected to providers " + name);

            // Automatically try to login the providers once bound
            if (mBinder.isSetup()) {
                Log.d(TAG, "Provider " + getProviderName() + " is setup! Trying to see if auth");
                if (!mBinder.isAuthenticated()) {
                    if (!mBinder.login()) {
                        Log.e(TAG, "Error while requesting login!");
                    }
                } else {
                    // Update playlists
                    ProviderAggregator.getDefault().getAllPlaylists();
                }
            }

            if (mAudioSocket != null) {
                mBinder.setAudioSocketName(mAudioSocket.getName());
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Remote exception occurred on the set providers", e);
        }

        mHandler.post(new Runnable() {
            public void run() {
                if (mListener != null) {
                    mListener.onServiceConnected(ProviderConnection.this);
                }
            }
        });
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        // Release the binder
        mBinder = null;
        super.onServiceDisconnected(name);
        Log.e(TAG, "Service disconnected: " + name);
    }

    @Override
    public AudioSocketHost createAudioSocket(final String socketName) {
        AudioSocketHost host = super.createAudioSocket(socketName);

        try {
            if (mBinder != null) {
                mBinder.setAudioSocketName(socketName);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Cannot assign audio socket to " + getProviderName(), e);
        }

        return host;
    }
}
