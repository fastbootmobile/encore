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
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import org.omnirom.music.app.BuildConfig;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.service.NativeHub;

/**
 * Abstract host class for providers and DSP service connections
 */
public abstract class AbstractProviderConnection implements ServiceConnection {
    protected static final boolean DEBUG = BuildConfig.DEBUG;
    private static final String TAG = "AbstractProviderConnection";

    private String mProviderName;
    private String mAuthorName;
    private String mPackage;
    private String mServiceName;
    private String mConfigurationActivity;
    private Context mContext;
    protected boolean mIsBound;
    protected String mAudioSocketName;
    protected ProviderIdentifier mIdentifier;
    protected PluginsLookup.ConnectionListener mListener;

    /**
     * Constructor
     * @param ctx The context to which this connection should be bound
     * @param providerName The name of the provider (example: 'Spotify')
     * @param pkg The package in which the service can be found (example: org.example.music)
     * @param serviceName The name of the service (example: .MusicService)
     * @param configActivity The name of the configuration activity in the aforementioned package
     */
    public AbstractProviderConnection(Context ctx, String providerName, String authorName, String pkg,
                                      String serviceName, String configActivity) {
        mContext = ctx;
        mProviderName = providerName;
        mAuthorName = authorName;
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
     * @return The author of this provider
     */
    public String getAuthorName() {
        return mAuthorName;
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
    public void unbindService(NativeHub hub) {
        if (mIsBound) {
            if (DEBUG) Log.d(TAG, "Unbinding service...");

            mContext.unbindService(this);
            Intent i = new Intent();
            i.setClassName(mPackage, mServiceName);
            mContext.stopService(i);
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

    /**
     * Called when the service has been bound and is connected
     * @param name The component name of the provider
     * @param service The binder object
     */
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        if (DEBUG) Log.d(TAG, "Connected to provider " + name);
        mIsBound = true;

        if (mListener != null) {
            mListener.onServiceConnected(this);
        }
    }

    /**
     * Called when the service has been disconnected/unbound
     * @param name The component name of the provider
     */
    @Override
    public void onServiceDisconnected(ComponentName name) {
        // Release the binder
        mIsBound = false;

        if (mListener != null) {
            mListener.onServiceDisconnected(this);
        }

        if (DEBUG) Log.d(TAG, "Disconnected from provider " + name);
    }

    /**
     * Assigns an audio socket to this provider and connects it to the provided name
     * @param socketName The name of the local socket
     * @return True if the AudioSocket Host has been created successfully
     */
    public boolean createAudioSocket(final NativeHub hub, final String socketName) {
        // Release the previous socket, if any
        if (mAudioSocketName != null) {
            hub.releaseHostSocket(socketName);
            mAudioSocketName = null;
        }

        // Create the new socket
        if (hub.createHostSocket(socketName, this instanceof DSPConnection)) {
            mAudioSocketName = socketName;
            return true;
        } else {
            return false;
        }
    }

    /**
     * @return The active audio socket for this provider
     */
    public String getAudioSocketName() {
        return mAudioSocketName;
    }
}
