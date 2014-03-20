package org.omnirom.music.providers;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import org.omnirom.music.app.BuildConfig;

public class ProviderConnection implements ServiceConnection {
    private static final boolean DEBUG = BuildConfig.DEBUG;
    private static final String TAG = "ProviderConnection";

    private String mProviderName;
    private String mPackage;
    private String mServiceName;
    private String mConfigurationActivity;
    private Context mContext;
    private boolean mIsErroneous;
    private IMusicProvider mBinder;

    public ProviderConnection(Context ctx, String providerName, String pkg, String serviceName,
                              String configActivity) {
        mContext = ctx;
        mProviderName = providerName;
        mPackage = pkg;
        mServiceName = serviceName;
        mConfigurationActivity = configActivity;

        mIsErroneous = false;

        // Try to bind to the service
        bindService();
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

    /**
     * @return true if binding to the service caused an error, false if the service works.
     */
    public boolean isErroneous() {
        return mIsErroneous;
    }

    public void unbindService() {
        if (mBinder == null) {
            Log.w(TAG, "unbindService(): Service is either not bound or already unbound");
            return;
        }

        mContext.unbindService(this);
    }

    public void bindService() {
        if (mBinder != null) {
            Log.w(TAG, "bindService(): Service seems already bound");
            return;
        }

        Intent i = new Intent();
        i.setClassName(mPackage, mServiceName);
        mContext.bindService(i, this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mBinder = IMusicProvider.Stub.asInterface(service);
        if (DEBUG) Log.d(TAG, "Connected to provider " + name);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        mBinder = null;
        if (DEBUG) Log.d(TAG, "Disconnected from provider " + name);
    }
}
