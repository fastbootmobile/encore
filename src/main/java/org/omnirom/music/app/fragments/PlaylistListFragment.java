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
import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;

import org.omnirom.music.app.MainActivity;
import org.omnirom.music.app.PlaylistActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.app.adapters.PlaylistListAdapter;
import org.omnirom.music.app.ui.MaterialTransitionDrawable;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.SearchResult;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ILocalCallback;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.ProviderAggregator;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple {@link android.support.v4.app.Fragment} subclass, showing a list of playlists.
 * Use the {@link PlaylistListFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class PlaylistListFragment extends Fragment implements ILocalCallback {
    private PlaylistListAdapter mAdapter;
    private Handler mHandler;
    private boolean mIsStandalone;
    private final ArrayList<Playlist> mPlaylistsUpdated = new ArrayList<Playlist>();

    private Runnable mUpdateListRunnable = new Runnable() {
        @Override
        public void run() {
            boolean didChange = false;
            synchronized (mPlaylistsUpdated) {
                for (Playlist p : mPlaylistsUpdated) {
                    didChange = mAdapter.addItemUnique(p) || didChange;
                }

                mPlaylistsUpdated.clear();
            }

            if (didChange) {
                mAdapter.notifyDataSetChanged();
            }
        }
    };

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param isStandalone Whether this fragment is embedded in My Songs or is the Playlists section
     * @return A new instance of fragment PlaylistListFragment.
     */
    public static PlaylistListFragment newInstance(boolean isStandalone) {
        PlaylistListFragment fragment = new PlaylistListFragment();
        fragment.setIsStandalone(isStandalone);
        return fragment;
    }

    /**
     * Default constructor. Use {@link #newInstance(boolean)} to create an instance of this fragment
     */
    public PlaylistListFragment() {
        mAdapter = new PlaylistListAdapter();
    }

    /**
     * Sets whether or not the fragment is displayed standalone (root of an activity's contents)
     * or within another container (e.g. a viewpager contents).
     * @param isStandalone Whether this fragment is embedded in My Songs or is the Playlists section
     */
    public void setIsStandalone(boolean isStandalone) {
        mIsStandalone = isStandalone;
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
        View root = inflater.inflate(R.layout.fragment_playlist, container, false);
        GridView playlistLayout = (GridView) root.findViewById(R.id.gvPlaylists);
        playlistLayout.setAdapter(mAdapter);

        // Setup the click listener
        playlistLayout.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                MainActivity act = (MainActivity) getActivity();
                PlaylistListAdapter.ViewHolder tag = (PlaylistListAdapter.ViewHolder) view.getTag();
                Intent intent = PlaylistActivity.craftIntent(act, mAdapter.getItem(position),
                        ((MaterialTransitionDrawable) tag.ivCover.getDrawable()).getFinalDrawable().getBitmap());

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ActivityOptions opt = ActivityOptions.makeSceneTransitionAnimation(getActivity(),
                            tag.ivCover, "itemImage");
                    act.startActivity(intent, opt.toBundle());
                } else {
                    act.startActivity(intent);
                }
            }
        });

        return root;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (mIsStandalone) {
            MainActivity mainActivity = (MainActivity) activity;
            mainActivity.onSectionAttached(MainActivity.SECTION_PLAYLISTS);
            mainActivity.setContentShadowTop(0);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ProviderAggregator.getDefault().addUpdateCallback(this);
        updatePlaylists();
    }

    @Override
    public void onPause() {
        super.onPause();
        ProviderAggregator.getDefault().removeUpdateCallback(this);
    }

    private void updatePlaylists() {
        new Thread() {
            public void run() {
                final List<Playlist> playlists = ProviderAggregator.getDefault().getAllPlaylists();
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mAdapter.addAllUnique(playlists);
                    }
                });
            }
        }.start();
    }

    @Override
    public void onSongUpdate(List<Song> s) {
    }

    @Override
    public void onAlbumUpdate(List<Album> a) {
    }

    @Override
    public void onPlaylistUpdate(final List<Playlist> p) {
        synchronized (mPlaylistsUpdated) {
            mPlaylistsUpdated.addAll(p);
        }

        mHandler.removeCallbacks(mUpdateListRunnable);
        mHandler.post(mUpdateListRunnable);
    }

    @Override
    public void onArtistUpdate(List<Artist> a) {
    }

    @Override
    public void onProviderConnected(IMusicProvider provider) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mAdapter.addAllUnique(ProviderAggregator.getDefault().getAllPlaylists());
            }
        });
    }

    @Override
    public void onSearchResult(SearchResult searchResult) {
    }
}
