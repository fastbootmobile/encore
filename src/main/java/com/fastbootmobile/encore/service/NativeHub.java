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

package com.fastbootmobile.encore.service;

import android.content.Context;
import android.util.Log;

import com.fastbootmobile.encore.cast.WSStreamer;

import org.java_websocket.WebSocketImpl;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Native Hub for audio sockets
 */
public class NativeHub {
    private static final String TAG = "NativeHub";

    static {
        System.loadLibrary("c++_shared");
        System.loadLibrary("protobuf");
        System.loadLibrary("nativeplayerjni");
    }

    private WSStreamer mStreamer;
    private WSStreamer mInsecureStreamer;
    private OnSampleWrittenListener mWrittenListener;

    // Used in native code
    private long mHandle;
    private byte[] mAudioMirrorBuffer;

    /**
     * Default constructor
     */
    public NativeHub(Context context) {
        Log.i(TAG, "Initializing native hub");
        WebSocketImpl.DEBUG = false;

        // Create the audio mirror buffer to stream audio to WebSocket, and the WS itself
        mStreamer = new WSStreamer(8887);

        try {
            String STORETYPE = "BKS";
            // Yeah, that's plain text... We just need an SSL cert,
            // we don't really care if people can decrypt the audio
            // stream, that's not the expected behavior here :)
            String KEYSTORE = "key.bks";
            String STOREPASSWORD = "encore";
            String KEYPASSWORD = "encore";

            KeyStore ks = KeyStore.getInstance(STORETYPE);
            InputStream is = context.getResources().getAssets().open(KEYSTORE);
            ks.load(is, STOREPASSWORD.toCharArray());

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, KEYPASSWORD.toCharArray());
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

            mStreamer.setWebSocketFactory(new DefaultSSLWebSocketServerFactory(sslContext));
            Log.d(TAG, "Web Socket Certificate Loaded");
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Unable to initialize WebSocket TLS security layer", e);
        } catch (KeyManagementException | KeyStoreException | IOException e) {
            Log.e(TAG, "Security error", e);
        } catch (CertificateException e) {
            Log.e(TAG, "Invalid SSL certificate", e);
        } catch (UnrecoverableKeyException e) {
            Log.e(TAG, "Key couldn't be read from keystore", e);
        }

        mInsecureStreamer = new WSStreamer(8886);

        nativeInitialize();
    }

    /**
     * Called when the playback service starts
     */
    public void onStart() {
        if (mAudioMirrorBuffer == null) {
            mAudioMirrorBuffer = new byte[262144];
        }
        if (mStreamer != null) {
            mStreamer.start();
        }
        if (mInsecureStreamer != null) {
            mInsecureStreamer.start();
        }
    }

    /**
     * Called when the playback service stops
     */
    public void onStop() {
        mAudioMirrorBuffer = null;
        try {
            mStreamer.stop();
            mInsecureStreamer.stop();
        } catch (IOException e) {
            Log.e(TAG, "IOException while stopping WS Streamer", e);
        } catch (InterruptedException e) {
            Log.e(TAG, "InterruptedException while stopping WS Streamer", e);
        }

        mStreamer = null;
        mInsecureStreamer = null;

        nativeShutdown();
    }

    /**
     * Sets the currently active DSP chain to use to route the audio signal
     * @param chain The chain to set
     */
    public void setDSPChain(String[] chain) {
        nativeSetDSPChain(chain);
    }

    /**
     * Creates an host socket with the provided name for a provider or DSP
     * @param name The name of the socket to create
     * @param isDsp Set this to true if the socket will be used by a DSP plugin
     * @return true if the socket has been successfully created, false otherwise
     */
    public boolean createHostSocket(String name, boolean isDsp) {
        return nativeCreateHostSocket(name, isDsp);
    }

    /**
     * Releases an host socket and closes all bound connections
     * @param name The name of the socket to release
     */
    public void releaseHostSocket(String name) {
        nativeReleaseHostSocket(name);
    }

    /**
     * Sets the native pointer of the active audio sink
     * @param handle The pointer
     */
    public void setSinkPointer(long handle) {
        nativeSetSinkPointer(handle);
    }

    /**
     * Reduce the volume because some other priority operation is happening
     * @param duck True to reduce volume, false to restore
     */
    public void setDucking(boolean duck) { nativeSetDucking(duck); }

    /**
     * Sets the listener that will be called when samples are written to the sink
     * @param listener The listener to use
     */
    public void setOnAudioWrittenListener(OnSampleWrittenListener listener) {
        mWrittenListener = listener;
    }

    // Called from native code
    public void onAudioMirrorWritten(int len, int sampleRate, int channels) {
        if (mAudioMirrorBuffer != null) {
            mStreamer.write(mAudioMirrorBuffer, len);
            mInsecureStreamer.write(mAudioMirrorBuffer, len);

            // We use audio mirroring writing for tracking track elapsed time
            if (mWrittenListener != null) {
                mWrittenListener.onSampleWritten(mAudioMirrorBuffer, len, sampleRate, channels);
            }
        }
    }

    // Native methods
    private native boolean nativeInitialize();
    private native void nativeShutdown();
    private native void nativeSetDSPChain(String[] chain);
    private native boolean nativeCreateHostSocket(String name, boolean isDsp);
    private native void nativeReleaseHostSocket(String name);
    private native void nativeSetSinkPointer(long handle);
    private native void nativeSetDucking(boolean duck);


    public interface OnSampleWrittenListener {
        void onSampleWritten(byte[] bytes, int len, int sampleRate, int channels);
    }
}
