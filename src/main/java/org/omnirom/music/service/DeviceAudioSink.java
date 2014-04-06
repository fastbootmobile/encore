package org.omnirom.music.service;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

/**
 * AudioSink that outputs audio to the device audio chipset (speakers, earbuds, etc)
 */
public class DeviceAudioSink implements AudioSink {

    private static final String TAG = "DeviceAudioSink";

    private AudioTrack mAudioTrack;
    private short[] mAudioBuffer = new short[262144];
    private int mAudioBufferOffset = 0;
    private Thread mAudioPushThread;
    private int mWrittenSamples;
    private final short[] mRmsSamples = new short[8192];

    private boolean mStop;

    /**
     * Runnable for the thread responsible of pushing audio data to the audio device
     */
    private final Runnable mAudioPushRunnable = new Runnable() {
        @Override
        public void run() {
            while (!mStop) {
                synchronized (mAudioPushRunnable) {
                    try {
                        // We wait for incoming data
                        wait();
                    } catch (InterruptedException e) {
                        break;
                    }

                    final int SIZE = 8192;
                    // Playback audio, if any
                    while (mAudioBufferOffset > 0) {
                        final int amountToWrite = Math.min(mAudioBufferOffset, SIZE);
                        int ret = mAudioTrack.write(mAudioBuffer, 0, amountToWrite);

                        if (ret == AudioTrack.ERROR_INVALID_OPERATION) {
                            Log.e(TAG, "INVALID OPERATION while writing to AudioTrack");
                        } else if (ret == AudioTrack.ERROR_BAD_VALUE) {
                            Log.e(TAG, "BAD VALUE while writing to AudioTrack");
                        } else {
                            mWrittenSamples += ret;

                            // Copy the last written samples to the RMS buffer
                            synchronized (mRmsSamples) {
                                final int offset = Math.max(amountToWrite - mRmsSamples.length, 0);
                                final int length = Math.min(mRmsSamples.length, amountToWrite);
                                System.arraycopy(mAudioBuffer, offset, mRmsSamples, 0, length);
                            }

                            if (ret < mAudioBufferOffset) {
                                //Log.w(TAG, "AudioTrack didn't write everything from the buffer!");
                                final int remaining = mAudioBufferOffset - ret;
                                System.arraycopy(mAudioBuffer, ret, mAudioBuffer, 0, remaining);
                                mAudioBufferOffset = remaining;
                            } else {
                                mAudioBufferOffset = 0;
                            }
                        }
                    }
                }
            }
        }
    };

    /**
     * Default constructor
     */
    public DeviceAudioSink() {
        mStop = false;
        mAudioPushThread = new Thread(mAudioPushRunnable);
        mAudioPushThread.start();
    }

    /**
     * Releases the provided thread
     * @param t The thread to release
     */
    private void releaseThread(Thread t) {
        if (t != null) {
            t.interrupt();
            try {
                t.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "Error while stopping thread", e);
            }
        }
    }

    /**
     * Releases all resources used by this audio sink
     */
    @Override
    public void release() {
        mStop = true;
        releaseThread(mAudioPushThread);

        if (mAudioTrack != null) {
            mAudioTrack.stop();
            mAudioTrack.release();
            mAudioTrack = null;
        }
    }

    /**
     * Configures this audio sink with the new settings
     * @param samplerate The sample rate, in number of samples per second
     * @param channels The number of channels, generally 1 for mono and 2 for stereo
     * @return true if the settings are supported, false if an error occured
     */
    @Override
    public boolean setup(int samplerate, int channels) {
        if (mAudioTrack != null) {
            // An audio track already exists for this host. We check if the playback settings
            // changed, otherwise we keep on using the same track.
            if (mAudioTrack.getSampleRate() != samplerate || mAudioTrack.getChannelCount() != channels) {
                // We stop the current audio track, and we'll make a new one
                mAudioTrack.stop();
                mAudioTrack.release();
                mAudioTrack = null;
            }
        }

        // We create a new audio track if we released the existing one or if we never had any for
        // this host.
        if (mAudioTrack == null) {
            try {
                mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, samplerate,
                        channels == 2 ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        AudioTrack.getMinBufferSize(samplerate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT),
                        AudioTrack.MODE_STREAM);

                mAudioTrack.play();

                Log.i(TAG, "Created audio track: " + samplerate + " Hz, " + channels + " channels");
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Unable to setup the audio sink track", e);
                return false;
            }
        }

        return true;
    }

    /**
     * Writes audio data to the sink
     * @param frames Frames to write
     * @param numframes Number of frames
     * @return The number of frames actually written
     */
    @Override
    public int write(short[] frames, int numframes) {
        int maxReadable;
        synchronized (mAudioPushRunnable) {
            // Copy the audio in the buffer, up until we fill our buffer
            maxReadable = Math.min(numframes, mAudioBuffer.length - mAudioBufferOffset);

            System.arraycopy(frames, 0, mAudioBuffer, mAudioBufferOffset, maxReadable);
            mAudioBufferOffset += numframes;

            mAudioPushRunnable.notify();
        }

        return maxReadable;
    }

    @Override
    public int getWrittenSamples() {
        synchronized (mAudioPushRunnable) {
            return mWrittenSamples;
        }
    }

    @Override
    public void flushSamples() {
        synchronized (mAudioPushRunnable) {
            mAudioBufferOffset = 0;
            mAudioTrack.flush();
        }
        mWrittenSamples = 0;
    }

    @Override
    public short[] getRmsSamples() {
        synchronized (mRmsSamples) {
            return mRmsSamples;
        }
    }
}
