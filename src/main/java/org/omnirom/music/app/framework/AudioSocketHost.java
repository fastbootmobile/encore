package org.omnirom.music.app.framework;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.SystemClock;
import android.util.Log;

import org.omnirom.music.provider.AudioSocket;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

/**
 *
 */
public class AudioSocketHost {

    private static final String TAG = "AudioSocketHost";

    private AudioTrack mAudioTrack;
    private short[] mAudioBuffer = new short[262144];
    private int mAudioBufferOffset = 0;
    private ByteBuffer mIntBuffer;
    private ByteBuffer mSamplesBuffer;

    private boolean mStop = false;
    private LocalServerSocket mSocket;
    private Thread mListenThread;
    private Thread mAudioPushThread;

    /**
     * Runnable for the thread responsible for processing network data
     */
    private Runnable mListenRunnable = new Runnable() {
        @Override
        public void run() {
            while (!mStop) {
                try {
                    Log.e(TAG, "AudioSocketHost waiting on incoming connection...");
                    LocalSocket client = mSocket.accept();
                    //BufferedInputStream bufferInput = new BufferedInputStream(client.getInputStream());
                    InputStream inputStream = client.getInputStream();
                    Log.e(TAG, "AudioSocketHost Client connected");

                    // We keep on reading data as long as the provider socket is connected.
                    // We assume here that DataInputStream is blocking and that all packets
                    // arrive in order.
                    while (true) {
                        //Log.e(TAG, "Waiting data...");
                        byte opcode = (byte) inputStream.read();

                        switch (opcode) {
                            case AudioSocket.OPCODE_FORMAT:
                                handlePacketFormat(inputStream);
                                break;

                            case AudioSocket.OPCODE_DATA:
                                handlePacketData(inputStream);
                                break;

                            case -1:
                                break;

                            default:
                                Log.e(TAG, "Unhandled input opcode: " + opcode);
                                break;
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "I/O error during socket operations", e);
                }

            }
        }
    };

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

                    // Playback audio, if any
                    if (mAudioBufferOffset > 0) {
                        int ret = mAudioTrack.write(mAudioBuffer, 0, mAudioBufferOffset);

                        if (ret == AudioTrack.ERROR_INVALID_OPERATION) {
                            Log.e(TAG, "INVALID OPERATION while writing to AudioTrack");
                        } else if (ret == AudioTrack.ERROR_BAD_VALUE) {
                            Log.e(TAG, "BAD VALUE while writing to AudioTrack");
                        } else {
                            if (ret < mAudioBufferOffset) {
                                Log.w(TAG, "AudioTrack didn't write everything from the buffer!");
                                final int remaining = mAudioBufferOffset - ret;
                                System.arraycopy(mAudioBuffer, ret, mAudioBuffer, 0, remaining);
                                mAudioBufferOffset = remaining + 1;
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
     * Creates and binds the server socket to the provided socket name
     * @param socketName The name of the socket to bind
     * @throws IOException
     */
    public AudioSocketHost(final String socketName) throws IOException {
        mIntBuffer = ByteBuffer.allocateDirect(4);
        mSamplesBuffer = ByteBuffer.allocateDirect(262144 * 2);
        mSocket = new LocalServerSocket(socketName);
        Log.i(TAG, "Created AudioSocketHost " + socketName);
    }

    /**
     * Starts the listening thread for incoming audio data. Before the same socket name can be
     * bound again, you must call release().
     * Note that an AudioSocketHost only handles one and only one provider (ie. one client connected
     * to the socket at a time).
     */
    public void startListening() {
        mStop = false;

        // Release the previous listener, if any
        releaseThread(mListenThread);
        mListenThread = new Thread(mListenRunnable);
        mListenThread.start();

        releaseThread(mAudioPushThread);
        mAudioPushThread = new Thread(mAudioPushRunnable);
        mAudioPushThread.start();
    }

    /**
     * Releases the socket and the listening threads
     */
    public void release() {
        mStop = true;

        releaseThread(mListenThread);
        releaseThread(mAudioPushThread);

        try {
            mSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "Unable to release the socket", e);
        }

        if (mAudioTrack != null) {
            mAudioTrack.stop();
            mAudioTrack.release();
            mAudioTrack = null;
        }
    }

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
     * Handles an incoming OPCODE_FORMAT packet
     * @param in The incoming data
     * @throws IOException
     */
    private void handlePacketFormat(InputStream in) throws IOException {
        byte channels = (byte) in.read();
        mIntBuffer.rewind();
        for (int i = 0; i < 4; i++) {
            int input = in.read();
            Log.e("FUCK", "THIS FUCKING SHIT READ " + input + " (i=" + i + ")");
            mIntBuffer.put((byte) input);
        }

        int sampleRate = mIntBuffer.getInt(0);

        setupAudioTrack(sampleRate, channels);
    }

    /**
     * Handles an incoming OPCODE_DATA packet
     * @param in The incoming data
     * @throws IOException
     */
    private void handlePacketData(InputStream in) throws IOException {
        mIntBuffer.rewind();
        for (int i = 0; i < 4; i++) {
            mIntBuffer.put((byte) in.read());
        }
        final int numFrames = mIntBuffer.getInt(0);


        synchronized (mAudioPushRunnable) {
            long timeMs = SystemClock.currentThreadTimeMillis();
            int totalRead = 0;
            int totalToRead = numFrames * 2;
            int sizeToRead = totalToRead;

            while (totalRead < totalToRead) {
                int read = in.read(mSamplesBuffer.array(), totalRead, sizeToRead);
                if (read >= 0) {
                    totalRead += read;
                }
                sizeToRead = totalToRead - totalRead;
            }

            for (int i = 0; i < numFrames; i++) {
                if (mAudioBufferOffset < mAudioBuffer.length) {
                    mAudioBuffer[mAudioBufferOffset + i] = mSamplesBuffer.getShort(i*2);
                } else {
                    Log.w(TAG, "Audio buffer is full! Dropping audio samples!");
                    break;
                }
            }

            mAudioBufferOffset += numFrames;

            // Log.i(TAG, "Read " + numFrames + " shorts in " + (SystemClock.currentThreadTimeMillis() - timeMs) + "ms");
            mAudioPushRunnable.notify();
        }
    }

    /**
     * Setups the audio track output. This is done upon incoming OPCODE_FORMAT packet. Note that
     * the AudioTrack is (re)created only if there is no AudioTrack already, or if the settings
     * changed.
     *
     * @param sampleRate The sample rate, in samples per second (Hz)
     * @param channels The number of channels
     */
    private void setupAudioTrack(int sampleRate, int channels) {
        if (mAudioTrack != null) {
            // An audio track already exists for this host. We check if the playback settings
            // changed, otherwise we keep on using the same track.
            if (mAudioTrack.getSampleRate() != sampleRate || mAudioTrack.getChannelCount() != channels) {
                // We stop the current audio track, and we'll make a new one
                mAudioTrack.stop();
                mAudioTrack.release();
                mAudioTrack = null;
            }
        }

        // We create a new audio track if we released the existing one or if we never had any for
        // this host.
        if (mAudioTrack == null) {
            mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
                    channels == 2 ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT) * 8,
                    AudioTrack.MODE_STREAM);

            mAudioTrack.play();

            Log.i(TAG, "Created audio track: " + sampleRate + " Hz, " + channels + " channels");
        }
    }
}
