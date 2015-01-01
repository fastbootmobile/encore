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

package org.omnirom.music.service;

import android.util.Log;

import org.omnirom.music.framework.WSStreamer;

import java.io.IOException;
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
    private boolean mIsStreamerUp;

    // Used in native code
    private long mHandle;
    private byte[] mAudioMirrorBuffer;

    /**
     * Default constructor
     */
    public NativeHub() {
        Log.i(TAG, "Initializing native hub");

        // Create the audio mirror buffer to stream audio to WebSocket, and the WS itself
        mAudioMirrorBuffer = new byte[262144];
        try {
            mStreamer = new WSStreamer(8887);
        } catch (UnknownHostException e) {
            Log.e(TAG, "Error: Cannot create WebSocket Streamer on port 8887", e);
        }

        nativeInitialize();
    }

    /**
     * Called when the playback service starts
     */
    public void onStart() {
        mStreamer.start();
    }

    /**
     * Called when the playback service stops
     */
    public void onStop() {
        try {
            mStreamer.stop();
        } catch (IOException e) {
            Log.e(TAG, "IOException while stopping WS Streamer", e);
        } catch (InterruptedException e) {
            Log.e(TAG, "InterruptedException while stopping WS Streamer", e);
        }
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

    // Called from native code
    public void onAudioMirrorWritten(int len) {
        mStreamer.write(mAudioMirrorBuffer, len);
    }

    // Native methods
    private native boolean nativeInitialize();
    private native void nativeSetDSPChain(String[] chain);
    private native boolean nativeCreateHostSocket(String name, boolean isDsp);
    private native void nativeReleaseHostSocket(String name);
    private native void nativeSetSinkPointer(long handle);
    private native void nativeSetDucking(boolean duck);
}
