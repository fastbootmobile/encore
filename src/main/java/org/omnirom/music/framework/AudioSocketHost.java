package org.omnirom.music.framework;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.SystemClock;
import android.util.Log;

import org.omnirom.music.providers.AudioSocket;
import org.omnirom.music.service.DSPProcessor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

/**
 * Host socket for the audio coming from a provider
 */
public class AudioSocketHost {

    public interface AudioSocketCallback {
        public void onAudioInput(AudioSocketHost socket, short[] frames, int numFrames);
        public void onFormatInput(AudioSocketHost socket, int channels, int sampleRate);
    }

    private static final String TAG = "AudioSocketHost";

    private ByteBuffer mIntBuffer;
    private ByteBuffer mSamplesBuffer;
    private short[] mSamplesShortBuffer = new short[262144];

    private boolean mStop = false;
    private LocalServerSocket mSocket;
    private Thread mListenThread;
    private String mSocketName;
    private InputStream mInputStream;
    private OutputStream mOutputStream;
    private AudioSocketCallback mCallback;

    /**
     * Runnable for the thread responsible for processing network data
     */
    private Runnable mListenRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                while (!mStop) {
                    LocalSocket client = mSocket.accept();
                    mInputStream = client.getInputStream();
                    mOutputStream = client.getOutputStream();

                    Log.e(TAG, "NOT STOP: " + mOutputStream);

                    // We keep on reading data as long as the providers socket is connected.
                    // We assume here that InputStream is blocking and that all packets
                    // arrive in order.
                    while (!mStop) {
                        if (mInputStream == null) break;

                        int opcode = mInputStream.read();

                        if (opcode == -1) break;

                        switch (opcode) {
                            case AudioSocket.OPCODE_FORMAT:
                                handlePacketFormat(mInputStream);
                                break;

                            case AudioSocket.OPCODE_DATA:
                                handlePacketData(mInputStream);
                                break;

                            default:
                                // If the provider is shifting information
                                Log.e(TAG, "Unhandled input opcode: " + opcode);
                                throw new IOException("Unhandled input opcode " + opcode);
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "I/O error during socket operations", e);
            }

        }
    };


    /**
     * Creates and binds the server socket to the provided socket name
     *
     * @param socketName The name of the socket to bind
     * @throws IOException
     */
    public AudioSocketHost(final String socketName) throws IOException {
        mIntBuffer = ByteBuffer.allocateDirect(4);
        mSamplesBuffer = ByteBuffer.allocateDirect(262144 * 2);
        mSocketName = socketName;
        mSocket = new LocalServerSocket(socketName);
        Log.i(TAG, "Created AudioSocketHost " + socketName);
    }

    public String getName() {
        return mSocketName;
    }

    public void setCallback(AudioSocketCallback callback) {
        mCallback = callback;
    }

    /**
     * Starts the listening thread for incoming audio data. Before the same socket name can be
     * bound again, you must call release().
     * Note that an AudioSocketHost only handles one and only one providers (ie. one client connected
     * to the socket at a time).
     */
    public void startListening() {
        mStop = false;
        if (mListenThread != null) {
            mListenThread.interrupt();
            try {
                mListenThread.join(10);
            } catch (InterruptedException e) {
                // ignore
            }
        }
        mListenThread = new Thread(mListenRunnable);
        mListenThread.start();
    }

    /**
     * Releases the socket and the listening threads
     */
    public void release() {
        mStop = true;

        try {
            if (mInputStream != null) {
                mInputStream.close();
                mInputStream = null;
            }

            mSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "Unable to release the socket", e);
        }
        releaseThread(mListenThread);
    }

    private void releaseThread(Thread t) {
        if (t != null) {
            t.interrupt();
            try {
                t.join(50);
            } catch (InterruptedException e) {
                Log.e(TAG, "Error while stopping thread", e);
            }
        }
    }

    /**
     * Handles an incoming OPCODE_FORMAT packet
     *
     * @param in The incoming data
     * @throws IOException
     */
    private void handlePacketFormat(InputStream in) throws IOException {
        byte channels = (byte) in.read();
        mIntBuffer.rewind();
        for (int i = 0; i < 4; i++) {
            int input = in.read();
            mIntBuffer.put((byte) input);
        }
        int sampleRate = mIntBuffer.getInt(0);

        Log.i(TAG, "Handling packet format: " + channels + ", sampleRate=" + sampleRate);

        if (mCallback != null) {
            mCallback.onFormatInput(this, channels, sampleRate);
        }
    }

    /**
     * Handles an incoming OPCODE_DATA packet
     *
     * @param in The incoming data
     * @throws IOException
     */
    private void handlePacketData(InputStream in) throws IOException {
        // long timeMs = SystemClock.currentThreadTimeMillis();

        if (in.read(mIntBuffer.array(), 0, 4) != 4) {
            Log.e(TAG, "Reading an int but read didn't return 4 bytes!");
        }
        final int numFrames = mIntBuffer.getInt(0);

        final int totalToRead = numFrames * 2; // 1 short = 2 bytes
        int totalRead = 0;
        int sizeToRead = totalToRead;

        while (totalRead < totalToRead) {
            int read = in.read(mSamplesBuffer.array(), totalRead, sizeToRead);
            if (read >= 0) {
                totalRead += read;
            }
            sizeToRead = totalToRead - totalRead;
        }

        mSamplesBuffer.asShortBuffer().get(mSamplesShortBuffer);
        if (mCallback != null) {
            mCallback.onAudioInput(this, mSamplesShortBuffer, numFrames);
        }

        // Log.i(TAG, "Read " + numFrames + " frames in " + (SystemClock.currentThreadTimeMillis() - timeMs) + "ms");
    }

    /**
     * Writes audio data back to the socket
     * @param frames The frames to write
     */
    public void writeAudioData(short[] frames, int numFrames) throws IOException {
        if (numFrames > mSamplesBuffer.capacity()) {
            throw new IllegalArgumentException("You must not pass more than " +
                    mSamplesBuffer.capacity() + " samples at a time");
        }
        /**
         * Audio data packet:
         * OPCODE_DATA      [byte]
         * NUM_FRAMES       [int] (number of short values to read)
         * SAMPLES          [short[]]
         */
        if (numFrames > 0 && mOutputStream != null) {
            mOutputStream.write(AudioSocket.OPCODE_DATA);
            mIntBuffer.rewind();
            mIntBuffer.putInt(numFrames);
            mOutputStream.write(mIntBuffer.array());

            mSamplesBuffer.rewind();
            mSamplesBuffer.asShortBuffer().put(frames, 0, numFrames);

            mOutputStream.write(mSamplesBuffer.array(), 0, numFrames * 2);

            mOutputStream.flush();
        }
    }
}
