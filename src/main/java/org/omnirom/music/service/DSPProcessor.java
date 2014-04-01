package org.omnirom.music.service;

public class DSPProcessor {

    private static final int DEFAULT_SAMPLE_RATE = 44100;
    private static final int DEFAULT_CHANNELS = 2;

    private AudioSink mSink;
    private int mSampleRate = DEFAULT_SAMPLE_RATE;
    private int mChannels = DEFAULT_CHANNELS;

    public DSPProcessor() {
    }

    public void setSink(AudioSink sink) {
        mSink = sink;
        mSink.setup(mSampleRate, mChannels);
    }

    public void setupSink(int sampleRate, int channels) {
        // We retain the setup information here so that we can reapply them if we switch sinks
        mSampleRate = sampleRate;
        mChannels = channels;

        if (mSink != null) {
            mSink.setup(mSampleRate, mChannels);
        }
    }

    public void inputAudio(short[] frames, int numframes) {
        // TODO: Write this to the DSP chain, then the DSP output to the sink
        if (mSink != null) {
            mSink.write(frames, numframes);
        }
    }
}
