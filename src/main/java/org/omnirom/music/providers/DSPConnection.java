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
public class DSPConnection extends ProviderConnection {

    private static final String TAG = "DSPConnection";

    private IDSPProvider mBinder;

    /**
     * Constructor
     *
     * @param ctx            The context to which this connection should be bound
     * @param providerName   The name of the provider (example: 'Bass Boost')
     * @param pkg            The package in which the service can be found (example: org.example.music)
     * @param serviceName    The name of the service (example: .BassBoostService)
     * @param configActivity The name of the configuration activity in the aforementioned package
     */
    public DSPConnection(Context ctx, String providerName, String pkg, String serviceName, String configActivity) {
        super(ctx, providerName, pkg, serviceName, configActivity);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mBinder = IDSPProvider.Stub.asInterface(service);
        mIsBound = true;
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
    }

    public IDSPProvider getDSPBinder() {
        return mBinder;
    }

    @Override
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
            Log.e(TAG, "Unable to setup the audio socket for the providers " + getProviderName(), e);
        }

        return mAudioSocket;
    }
}
