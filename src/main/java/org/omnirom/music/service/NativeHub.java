package org.omnirom.music.service;

import android.util.Log;

import org.omnirom.music.framework.WSStreamer;

import java.net.UnknownHostException;

/**
 * Native Hub for audio sockets
 */
public class NativeHub {
    private static final String TAG = "NativeHub";

    static {
        System.loadLibrary("protobuf");
        System.loadLibrary("nativeplayerjni");
    }

    private WSStreamer mStreamer;

    // Used in native code
    private long mHandle;
    private byte[] mAudioMirrorBuffer;

    /**
     * Default constructor
     */
    public NativeHub() {
        Log.i(TAG, "Initializing native hub");

        // Create the audio mirror buffer to stream audio to WebSocket, and the WS itself
        mAudioMirrorBuffer = new byte[32768];
        try {
            mStreamer = new WSStreamer(8887);
            mStreamer.start();
        } catch (UnknownHostException e) {
            Log.e(TAG, "Error: Cannot create WebSocket Streamer on port 8887", e);
        }

        nativeInitialize();
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
     * Sets the native pointer of the active audio sink
     * @param handle The pointer
     */
    public void setSinkPointer(long handle) {
        nativeSetSinkPointer(handle);
    }

    // Called from native code
    public void onAudioMirrorWritten(int len) {
        mStreamer.write(mAudioMirrorBuffer, len);
    }

    // Native methods
    private native boolean nativeInitialize();
    private native void nativeSetDSPChain(String[] chain);
    private native boolean nativeCreateHostSocket(String name, boolean isDsp);
    private native void nativeSetSinkPointer(long handle);
}
