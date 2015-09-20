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
import android.os.DeadObjectException;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.RemoteException;
import android.os.TransactionTooLargeException;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.app.adapters.SongsListAdapter;
import com.fastbootmobile.encore.framework.PlaybackProxy;
import com.fastbootmobile.encore.framework.PluginsLookup;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.IMusicProvider;
import com.fastbootmobile.encore.providers.ProviderConnection;
import com.fastbootmobile.encore.service.BasePlaybackCallback;
import com.fastbootmobile.encore.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment showing a list of Songs
 */
public class SongsFragment extends Fragment {
    private static final String TAG = "SongsFragment";

    private SongsListAdapter mSongsListAdapter;
    private Handler mHandler;
    private boolean mAdapterSet = false;
    private ListView mListView;

    private BasePlaybackCallback mPlaybackCallback = new BasePlaybackCallback() {
        @Override
        public void onSongStarted(boolean buffering, Song s) throws RemoteException {
            mHandler.post(new Runnable() {
                public void run() {
                    mSongsListAdapter.notifyDataSetChanged();
                }
            });
        }
    };

    private AdapterView.OnItemClickListener mItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
            // Play the song along with every other
            Song song = mSongsListAdapter.getItem(i);
            if (Utils.canPlaySong(song)) {
                PlaybackProxy.clearQueue();

                // Queue all songs
                List<Song> songs = mSongsListAdapter.getItems();
                for (Song s : songs) {
                    PlaybackProxy.queueSong(s, false);
                }

                // Play the requested one
                PlaybackProxy.playAtIndex(i);
            } else if (song == null) {
                Log.e(TAG, "Trying to play null song!");
            } else {
                Utils.shortToast(getActivity(), R.string.toast_track_unavailable);
            }
        }
    };

    public static SongsFragment newInstance() {
        return new SongsFragment();
    }

    public SongsFragment() {
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_songs, container, false);
        assert root != null;
        mListView = (ListView) root.findViewById(R.id.songsList);
        mListView.setFastScrollEnabled(true);

        mSongsListAdapter = new SongsListAdapter(true);

        new GetAllSongsTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        mListView.setOnItemClickListener(mItemClickListener);
        return root;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        PlaybackProxy.addCallback(mPlaybackCallback);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        PlaybackProxy.removeCallback(mPlaybackCallback);
    }

    private class GetAllSongsTask extends AsyncTask<Void, List<Song>, List<Song>> {
        @Override
        protected List<Song> doInBackground(Void... params) {
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

            List<ProviderConnection> providers = PluginsLookup.getDefault().getAvailableProviders();
            final List<Song> songsToAdd = new ArrayList<>();
            for (ProviderConnection providerConnection : providers) {
                try {
                    IMusicProvider provider = providerConnection.getBinder();
                    if (provider != null) {
                        int limit = 100;
                        int offset = 0;

                        while (true) {
                            try {
                                List<Song> songs = provider.getSongs(offset, limit);

                                if (songs == null || songs.size() == 0) {
                                    // Null or empty list, stop here
                                    break;
                                }

                                for (Song song : songs) {
                                    if (song != null) {
                                        songsToAdd.add(song);
                                    }
                                }

                                publishProgress(new ArrayList<>(songsToAdd));
                                songsToAdd.clear();

                                if (songs.size() < limit) {
                                    // Less songs than requested, assume we're at the end
                                    Log.d(TAG, "Got " + songs.size() + " songs, limit="
                                            + limit + ", assuming end of list");
                                    break;
                                } else {
                                    offset += limit;
                                }
                            } catch (TransactionTooLargeException e) {
                                limit -= 10;

                                if (limit <= 0) {
                                    Log.e(TAG, "Transaction failed even at limit = 0, bailing out", e);
                                    break;
                                }

                                Log.w(TAG, "Transaction too large, reducing limit to " + limit);
                            }
                        }
                    }
                } catch (DeadObjectException e) {
                    Log.e(TAG, "Provider died while getting songs");
                } catch (RemoteException e) {
                    Log.w(TAG, "Cannot get songs from a provider", e);
                }
            }

            return songsToAdd;
        }

        @SafeVarargs
        @Override
        protected final void onProgressUpdate(List<Song>... values) {
            List<Song> songs = values[0];
            if (songs.size() > 0) {
                onPostExecute(songs);
            }
        }

        @Override
        protected void onPostExecute(List<Song> songs) {
            mSongsListAdapter.putAll(songs);
            mSongsListAdapter.sortAll();

            if (mAdapterSet) {
                mSongsListAdapter.notifyDataSetChanged();
            } else {
                mListView.setAdapter(mSongsListAdapter);

                View root = getView();
                if (root != null) {
                    root.findViewById(R.id.songsProgress).setVisibility(View.GONE);
                }

                mAdapterSet = true;
            }
        }
    }
}
