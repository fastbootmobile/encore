package org.omnirom.music.framework;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.SystemClock;
import android.util.Log;

import org.omnirom.music.providers.AudioSocket;
import org.omnirom.music.service.DSPProcessor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

/**
 * Host socket for the audio coming from a provider
 */
public class AudioSocketHost {

    private static final String TAG = "AudioSocketHost";

    private ByteBuffer mIntBuffer;
    private ByteBuffer mSamplesBuffer;
    private short[] mSamplesShortBuffer = new short[262144];

    private boolean mStop = false;
    private LocalServerSocket mSocket;
    private Thread mListenThread;
    private DSPProcessor mDSP;
    private String mSocketName;
    private InputStream mInputStream;

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

    public void setDSP(DSPProcessor dsp) {
        mDSP = dsp;
    }

    /**
     * Starts the listening thread for incoming audio data. Before the same socket name can be
     * bound again, you must call release().
     * Note that an AudioSocketHost only handles one and only one providers (ie. one client connected
     * to the socket at a time).
     */
    public void startListening() {
        mStop = false;

        // Release the previous listener, if any. As the thread might be waiting in accept(),
        // we must first close the socket.
        try {
            mSocket.close();
        } catch (IOException e) {
            // Voluntarily left blank
        }
        releaseThread(mListenThread);

        // We then restart the socket
        try {
            mSocket = new LocalServerSocket(mSocketName);
        } catch (IOException e) {
            // Shouldn't happen
            Log.e(TAG, "Cannot re-open server socket for audio input", e);
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

        if (mDSP != null) {
            mDSP.setupSink(sampleRate, channels);
        }
    }

    /**
     * Handles an incoming OPCODE_DATA packet
     *
     * @param in The incoming data
     * @throws IOException
     */
    private void handlePacketData(InputStream in) throws IOException {
        long timeMs = SystemClock.currentThreadTimeMillis();

        in.read(mIntBuffer.array(), 0, 4);
        final int numFrames = mIntBuffer.getInt(0);

        int totalRead = 0;
        int sizeToRead = numFrames;

        while (totalRead < numFrames) {
            int read = in.read(mSamplesBuffer.array(), totalRead, sizeToRead);
            if (read >= 0) {
                totalRead += read;
            }
            sizeToRead = numFrames - totalRead;
        }

        mSamplesBuffer.asShortBuffer().get(mSamplesShortBuffer);
        mDSP.inputAudio(mSamplesShortBuffer, numFrames);

        Log.i(TAG, "Read " + numFrames + " frames in " + (SystemClock.currentThreadTimeMillis() - timeMs) + "ms");
    }

}
