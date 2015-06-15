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

import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_10;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.drafts.Draft_75;
import org.java_websocket.drafts.Draft_76;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * WebSocket Streaming server class to stream audio to Chromecast and webcast
 */
public class WSStreamer extends WebSocketServer {
    private static final String TAG = "WSStreamer";

    private static final List<Draft> sWSSDrafts = new ArrayList<>();

    static {
        sWSSDrafts.add(new Draft_10());
        sWSSDrafts.add(new Draft_17());
        sWSSDrafts.add(new Draft_75());
        sWSSDrafts.add(new Draft_76());
    }

    public WSStreamer(int port) {
        super(new InetSocketAddress(port), sWSSDrafts);
    }

    public WSStreamer(InetSocketAddress addr) {
        super(addr);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake clientHandshake) {
        Log.d(TAG, "Streaming client connected: "
                + conn.getRemoteSocketAddress().getAddress().getHostAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String s, boolean b) {
        Log.d(TAG, "Streaming client disconnected: " + s);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        // We're not expecting any reply, so just display whatever we received
        Log.d(TAG, "Client message: " + message);
    }

    @Override
    public void onError(WebSocket conn, Exception e) {
        Log.e(TAG, "Error occurred on socket", e);
    }

    public void write(byte[] frames, int numframes) {
        final Collection<WebSocket> clients = connections();
        if (clients.size() > 0) {
            byte[] specificFrames = new byte[numframes];
            System.arraycopy(frames, 0, specificFrames, 0, numframes);

            for (WebSocket client : clients) {
                if (client.isOpen()) {
                    client.send(specificFrames);
                }
            }
        }
    }

}
