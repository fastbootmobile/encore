package org.omnirom.music.app;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PluginsLookup {

    private static final boolean DEBUG = BuildConfig.DEBUG;
    private static final String TAG = "PluginsLookup";

    private static final String ACTION_PICK_PROVIDER = "org.omnirom.music.action.PICK_PROVIDER";

    private static final String DATA_PACKAGE = "package";
    private static final String DATA_SERVICE = "service";

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
     * Read all the services providers from the package manager for the PICK_PROVIDER action
     */
    private List<HashMap<String, String>> fetchProviders() {
        Intent baseIntent = new Intent(ACTION_PICK_PROVIDER);
        baseIntent.setFlags(Intent.FLAG_DEBUG_LOG_RESOLUTION);

        return fetchServicesForIntent(baseIntent);
    }

    private List<HashMap<String, String>> fetchServicesForIntent(Intent intent) {
        PackageManager pm = mContext.getPackageManager();
        assert(pm != null);

        List<HashMap<String, String>> services = new ArrayList<HashMap<String, String>>();

        // We query the PackageManager for all services implementing this intent
        List<ResolveInfo> list = pm.queryIntentServices(intent, PackageManager.GET_RESOLVED_FILTER);

        for (ResolveInfo info : list) {
            ServiceInfo sinfo = info.serviceInfo;

            if (sinfo != null) {
                HashMap<String, String> item = new HashMap<String, String>();
                item.put(DATA_PACKAGE, sinfo.packageName);
                item.put(DATA_SERVICE, sinfo.name);

                if (DEBUG) Log.d(TAG, "Found provider plugin: " + sinfo.packageName + ", " + sinfo.name);

                services.add(item);
            }
        }

        return services;
    }
}
