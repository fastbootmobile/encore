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

import android.app.ActivityOptions;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;

import org.omnirom.music.app.AlbumActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.adapters.AlbumsAdapter;
import org.omnirom.music.app.ui.MaterialTransitionDrawable;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.SearchResult;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ILocalCallback;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.ProviderAggregator;

import java.util.List;

/**
 * Fragment displaying a list of albums
 */
public class AlbumsFragment extends Fragment implements ILocalCallback, ProviderAggregator.OfflineModeListener {
    private AlbumsAdapter mAdapter;
    private Handler mHandler;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment AlbumsFragment.
     */
    public static AlbumsFragment newInstance() {
        return new AlbumsFragment();
    }

    /**
     * Default constructor for the fragment. You should not use this constructor but rather
     * use {@link #newInstance()}.
     */
    public AlbumsFragment() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAdapter = new AlbumsAdapter(getResources());
        mHandler = new Handler();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        final View root = inflater.inflate(R.layout.fragment_albums, container, false);
        final GridView albumLayout = (GridView) root.findViewById(R.id.gvAlbums);
        albumLayout.setAdapter(mAdapter);

        final List<Album> allAlbums = ProviderAggregator.getDefault().getCache().getAllAlbums();
        mAdapter.addAllUnique(allAlbums);
        mAdapter.notifyDataSetChanged();

        // Setup the click listener
        albumLayout.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AlbumsAdapter.ViewHolder tag = (AlbumsAdapter.ViewHolder) view.getTag();
                ImageView ivCover = tag.ivCover;
                Album item = mAdapter.getItem(position);

                Bitmap hero = ((MaterialTransitionDrawable) ivCover.getDrawable()).getFinalDrawable().getBitmap();
                Intent intent = AlbumActivity.craftIntent(getActivity(), hero,
                        item.getRef(), item.getProvider(), tag.itemColor);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ActivityOptions opt = ActivityOptions.makeSceneTransitionAnimation(getActivity(),
                            tag.ivCover, "itemImage");
                    getActivity().startActivity(intent, opt.toBundle());
                } else {
                    startActivity(intent);
                }
            }
        });

        return root;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onResume() {
        super.onResume();
        ProviderAggregator.getDefault().addUpdateCallback(this);
        ProviderAggregator.getDefault().registerOfflineModeListener(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPause() {
        super.onPause();
        ProviderAggregator.getDefault().removeUpdateCallback(this);
        ProviderAggregator.getDefault().unregisterOfflineModeListener(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSongUpdate(List<Song> s) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAlbumUpdate(final List<Album> a) {
        // AddAllUnique only adds loaded entities
        if (mAdapter.addAllUnique(a)) {
            mAdapter.notifyDataSetChanged();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPlaylistUpdate(List<Playlist> p) {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onArtistUpdate(List<Artist> a) {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onProviderConnected(IMusicProvider provider) {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSearchResult(List<SearchResult> searchResult) {

    }

    @Override
    public void onOfflineModeChange(boolean enabled) {
        mHandler.post(new Runnable() {
            public void run() {
                mAdapter.notifyDataSetChanged();
            }
        });
    }
}