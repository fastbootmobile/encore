package org.omnirom.music.framework;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.media.MediaControlIntent;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
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

import org.omnirom.music.app.R;

import java.io.IOException;

/**
 * Created by Guigui on 10/09/2014.
 */
public class CastModule extends MediaRouter.Callback {
    private static final String TAG = "CastModule";

    private Context mContext;
    private Handler mHandler;

    private MediaRouter mMediaRouter;
    private CastPresentation mPresentation;
    private MediaRouteSelector mSelector;
    private CastDevice mSelectedDevice;
    private GoogleApiClient mApiClient;
    private CastListener mCastListener;
    private ConnectionCallbacks mConnectionCallbacks;
    private ConnectionFailedListener mConnectionFailedListener;

    public CastModule(Context ctx) {
        mContext = ctx;
        mHandler = new Handler();
        mConnectionCallbacks = new ConnectionCallbacks();
        mConnectionFailedListener = new ConnectionFailedListener();

        mMediaRouter = MediaRouter.getInstance(ctx);
        mSelector = new MediaRouteSelector.Builder()
                // These are the framework-supported intents
                .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
                .addControlCategory(CastMediaControlIntent.categoryForCast("FB626268"))
                .build();

        mCastListener = new CastListener();
    }

    public void onStart() {
        mMediaRouter.addCallback(mSelector, this,
                MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
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
        updateCast(route);
    }

    @Override
    public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo route) {
        Log.d(TAG, "onRouteUnselected: route=" + route);

        // secondary output device
        mSelectedDevice = null;
        updateCast(route);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void updateCast(MediaRouter.RouteInfo route) {
        if (mSelectedDevice == null) {
            if ((mApiClient != null) && mApiClient.isConnected()) {
                mApiClient.disconnect();
            }
        } else {
            Log.d(TAG, "acquiring controller for " + mSelectedDevice);
            try {
                Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions.builder(
                        mSelectedDevice, mCastListener);
                apiOptionsBuilder.setVerboseLoggingEnabled(true);

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

    private void attachMediaPlayer() {

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

            if ("Chromecast Home Screen".equals(status)) {
                Cast.CastApi.launchApplication(mApiClient, "FB626268", true)
                        .setResultCallback(new ApplicationConnectionResultCallback("LaunchApp"));
            }
            //setApplicationStatus(status);
        }

        @Override
        public void onApplicationDisconnected(int statusCode) {
            Log.d(TAG, "onApplicationDisconnected: statusCode=" + statusCode);
           /* mAppMetadata = null;
            detachMediaPlayer();
            clearMediaState();
            updateButtonStates();
            if (statusCode != CastStatusCodes.SUCCESS) {
                // This is an unexpected disconnect.
                setApplicationStatus(getString(R.string.status_app_disconnected));
            }*/
        }
    }

    private final class ApplicationConnectionResultCallback implements
            ResultCallback<Cast.ApplicationConnectionResult> {
        private final String mClassTag;

        public ApplicationConnectionResultCallback(String suffix) {
            mClassTag = TAG + "_" + suffix;
        }

        private String statusCodeToString(int code) {
            switch (code) {
                case CastStatusCodes.APPLICATION_NOT_FOUND:
                    return "Application not found";

                case CastStatusCodes.APPLICATION_NOT_RUNNING:
                    return "Application not running";

                case CastStatusCodes.AUTHENTICATION_FAILED:
                    return "Authentication failed";

                case CastStatusCodes.CANCELED:
                    return "Canceled";

                case CastStatusCodes.INTERNAL_ERROR:
                    return "Internal error";

                case CastStatusCodes.INTERRUPTED:
                    return "Interrupted";

                case CastStatusCodes.INVALID_REQUEST:
                    return "Invalid request";

                case CastStatusCodes.MESSAGE_SEND_BUFFER_TOO_FULL:
                    return "Message send buffer too full";

                case CastStatusCodes.MESSAGE_TOO_LARGE:
                    return "Message too large";

                case CastStatusCodes.NETWORK_ERROR:
                    return "Network error";

                case CastStatusCodes.NOT_ALLOWED:
                    return "Not allowed";

                case CastStatusCodes.SUCCESS:
                    return "Success";

                case CastStatusCodes.TIMEOUT:
                    return "Operation timed out";

                case CastStatusCodes.UNKNOWN_ERROR:
                    return "Unknown error";

                default:
                    return null;
            }
        }

        @Override
        public void onResult(Cast.ApplicationConnectionResult result) {
            Status status = result.getStatus();
            Log.d(mClassTag, "ApplicationConnectionResultCallback.onResult: statusCode "
                    + status.getStatusCode() + ": " + statusCodeToString(status.getStatusCode()));
            if (status.isSuccess()) {
                ApplicationMetadata applicationMetadata = result.getApplicationMetadata();
                String sessionId = result.getSessionId();
                String applicationStatus = result.getApplicationStatus();
                boolean wasLaunched = result.getWasLaunched();
                Log.d(mClassTag, "application name: " + applicationMetadata.getName()
                        + ", status: " + applicationStatus + ", sessionId: " + sessionId
                        + ", wasLaunched: " + wasLaunched);
                attachMediaPlayer();
                /*setApplicationStatus(applicationStatus);
                attachMediaPlayer();
                mAppMetadata = applicationMetadata;
                startRefreshTimer();
                updateButtonStates();
                Log.d(mClassTag, "mShouldPlayMedia is " + mShouldPlayMedia);
                if (mShouldPlayMedia) {
                    mShouldPlayMedia = false;
                    Log.d(mClassTag, "now loading media");
                    playMedia(mSelectedMedia);
                } else {
                    // Synchronize with the receiver's state.
                    requestMediaStatus();
                }*/
            } else {
                Log.e(TAG, "App launch error");
            }
        }
    }
}
