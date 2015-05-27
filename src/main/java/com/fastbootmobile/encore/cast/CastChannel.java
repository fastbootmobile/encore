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

import android.util.Log;

import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

/**
 * Channel used to communicate with the Chromecast during a cast
 */
public class CastChannel implements Cast.MessageReceivedCallback {
    private static final String TAG = "CastChannel";
    private static final String NAMESPACE = "urn:x-cast:com.fastbootmobile.encore.cast";
    private static final boolean DEBUG = true;

    public CastChannel() {
    }

    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public void onMessageReceived(CastDevice castDevice, String ns, String message) {
        if (DEBUG) Log.d(TAG, "Received message on " + ns + ": " + message);
    }

    public void sendMessage(GoogleApiClient client, String message) {
        if (DEBUG) Log.d(TAG, "Sending message (ns=" + NAMESPACE + "): " + message);
        Cast.CastApi.sendMessage(client, NAMESPACE, message)
                .setResultCallback(new SendMessageResultCallback(message));
    }

    private final class SendMessageResultCallback implements ResultCallback<Status> {
        private String mMessage;

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
