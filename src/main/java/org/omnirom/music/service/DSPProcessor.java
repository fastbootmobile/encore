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

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.protobuf.ByteString;

import org.omnirom.music.app.Utils;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.framework.WSStreamer;
import org.omnirom.music.providers.AudioHostSocket;
import org.omnirom.music.providers.AudioSocket;
import org.omnirom.music.providers.DSPConnection;
import org.omnirom.music.providers.ProviderIdentifier;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import omnimusic.Plugin;

/**
 * Class responsible for grabbing the audio from a provider, pushing it through the DSP chain,
 * and playing it to a sink
 */
public class DSPProcessor {
    private static final String TAG = "DSPProcessor";

    private static final String PREFS_DSP_CHAIN = "DSP_Chain";
    private static final String PREF_KEY_CHAIN = "chain";

    private static final int DEFAULT_SAMPLE_RATE = 44100;
    private static final int DEFAULT_CHANNELS = 2;

    private AudioSink mSink;
    private int mSampleRate = DEFAULT_SAMPLE_RATE;
    private int mChannels = DEFAULT_CHANNELS;
    private int mLastRms = 0;
    private WSStreamer mStreamer;
    private PlaybackService mPlaybackService;
    private List<ProviderIdentifier> mDSPChain;
    private AudioSocket mLastProviderSocket;

    private AudioSocket.ISocketCallback mProviderCallback = new AudioSocket.ISocketCallback() {
        @Override
        public void onAudioData(AudioSocket socket, Plugin.AudioData message) {
            // If we have plugins setup in our DSP chain, process through them, otherwise just
            // consume the audio on the sink directly
            ByteString bytes = message.getSamples();
            byte[] data = bytes.toByteArray();
            int length = bytes.size();

            mLastProviderSocket = socket;

            if (length > 0) {
                if (mDSPChain.size() > 0) {
                    inputProviderAudio(data, length);
                } else {
                    inputDSPAudio(socket, data, length);
                }
            } else {
                if (mSink != null) {
                    mSink.flushSamples();
                }
            }
        }

        @Override
        public void onAudioResponse(AudioSocket socket, Plugin.AudioResponse message) {
        }

        @Override
        public void onRequest(AudioSocket socket, Plugin.Request message) {
            Plugin.Request.RequestType request = message.getRequest();

            try {
                switch (request) {
                    case BUFFER_INFO:
                        if (mSink != null) {
                            socket.writeBufferInfo((int) mSink.getWrittenSamples(), mSink.getDropouts());
                        }
                        break;

                    case FORMAT_INFO:
                        socket.writeFormatData(mChannels, mSampleRate);
                        break;
                }
            } catch (IOException e) {
                Log.e(TAG, "Cannot send response to request", e);
            }
        }

        @Override
        public void onFormatInfo(AudioSocket socket, Plugin.FormatInfo message) {
            setupSink(message.getSamplingRate(), message.getChannels());

           /* if (mDSPChain.size() > 0) {
                // Notify the DSP of the new audio format as well
                PluginsLookup lookup = PluginsLookup.getDefault();
                for (ProviderIdentifier id : mDSPChain) {
                    DSPConnection conn = lookup.getDSP(id);
                    if (conn.getAudioSocket() != null) {
                        try {
                            conn.getAudioSocket().writeFormatData(message.getChannels(),
                                    message.getSamplingRate());
                        } catch (IOException e) {
                            Log.e(TAG, "Cannot notify " + id + " of new format", e);
                        }
                    }
                }
            }*/
        }

        @Override
        public void onBufferInfo(AudioSocket socket, Plugin.BufferInfo message) {
        }
    };

    private AudioSocket.ISocketCallback mDSPCallback = new AudioSocket.ISocketCallback() {
        @Override
        public void onAudioData(AudioSocket socket, Plugin.AudioData message) {
            final ByteString bytes = message.getSamples();
            inputDSPAudio(socket, bytes.toByteArray(), bytes.size());
        }

        @Override
        public void onAudioResponse(AudioSocket socket, Plugin.AudioResponse message) {

        }

        @Override
        public void onRequest(AudioSocket socket, Plugin.Request message) {

        }

        @Override
        public void onFormatInfo(AudioSocket socket, Plugin.FormatInfo message) {

        }

        @Override
        public void onBufferInfo(AudioSocket socket, Plugin.BufferInfo message) {

        }
    };

    /**
     * Default constructor
     */
    public DSPProcessor(PlaybackService pbs) {
        mPlaybackService = pbs;
        mDSPChain = new ArrayList<ProviderIdentifier>();
        try {
            mStreamer = new WSStreamer(8887);
            mStreamer.start();
        } catch (UnknownHostException e) {
            Log.e(TAG, "Error port 8887", e);
        }
    }

    public AudioSocket.ISocketCallback getProviderCallback() {
        return mProviderCallback;
    }

