package org.omnirom.music.framework;

import android.util.Log;

import org.java_websocket.WebSocket;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

/**
 * Created by Guigui on 10/09/2014.
 */
public class WSStreamer extends WebSocketServer {
    private static final String TAG = "WSStreamer";

    private WebSocket mClient;

    public WSStreamer(int port) throws UnknownHostException {
        super(new InetSocketAddress(port));
    }

    public WSStreamer(InetSocketAddress addr) {
        super(addr);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake clientHandshake) {
        Log.e(TAG, "Client connected: " + conn.getRemoteSocketAddress().getAddress().getHostAddress());
        if (mClient != null) {
            Log.e(TAG, "Client kicked as we already have one");
            conn.closeConnection(0, "Only one client is allowed");
        } else {
            mClient = conn;
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String s, boolean b) {
        Log.e(TAG, "Client disconnected");
        if (conn == mClient) {
            mClient = null;
        }
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
        if (mClient != null) {
            if (mClient.isOpen()) {
                mClient.send(frames);
            } else {
                mClient = null;
            }
        }
    }

}
