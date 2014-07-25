package org.omnirom.music.providers;

import android.content.ComponentName;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import org.omnirom.music.framework.AudioSocketHost;

/**
 * Created by Guigui on 23/07/2014.
 */
public class DSPConnection extends AbstractProviderConnection {

    private static final String TAG = "DSPConnection";

    private IDSPProvider mBinder;
    private AudioSocketHost mSocket;

    /**
     * Constructor
     *
     * @param ctx            The context to which this connection should be bound
     * @param providerName   The name of the provider (example: 'Bass Boost')
     * @param pkg            The package in which the service can be found (example: org.example.music)
     * @param serviceName    The name of the service (example: .BassBoostService)
     * @param configActivity The name of the configuration activity in the aforementioned package
     */
    public DSPConnection(Context ctx, String providerName, String authorName, String pkg, String serviceName, String configActivity) {
        super(ctx, providerName, authorName, pkg, serviceName, configActivity);
    }

    public IDSPProvider getBinder() {
        return mBinder;
    }

    @Override
    public void unbindService() {
        if (mIsBound) {
            mBinder = null;
        }

        super.unbindService();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mBinder = IDSPProvider.Stub.asInterface(service);

        if (DEBUG) Log.d(TAG, "Connected to providers " + name);

        if (mListener != null) {
            mListener.onServiceConnected(this);
        }

        try {
            // Tell the provider its identifier
            mBinder.setIdentifier(mIdentifier);
        } catch (RemoteException e) {
            Log.e(TAG, "Remote exception occurred on the set DSP effect", e);
        }

        if (mSocket != null) {
            try {
                mBinder.setAudioSocketName(mSocket.getName());
            } catch (RemoteException e) {
                Log.e(TAG, "Cannot restore audio socket to DSP effect", e);
            }
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        // Release the binder
        mBinder = null;
        super.onServiceDisconnected(name);
    }

    @Override
    public AudioSocketHost createAudioSocket(final String socketName) {
        mSocket = super.createAudioSocket(socketName);

        if (mBinder != null) {
            try {
                mBinder.setAudioSocketName(socketName);
            } catch (RemoteException e) {
                Log.e(TAG, "Cannot assign audio socket to " + getProviderName(), e);
            }
        } else {
            bindService();
        }

        return mSocket;
    }
}
