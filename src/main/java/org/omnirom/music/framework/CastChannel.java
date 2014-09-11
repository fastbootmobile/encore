package org.omnirom.music.framework;

import android.util.Log;

import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

/**
 * Created by Guigui on 11/09/2014.
 */
public class CastChannel implements Cast.MessageReceivedCallback {
    private static final String TAG = "CastChannel";
    private static final String NAMESPACE = "urn:x-cast:org.omnirom.music.cast";

    public CastChannel() {

    }

    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public void onMessageReceived(CastDevice castDevice, String ns, String message) {
        Log.d(TAG, "Received message on " + ns + ": " + message);
    }

    public void sendMessage(GoogleApiClient client, String message) {
        Log.d(TAG, "Sending message (ns=" + NAMESPACE + "): " + message);
        Cast.CastApi.sendMessage(client, NAMESPACE, message)
                .setResultCallback(new SendMessageResultCallback(message));
    }

    private final class SendMessageResultCallback implements ResultCallback<Status> {
        String mMessage;

        SendMessageResultCallback(String message) {
            mMessage = message;
        }

        @Override
        public void onResult(Status result) {
            if (!result.isSuccess()) {
                Log.d(TAG, "Failed to send message. statusCode: " + result.getStatusCode()
                        + " message: " + mMessage);
            } else {
                Log.d(TAG, "Message send successful");
            }
        }
    }
}