    public AudioSocket.ISocketCallback getDSPCallback() {
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
            // We have an active sink, so set the stuff directly
            if (!mSink.setup(mSampleRate, mChannels)) {
                Log.e(TAG, "Sink cannot setup at " + mSampleRate + " Hz, " + channels + " channels");
                return false;
            } else {
                return true;
            }
        } else {
            // We don't have a sink, so we assume it's set properly and it will be set when a sink
            // is created.
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
    public void inputProviderAudio(byte[] frames, int numframes) {
        // Feed the audio to the first element in the DSP chain
        feedPlugin(mDSPChain.get(0), frames, numframes);
    }

    /**
     * Inputs audio from a DSP plugin (or the provider if there are no DSP plugins) to the next
     * plugin in the chain of the active sink if there are no more plugins.
     * @param socket The incoming socket
     * @param frames Incoming frames, as short samples (only INT16 is supported)
     * @param numframes The number of frames to read from the array
     */
    public void inputDSPAudio(AudioSocket socket, byte[] frames, int numframes) {
        // Check if we have more plugins to go through
        PluginsLookup plugins = PluginsLookup.getDefault();
        int currentPlugin = 0;
        for (ProviderIdentifier pi : mDSPChain) {
            DSPConnection dsp = plugins.getDSP(pi);
            if (dsp.getAudioSocket().getSocketName().equals(socket.getSocketName())) {
                break;
            }
            currentPlugin++;
        }

        if (mDSPChain.size() == 0 || currentPlugin == mDSPChain.size() - 1) {
            // We're at the end of the DSP chain, so push that out to the current audio sink
            if (mSink != null) {
                int written = mSink.write(frames, numframes);
                writeAudioResponse(written);
            }
            if (mStreamer != null) {
                mStreamer.write(frames, numframes);
            }
        } else {
            // More plugins, feed them
            feedPlugin(mDSPChain.get(currentPlugin + 1), frames, numframes);
        }

        // We have audio frames, so don't shutdown the service
        mPlaybackService.resetShutdownTimeout();
    }

    private void feedPlugin(ProviderIdentifier id, byte[] frames, int numFrames) {
        DSPConnection conn = PluginsLookup.getDefault().getDSP(id);
        AudioHostSocket host = conn.getAudioSocket();
        if (host == null) {
            // DSP effects don't signal their connectivity state, so they're not set-up at the
            // same time as the providers
            host = mPlaybackService.assignProviderAudioSocket(conn);
        }

        try {
            host.writeAudioData(frames, 0, numFrames);
        } catch (IOException e) {
            // Plugin died, try to reconnect the socket and try again
            mPlaybackService.assignProviderAudioSocket(conn);

            try {
                host.writeAudioData(frames, 0, numFrames);
            } catch (IOException e1) {
                // Failed again, fallback to output
                if (mSink != null){
                    int written = mSink.write(frames, numFrames);
                    writeAudioResponse(written);
                }
            }
        }
    }

    /**
     * Returns the current RMS level of the last 1/60 * sampleRate frames
     * @return The RMS level
     */
    public int getRms() {
        if (mSink != null) {
            short[] rmsFrames = mSink.getRmsSamples();
            mLastRms = Utils.calculateRMSLevel(rmsFrames, rmsFrames.length);
        }

        return mLastRms;
    }

    /**
     * Sets the current active DSP plugins chain and saves it
     * @param ctx A valid context
     * @param chain The chain of plugins to use
     */
    public void setActiveChain(Context ctx, List<ProviderIdentifier> chain) {
        // We make a copy to avoid any external modification
        mDSPChain = new ArrayList<ProviderIdentifier>(chain);

        // Save it
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_DSP_CHAIN, 0);
        Set<String> identifiers = new TreeSet<String>();

        for (ProviderIdentifier identifier : chain) {
            identifiers.add(identifier.serialize());
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(PREF_KEY_CHAIN, identifiers);
        editor.apply();
    }

    /**
     * @return The current active chain of DSP plugins
     */
    public List<ProviderIdentifier> getActiveChain() {
        return mDSPChain;
    }

    /**
     * Restores the saved chain
     * @param ctx A valid context
     */
    public void restoreChain(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_DSP_CHAIN, 0);
        Set<String> identifiers = prefs.getStringSet(PREF_KEY_CHAIN, null);
        mDSPChain = new ArrayList<ProviderIdentifier>();

        if (identifiers != null) {
            for (String id : identifiers) {
                ProviderIdentifier identifier = ProviderIdentifier.fromSerialized(id);
                if (identifier != null) {
                    mDSPChain.add(identifier);
                } else {
                    Log.e(TAG, "Cannot restore from serialized string " + id);
                }
            }
        }
    }

    private void writeAudioResponse(int written) {
        if (mLastProviderSocket != null) {
            try {
                mLastProviderSocket.writeAudioResponse(written);
            } catch (IOException e) {
                Log.e(TAG, "Cannot write audio response", e);
            }
        }
    }
}
