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

package com.fastbootmobile.encore.app.fragments;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v7.widget.CardView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.fastbootmobile.encore.app.MainActivity;
import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.app.SearchActivity;
import com.fastbootmobile.encore.app.ui.AnimatedMicButton;
import com.fastbootmobile.encore.framework.EchoPrint;
import com.fastbootmobile.encore.providers.ProviderAggregator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Fragment displaying the controls for the song fingerprinting and recognition system
 */
public class RecognitionFragment extends Fragment implements EchoPrint.PrintCallback {
    private static final String TAG = "RecognitionFragment";

    private static final int MSG_AUDIO_LEVEL = 1;
    private static final int MSG_RESULT = 2;
    private static final int MSG_NO_RESULT = 3;
    private static final int MSG_ERROR = 4;

    private static final int FADE_DURATION = 500;

    private EchoPrint mActivePrint;
    private EchoPrint.PrintResult mLastResult;

    private LinearLayout mButtonLayout;
    private AnimatedMicButton mRecognitionButton;
    private TextView mTvStatus;
    private TextView mTvDetails;

    private CardView mCardResult;
    private TextView mTvTitle;
    private TextView mTvArtist;
    private TextView mTvAlbum;
    private ImageView mIvArt;
    private Button mSearchButton;


    private TextView mTvOfflineError;

    private static class RecognitionHandler extends Handler {
        private WeakReference<RecognitionFragment> mParent;

        public RecognitionHandler(WeakReference<RecognitionFragment> parent) {
            mParent = parent;
        }

        @Override
        public void handleMessage(Message msg) {
            RecognitionFragment parent = mParent.get();

            if (parent != null) {
                if (msg.what == MSG_AUDIO_LEVEL) {
                    float value = (Float) msg.obj;
                    if (value >= 0.0f) {
                        parent.setVoiceLevel(value);
                    }
                } else if (msg.what == MSG_RESULT) {
                    parent.showLastResult();
                } else if (msg.what == MSG_NO_RESULT) {
                    parent.onNoResults();
                } else if (msg.what == MSG_ERROR) {
                    parent.showErrorToast();
                    parent.onNoResults();
                }
            }
        }
    }

    private RecognitionHandler mHandler;

    private Runnable mStopRecognition = new Runnable() {
        @Override
        public void run() {
            // As long as we haven't received either onNoMatch or onResult, the audio data
            // should be in a processing state
            mActivePrint.stopRecording();
            mRecognitionButton.setActive(false);
            onStoppedAndRecognizing();
        }
    };

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment RecognitionFragment.
     */
    public static RecognitionFragment newInstance() {
        return new RecognitionFragment();
    }

    public RecognitionFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new RecognitionHandler(new WeakReference<>(this));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        RelativeLayout root = (RelativeLayout) inflater.inflate(R.layout.fragment_recognition, container, false);

        // Recognition layout
        mButtonLayout = (LinearLayout) root.findViewById(R.id.llRecognitionButton);
        mRecognitionButton = (AnimatedMicButton) root.findViewById(R.id.btnStartRec);
        mTvStatus = (TextView) root.findViewById(R.id.tvStatus);
        mTvDetails = (TextView) root.findViewById(R.id.tvDetailsText);

        // Result layout
        mCardResult = (CardView) root.findViewById(R.id.cardResult);
        mTvAlbum = (TextView) root.findViewById(R.id.tvAlbumName);
        mTvArtist = (TextView) root.findViewById(R.id.tvArtistName);
        mTvTitle = (TextView) root.findViewById(R.id.tvTrackName);
        mIvArt = (ImageView) root.findViewById(R.id.ivRecognitionArt);
        mSearchButton = (Button) root.findViewById(R.id.btnSearch);

        mTvOfflineError = (TextView) root.findViewById(R.id.tvErrorMessage);
        mTvOfflineError.setText(R.string.error_recognition_unavailable_offline);

        ProviderAggregator aggregator = ProviderAggregator.getDefault();
        mTvOfflineError.setVisibility(aggregator.isOfflineMode() ? View.VISIBLE : View.GONE);

        mRecognitionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mActivePrint == null) {
                    mActivePrint = new EchoPrint(RecognitionFragment.this);
                    mActivePrint.startRecording();
                    onRecognitionStartUI();

                    // The buffer has a max size of 20 seconds, so we force stop at around 19 seconds
                    mHandler.postDelayed(mStopRecognition, 19000);
                } else {
                    mHandler.removeCallbacks(mStopRecognition);
                    mStopRecognition.run();
                }
            }
        });

        mSearchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent search = new Intent(getActivity(), SearchActivity.class);
                search.setAction(Intent.ACTION_SEARCH);
                search.putExtra(SearchManager.QUERY, mLastResult.ArtistName + " " + mLastResult.TrackName);
                startActivity(search);
            }
        });

        return root;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        MainActivity mainActivity = (MainActivity) activity;
        mainActivity.onSectionAttached(MainActivity.SECTION_RECOGNITION);
    }

    @Override
    public void onResult(EchoPrint.PrintResult result) {
        mLastResult = result;
        mHandler.sendEmptyMessage(MSG_RESULT);
        mHandler.removeCallbacks(mStopRecognition);
    }

    @Override
    public void onNoMatch() {
        mHandler.sendEmptyMessage(MSG_NO_RESULT);
    }

    @Override
    public void onAudioLevel(final float level) {
        mHandler.obtainMessage(MSG_AUDIO_LEVEL, level).sendToTarget();
    }

    @Override
    public void onError() {
        mHandler.obtainMessage(MSG_ERROR).sendToTarget();
    }

    private void onStoppedAndRecognizing() {
        onRecognitionStopUI();
    }

    private void loadAlbumArt(final String urlString, final ImageView iv) {
        new Thread() {
            public void run() {
                URL url;
                try {
                    url = new URL(urlString);
                } catch (MalformedURLException e) {
                    // Too bad
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mIvArt.setImageResource(R.drawable.album_placeholder);
                        }
                    });
                    return;
                }

                Log.d(TAG, "Loading album art: " + urlString);

                try {
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    InputStream is = conn.getInputStream();
                    byte[] buffer = new byte[8192];
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    int read;
                    while ((read = is.read(buffer)) > 0) {
                        baos.write(buffer, 0, read);
                    }

                    final Bitmap bmp = BitmapFactory.decodeByteArray(baos.toByteArray(), 0, baos.size());
                    if (bmp != null) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                iv.setImageBitmap(bmp);
                                iv.setVisibility(View.VISIBLE);
                            }
                        });
                    } else {
                        Log.e(TAG, "Null bitmap from image");
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error downloading album art", e);
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            iv.setImageResource(R.drawable.album_placeholder);
                            iv.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }
        }.start();
    }

    public void setVoiceLevel(float level) {
        mRecognitionButton.setLevel(level);
    }

    public void onRecognitionStartUI() {
        mRecognitionButton.setActive(true);
        if (mTvDetails.getAlpha() > 0) {
            // Hide the details text
            mTvDetails.animate()
                    .alpha(0)
                    .translationY(mTvDetails.getMeasuredHeight())
                    .setDuration(FADE_DURATION)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }
        mTvStatus.setText(R.string.recognition_status_listening);
        hideResultCard();
    }

    public void onRecognitionStopUI() {
        // Disable the button and prevent clicking it while we're processing
        mRecognitionButton.setActive(false);
        mRecognitionButton.setEnabled(false);

        mTvStatus.setText(R.string.recognition_status_recognizing);
    }

    public void onNoResults() {
        mActivePrint = null;
        showNoResult();
    }

    public void showLastResult() {
        mActivePrint = null;

        showResultCard();
        mTvAlbum.setText(mLastResult.AlbumName);
        mTvTitle.setText(mLastResult.TrackName);
        mTvArtist.setText(mLastResult.ArtistName);

        mRecognitionButton.setActive(false);
        mRecognitionButton.setEnabled(true);
        mTvStatus.setText(R.string.recognition_status_idle);

        // Load the album art in a thread
        loadAlbumArt(mLastResult.AlbumImageUrl, mIvArt);
    }

    public void showResultCard() {
        mCardResult.animate().alpha(1).translationY(-mCardResult.getMeasuredHeight())
                .setDuration(FADE_DURATION).setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
        mButtonLayout.animate().translationY(-mCardResult.getMeasuredHeight())
                .setDuration(FADE_DURATION).setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    public void hideResultCard() {
        mCardResult.animate().alpha(0).translationY(mCardResult.getMeasuredHeight())
                .setDuration(FADE_DURATION).setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
        mButtonLayout.animate().translationY(0)
                .setDuration(FADE_DURATION).setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    public void showNoResult() {
        mRecognitionButton.setActive(false);
        mRecognitionButton.setEnabled(true);
        mTvStatus.setText(R.string.recognition_status_no_result);
    }

    public void showErrorToast() {
        Toast.makeText(getActivity(), R.string.toast_recognition_error, Toast.LENGTH_SHORT).show();
    }
}
