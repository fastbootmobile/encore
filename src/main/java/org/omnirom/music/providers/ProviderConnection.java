package org.omnirom.music.providers;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import org.omnirom.music.app.BuildConfig;
import org.omnirom.music.framework.AudioSocketHost;
import org.omnirom.music.framework.PluginsLookup;

import java.security.Provider;

/**
 * Represents a connection to an audio provider service
 */
public class ProviderConnection implements ServiceConnection {
    private static final boolean DEBUG = BuildConfig.DEBUG;
    private static final String TAG = "ProviderConnection";

    private String mProviderName;
    private String mPackage;
    private String mServiceName;
    private String mConfigurationActivity;
    private Context mContext;
    private IMusicProvider mBinder;
    private boolean mIsBound;
    private AudioSocketHost mAudioSocket;
    private ProviderIdentifier mIdentifier;
    private PluginsLookup.ConnectionListener mListener;

    /**
     * Constructor
     * @param ctx The context to which this connection should be bound
     * @param providerName The name of the provider (example: 'Spotify')
     * @param pkg The package in which the service can be found (example: org.example.music)
     * @param serviceName The name of the service (example: .MusicService)
     * @param configActivity The name of the configuration activity in the aforementioned package
     */
    public ProviderConnection(Context ctx, String providerName, String pkg, String serviceName,
                              String configActivity) {
        mContext = ctx;
        mProviderName = providerName;
        mPackage = pkg;
        mServiceName = serviceName;
        mConfigurationActivity = configActivity;

        mIsBound = false;

        // Retain a generic identity of this provider
        mIdentifier = new ProviderIdentifier(mPackage, mServiceName, mProviderName);

        // Try to bind to the service
        bindService();
    }

    /**
     * Sets the listener for this provider connection
     * @param listener The listener
     */
    public void setListener(PluginsLookup.ConnectionListener listener) {
        mListener = listener;
    }

    /**
     * @return The provider identifier for this connection
     */
    public ProviderIdentifier getIdentifier() {
        return mIdentifier;
    }

    /**
     * @return The remote binder for this connection, or null if the service is not bound
     */
    public IMusicProvider getBinder() {
        return mBinder;
    }

    /**
     * @return The name of the package in which this service is
     */
    public String getPackage() {
        return mPackage;
    }

    /**
     * @return The name of the actual service running this provider. See {@link #getProviderName()}
     */
    public String getServiceName() {
        return mServiceName;
    }

    /**
     * @return The name of this provider
     */
    public String getProviderName() {
        return mProviderName;
    }

    /**
     * @return Returns the canonical name of the class handling the configuration activity for this
     *         provider
     */
    public String getConfigurationActivity() {
        return mConfigurationActivity;
    }

    /**
     * Unbinds the service and unregisters the provider from this instance
     */
    public void unbindService() {
        if (mIsBound) {
            if (DEBUG) Log.d(TAG, "Unbinding service...");
            ProviderAggregator.getDefault().unregisterProvider(this);
            mContext.unbindService(this);
            Intent i = new Intent();
            i.setClassName(mPackage, mServiceName);
            mContext.stopService(i);
            mBinder = null;
            mIsBound = false;
        }
    }

    /**
     * Binds the service. Only valid if the service isn't already bound. Note that a short delay
     * might occur between the time of the bind request and the actual Binder being available.
     */
    public void bindService() {
        if (mIsBound) {
            // Log.w(TAG, "bindService(): Service seems already bound");
            return;
        }

        if (DEBUG) Log.d(TAG, "Binding service...");
        Intent i = new Intent();
        i.setClassName(mPackage, mServiceName);
        mContext.startService(i);
        mContext.bindService(i, this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mBinder = IMusicProvider.Stub.asInterface(service);
        ProviderAggregator.getDefault().registerProvider(this);
        mIsBound = true;
        if (DEBUG) Log.d(TAG, "Connected to providers " + name);

        if (mListener != null) {
            mListener.onServiceConnected(this);
        }

        try {
            // Tell the provider its identifier
            mBinder.setIdentifier(mIdentifier);

            // Automatically try to login the providers once bound
            if (mBinder.isSetup()) {
                Log.d(TAG, "Provider " + mProviderName + " is setup! Trying to see if auth");
                if (!mBinder.isAuthenticated()) {
                    if (!mBinder.login()) {
                        Log.e(TAG, "Error while requesting login!");
                    }
                } else {
                    // Update playlists
                    ProviderAggregator.getDefault().getAllPlaylists();
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Remote exception occurred on the set providers", e);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        // Release the binder
        mBinder = null;
        mIsBound = false;

        if (mListener != null) {
            mListener.onServiceDisconnected(this);
        }

        if (DEBUG) Log.d(TAG, "Disconnected from providers " + name);
    }

    /**
     * Assigns an audio socket to this provider and connects it to the provided name
     * @param socketName The name of the local socket
     * @return The AudioSocketHost that has been created
     */
    public AudioSocketHost createAudioSocket(final String socketName) {
        // Remove the previous socket, if any
        if (mAudioSocket != null) {
            mAudioSocket.release();
            mAudioSocket = null;
        }

        // Assign the provider an audio socket
        try {
            mAudioSocket = new AudioSocketHost(socketName);
            mAudioSocket.startListening();
            mBinder.setAudioSocketName(socketName);
        } catch (Exception e) {
            Log.e(TAG, "Unable to setup the audio socket for the providers " + mProviderName, e);
        }

        return mAudioSocket;
    }
}
