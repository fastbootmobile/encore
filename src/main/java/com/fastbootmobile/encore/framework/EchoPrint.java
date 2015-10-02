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

package com.fastbootmobile.encore.framework;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.Log;

import com.fastbootmobile.encore.app.BuildConfig;
import com.fastbootmobile.encore.utils.WaveHeader;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class helping audio fingerprinting for recognition
 */
public class EchoPrint {
    private static final String TAG = "EchoPrint";
    private static final boolean DEBUG = BuildConfig.DEBUG;
    private static final String USER_AGENT = "User-Agent: AppNumber=48000 APIVersion=2.1.0.0 DEV=Android UID=dkl109sas19s";
    private static final String MIME_TYPE = "audio/wav";

    private static final int SAMPLE_RATE = 11025;
    private static final short BIT_DEPTH = 16;
    private static final short CHANNELS = 1;
    private static final int TRY_MATCH_INTERVAL = 3000;  // Try getting a match every 3 seconds

    /**
     * Helper thread class to record the data to send
     */
    private class RecorderThread extends Thread {
        private boolean mDataSending = false;
        private boolean mResultGiven = false;
        private long mLastMatchTryTime;

        public void run() {
            if (DEBUG) Log.d(TAG, "Started recording reading...");

            mLastMatchTryTime = SystemClock.uptimeMillis();

            while (!isInterrupted() && mBufferIndex < mBuffer.length) {
                int read;
                synchronized (this) {
                    read = mRecorder.read(mBuffer, mBufferIndex, Math.min(512, mBuffer.length - mBufferIndex));

                    if (read == AudioRecord.ERROR_BAD_VALUE) {
                        Log.e(TAG, "BAD_VALUE while reading recorder");
                        break;
                    } else if (read == AudioRecord.ERROR_INVALID_OPERATION) {
                        Log.e(TAG, "INVALID_OPERATION while reading recorder");
                        break;
                    } else if (read >= 0) {
                        mBufferIndex += read;
                    }
                }

                if (read >= 0) {
                    mCallback.onAudioLevel((float) computeAverageAmplitude(mBuffer, mBufferIndex - 10, 4));

                    long currentTime = SystemClock.uptimeMillis();
                    if (currentTime - mLastMatchTryTime >= TRY_MATCH_INTERVAL) {
                        tryMatchCurrentBuffer();
                        mLastMatchTryTime = SystemClock.uptimeMillis();
                    }
                }
            }

            if (DEBUG) Log.d(TAG, "Broke out of recording loop, mResultGiven=" + mResultGiven);

            if (!mResultGiven) {
                tryMatchCurrentBuffer();
            }
        }

        private double computeAverageAmplitude(byte[] buffer, int startSample, int numSamples) {
            // Assuming 16 bits depth
            double sum = 0.0f;
            for (int i = 0; i < numSamples; ++i) {
                sum += computeAmplitude(buffer, startSample + i * 2);
            }

            return sum / ((double) numSamples);
        }

        private double computeAmplitude(byte[] buffer, int sample) {
            // Assuming 16 bits depth
            int amplitude = (buffer[sample] & 0xff) << 8 | buffer[sample + 1];
            // decibel: return 20.0 * Math.log10((double)Math.abs(amplitude) / 32768.0);
            return amplitude / 65536.0;
        }

        public void tryMatchCurrentBuffer() {
            if (mBufferIndex > 0) {
                new Thread() {
                    public void run() {
                        // Allow only one upload call at a time
                        if (mDataSending) {
                            Log.d(TAG, "Not sending, data already sending");
                            return;
                        }

                        mDataSending = true;

                        byte[] copy;
                        int length;
                        synchronized (RecorderThread.this) {
                            length = mBufferIndex;
                        }

                        copy = new byte[length];
                        System.arraycopy(mBuffer, 0, copy, 0, length);

                        String output_xml = sendAudioData(copy, length);
                        parseXmlResult(output_xml);
                        mDataSending = false;
                    }
                }.start();
            } else {
                Log.e(TAG, "0 bytes recorded!?");
            }
        }

        private String sendAudioData(byte[] inputBuffer, int length) {
            if(DEBUG) Log.d(TAG, "Preparing to send audio data: " + length + " bytes");
            try {
                URL url = new URL("http://search.midomi.com:443/v2/?method=search&type=identify");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.addRequestProperty("User-Agent", USER_AGENT);
                conn.addRequestProperty("Content-Type", MIME_TYPE);
                conn.setDoOutput(true);
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(10000);

                // Write the WAVE audio header, then the PCM data
                if (DEBUG) Log.d(TAG, "Sending mic data, " + length + " bytes...");
                WaveHeader header = new WaveHeader(WaveHeader.FORMAT_PCM, CHANNELS,
                        SAMPLE_RATE, BIT_DEPTH, length);
                header.write(conn.getOutputStream());
                conn.getOutputStream().write(inputBuffer, 0, length);

                InputStream is = conn.getInputStream();
                byte[] buffer = new byte[8192];
                int read;
                StringBuilder sb = new StringBuilder();
                while ((read = is.read(buffer)) > 0) {
                    sb.append(new String(buffer, 0, read));
                }

                return sb.toString();
            } catch (IOException e) {
                Log.e(TAG, "Error while sending audio data", e);
                mDataSending = false;
            }

            return "";
        }

