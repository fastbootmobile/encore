package org.omnirom.music.framework;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.media.MediaControlIntent;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.text.format.Formatter;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.CastStatusCodes;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import org.json.JSONException;
import org.json.JSONObject;
import org.omnirom.music.app.R;

import java.io.IOException;
import java.net.InetAddress;

/**
 * Created by Guigui on 10/09/2014.
 */
public class CastModule extends MediaRouter.Callback {
    private static final String TAG = "CastModule";


    private Context mContext;
    private Handler mHandler;

    private MediaRouter mMediaRouter;
    private MediaRouteSelector mSelector;
    private CastDevice mSelectedDevice;
    private GoogleApiClient mApiClient;
    private CastListener mCastListener;
    private ConnectionCallbacks mConnectionCallbacks;
    private ConnectionFailedListener mConnectionFailedListener;
    private boolean mShouldStart;
    private CastChannel mCastChannel;

    public CastModule(Context ctx) {
        mContext = ctx;
        mHandler = new Handler();
        mConnectionCallbacks = new ConnectionCallbacks();
        mConnectionFailedListener = new ConnectionFailedListener();

        mMediaRouter = MediaRouter.getInstance(ctx);
        mSelector = new MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast("FB626268"))
                .build();

        mCastListener = new CastListener();
        mCastChannel = new CastChannel();
    }

    public void onStart() {
        mMediaRouter.addCallback(mSelector, this, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
    }

    public void onStop() {
        mMediaRouter.removeCallback(this);
    }

    public MediaRouteSelector getSelector() {
        return mSelector;
    }

    @Override
    public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo route) {
        Log.d(TAG, "onRouteSelected: route=" + route);

        // secondary output device
        mSelectedDevice = CastDevice.getFromBundle(route.getExtras());
        updateCast();
    }

    @Override
    public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo route) {
        Log.d(TAG, "onRouteUnselected: route=" + route);

        // secondary output device
        mSelectedDevice = null;
        updateCast();
    }

    private void updateCast() {
        if (mSelectedDevice == null) {
            if ((mApiClient != null) && mApiClient.isConnected()) {
                mApiClient.disconnect();
            }
        } else {
            Log.d(TAG, "acquiring controller for " + mSelectedDevice);
            try {
                Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions.builder(
                        mSelectedDevice, mCastListener)
                        .setVerboseLoggingEnabled(true);

                mApiClient = new GoogleApiClient.Builder(mContext)
                        .addApi(Cast.API, apiOptionsBuilder.build())
                        .addConnectionCallbacks(mConnectionCallbacks)
                        .addOnConnectionFailedListener(mConnectionFailedListener)
                        .build();

                mApiClient.connect();
            } catch (IllegalStateException e) {
                Log.w(TAG, "error while creating a device controller", e);
            }
        }
    }

    private String getWiFiIpAddress() {
        WifiManager wifiMgr = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
        int ip = wifiInfo.getIpAddress();
        return Formatter.formatIpAddress(ip);
    }

    private void attachMediaPlayer() {
        // Get the device's Wi-Fi IP address (Wi-Fi is obviously enabled for Chromecast)
        JSONObject object = new JSONObject();
        try {
            object.put("command", "connect");
            object.put("address", getWiFiIpAddress());
        } catch (JSONException e) {
            Log.e(TAG, "Cannot build JSON object!", e);
        }

        mCastChannel.sendMessage(mApiClient, object.toString());
    }


    private class ConnectionCallbacks implements GoogleApiClient.ConnectionCallbacks {
        @Override
        public void onConnectionSuspended(int cause) {
            Log.d(TAG, "ConnectionCallbacks.onConnectionSuspended");
        }

        @Override
        public void onConnected(final Bundle connectionHint) {
            Log.d(TAG, "ConnectionCallbacks.onConnected");

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (!mApiClient.isConnected()) {
                        // We got disconnected while this runnable was pending execution.
                        return;
                    }


                    try {
                        mShouldStart = true;
                        Cast.CastApi.requestStatus(mApiClient);
                    } catch (IOException e) {
                        Log.d(TAG, "error requesting status", e);
                    }
                }
            });
        }
    }

    private class ConnectionFailedListener implements GoogleApiClient.OnConnectionFailedListener {
        @Override
        public void onConnectionFailed(ConnectionResult result) {
            Log.d(TAG, "onConnectionFailed");
        }
    }

    private class CastListener extends Cast.Listener {
        @Override
        public void onVolumeChanged() {
            /*refreshDeviceVolume(Cast.CastApi.getVolume(mApiClient),
                    Cast.CastApi.isMute(mApiClient));*/
        }

        @Override
        public void onApplicationStatusChanged() {
            String status = Cast.CastApi.getApplicationStatus(mApiClient);
            Log.d(TAG, "onApplicationStatusChanged; status=" + status);

            if (mShouldStart) {
                Cast.CastApi.launchApplication(mApiClient, "FB626268", true)
                        .setResultCallback(new ApplicationConnectionResultCallback("LaunchApp"));
                mShouldStart = false;
            }
        }

        @Override
        public void onApplicationDisconnected(int statusCode) {
            Log.d(TAG, "onApplicationDisconnected: statusCode=" + statusCode);
        }
    }

    private final class ApplicationConnectionResultCallback implements
            ResultCallback<Cast.ApplicationConnectionResult> {
        private final String mClassTag;

        public ApplicationConnectionResultCallback(String suffix) {
            mClassTag = TAG + "_" + suffix;
        }

        @Override
        public void onResult(Cast.ApplicationConnectionResult result) {
            Status status = result.getStatus();
            Log.d(mClassTag, "ApplicationConnectionResultCallback.onResult: " + status);

            if (status.isSuccess()) {
                // Our app is launched on the Chromecast
                try {
                    Cast.CastApi.setMessageReceivedCallbacks(mApiClient,
                            mCastChannel.getNamespace(), mCastChannel);
                    Log.d(TAG, "Registered callback for " + mCastChannel.getNamespace());
                } catch (IOException e) {
                    Log.w(TAG, "Exception while launching application", e);
                }

                // Ask the device to connect to this sender's websocket
                attachMediaPlayer();
            } else {
                Log.e(TAG, "App launch error");
            }
        }
    }
}
