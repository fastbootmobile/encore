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

package org.omnirom.music.app.fragments;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.omnirom.music.app.MainActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.SearchActivity;
import org.omnirom.music.framework.EchoPrint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Fragment displaying the controls for the song fingerprinting and recognition system
 */
public class RecognitionFragment extends Fragment {
    private static final String TAG = "RecognitionFragment";

    private Handler mHandler;
    private EchoPrint mActivePrint;
    private EchoPrint.PrintResult mLastResult;
    private TextView mTvOops;
    private TextView mTvTitle;
    private TextView mTvArtist;
    private TextView mTvAlbum;
    private ImageView mIvArt;
    private Button mRecognitionButton;
    private Button mSearchButton;
    private ProgressBar mProgressRecognizing;

    private Runnable mStopRecognition = new Runnable() {
        @Override
        public void run() {
            mRecognitionButton.setEnabled(false);
            mRecognitionButton.setText(R.string.recognizing);
            mProgressRecognizing.setVisibility(View.VISIBLE);
            mTvOops.setVisibility(View.GONE);
            mSearchButton.setVisibility(View.GONE);

            new Thread() {
                public void run() {
                    try {
                        mLastResult = mActivePrint.stopAndSend();
                    } catch (IOException e) {
                        Log.e(TAG, "Error while sending audio", e);
                    }

                    Log.d(TAG, "Result: " + mLastResult);
                    mHandler.post(mShowRecognitionResult);
                    mHandler.post(mResetRecognition);
                }
            }.start();
        }
    };

    private Runnable mShowRecognitionResult = new Runnable() {
        @Override
        public void run() {
            if (mLastResult == null) {
                mTvOops.setVisibility(View.VISIBLE);
                mSearchButton.setVisibility(View.GONE);
                mTvAlbum.setText(null);
                mTvArtist.setText(null);
                mTvTitle.setText(null);
            } else {
                mTvOops.setVisibility(View.GONE);
                mTvAlbum.setText(mLastResult.AlbumName);
                mTvTitle.setText(mLastResult.TrackName);
                mTvArtist.setText(mLastResult.ArtistName);
                mSearchButton.setVisibility(View.VISIBLE);

                // Load the album art in a thread
                new Thread() {
                    public void run () {
                        URL url;
                        try {
                            url = new URL(mLastResult.AlbumImageUrl);
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

                        try {
                            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                            InputStream is = conn.getInputStream();
                            byte[] buffer = new byte[2048];
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            int read;
                            while ((read = is.read(buffer)) > 0) {
                                baos.write(buffer, 0, read);
                            }

                            final Bitmap bmp = BitmapFactory.decodeByteArray(baos.toByteArray(), 0, baos.size());
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    mIvArt.setImageBitmap(bmp);
                                }
                            });
                        } catch (IOException e) {
                            Log.e(TAG, "Error downloading album art", e);
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    mIvArt.setImageResource(R.drawable.album_placeholder);
                                }
                            });
                        }
                    }
                }.start();
            }
        }
    };

    private Runnable mResetRecognition = new Runnable() {
        @Override
        public void run() {
            mActivePrint = null;
            mProgressRecognizing.setVisibility(View.GONE);
            mRecognitionButton.setEnabled(true);
            mRecognitionButton.setText(R.string.start_recognition);
        }
    };

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
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
        mHandler = new Handler();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        RelativeLayout root = (RelativeLayout) inflater.inflate(R.layout.fragment_recognition, container, false);
        mRecognitionButton = (Button) root.findViewById(R.id.btnStartRec);
        mTvAlbum = (TextView) root.findViewById(R.id.tvAlbumName);
        mTvArtist = (TextView) root.findViewById(R.id.tvArtistName);
        mTvTitle = (TextView) root.findViewById(R.id.tvTrackName);
        mTvOops = (TextView) root.findViewById(R.id.tvCannotRecognize);
        mIvArt = (ImageView) root.findViewById(R.id.ivRecognitionArt);
        mProgressRecognizing = (ProgressBar) root.findViewById(R.id.pbRecognizing);
        mSearchButton = (Button) root.findViewById(R.id.btnSearch);

        mRecognitionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mTvAlbum.setText(null);
                mTvArtist.setText(null);
                mTvTitle.setText(null);
                mIvArt.setVisibility(View.INVISIBLE);
                mSearchButton.setVisibility(View.GONE);

                if (mActivePrint == null) {
                    mActivePrint = new EchoPrint();
                    mActivePrint.startRecording();

                    mRecognitionButton.setText(R.string.stop_recognition);

                    // The buffer has a max size of 20 seconds, so we force stop at around 19 seconds
                    mHandler.postDelayed(mStopRecognition, 19000);
                } else {
                    mHandler.removeCallbacks(mStopRecognition);
                    mHandler.post(mStopRecognition);
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
        mainActivity.setContentShadowTop(0);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

}
