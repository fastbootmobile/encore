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

import android.app.ActivityOptions;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
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

import com.fastbootmobile.encore.app.AlbumActivity;
import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.app.adapters.AlbumsAdapter;
import com.fastbootmobile.encore.app.ui.MaterialTransitionDrawable;
import com.fastbootmobile.encore.model.Album;
import com.fastbootmobile.encore.model.Artist;
import com.fastbootmobile.encore.model.Playlist;
import com.fastbootmobile.encore.model.SearchResult;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.ILocalCallback;
import com.fastbootmobile.encore.providers.IMusicProvider;
import com.fastbootmobile.encore.providers.ProviderAggregator;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment displaying a list of albums
 */
public class AlbumsFragment extends Fragment implements ILocalCallback, ProviderAggregator.OfflineModeListener {
    private AlbumsAdapter mAdapter;
    private GridView mGridView;
    private Handler mHandler;
    private boolean mAdapterSet = false;

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
        mGridView = (GridView) root.findViewById(R.id.gvAlbums);
        mGridView.setFastScrollEnabled(true);

        // Get the albums
        new GetAlbumsTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        // Setup the click listener
        mGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
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
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mAdapter.notifyDataSetChanged();
                }
            });
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
    public void onPlaylistRemoved(String ref) {
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

    private class GetAlbumsTask extends AsyncTask<Void, Void, List<Album>> {

        @Override
        protected List<Album> doInBackground(Void... params) {
            List<Album> outputList = new ArrayList<>();
            List<Album> cachedAlbums = ProviderAggregator.getDefault().getCache().getAllAlbums();

            for (Album album : cachedAlbums) {
                outputList.add(ProviderAggregator.getDefault().retrieveAlbum(album.getRef(),
                        album.getProvider()));
            }

            return outputList;
        }

        @Override
        protected void onPostExecute(List<Album> albums) {
            mAdapter.addAllUnique(albums);
            mAdapter.notifyDataSetChanged();

            if (mAdapterSet) {
                mAdapter.notifyDataSetChanged();
            } else {
                mGridView.setAdapter(mAdapter);
            }
        }
    }
}