package org.omnirom.music.service;

import android.util.Log;

/**
 * Native Hub for audio sockets
 */
public class NativeHub {
    private static final String TAG = "NativeHub";

    static {
        System.loadLibrary("protobuf");
        System.loadLibrary("nativeplayerjni");
    }

    // Used in native code
    private long mHandle;

    /**
     * Default constructor
     */
    public NativeHub() {
        Log.i(TAG, "Initializing native hub");
        nativeInitialize();
    }

    public void setDSPChain(String[] chain) {
        nativeSetDSPChain(chain);
    }

    public boolean createHostSocket(String name, boolean isDsp) {
        return nativeCreateHostSocket(name, isDsp);
    }

    public void setSinkPointer(long handle) {
        nativeSetSinkPointer(handle);
    }

    // Native methods
    private native boolean nativeInitialize();
    private native void nativeSetDSPChain(String[] chain);
    private native boolean nativeCreateHostSocket(String name, boolean isDsp);
    private native void nativeSetSinkPointer(long handle);
}
