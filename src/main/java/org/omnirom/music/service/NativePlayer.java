package org.omnirom.music.service;

import android.util.Log;

/**
 * Native (OpenSL) audio playlist
 */
public class NativePlayer {
    private static final String TAG = "NativePlayer";

    static {
        System.loadLibrary("c++_shared");
        System.loadLibrary("protobuf");
        System.loadLibrary("nativeplayerjni");
    }

    // Used in native code
    private long mHandle;

    /**
     * Default constructor
     */
    public NativePlayer() {
        Log.i(TAG, "Initializing native player");
        nativeInitialize();
    }

    /**
     * Sets the input audio format
     * @param sample_rate Sample rate, in hertz (number of samples per second)
     * @param channels Number of channels (generally 2 for stereo)
     * @param depth Bit depth (16 bits generally)
     * @return true if the format is valid and has been set, false otherwise
     */
    public boolean setAudioFormat(int sample_rate, int channels, int depth) {
        return nativeSetAudioFormat(sample_rate, channels, depth);
    }

    /**
     * Enqueue new data to be played
     * @param data Data to be played
     * @param length Number of bytes to read from the array
     * @return Number of bytes written in queue
     */
    public int enqueue(byte[] data, int length) {
        if (data != null && length > 0) {
            return nativeEnqueue(data, length);
        } else {
            return 0;
        }
    }

    /**
     * @return The number of samples in the player buffer
     */
    public int getBufferedCount() {
        return nativeGetBufferedCount();
    }

    private native boolean nativeInitialize();
    private native boolean nativeSetAudioFormat(int sample_rate, int channels, int depth);
    private native int nativeEnqueue(byte[] data, int length);
    private native int nativeEnqueueShort(short[] data, int length);
    private native int nativeGetBufferedCount();
}
