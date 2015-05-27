package com.fastbootmobile.encore.providers.bassboost;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;

import com.fastbootmobile.encore.providers.AudioClientSocket;
import com.fastbootmobile.encore.providers.AudioSocket;
import com.fastbootmobile.encore.providers.IDSPProvider;
import com.fastbootmobile.encore.providers.ProviderIdentifier;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

import omnimusic.Plugin;

public class PluginService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "PluginService";

    private ProviderIdentifier mIdentifier;
    private AudioSocket mSocket;
    private static BiQuadFilter mFilterL = new BiQuadFilter();

    short[] mSamplesBuffer = new short[16384];
    byte[] mBytesBuffer = new byte[32768];

    AudioClientSocket.ISocketCallback mSocketCallback = new AudioSocket.ISocketCallback() {
        @Override
        public void onAudioData(AudioSocket socket, Plugin.AudioData.Builder message) {
            ByteBuffer inputBytes = message.getSamples().asReadOnlyByteBuffer();
            ShortBuffer shortBuf = inputBytes.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();

            final int numShorts = shortBuf.limit();

            if (mSamplesBuffer.length < numShorts) {
                mSamplesBuffer = new short[numShorts];
            }

            shortBuf.get(mSamplesBuffer, 0, numShorts);


            for (int i = 0; i < numShorts; i += 2) {
                int inL = mSamplesBuffer[i];
                int inR = mSamplesBuffer[i + 1];

                int boost = mFilterL.process(inL + inR);

                mSamplesBuffer[i] = BiQuadFilter.clamp16(inL + boost);
                mSamplesBuffer[i + 1] = BiQuadFilter.clamp16(inR + boost);
            }

            // push it back
            try {
                final int numBytes = numShorts * 2;
                if (mBytesBuffer.length < numBytes) {
                    mBytesBuffer = new byte[numBytes];
                }

                ByteBuffer.wrap(mBytesBuffer)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .asShortBuffer()
                        .put(mSamplesBuffer, 0, numShorts);
                mSocket.writeAudioData(mBytesBuffer, 0, numBytes);
            } catch (IOException e) {
                Log.e(TAG, "Cannot write audio data", e);
            }
        }

        @Override
        public void onAudioResponse(AudioSocket socket, Plugin.AudioResponse.Builder message) {

        }

        @Override
        public void onRequest(AudioSocket socket, Plugin.Request.Builder message) {

        }

        @Override
        public void onFormatInfo(AudioSocket socket, Plugin.FormatInfo.Builder message) {

        }

        @Override
        public void onBufferInfo(AudioSocket socket, Plugin.BufferInfo.Builder message) {

        }
    };

    public PluginService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(PluginService.this);
        prefs.registerOnSharedPreferenceChangeListener(this);
        updateFilterSettings();
    }

    public void updateFilterSettings() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(PluginService.this);
        String frequency = prefs.getString("center_frequency", "55");
        String gain = prefs.getString("gain", "0");
        double dfrequency = Double.parseDouble(frequency);
        double dgain = Double.parseDouble(gain);

        mFilterL.setLowPass(10, dfrequency, 44100.0, dgain / 666.0);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void setupAudioSocket(final String socketName) {
        Log.d(TAG, "setupAudioSocket(" + socketName + ")");
        try {
            if (mSocket == null) {
                mSocket = new AudioClientSocket();
            }

            mSocket.connect(socketName);
            mSocket.setCallback(mSocketCallback);
        } catch (IOException e) {
            Log.e(TAG, "Cannot open the socket for audio data", e);
        }
    }

    private IDSPProvider.Stub mBinder = new IDSPProvider.Stub() {

        @Override
        public int getVersion() throws RemoteException {
            return 1;
        }

        @Override
        public void setIdentifier(ProviderIdentifier identifier) throws RemoteException {
            mIdentifier = identifier;
        }

        @Override
        public void setAudioSocketName(String socketName) throws RemoteException {
            setupAudioSocket(socketName);
        }

    };

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        updateFilterSettings();
    }
}
