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

package com.fastbootmobile.encore.cast;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.text.format.Formatter;
import android.util.Log;

import com.fastbootmobile.encore.framework.PlaybackProxy;
import com.fastbootmobile.encore.model.Artist;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.ProviderAggregator;
import com.fastbootmobile.encore.service.BasePlaybackCallback;
import com.fastbootmobile.encore.service.IPlaybackCallback;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Module allowing casting to Chromecast and other MediaRouter-enabled receivers
 */
public class CastModule extends MediaRouter.Callback {
    private static final String TAG = "CastModule";
    private static final String CAST_APP_ID = "FB626268";

    private static final String JSON_KEY_COMMAND = "command";
    private static final String COMMAND_CONNECT = "connect";
    private static final String COMMAND_EVT_SONGSTARTED = "songstarted";
    private static final String COMMAND_EVT_PAUSED = "paused";
    private static final String COMMAND_EVT_RESUMED = "resumed";

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
    private boolean mIsAppUp;

    private IPlaybackCallback.Stub mPlaybackCallback = new BasePlaybackCallback() {
        @Override
        public void onSongStarted(boolean buffering, Song s) throws RemoteException {
            if (mIsAppUp) {
                final ProviderAggregator aggregator = ProviderAggregator.getDefault();
                Artist a = aggregator.retrieveArtist(s.getArtist(), s.getProvider());

                try {
                    JSONObject msg = new JSONObject();
                    msg.put(JSON_KEY_COMMAND, COMMAND_EVT_SONGSTARTED);
                    msg.put("title", s.getTitle());
                    msg.put("artist", a != null ? a.getName() : "<Not loaded>");
                    msg.put("duration", s.getDuration());

                    mCastChannel.sendMessage(mApiClient, msg.toString());
                } catch (JSONException e) {
                    Log.e(TAG, "Cannot create JSON to notify Cast of new song", e);
                }
            }
        }

        @Override
        public void onPlaybackPause() throws RemoteException {
            if (mIsAppUp) {
                try {
                    JSONObject msg = new JSONObject();
                    msg.put(JSON_KEY_COMMAND, COMMAND_EVT_PAUSED);
                    mCastChannel.sendMessage(mApiClient, msg.toString());
                } catch (JSONException e) {
                    Log.e(TAG, "Cannot create JSON to notify Cast of pause event", e);
                }
            }
        }

        @Override
        public void onPlaybackResume() throws RemoteException {
            if (mIsAppUp) {
                try {
                    JSONObject msg = new JSONObject();
                    msg.put(JSON_KEY_COMMAND, COMMAND_EVT_RESUMED);
                    mCastChannel.sendMessage(mApiClient, msg.toString());
                } catch (JSONException e) {
                    Log.e(TAG, "Cannot create JSON to notify Cast of resume event", e);
                }
            }
        }
    };

    /**
     * Default constructor
     * @param ctx The host context of the module
     */
    public CastModule(Context ctx) {
        mContext = ctx;
        mHandler = new Handler();
        mConnectionCallbacks = new ConnectionCallbacks();
        mConnectionFailedListener = new ConnectionFailedListener();

        mMediaRouter = MediaRouter.getInstance(ctx);
        mSelector = new MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(CAST_APP_ID))
                .build();

        mCastListener = new CastListener();
        mCastChannel = new CastChannel();
    }

    /**
     * Called when the main activity starts
     */
    public void onStart() {
        mMediaRouter.addCallback(mSelector, this, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
        PlaybackProxy.addCallback(mPlaybackCallback);
    }

    /**
     * Called when the main activity stops or pauses
     */
    public void onStop() {
        mMediaRouter.removeCallback(this);
        PlaybackProxy.removeCallback(mPlaybackCallback);
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
            Log.d(TAG, "Acquiring controller for " + mSelectedDevice);
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
                Log.w(TAG, "Error while creating a device controller", e);
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
            object.put(JSON_KEY_COMMAND, COMMAND_CONNECT);
            object.put("address", getWiFiIpAddress());
        } catch (JSONException e) {
            Log.e(TAG, "Cannot build JSON object!", e);
        }

        mCastChannel.sendMessage(mApiClient, object.toString());
        mIsAppUp = true;
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
            try {
                String status = Cast.CastApi.getApplicationStatus(mApiClient);
                Log.d(TAG, "onApplicationStatusChanged; status=" + status);

                if (mShouldStart && mApiClient.isConnected()) {
                    Cast.CastApi.launchApplication(mApiClient, CAST_APP_ID, true)
                            .setResultCallback(new ApplicationConnectionResultCallback("LaunchApp"));
                    mShouldStart = false;
                } else {
                    mIsAppUp = false;
                }
            } catch (IllegalStateException e) {
                // Not connected to a device
                mIsAppUp = false;
            }
        }

        @Override
        public void onApplicationDisconnected(int statusCode) {
            Log.d(TAG, "onApplicationDisconnected: statusCode=" + statusCode);
            mIsAppUp = false;
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
