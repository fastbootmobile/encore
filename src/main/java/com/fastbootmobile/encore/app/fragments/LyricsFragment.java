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
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.fastbootmobile.encore.api.chartlyrics.ChartLyricsClient;
import com.fastbootmobile.encore.api.common.RateLimitException;
import com.fastbootmobile.encore.app.MainActivity;
import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.framework.PlaybackProxy;
import com.fastbootmobile.encore.model.Artist;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.ProviderAggregator;
import com.fastbootmobile.encore.service.BasePlaybackCallback;

import java.io.IOException;

/**
 * A fragment containing a simple view for lyrics.
 */
public class LyricsFragment extends Fragment {
    private static final String TAG = "LyricsFragment";

    private TextView mTvLyrics;
    private ProgressBar mPbLoading;
    private ChartLyricsClient.LyricsResponse mLyrics;
    private Song mCurrentSong;
    private Handler mHandler = new Handler();
    private AsyncTask mLyricsTask;

    private BasePlaybackCallback mPlaybackCallback = new BasePlaybackCallback() {
        @Override
        public void onSongStarted(boolean buffering, Song s) {
            if (mCurrentSong == null || !s.equals(mCurrentSong)) {
                mCurrentSong = s;
                getLyrics(s);
            }
        }
    };

    public static LyricsFragment newInstance() {
        return new LyricsFragment();
    }

    public LyricsFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ScrollView root = (ScrollView) inflater.inflate(R.layout.fragment_lyrics, container, false);
        mTvLyrics = (TextView) root.findViewById(R.id.tvLyrics);
        mPbLoading = (ProgressBar) root.findViewById(R.id.pbLoading);

        updateLyrics();

        return root;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        MainActivity mainActivity = (MainActivity) activity;
        mainActivity.onSectionAttached(MainActivity.SECTION_LYRICS);
    }

    @Override
    public void onResume() {
        super.onResume();
        PlaybackProxy.addCallback(mPlaybackCallback);
        updateLyrics();
    }

    @Override
    public void onPause() {
        super.onPause();
        PlaybackProxy.removeCallback(mPlaybackCallback);
    }

    private void updateLyrics() {
        mCurrentSong = PlaybackProxy.getCurrentTrack();
        if (mCurrentSong != null && mCurrentSong.getArtist() != null) {
            getLyrics(mCurrentSong);
        } else if (mCurrentSong != null) {
            mTvLyrics.setText(R.string.lyrics_no_artist_error);
            mPbLoading.setVisibility(View.GONE);
        } else {
            mTvLyrics.setText(R.string.lyrics_placeholder);
            mPbLoading.setVisibility(View.GONE);
        }
    }

    private void getLyrics(final Song song) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mLyricsTask != null) {
                    mLyricsTask.cancel(true);
                }
                mLyricsTask = new GetLyricsTask().execute(song);
            }
        });
    }

    private class GetLyricsTask extends AsyncTask<Song, Void, ChartLyricsClient.LyricsResponse> {
        private Song mSong;

        @Override
        protected void onPreExecute() {
            mTvLyrics.setVisibility(View.GONE);
            mPbLoading.setVisibility(View.VISIBLE);
        }

        @Override
        protected ChartLyricsClient.LyricsResponse doInBackground(Song... params) {
            mSong = params[0];
            Artist artist = ProviderAggregator.getDefault().retrieveArtist(mSong.getArtist(), mSong.getProvider());

            ChartLyricsClient.LyricsResponse lyrics = null;
            if (artist != null) {
                boolean resetByPeer = true;

                while (resetByPeer && !isCancelled()) {
                    try {
                        lyrics = ChartLyricsClient.getSongLyrics(artist.getName(), mSong.getTitle());
                        resetByPeer = false;
                    } catch (IOException e) {
                        if (e.getMessage().contains("Connection reset by peer")) {
                            // ChartLyrics API resets connection to throttle fetching. Retry every few
                            // seconds until we get them.
                            try {
                                Thread.sleep(3000);
                            } catch (InterruptedException e1) {
                                break;
                            }
                            resetByPeer = true;
                        } else {
                            Log.e(TAG, "Cannot get lyrics", e);
                            resetByPeer = false;
                        }
                    } catch (RateLimitException e) {
                        Log.e(TAG, "Cannot get lyrics", e);
                        resetByPeer = false;
                    }
                }
            }

            return lyrics;
        }

        @Override
        protected void onPostExecute(ChartLyricsClient.LyricsResponse s) {
            mLyrics = s;

            if (mTvLyrics != null && mSong.equals(mCurrentSong) && !isCancelled()) {
                mTvLyrics.setVisibility(View.VISIBLE);
                mPbLoading.setVisibility(View.GONE);

                if (s == null || s.lyrics == null) {
                    mTvLyrics.setText(R.string.lyrics_not_found);
                } else {
                    StringBuilder result = new StringBuilder();
                    if (s.title != null) {
                        result.append(s.title);
                        result.append("\n");
                    }

                    if (s.artist != null) {
                        result.append(s.artist);
                        result.append("\n");
                    }

                    if (s.artist != null || s.title != null) {
                        // Add some padding if we had artist or title
                        result.append("\n\n");
                    }

                    if (s.lyrics != null) {
                        result.append(s.lyrics);
                    }

                    mTvLyrics.setText(result.toString());
                }
            }
        }
    }
}