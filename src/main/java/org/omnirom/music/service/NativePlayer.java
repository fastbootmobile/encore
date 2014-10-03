package org.omnirom.music.service;

import android.util.Log;

/**
 * Created by Guigui on 26/08/2014.
 */
public class NativePlayer {

    private static final String TAG = "NativePlayer";

    static {
        System.loadLibrary("c++_shared");
        System.loadLibrary("protobuf");
        System.loadLibrary("nativeplayerjni");
    }


    private long mHandle;

    public NativePlayer() {
        Log.e(TAG, "NATIVE INITIALIZE");
        nativeInitialize();
    }

    public boolean setAudioFormat(int sample_rate, int channels, int depth) {
        return nativeSetAudioFormat(sample_rate, channels, depth);
    }

    public int enqueue(byte[] data, int length) {
        if (data != null && length > 0) {
            return nativeEnqueue(data, length);
        } else {
            return 0;
        }
    }

    public int enqueue(short[] data, int length) {
        if (data != null && length > 0) {
            return nativeEnqueueShort(data, length);
        } else {
            return 0;
        }
    }

    private native boolean nativeInitialize();
    private native boolean nativeSetAudioFormat(int sample_rate, int channels, int depth);
    private native int nativeEnqueue(byte[] data, int length);
    private native int nativeEnqueueShort(short[] data, int length);
}
