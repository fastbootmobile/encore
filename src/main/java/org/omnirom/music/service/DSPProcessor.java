package org.omnirom.music.service;

import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import org.omnirom.music.app.Utils;
import org.omnirom.music.framework.AudioSocketHost;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.providers.AudioSocket;
import org.omnirom.music.providers.DSPConnection;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderConnection;
import org.omnirom.music.providers.ProviderIdentifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
    private List<ProviderIdentifier> mDSPChain;

    private AudioSocketHost.AudioSocketCallback mProviderCallback = new AudioSocketHost.AudioSocketCallback() {
        @Override
        public void onAudioInput(AudioSocketHost socket, short[] frames, int numFrames) {
            // If we have plugins setup in our DSP chain, process through them, otherwise just
            // consume the audio on the sink directly
            if (mDSPChain.size() > 0) {
                inputProviderAudio(frames, numFrames);
            } else {
                inputDSPAudio(socket, frames, numFrames);
            }
        }

        @Override
        public void onFormatInput(AudioSocketHost socket, int channels, int sampleRate) {
            setupSink(sampleRate, channels);
        }
    };

    private AudioSocketHost.AudioSocketCallback mDSPCallback = new AudioSocketHost.AudioSocketCallback() {
        @Override
        public void onAudioInput(AudioSocketHost socket, short[] frames, int numFrames) {
            inputDSPAudio(socket, frames, numFrames);
        }

        @Override
        public void onFormatInput(AudioSocketHost socket, int channels, int sampleRate) {

        }
    };

    /**
     * Default constructor
     */
    public DSPProcessor(PlaybackService pbs) {
        mPlaybackService = pbs;
        mDSPChain = new ArrayList<ProviderIdentifier>();
    }

    public AudioSocketHost.AudioSocketCallback getProviderCallback() {
        return mProviderCallback;
    }

    public AudioSocketHost.AudioSocketCallback getDSPCallback() {
        return mDSPCallback;
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
    public void inputProviderAudio(short[] frames, int numframes) {
        // Feed the audio to the first element in the DSP chain
        feedPlugin(mDSPChain.get(0), frames, numframes);
    }

    public void inputDSPAudio(AudioSocketHost socket, short[] frames, int numframes) {
        // Check if we have more plugins to go through
        PluginsLookup plugins = PluginsLookup.getDefault();
        int currentPlugin = 0;
        for (ProviderIdentifier pi : mDSPChain) {
            DSPConnection dsp = plugins.getDSP(pi);
            if (dsp.getAudioSocket().getName().equals(socket.getName())) {
                break;
            }
            currentPlugin++;
        }

        if (mDSPChain.size() == 0 || currentPlugin == mDSPChain.size() - 1) {
            // We're at the end of the DSP chain, so push that out to the current audio sink
            if (mSink != null) {
                mSink.write(frames, numframes);
            }
        } else {
            // More plugins, feed them
            feedPlugin(mDSPChain.get(currentPlugin + 1), frames, numframes);
        }

        // We have audio frames, so don't shutdown the service
        mPlaybackService.resetShutdownTimeout();
    }

    private void feedPlugin(ProviderIdentifier id, short[] frames, int numFrames) {
        DSPConnection conn = PluginsLookup.getDefault().getDSP(id);
        AudioSocketHost host = conn.getAudioSocket();
        if (host == null) {
            // DSP effects don't signal their connectivity state, so they're not set-up at the
            // same time as the providers
            host = mPlaybackService.assignProviderAudioSocket(conn);
        }
        try {
            host.writeAudioData(frames, numFrames);
        } catch (IOException e) {
            // Plugin died, try to reconnect the socket and try again
            mPlaybackService.assignProviderAudioSocket(conn);

            try {
                host.writeAudioData(frames, numFrames);
            } catch (IOException e1) {
                // Failed again, fallback to output
                if (mSink != null){
                    mSink.write(frames, numFrames);
                }
            }
        }
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

    public void setActiveChain(List<ProviderIdentifier> chain) {
        // We make a copy to avoid any external modification
        mDSPChain = new ArrayList<ProviderIdentifier>(chain);
    }

    public List<ProviderIdentifier> getActiveChain() {
        return mDSPChain;
    }
}
