package org.omnirom.music.service;

/**
 * Created by Guigui on 29/08/2014.
 */
public class NativeAudioSink implements AudioSink {

    private NativePlayer mPlayer;

    public NativeAudioSink() {
        mPlayer = new NativePlayer();
    }

    @Override
    public void release() {

    }

    @Override
    public boolean setup(int samplerate, int channels) {
        // Assume 16 bits depth
        return mPlayer.setAudioFormat(samplerate, channels, 16);
    }

    @Override
    public int write(byte[] frames, int numframes) {
        return mPlayer.enqueue(frames, numframes);
    }

    @Override
    public int getWrittenSamples() {
        return 0;
    }

    @Override
    public void flushSamples() {

    }

    @Override
    public short[] getRmsSamples() {
        return new short[0];
    }
}
