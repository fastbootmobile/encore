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

package com.fastbootmobile.encore.voice;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import com.fastbootmobile.encore.app.BuildConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for voice commands
 */
public class VoiceRecognizer implements RecognitionListener {
    private static final String TAG = "VoiceRecognizer";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private SpeechRecognizer mSpeechRecognizer;
    private Intent mSpeechRecognizerIntent;
    private Listener mListener;

    public VoiceRecognizer(Context context) {
        mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        mSpeechRecognizer.setRecognitionListener(this);

        mSpeechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        mSpeechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        mSpeechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,
                context.getPackageName());
        mSpeechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 10);
        mSpeechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        mSpeechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "What can I do?");
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void startListening() {
        mSpeechRecognizer.startListening(mSpeechRecognizerIntent);
    }

    /**
     * Called when the endpointer is ready for the user to start speaking.
     *
     * @param params Reserved for future use
     */
    @Override
    public void onReadyForSpeech(Bundle params) {
        if (DEBUG) Log.d(TAG, "Ready for speech");

        if (mListener != null) {
            mListener.onReadyForSpeech();
        }
    }

    /**
     * The user has started to speak.
     */
    @Override
    public void onBeginningOfSpeech() {
        if (DEBUG) Log.d(TAG, "Began speaking");

        if (mListener != null) {
            mListener.onBeginningOfSpeech();
        }
    }

    /**
     * The sound level in the audio stream has changed. There is no guarantee that this method will
     * be called.
     *
     * @param rmsdB the new RMS dB value
     */
    @Override
    public void onRmsChanged(float rmsdB) {
        if (mListener != null) {
            mListener.onRmsChanged(rmsdB);
        }
    }

    /**
     * More sound has been received. The purpose of this function is to allow giving feedback to
     * the user regarding the captured audio. There is no guarantee that this method will be called.
     *
     * @param buffer a buffer containing a sequence of big-endian 16-bit integers representing a
     *               single channel audio stream. The sample rate is implementation dependent.
     */
    @Override
    public void onBufferReceived(byte[] buffer) {

    }

    /**
     * Called after the user stops speaking.
     */
    @Override
    public void onEndOfSpeech() {
        if (DEBUG) Log.d(TAG, "End of speech");

        if (mListener != null) {
            mListener.onEndOfSpeech();
        }
    }

    /**
     * A network or recognition error occurred.
     *
     * @param error code defined in {@link android.speech.SpeechRecognizer}
     */
    @Override
    public void onError(int error) {
        if (DEBUG) {
            String errorMsg;
            switch (error) {
                case SpeechRecognizer.ERROR_AUDIO:
                    errorMsg = "Audio recording error";
                    break;

                case SpeechRecognizer.ERROR_CLIENT:
                    errorMsg = "No voice recnogition service available";
                    break;

                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    errorMsg = "Insufficient permissions";
                    break;

                case SpeechRecognizer.ERROR_NETWORK:
                    errorMsg = "Network error";
                    break;

                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    errorMsg = "Network operation timed out";
                    break;

                case SpeechRecognizer.ERROR_NO_MATCH:
                    errorMsg = "No matches";
                    break;

                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    errorMsg = "Recognition service is busy";
                    break;

                case SpeechRecognizer.ERROR_SERVER:
                    errorMsg = "Server error";
                    break;

                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    errorMsg = "No speech input";
                    break;

                default:
                    errorMsg = "Unknown error";
                    break;
            }

            Log.d(TAG, "Error " + error + ": " + errorMsg);
        }

        if (mListener != null) {
            mListener.onError(error);
        }
    }

    /**
     * Called when recognition results are ready.
     *
     * @param results the recognition results. To retrieve the results in ArrayList<String> format
     *                use getStringArrayList(String) with RESULTS_RECOGNITION as a parameter. A
     *                float array of confidence values might also be given in CONFIDENCE_SCORES.
     */
    @Override
    public void onResults(Bundle results) {
        if (DEBUG) Log.d(TAG, "Results received");

        ArrayList<String> data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

        if (mListener != null) {
            mListener.onResults(data);
        }
    }

    /**
     * Called when partial recognition results are available. The callback might be called at any
     * time between onBeginningOfSpeech() and onResults(Bundle) when partial results are ready.
     * This method may be called zero, one or multiple times for each call to
     * {@link #startListening()}, depending on the speech recognition service implementation.
     * To request partial results, use EXTRA_PARTIAL_RESULTS
     *
     * @param partialResults the returned results. To retrieve the results in ArrayList<String>
     *                       format use getStringArrayList(String) with RESULTS_RECOGNITION as
     *                       a parameter
     */
    @Override
    public void onPartialResults(Bundle partialResults) {
        if (DEBUG) Log.d(TAG, "Partial results received");

        ArrayList<String> data = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

        if (mListener != null) {
            mListener.onPartialResults(data);
        }
    }

    @Override
    public void onEvent(int eventType, Bundle params) {
        if (DEBUG) Log.d(TAG, "Event " + eventType);
    }

    public interface Listener {
        public void onReadyForSpeech();

        public void onBeginningOfSpeech();

        public void onEndOfSpeech();

        public void onRmsChanged(float rmsdB);

        public void onError(int error);

        public void onResults(List<String> results);

        public void onPartialResults(List<String> results);
    }
}
