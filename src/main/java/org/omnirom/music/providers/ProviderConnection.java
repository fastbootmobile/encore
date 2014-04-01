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

    public ProviderConnection(Context ctx, String providerName, String pkg, String serviceName,
                              String configActivity) {
        mContext = ctx;
        mProviderName = providerName;
        mPackage = pkg;
        mServiceName = serviceName;
        mConfigurationActivity = configActivity;

        mIsBound = false;

        // Retain a generic identity of this providers
        mIdentifier = new ProviderIdentifier(mPackage, mServiceName, mProviderName);

        // Try to bind to the service
        bindService();
    }

    public void setListener(PluginsLookup.ConnectionListener listener) {
        mListener = listener;
    }

    public ProviderIdentifier getIdentifier() {
        return mIdentifier;
    }

    public IMusicProvider getBinder() {
        return mBinder;
    }

    public String getPackage() {
        return mPackage;
    }

    public String getServiceName() {
        return mServiceName;
    }

    public String getProviderName() {
        return mProviderName;
    }

    public String getConfigurationActivity() {
        return mConfigurationActivity;
    }

    public void unbindService() {
        if (mIsBound) {
            if (DEBUG) Log.d(TAG, "Unbinding service...");
            ProviderAggregator.getDefault().unregisterProvider(this);
            mContext.unbindService(this);
            mIsBound = false;
        }
    }

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
                if (!mBinder.isAuthenticated()) {
                    Log.d(TAG, "Provider is setup! Trying to log in!");
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
