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

package org.omnirom.music.framework;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import org.omnirom.music.app.BuildConfig;
import org.omnirom.music.model.BoundEntity;
import org.omnirom.music.providers.AbstractProviderConnection;
import org.omnirom.music.providers.Constants;
import org.omnirom.music.providers.DSPConnection;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.MultiProviderPlaylistProvider;
import org.omnirom.music.providers.ProviderConnection;
import org.omnirom.music.providers.ProviderIdentifier;
import org.omnirom.music.service.IPlaybackService;
import org.omnirom.music.service.NativeHub;
import org.omnirom.music.service.PlaybackService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Plugins Manager class
 */
public class PluginsLookup {

    private static final boolean DEBUG = false;
    private static final String TAG = "PluginsLookup";

    public static final String DATA_PACKAGE = "package";
    public static final String DATA_SERVICE = "service";
    public static final String DATA_NAME = "name";
    public static final String DATA_AUTHOR = "author";
    public static final String DATA_CONFIGCLASS = "configclass";

    public static interface ConnectionListener {
        public void onServiceConnected(AbstractProviderConnection connection);
        public void onServiceDisconnected(AbstractProviderConnection connection);
    }

    private Context mContext;
    private final List<ProviderConnection> mConnections;
    private final List<DSPConnection> mDSPConnections;
    private List<ConnectionListener> mConnectionListeners;
    private IPlaybackService mPlaybackService;
    private ProviderConnection mMultiProviderConnection;
    private Handler mHandler;
    private int mServiceUsage;
    private ServiceConnection mPlaybackConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mPlaybackService = IPlaybackService.Stub.asInterface(service);
            Log.i(TAG, "Connected to Playback Service");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mPlaybackService = null;
        }
    };

    private ConnectionListener mProviderListener = new ConnectionListener() {
        @Override
        public void onServiceConnected(AbstractProviderConnection connection) {
            for (ConnectionListener listener : mConnectionListeners) {
                listener.onServiceConnected(connection);
            }
        }

        @Override
        public void onServiceDisconnected(AbstractProviderConnection connection) {
            for (ConnectionListener listener : mConnectionListeners) {
                listener.onServiceDisconnected(connection);
            }
        }
    };

    private Runnable mShutdownPlaybackRunnable = new Runnable() {
        @Override
        public void run() {
            mContext.unbindService(mPlaybackConnection);
            mPlaybackService = null;
        }
    };

    private static final PluginsLookup INSTANCE = new PluginsLookup();

    public static PluginsLookup getDefault() {
        return INSTANCE;
    }

    private PluginsLookup() {
        mConnections = new ArrayList<ProviderConnection>();
        mDSPConnections = new ArrayList<DSPConnection>();
        mConnectionListeners = new ArrayList<ConnectionListener>();
        mHandler = new Handler();
    }

    public void initialize(Context context) {
        mContext = context;
        MultiProviderPlaylistProvider multiproviderPlaylistProvider = new MultiProviderPlaylistProvider(mContext);
        requestUpdatePlugins();

        // Inject our Multi-Provider Playlist provider
        HashMap<String, String> item = new HashMap<String, String>();
        item.put(DATA_PACKAGE, "org.omnirom.music.providers");
        item.put(DATA_SERVICE, "org.omnirom.music.providers.MultiProviderPlaylistProvider");
        item.put(DATA_NAME, "MultiProviderPlaylistProvider");
        item.put(DATA_AUTHOR, "The OmniROM Project");
        item.put(DATA_CONFIGCLASS, null);
        mMultiProviderConnection = new ProviderConnection(mContext,item.get(DATA_NAME),
                item.get(DATA_AUTHOR),
                item.get(DATA_PACKAGE), item.get(DATA_SERVICE),
                item.get(DATA_CONFIGCLASS));

        mMultiProviderConnection.setListener(mProviderListener);
        mConnections.add(mMultiProviderConnection);
        mMultiProviderConnection.onServiceConnected(new ComponentName("org.omnirom.music.providers", "org.omnirom.music.providers.MultiProviderPlaylistProvider"),
                multiproviderPlaylistProvider.asBinder());

        connectPlayback();
    }

    public void incPlaybackUsage() {
        mServiceUsage++;
        connectPlayback();
    }

    public void decPlaybackUsage() {
        mServiceUsage--;

        if (mServiceUsage == 0) {
            releasePlaybackServiceIfPossible();
        }
    }

    public void requestUpdatePlugins() {
        new Thread() {
            public void run() {
                updatePlugins();
            }
        }.start();
    }

    public void registerProviderListener(ConnectionListener listener) {
        mConnectionListeners.add(listener);
    }

    public void removeProviderListener(ConnectionListener listener) {
        mConnectionListeners.remove(listener);
    }

    public ProviderConnection getMultiProviderPlaylistProvider(){
        return mMultiProviderConnection;
    }

    /**
     * Connects the app to the playback service
     */
    private void connectPlayback() {
        mHandler.removeCallbacks(mShutdownPlaybackRunnable);

        if (mPlaybackService == null) {
            Intent i = new Intent(mContext, PlaybackService.class);
            mContext.bindService(i, mPlaybackConnection,
                    Context.BIND_AUTO_CREATE | Context.BIND_ABOVE_CLIENT);
        }
    }

    /**
     * Update the list of plugins
     */
    public void updatePlugins() {
        fetchProviders();
        fetchDSPs();
    }

    public void releasePlaybackServiceIfPossible() {
        if (mPlaybackService != null) {
            final int state = PlaybackProxy.getState();

            if (state == PlaybackService.STATE_PAUSED || state == PlaybackService.STATE_STOPPED) {
                releasePlaybackService();
            }
        }
    }

    private void releasePlaybackService() {
        if (mPlaybackService != null) {
            mHandler.removeCallbacks(mShutdownPlaybackRunnable);
            mHandler.postDelayed(mShutdownPlaybackRunnable, 1000);
        }
    }

    public void tearDown(NativeHub hub) {
        Log.i(TAG, "tearDown()");
        releasePlaybackService();

        synchronized (mConnections) {
            for (ProviderConnection connection : mConnections) {
                connection.unbindService(hub);
            }
            for (DSPConnection connection : mDSPConnections) {
                connection.unbindService(hub);
            }
        }
    }

    IPlaybackService getPlaybackService() {
        return getPlaybackService(true);
    }

    IPlaybackService getPlaybackService(boolean connectIfUnavailable) {
        if (mPlaybackService == null && connectIfUnavailable) {
            connectPlayback();
        }
        return mPlaybackService;
    }

    public ProviderConnection getProvider(ProviderIdentifier id) {
        if (id == null) {
            Log.e(TAG, "getProvider called with null identifier");
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            for (StackTraceElement element : stack) {
                Log.e(TAG, element.toString());
            }
            return null;
        }

        synchronized (mConnections) {
            for (ProviderConnection connection : mConnections) {
                if (connection.getIdentifier().equals(id)) {
                    return connection;
                }
            }
        }

        return null;
    }

    public DSPConnection getDSP(ProviderIdentifier id) {
        synchronized (mDSPConnections) {
            for (DSPConnection connection : mDSPConnections) {
                if (connection.getIdentifier().equals(id)) {
                    return connection;
                }
            }
        }

        return null;
    }

    /**
     * Returns the list of available music content providers. See DATA_** for the list of keys
     * available
     * @return A list of providers available. Each ProviderConnection is mutable, which means
     * you can bind and unbind the instances as you wish.
     */
    public List<ProviderConnection> getAvailableProviders() {
        // That list may be modified, so we return a copy
        synchronized (mConnections) {
            return new ArrayList<ProviderConnection>(mConnections);
        }
    }

    public List<DSPConnection> getAvailableDSPs() {
        synchronized (mDSPConnections) {
            return new ArrayList<DSPConnection>(mDSPConnections);
        }
    }

    /**
     * Read all the services providers from the package manager for the PICK_PROVIDER action
     */
    private List<HashMap<String, String>> fetchProviders() {
        Intent baseIntent = new Intent(Constants.ACTION_PICK_PROVIDER);
        // baseIntent.setFlags(Intent.FLAG_DEBUG_LOG_RESOLUTION);

        return fetchServicesForIntent(baseIntent, false);
    }

    /**
     * Read all the services providers from the package manager for the PICK_DSP_PROVIDER action
     */
    private List<HashMap<String, String>> fetchDSPs() {
        Intent baseIntent = new Intent(Constants.ACTION_PICK_DSP_PROVIDER);
        return fetchServicesForIntent(baseIntent, true);
    }

    private synchronized List<HashMap<String, String>> fetchServicesForIntent(Intent intent, boolean isDSP) {
        PackageManager pm = mContext.getPackageManager();
        assert(pm != null);

        List<HashMap<String, String>> services = new ArrayList<HashMap<String, String>>();

        // We query the PackageManager for all services implementing this intent
        List<ResolveInfo> list = pm.queryIntentServices(intent,
                PackageManager.GET_META_DATA | PackageManager.GET_RESOLVED_FILTER);

        for (ResolveInfo info : list) {
            ServiceInfo sinfo = info.serviceInfo;

            if (sinfo != null) {
                HashMap<String, String> item = new HashMap<String, String>();
                item.put(DATA_PACKAGE, sinfo.packageName);
                item.put(DATA_SERVICE, sinfo.name);

                if (sinfo.metaData != null) {
                    item.put(DATA_NAME, sinfo.metaData.getString(Constants.METADATA_PROVIDER_NAME));
                    item.put(DATA_CONFIGCLASS, sinfo.metaData.getString(Constants.METADATA_CONFIG_CLASS));
                    item.put(DATA_AUTHOR, sinfo.metaData.getString(Constants.METADATA_PROVIDER_AUTHOR));
                }

                String providerName = item.get(DATA_NAME);
                if (DEBUG) Log.d(TAG, "Found providers plugin: " + sinfo.packageName + ", "
                        + sinfo.name + ", name:" + providerName);

                if (providerName != null) {
                    boolean found = false;
                    synchronized (mDSPConnections) {
                        for (DSPConnection conn : mDSPConnections) {
                            if (conn.getPackage().equals(sinfo.packageName)
                                    && conn.getServiceName().equals(sinfo.name)) {
                                found = true;
                                conn.bindService();
                                break;
                            }
                        }
                    }

                    if (!found) {
                        synchronized (mConnections) {
                            for (ProviderConnection conn : mConnections) {
                                if (conn.getPackage().equals(sinfo.packageName)
                                        && conn.getServiceName().equals(sinfo.name)) {
                                    found = true;
                                    conn.bindService();
                                    break;
                                }
                            }
                        }
                    }

                    if (!found) {
                        if (isDSP) {
                            DSPConnection conn = new DSPConnection(mContext, providerName,
                                    item.get(DATA_AUTHOR),
                                    item.get(DATA_PACKAGE), item.get(DATA_SERVICE),
                                    item.get(DATA_CONFIGCLASS));
                            conn.setListener(mProviderListener);
                            synchronized (mDSPConnections) {
                                mDSPConnections.add(conn);
                            }
                        } else {
                            ProviderConnection conn = new ProviderConnection(mContext, providerName,
                                    item.get(DATA_AUTHOR),
                                    item.get(DATA_PACKAGE), item.get(DATA_SERVICE),
                                    item.get(DATA_CONFIGCLASS));
                            conn.setListener(mProviderListener);
                            synchronized (mConnections) {
                                mConnections.add(conn);
                            }
                        }

                    }
                }

                services.add(item);
            }
        }

        return services;
    }

    public RecyclingBitmapDrawable getCachedLogo(final Resources res, final BoundEntity entity) {
        return getCachedLogo(res, entity.getProvider(), entity.getLogo());
    }

    public RecyclingBitmapDrawable getCachedLogo(final Resources res, ProviderIdentifier id, String ref) {
        RecyclingBitmapDrawable output = ImageCache.getDefault().get(res, ref);
        if (output == null && id != null) {
            try {
                IMusicProvider binder = getProvider(id).getBinder();
                if (binder != null) {
                    Bitmap bmp = getProvider(id).getBinder().getLogo(ref);

                    if (bmp != null) {
                        output = ImageCache.getDefault().put(res, ref, bmp, true);
                    }
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to get source logo", e);
            }
        }

        return output;
    }

}
