package org.omnirom.music.service;

import android.os.SystemClock;
import android.util.Log;

import org.omnirom.music.app.Utils;

/**
 * Class responsible for grabbing the audio from a provider, pushing it through the DSP chain,
 * and drawable it to a sink
 */
public class DSPProcessor {

    private static final String TAG = "DSPProcessor";

    private static final int DEFAULT_SAMPLE_RATE = 44100;
    private static final int DEFAULT_CHANNELS = 2;

    private AudioSink mSink;
    private int mSampleRate = DEFAULT_SAMPLE_RATE;
    private int mChannels = DEFAULT_CHANNELS;
    private long mLastRmsPoll = 0;
    private int mLastRms = 0;
    private PlaybackService mPlaybackService;

    /**
     * Default constructor
     */
    public DSPProcessor(PlaybackService pbs) {
        mPlaybackService = pbs;
    }

    /**
     * Defines the active sink. Please note that the sink MUST support the existing sample rate
     * and channels configuration, or at least the default sample rate and channel count.
     * @param sink The new sink to which the audio will be directed
     */
    public void setSink(AudioSink sink) {
        mSink = sink;
        if (!mSink.setup(mSampleRate, mChannels)) {
            mSampleRate = DEFAULT_SAMPLE_RATE;
            mChannels = DEFAULT_CHANNELS;

            if (mSink.setup(mSampleRate, mChannels)) {
                Log.w(TAG, "Sink doesn't support existing audio settings, reset to default");
            } else {
                throw new IllegalArgumentException("The sink doesn't support the active sample rate" +
                        "and channel count, neither the default settings");
            }
        }
    }

    /**
     * Configures the active sink (or any future sink if none is currently active) with the provided
     * sample rate and channels count.
     * @param sampleRate The new sample rate, in number of samples per second
     * @param channels The number of channels (1 = mono, 2 = stereo)
     * @return true if the setup succeeded, false otherwise
     */
    public boolean setupSink(int sampleRate, int channels) {
        // We retain the setup information here so that we can reapply them if we switch sinks
        mSampleRate = sampleRate;
        mChannels = channels;

        if (mSink != null) {
            return mSink.setup(mSampleRate, mChannels);
        } else {
            return true;
        }
    }

    /**
     * Inputs audio from the provider to the DSP chain, and then the active sink.
     * Note that while AudioSocketHost does some buffering, it's up to the sink to handle any
     * overflowing data.
     * @param frames Incoming frames, as short samples (only INT16 is supported)
     * @param numframes The number of frames to read from the array
     */
    public void inputAudio(short[] frames, int numframes) {
        // TODO: Write this to the DSP chain, then the DSP drawable to the sink
        if (mSink != null) {
            mSink.write(frames, numframes);
        }

        // We have audio frames, so don't shutdown the service
        mPlaybackService.resetShutdownTimeout();
    }

    /**
     * Returns the current RMS level of the last 1/60 * sampleRate frames
     * @return The RMS level
     */
    public int getRms() {
        final long currTime = SystemClock.elapsedRealtime();
        if (/*currTime - mLastRmsPoll >= 1000/60 && */mSink != null) {
            short[] rmsFrames = mSink.getRmsSamples();
            mLastRms = Utils.calculateRMSLevel(rmsFrames, rmsFrames.length);
            mLastRmsPoll = currTime;
        }

        return mLastRms;
    }
}
