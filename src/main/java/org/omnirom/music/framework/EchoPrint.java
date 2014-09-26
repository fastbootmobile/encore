package org.omnirom.music.framework;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import android.util.Xml;

import com.echonest.api.v4.Song;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Guigui on 19/09/2014.
 */
public class EchoPrint {
    private static final String TAG = "EchoPrint";
    private static final String USER_AGENT = "User-Agent: AppNumber=48000 APIVersion=2.1.0.0 DEV=Android UID=dkl109sas19s";
    private static final String MIME_TYPE = "audio/wav";

    private static final int SAMPLE_RATE = 11025;
    private static final short BIT_DEPTH = 16;
    private static final short CHANNELS = 1;

    private class RecorderThread extends Thread {
        public void run() {
            Log.e(TAG, "Started recording reading...");

            while (!isInterrupted() && mBufferIndex < mBuffer.length) {
                int read = mRecorder.read(mBuffer, mBufferIndex, mBuffer.length - mBufferIndex);

                if (read == AudioRecord.ERROR_BAD_VALUE) {
                    Log.e(TAG, "BAD_VALUE while reading recorder");
                    break;
                } else if (read == AudioRecord.ERROR_INVALID_OPERATION) {
                    Log.e(TAG, "INVALID_OPERATION while reading recorder");
                    break;
                } else {
                    mBufferIndex += read;
                }
            }

            Log.e(TAG, "Broke out of recording loop");
        }
    }

    public class PrintResult {
        public String ArtistName;
        public String AlbumName;
        public String TrackName;
        public String AlbumImageUrl;

        public String toString() {
            return ArtistName + " - " + TrackName + " (" + AlbumName + "); " + AlbumImageUrl;
        }
    }

    private byte[] mBuffer;
    private int mBufferIndex;
    private AudioRecord mRecorder;
    private RecorderThread mRecThread;

    /**
     * Creates an EchoPrint matching instances and allocates audio buffers
     */
    public EchoPrint() {
        // We limit to 20 seconds of recording. We size our buffer to store 20 seconds of
        // audio at 11025 Hz, 16 bits (2 bytes).
        int bufferSize = SAMPLE_RATE * 20 * 2;
        mBuffer = new byte[bufferSize];
    }

    /**
     * Starts the recording and the recorder thread
     */
    public void startRecording() {
        int minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        mRecorder = new AudioRecord(MediaRecorder.AudioSource.CAMCORDER, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufSize * 10);
        mRecorder.startRecording();
        mBufferIndex = 0;
        mRecThread = new RecorderThread();
        mRecThread.start();
    }

    /**
     * Stops and query results to the EchoNest API. This must be called from a thread as it sends
     * data over the network.
     * @return A PrintResult with the match, or null in case of error
     */
    public PrintResult stopAndSend() throws IOException {
        if (mRecorder == null && mRecThread == null) {
            return null;
        }

        if (mRecThread != null && mRecThread.isAlive()) {
            mRecThread.interrupt();
        }

        if (mRecorder != null) {
            mRecorder.stop();
        }

        Log.d(TAG, "Identifying song, " + mBufferIndex + " bytes...");

        if (mBufferIndex > 0) {
            try {
                URL url = new URL("http://search.midomi.com:443/v2/?method=search&type=identify");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.addRequestProperty("User-Agent", USER_AGENT);
                conn.addRequestProperty("Content-Type", MIME_TYPE);
                conn.setDoOutput(true);

                // Write the WAVE audio header, then the PCM data
                WaveHeader header = new WaveHeader(WaveHeader.FORMAT_PCM, CHANNELS,
                        SAMPLE_RATE, BIT_DEPTH, mBufferIndex);
                header.write(conn.getOutputStream());
                conn.getOutputStream().write(mBuffer, 0, mBufferIndex);

                InputStream is = conn.getInputStream();
                byte[] buffer = new byte[1024];
                int read;
                StringBuilder sb = new StringBuilder();
                while ((read = is.read(buffer)) > 0) {
                    sb.append(new String(buffer, 0, read));
                }

                String output_xml = sb.toString();
                //Log.d(TAG, output_xml)

                if (output_xml.contains("did not hear any music") || output_xml.contains("no close matches")) {
                    // No result
                    return null;
                } else {
                    // Return result where everything is fine
                    PrintResult result = new PrintResult();

                    Pattern data_re = Pattern.compile("<track .*?artist_name=\"(.*?)\".*?album_name=\"(.*?)\".*?track_name=\"(.*?)\".*?album_primary_image=\"(.*?)\".*?>",
                            Pattern.DOTALL | Pattern.MULTILINE);
                    Matcher match = data_re.matcher(output_xml.replaceAll("\n", ""));

                    if (match.find()) {
                        result.ArtistName = match.group(1);
                        result.AlbumName = match.group(2);
                        result.TrackName = match.group(3);
                        result.AlbumImageUrl = match.group(4);
                    } else {
                        Log.e(TAG, "Regular expression didn't match!");
                        return null;
                    }

                    return result;
                }
            } catch (MalformedURLException e) {
                Log.e(TAG, "Error", e);
            } catch (IOException e) {
                Log.e(TAG, "Error", e);
            }
        } else {
            Log.e(TAG, "0 bytes recorded!?");
        }

        return null;
    }

    /**
     * Aborts the current recording
     */
    public void stopAndCancel() {
        if (mRecThread != null && mRecThread.isAlive()) {
            mRecThread.interrupt();
        }
        mRecorder.stop();
    }
}
