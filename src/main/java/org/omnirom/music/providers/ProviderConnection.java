package org.omnirom.music.providers;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
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
    private boolean mIsBound;

    public ProviderConnection(Context ctx, String providerName, String pkg, String serviceName,
                              String configActivity) {
        mContext = ctx;
        mProviderName = providerName;
        mPackage = pkg;
        mServiceName = serviceName;
        mConfigurationActivity = configActivity;

        mIsErroneous = false;
        mIsBound = false;

        // Try to bind to the service
        bindService();
    }

    public IMusicProvider getBinder() {
        return mBinder;
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
        if (mIsBound) {
            if (DEBUG) Log.d(TAG, "UNBinding service...");
            mContext.unbindService(this);
            mIsBound = false;
        }
    }

    public void bindService() {
        if (mIsBound) {
            Log.w(TAG, "bindService(): Service seems already bound");
            return;
        }

        if (DEBUG) Log.d(TAG, "Binding service...");
        Intent i = new Intent();
        i.setClassName(mPackage, mServiceName);
        mContext.bindService(i, this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mBinder = IMusicProvider.Stub.asInterface(service);
        ProviderAggregator.getDefault().registerProvider(this);
        mIsBound = true;
        if (DEBUG) Log.d(TAG, "Connected to provider " + name);

        // Automatically try to login the provider once bound
        try {
            if (mBinder.isSetup() && !mBinder.isAuthenticated()) {
                Log.d(TAG, "Provider is setup! Trying to log in!");
                if (!mBinder.login()) {
                    Log.e(TAG, "Error while requesting login!");
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Remote exception occurred on the set provider", e);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        ProviderAggregator.getDefault().unregisterProvider(this);
        mBinder = null;
        mIsBound = false;
        if (DEBUG) Log.d(TAG, "Disconnected from provider " + name);
    }
}
