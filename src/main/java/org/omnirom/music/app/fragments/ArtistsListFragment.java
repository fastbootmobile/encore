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
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.lucasr.twowayview.ItemClickSupport;
import org.lucasr.twowayview.TwoWayView;
import org.lucasr.twowayview.widget.DividerItemDecoration;
import org.omnirom.music.app.ArtistActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.adapters.ArtistsAdapter;
import org.omnirom.music.app.ui.AlbumArtImageView;
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
 * Fragment displaying a list of artists
 */
public class ArtistsListFragment extends Fragment implements ILocalCallback {

    private ArtistsAdapter mAdapter;
    private Handler mHandler;

    private final ItemClickSupport.OnItemClickListener mItemClickListener = new ItemClickSupport.OnItemClickListener() {
        @Override
        public void onItemClick(RecyclerView parent, View view, int position, long id) {
            final ArtistsAdapter.ViewHolder tag = (ArtistsAdapter.ViewHolder) view.getTag();
            final Context ctx = view.getContext();
            String artistRef = mAdapter.getItem(tag.position).getRef();
            Intent intent = ArtistActivity.craftIntent(ctx, tag.srcBitmap, artistRef, tag.itemColor);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AlbumArtImageView ivCover = tag.ivCover;
                TextView tvTitle = tag.tvTitle;
                ActivityOptions opt = ActivityOptions.makeSceneTransitionAnimation(getActivity(),
                    ivCover, "itemImage");

                ctx.startActivity(intent, opt.toBundle());
            } else {
                ctx.startActivity(intent);
            }
        }
    };

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment PlaylistListFragment.
     */
    public static ArtistsListFragment newInstance() {
        return new ArtistsListFragment();
    }

    /**
     * Default constructor
     */
    public ArtistsListFragment() {
        mAdapter = new ArtistsAdapter();
        mHandler = new Handler();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_artists, container, false);
        TwoWayView artistLayout = (TwoWayView) root.findViewById(R.id.twvArtists);
        artistLayout.setAdapter(mAdapter);

        final Drawable divider = getResources().getDrawable(R.drawable.divider);
        artistLayout.addItemDecoration(new DividerItemDecoration(divider));

        final ItemClickSupport itemClick = ItemClickSupport.addTo(artistLayout);
        itemClick.setOnItemClickListener(mItemClickListener);

        new Thread() {
            public void run() {
                final List<Artist> artists = ProviderAggregator.getDefault().getCache().getAllArtists();
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mAdapter.addAllUnique(artists);
                        mAdapter.notifyDataSetChanged();
                    }
                });
            }
        }.start();

        return root;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onResume() {
        super.onResume();
        ProviderAggregator.getDefault().addUpdateCallback(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPause() {
        super.onPause();
        ProviderAggregator.getDefault().removeUpdateCallback(this);
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
    public void onAlbumUpdate(List<Album> a) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPlaylistUpdate(final List<Playlist> p) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onArtistUpdate(List<Artist> artists) {
        for (Artist a : artists) {
            int index = mAdapter.indexOf(a);
            if (index >= 0) {
                mAdapter.notifyItemChanged(index);
            }
        }
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
    public void onSearchResult(SearchResult searchResult) {
    }
}