        private void parseXmlResult(String xml) {
            if (xml.contains("did not hear any music") || xml.contains("no close matches")) {
                // No result
                if (DEBUG) Log.d(TAG, "No match (did not hear/no close)");
                reportResult(null);
            } else {
                // Return result where everything is fine
                PrintResult result = new PrintResult();

                Pattern data_re = Pattern.compile("<track .*?artist_name=\"(.*?)\".*?album_name=\"(.*?)\".*?track_name=\"(.*?)\".*?album_primary_image=\"(.*?)\".*?>",
                        Pattern.DOTALL | Pattern.MULTILINE);
                Matcher match = data_re.matcher(xml.replaceAll("\n", ""));

                if (match.find()) {
                    result.ArtistName = match.group(1);
                    result.AlbumName = match.group(2);
                    result.TrackName = match.group(3);
                    result.AlbumImageUrl = match.group(4);
                    if (DEBUG) Log.d(TAG, "Got a match! " + result);
                } else {
                    Log.w(TAG, "Regular expression didn't match!");
                    reportResult(null);
                }

                reportResult(result);
            }
        }

        private void reportResult(PrintResult result) {
            // If the recording is still active and we have no match, don't do anything. Otherwise,
            // report the result.
            if (mRecorder == null && result == null) {
                if (DEBUG) Log.d(TAG, "Reporting onNoMatch");
                mCallback.onNoMatch();
            } else if (result != null) {
                if (DEBUG) Log.d(TAG, "Reporting result");
                mResultGiven = true;
                if (mRecorder != null) {
                    stopRecording();
                }
                mCallback.onResult(result);
            }
        }
    }

    /**
     * Class storing fingerprinting results
     */
    public static class PrintResult {
        public String ArtistName;
        public String AlbumName;
        public String TrackName;
        public String AlbumImageUrl;

        @Override
        public String toString() {
            return ArtistName + " - " + TrackName + " (" + AlbumName + "); " + AlbumImageUrl;
        }
    }

    /**
     * Interface for receiving results and information during printing
     */
    public interface PrintCallback {
        /**
         * Called when the API returns a match
         *
         * @param result The matched track
         */
        void onResult(PrintResult result);

        /**
         * Called when the API returned no match
         */
        void onNoMatch();

        /**
         * Called frequently with the audio level
         *
         * @param level The microphone audio level, between 0 and 1
         */
        void onAudioLevel(float level);

        /**
         * Called when an error occurred
         */
        void onError();
    }


    private byte[] mBuffer;
    private int mBufferIndex;
    private AudioRecord mRecorder;
    private RecorderThread mRecThread;
    private PrintCallback mCallback;

    /**
     * Creates an EchoPrint matching instances and allocates audio buffers
     */
    public EchoPrint(@NonNull PrintCallback callback) {
        mCallback = callback;

        // We limit to 20 seconds of recording. We size our buffer to store 20 seconds of
        // audio at 11025 Hz, 16 bits (2 bytes) ; total of 430KB uploaded max
        int bufferSize = SAMPLE_RATE * 20 * 2;
        mBuffer = new byte[bufferSize];
    }

    /**
     * Starts the recording and the recorder thread. Results will be posted in the PrintCallback
     * that was given to the class constructor.
     */
    public void startRecording() {
        final int minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        mRecorder = new AudioRecord(MediaRecorder.AudioSource.CAMCORDER, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufSize);

        mBufferIndex = 0;
        try {
            mRecorder.startRecording();
            mRecThread = new RecorderThread();
            mRecThread.start();
        } catch (IllegalStateException e) {
            Log.e(TAG, "Cannot start recording for recognition", e);
            mCallback.onError();
        }
    }

    /**
     * If startRecording was called and still active, the recording system will be stopped and
     * pending data, if any, will be sent to the API to get a match.
     */
    public void stopRecording() {
        if (mRecThread != null && mRecThread.isAlive()) {
            if (DEBUG) Log.d(TAG, "Interrupting recorder thread");
            mRecThread.interrupt();
        }

        if (mRecorder != null) {
            if (DEBUG) Log.d(TAG, "Stopping recorder");
            mRecorder.stop();
            mRecorder = null;
        }
    }
}
