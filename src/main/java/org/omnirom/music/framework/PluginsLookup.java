package org.omnirom.music.framework;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.media.Image;
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
import org.omnirom.music.service.PlaybackService;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PluginsLookup {

    private static final boolean DEBUG = BuildConfig.DEBUG;
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
    private List<ProviderConnection> mConnections;
    private List<DSPConnection> mDSPConnections;
    private List<ConnectionListener> mConnectionListeners;
    private IPlaybackService mPlaybackService;
    private MultiProviderPlaylistProvider mMultiProviderPlaylistProvider;
    private ProviderConnection mMultiProviderConnection;
    private ServiceConnection mPlaybackConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mPlaybackService = (IPlaybackService) iBinder;
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

    private static final PluginsLookup INSTANCE = new PluginsLookup();

    public static PluginsLookup getDefault() {
        return INSTANCE;
    }

    private PluginsLookup() {
        mConnections = new ArrayList<ProviderConnection>();
        mDSPConnections = new ArrayList<DSPConnection>();
        mConnectionListeners = new ArrayList<ConnectionListener>();
    }

    public void initialize(Context context) {
        mContext = context;
        mMultiProviderPlaylistProvider = new MultiProviderPlaylistProvider(mContext);
        new Thread() {
            public void run() {
                updatePlugins();
            }
        }.start();

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
        mMultiProviderConnection.onServiceConnected(new ComponentName("org.omnirom.music.providers","org.omnirom.music.providers.MultiProviderPlaylistProvider"),
                mMultiProviderPlaylistProvider.asBinder());
    }

    public void registerProviderListener(ConnectionListener listener) {
        mConnectionListeners.add(listener);
    }
    public ProviderConnection getMultiProviderPlaylistProvider(){
        return mMultiProviderConnection;
    }
    public void removeProviderListener(ConnectionListener listener) {
        mConnectionListeners.remove(listener);
    }

    public void connectPlayback() {
        Intent i = new Intent(mContext, PlaybackService.class);
        mContext.startService(i);
        mContext.bindService(i, mPlaybackConnection, Context.BIND_AUTO_CREATE);
    }

    public void updatePlugins() {
        fetchProviders();
        fetchDSPs();
    }

    public void tearDown() {
        Log.i(TAG, "tearDown()");
        if (mPlaybackService != null) {
            mContext.unbindService(mPlaybackConnection);
            mPlaybackService = null;
        }
        for (ProviderConnection connection : mConnections) {
            connection.unbindService();
        }
        mConnections.clear();
    }

    public IPlaybackService getPlaybackService() {
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

        for (ProviderConnection connection : mConnections) {
            if (connection.getIdentifier().equals(id)) {
                return connection;
            }
        }

        return null;
    }

    public DSPConnection getDSP(ProviderIdentifier id) {
        for (DSPConnection connection : mDSPConnections) {
            if (connection.getIdentifier().equals(id)) {
                return connection;
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
        Log.e(TAG, "Available providers: " + mConnections.size());
        // That list may be modified, so we return a copy
        return new ArrayList<ProviderConnection>(mConnections);
    }

    public List<DSPConnection> getAvailableDSPs() {
        return new ArrayList<DSPConnection>(mDSPConnections);
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

    private List<HashMap<String, String>> fetchServicesForIntent(Intent intent, boolean isDSP) {
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
                    for (DSPConnection conn : mDSPConnections) {
                        if (conn.getPackage().equals(sinfo.packageName)
                                && conn.getServiceName().equals(sinfo.name)) {
                            found = true;
                            conn.bindService();
                            break;
                        }
                    }

                    if (!found) {
                        for (ProviderConnection conn : mConnections) {
                            if (conn.getPackage().equals(sinfo.packageName)
                                    && conn.getServiceName().equals(sinfo.name)) {
                                found = true;
                                conn.bindService();
                                break;
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
                            mDSPConnections.add(conn);
                        } else {
                            ProviderConnection conn = new ProviderConnection(mContext, providerName,
                                    item.get(DATA_AUTHOR),
                                    item.get(DATA_PACKAGE), item.get(DATA_SERVICE),
                                    item.get(DATA_CONFIGCLASS));
                            conn.setListener(mProviderListener);
                            mConnections.add(conn);
                        }

                    }
                }

                services.add(item);
            }
        }

        return services;
    }

    public Bitmap getCachedLogo(BoundEntity entity) {
        return getCachedLogo(entity.getProvider(), entity.getLogo());
    }

    public Bitmap getCachedLogo(ProviderIdentifier id, String ref) {
        Bitmap output = ImageCache.getDefault().get(ref);
        if (output == null && id != null) {
            try {
                IMusicProvider binder = getProvider(id).getBinder();
                if (binder != null) {
                    output = getProvider(id).getBinder().getLogo(ref);

                    if (output != null) {
                        ImageCache.getDefault().put(ref, output, true);
                    }
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to get source logo", e);
            }
        }

        return output;
    }

}
