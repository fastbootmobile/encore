package org.omnirom.music.framework;

import android.util.Log;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Collection;

/**
 * Created by Guigui on 10/09/2014.
 */
public class WSStreamer extends WebSocketServer {
    private static final String TAG = "WSStreamer";

    public WSStreamer(int port) throws UnknownHostException {
        super(new InetSocketAddress(port));
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
        Log.d(TAG, "Client message: " + message);
    }

    @Override
    public void onError(WebSocket conn, Exception e) {
        Log.e(TAG, "Error occurred on socket", e);
    }

    public void write(byte[] frames, int numframes) {
        Collection<WebSocket> clients = connections();
        for (WebSocket client : clients) {
            if (client.isOpen()) {
                client.send(frames);
            }
        }
    }

}
