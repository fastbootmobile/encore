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

package org.omnirom.music.providers;

import android.content.ComponentName;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

/**
 * Class representing a connection to a DSP provider service
 */
public class DSPConnection extends AbstractProviderConnection {
    private static final String TAG = "DSPConnection";

    private IDSPProvider mBinder;
    private AudioHostSocket mSocket;

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

    /**
     * @return The binder class for the provider
     */
    public IDSPProvider getBinder() {
        return mBinder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unbindService() {
        if (mIsBound) {
            mBinder = null;
        }

        super.unbindService();
    }

    /**
     * {@inheritDoc}
     */
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
                mBinder.setAudioSocketName(mSocket.getSocketName());
            } catch (RemoteException e) {
                Log.e(TAG, "Cannot restore audio socket to DSP effect", e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onServiceDisconnected(ComponentName name) {
        // Release the binder
        mBinder = null;
        super.onServiceDisconnected(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AudioHostSocket createAudioSocket(final String socketName) {
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
