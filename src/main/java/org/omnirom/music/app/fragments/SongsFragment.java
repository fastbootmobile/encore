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
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import org.omnirom.music.app.R;
import org.omnirom.music.utils.Utils;
import org.omnirom.music.app.adapters.SongsListAdapter;
import org.omnirom.music.framework.PlaybackProxy;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.ProviderConnection;
import org.omnirom.music.service.BasePlaybackCallback;

import java.util.List;

/**
 * Fragment showing a list of Songs
 */
public class SongsFragment extends Fragment {
    private static final String TAG = "SongsFragment";

    private SongsListAdapter mSongsListAdapter;
    private Handler mHandler;

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
            // Play the song
            Song song = mSongsListAdapter.getItem(i);
            if (Utils.canPlaySong(song)) {
                PlaybackProxy.playSong(song);
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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mHandler = new Handler();

        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_songs, container, false);
        assert root != null;
        ListView songsList = (ListView) root.findViewById(R.id.songsList);

        mSongsListAdapter = new SongsListAdapter(true);
        songsList.setAdapter(mSongsListAdapter);

        List<ProviderConnection> providers = PluginsLookup.getDefault().getAvailableProviders();
        for (ProviderConnection providerConnection : providers) {
            try {
                IMusicProvider provider = providerConnection.getBinder();
                if (provider != null) {
                    List<Song> songs = provider.getSongs();
                    if (songs != null) {
                        for (Song song : songs) {
                            if (song != null) {
                                mSongsListAdapter.put(song);
                            }
                        }
                    }
                }
            } catch (DeadObjectException e) {
                Log.e(TAG, "Provider died while getting songs");
            } catch (RemoteException e) {
                Log.w(TAG, "Cannot get songs from a provider", e);
            }
        }

        mSongsListAdapter.sortAll();
        mSongsListAdapter.notifyDataSetChanged();

        songsList.setOnItemClickListener(mItemClickListener);
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

}
