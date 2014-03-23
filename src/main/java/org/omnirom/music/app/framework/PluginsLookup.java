package org.omnirom.music.app.framework;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.util.Log;

import org.omnirom.music.app.BuildConfig;
import org.omnirom.music.provider.Constants;
import org.omnirom.music.providers.ProviderConnection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PluginsLookup {

    private static final boolean DEBUG = BuildConfig.DEBUG;
    private static final String TAG = "PluginsLookup";


    public static final String DATA_PACKAGE = "package";
    public static final String DATA_SERVICE = "service";
    public static final String DATA_NAME = "name";
    public static final String DATA_CONFIGCLASS = "configclass";

    private Context mContext;
    private List<HashMap<String, String>> mProviders;

    public PluginsLookup(Context context) {
        mContext = context;
        updatePlugins();
    }

    public void updatePlugins() {
        mProviders = fetchProviders();
        // mDSPs = fetchDSPs();
    }

    /**
     * Returns the list of available music content providers. See DATA_** for the list of keys
     * available
     * @return A list of providers available. Each ProviderConnection is mutable, which means
     * you can bind and unbind the instances as you wish.
     */
    public List<ProviderConnection> getAvailableProviders() {
        List<ProviderConnection> providers = new ArrayList<ProviderConnection>();

        for (HashMap<String, String> providerData : mProviders) {
            String providerName = providerData.get(DATA_NAME);

            if (providerName != null) {
                ProviderConnection conn = new ProviderConnection(mContext, providerName,
                        providerData.get(DATA_PACKAGE), providerData.get(DATA_SERVICE),
                        providerData.get(DATA_CONFIGCLASS));

                providers.add(conn);
            }
        }

        return providers;
    }

    /**
     * Read all the services providers from the package manager for the PICK_PROVIDER action
     */
    private List<HashMap<String, String>> fetchProviders() {
        Intent baseIntent = new Intent(Constants.ACTION_PICK_PROVIDER);
        baseIntent.setFlags(Intent.FLAG_DEBUG_LOG_RESOLUTION);

        return fetchServicesForIntent(baseIntent);
    }

    private List<HashMap<String, String>> fetchServicesForIntent(Intent intent) {
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
                }

                if (DEBUG) Log.d(TAG, "Found provider plugin: " + sinfo.packageName + ", "
                        + sinfo.name + ", name:" + item.get(DATA_NAME));

                services.add(item);
            }
        }

        return services;
    }
}
