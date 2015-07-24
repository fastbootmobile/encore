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

package com.fastbootmobile.encore.framework;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.art.ImageCache;
import com.fastbootmobile.encore.art.RecyclingBitmapDrawable;
import com.fastbootmobile.encore.model.BoundEntity;
import com.fastbootmobile.encore.providers.AbstractProviderConnection;
import com.fastbootmobile.encore.providers.Constants;
import com.fastbootmobile.encore.providers.DSPConnection;
import com.fastbootmobile.encore.providers.IMusicProvider;
import com.fastbootmobile.encore.providers.InjectedProviderConnection;
import com.fastbootmobile.encore.providers.MultiProviderPlaylistProvider;
import com.fastbootmobile.encore.providers.ProviderConnection;
import com.fastbootmobile.encore.providers.ProviderIdentifier;
import com.fastbootmobile.encore.service.IPlaybackService;
import com.fastbootmobile.encore.service.NativeHub;
import com.fastbootmobile.encore.service.PlaybackService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Plugins Manager class
 */
public class PluginsLookup {

    private static final boolean DEBUG = false;
    private static final String TAG = "PluginsLookup";

    public static final int BUNDLED_PROVIDERS_COUNT = 2; // Local and Multi-provider

    public static final String DATA_PACKAGE = "package";
    public static final String DATA_SERVICE = "service";
    public static final String DATA_NAME = "name";
    public static final String DATA_AUTHOR = "author";
    public static final String DATA_CONFIGCLASS = "configclass";

    private static final String PREFS_PLUGINS = "plugins";
    private static final String PREF_KNOWN_PLUGINS = "known_plugins";

    public interface ConnectionListener {
        void onServiceConnected(AbstractProviderConnection connection);
        void onServiceDisconnected(AbstractProviderConnection connection);
    }

    private Context mContext;
    private final List<ProviderConnection> mConnections;
    private final List<DSPConnection> mDSPConnections;
    private List<ConnectionListener> mConnectionListeners;
    private IPlaybackService mPlaybackService;
    private MultiProviderPlaylistProvider mMultiProviderPlaylistProvider;
    private ProviderConnection mMultiProviderConnection;
    private Handler mHandler;
    private int mServiceUsage;
    private Set<ProviderConnection> mNewServices;
    private ServiceConnection mPlaybackConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mPlaybackService = IPlaybackService.Stub.asInterface(service);
            PlaybackProxy.notifyPlaybackConnected();
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
            mContext.stopService(new Intent(mContext, PlaybackService.class));
            mContext.unbindService(mPlaybackConnection);
            mPlaybackService = null;
        }
    };

    private static final PluginsLookup INSTANCE = new PluginsLookup();

    public static PluginsLookup getDefault() {
        return INSTANCE;
    }

    private PluginsLookup() {
        mConnections = new ArrayList<>();
        mDSPConnections = new ArrayList<>();
        mConnectionListeners = new ArrayList<>();
        mNewServices = new TreeSet<>();
        mHandler = new Handler();
    }

    public void initialize(Context context) {
        mContext = context;
        requestUpdatePlugins();
        injectMultiProviderPlaylistProvider();
        connectPlayback();
    }

    private void injectMultiProviderPlaylistProvider() {
        // Inject our Multi-Provider Playlist provider
        mMultiProviderPlaylistProvider = new MultiProviderPlaylistProvider(mContext);
        mMultiProviderConnection = injectProvider(mMultiProviderPlaylistProvider,
                "com.fastbootmobile.encore.providers", "com.fastbootmobile.encore.providers.MultiProviderPlaylistProvider",
                mContext.getString(R.string.multiprovider_name), "The OmniROM Project", null);
    }

    private InjectedProviderConnection injectProvider(IMusicProvider provider, String pkg,
                                                      String service, String name, String author,
                                                      String configClass) {
        InjectedProviderConnection pc = new InjectedProviderConnection(provider, mContext, name,
                author, pkg, service, configClass);
        pc.setListener(mProviderListener);
        synchronized (mConnections) {
            mConnections.add(pc);
        }

        return pc;
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
            mContext.startService(i);
            mContext.bindService(i, mPlaybackConnection, Context.BIND_ABOVE_CLIENT);
        }
    }

    /**
     * Update the list of plugins
     */
    public void updatePlugins() {
        fetchProviders();
        fetchDSPs();

        SharedPreferences prefs = mContext.getSharedPreferences(PREFS_PLUGINS, 0);
        Set<String> knownPlugins = new TreeSet<>(prefs.getStringSet(PREF_KNOWN_PLUGINS, new TreeSet<String>()));

        if (knownPlugins.size() == 0) {
            // First start, fill in all plugins
            for (ProviderConnection connection : mConnections) {
                knownPlugins.add(connection.getServiceName());
            }

            prefs.edit().putStringSet(PREF_KNOWN_PLUGINS, knownPlugins).apply();
        } else {
            // Check if there's any new plugin
            for (ProviderConnection connection : mConnections) {
                if (!knownPlugins.contains(connection.getServiceName())) {
                    knownPlugins.add(connection.getServiceName());
                    mNewServices.add(connection);
                }
            }

            prefs.edit().putStringSet(PREF_KNOWN_PLUGINS, knownPlugins).apply();
        }
    }

    public Set<ProviderConnection> getNewPlugins() {
        return mNewServices;
    }

    public void resetNewPlugins() {
        mNewServices.clear();
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

    public ProviderConnection getProviderByName(final String name) {
        synchronized (mConnections) {
            for (ProviderConnection connection : mConnections) {
                if (connection.getProviderName().equalsIgnoreCase(name)) {
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
            return new ArrayList<>(mConnections);
        }
    }

    public List<DSPConnection> getAvailableDSPs() {
        synchronized (mDSPConnections) {
            return new ArrayList<>(mDSPConnections);
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

        List<HashMap<String, String>> services = new ArrayList<>();

        // We query the PackageManager for all services implementing this intent
        List<ResolveInfo> list = pm.queryIntentServices(intent,
                PackageManager.GET_META_DATA | PackageManager.GET_RESOLVED_FILTER);

        for (ResolveInfo info : list) {
            ServiceInfo sinfo = info.serviceInfo;

            if (sinfo != null) {
                HashMap<String, String> item = new HashMap<>();
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
        RecyclingBitmapDrawable output = ImageCache.getDefault().get(res, ref, 500);
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
