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
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import com.fastbootmobile.encore.app.MainActivity;
import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.app.adapters.HistoryAdapter;
import com.fastbootmobile.encore.framework.ListenLogger;
import com.fastbootmobile.encore.framework.PlaybackProxy;
import com.fastbootmobile.encore.model.Album;
import com.fastbootmobile.encore.model.Artist;
import com.fastbootmobile.encore.model.Playlist;
import com.fastbootmobile.encore.model.SearchResult;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.ILocalCallback;
import com.fastbootmobile.encore.providers.IMusicProvider;
import com.fastbootmobile.encore.providers.ProviderAggregator;

import java.util.List;

/**
 * A fragment containing a simple view for history.
 */
public class HistoryFragment extends Fragment implements ILocalCallback {
    private HistoryAdapter mAdapter;
    private Handler mHandler = new Handler();

    public static HistoryFragment newInstance() {
        return new HistoryFragment();
    }

    public HistoryFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ListView rootView = (ListView) inflater.inflate(R.layout.fragment_history, container, false);
        mAdapter = new HistoryAdapter(container.getContext());
        rootView.setAdapter(mAdapter);
        rootView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ListenLogger.LogEntry entry = mAdapter.getItem(position);
                Song song = ProviderAggregator.getDefault().retrieveSong(entry.getReference(), entry.getIdentifier());
                PlaybackProxy.playSong(song);
            }
        });
        return rootView;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        MainActivity mainActivity = (MainActivity) activity;
        mainActivity.onSectionAttached(MainActivity.SECTION_HISTORY);
    }

    @Override
    public void onResume() {
        super.onResume();
        ProviderAggregator.getDefault().addUpdateCallback(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        ProviderAggregator.getDefault().removeUpdateCallback(this);
    }

    @Override
    public void onSongUpdate(List<Song> s) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onAlbumUpdate(List<Album> a) {
    }

    @Override
    public void onPlaylistUpdate(List<Playlist> p) {
    }

    @Override
    public void onPlaylistRemoved(String ref) {
    }

    @Override
    public void onArtistUpdate(List<Artist> a) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onProviderConnected(IMusicProvider provider) {
    }

    @Override
    public void onSearchResult(List<SearchResult> searchResult) {
    }
}