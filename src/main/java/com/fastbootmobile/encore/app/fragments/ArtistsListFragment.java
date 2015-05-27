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
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.lucasr.twowayview.ItemClickSupport;
import org.lucasr.twowayview.TwoWayView;
import org.lucasr.twowayview.widget.DividerItemDecoration;
import com.fastbootmobile.encore.app.ArtistActivity;
import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.app.adapters.ArtistsAdapter;
import com.fastbootmobile.encore.app.ui.AlbumArtImageView;
import com.fastbootmobile.encore.framework.PluginsLookup;
import com.fastbootmobile.encore.model.Album;
import com.fastbootmobile.encore.model.Artist;
import com.fastbootmobile.encore.model.Playlist;
import com.fastbootmobile.encore.model.SearchResult;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.ILocalCallback;
import com.fastbootmobile.encore.providers.IMusicProvider;
import com.fastbootmobile.encore.providers.ProviderAggregator;
import com.fastbootmobile.encore.providers.ProviderConnection;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment displaying a list of artists
 */
public class ArtistsListFragment extends Fragment implements ILocalCallback {
    private static final String TAG = "ArtistsListFragment";

    private ArtistsAdapter mAdapter;
    private Handler mHandler;
    private boolean mAdapterSet;
    private TwoWayView mArtistLayout;

    private final ItemClickSupport.OnItemClickListener mItemClickListener = new ItemClickSupport.OnItemClickListener() {
        @Override
        public void onItemClick(RecyclerView parent, View view, int position, long id) {
            final ArtistsAdapter.ViewHolder tag = (ArtistsAdapter.ViewHolder) view.getTag();
            final Context ctx = view.getContext();
            Artist artist = mAdapter.getItem(tag.position);
            Intent intent = ArtistActivity.craftIntent(ctx, tag.srcBitmap, artist.getRef(),
                    artist.getProvider(), tag.itemColor);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AlbumArtImageView ivCover = tag.ivCover;
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_artists, container, false);
        mArtistLayout = (TwoWayView) root.findViewById(R.id.twvArtists);

        final Drawable divider = getResources().getDrawable(R.drawable.divider);
        mArtistLayout.addItemDecoration(new DividerItemDecoration(divider));

        final ItemClickSupport itemClick = ItemClickSupport.addTo(mArtistLayout);
        itemClick.setOnItemClickListener(mItemClickListener);

        // Get artists
        new GetArtistsTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

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
    public void onPlaylistRemoved(String ref) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onArtistUpdate(final List<Artist> artists) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                for (Artist a : artists) {
                    int index = mAdapter.indexOf(a);
                    if (index >= 0) {
                        mAdapter.notifyItemChanged(index);
                    }
                }
            }
        });
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

    private class GetArtistsTask extends AsyncTask<Void, Void, List<Artist>> {

        @Override
        protected List<Artist> doInBackground(Void... params) {
            List<ProviderConnection> providers = PluginsLookup.getDefault().getAvailableProviders();
            final List<Artist> artists = new ArrayList<>();
            for (ProviderConnection providerConnection : providers) {
                try {
                    IMusicProvider provider = providerConnection.getBinder();
                    if (provider != null) {
                        List<Artist> providerArtists = provider.getArtists();
                        if (providerArtists != null) {
                            artists.addAll(providerArtists);
                        }
                    }
                } catch (DeadObjectException e) {
                    Log.e(TAG, "Provider died while getting artists");
                } catch (RemoteException e) {
                    Log.w(TAG, "Cannot get artists from a provider", e);
                }
            }

            mAdapter.addAllUnique(artists);
            return artists;
        }

        @Override
        protected void onPostExecute(List<Artist> artists) {

            if (mAdapterSet) {
                mAdapter.notifyDataSetChanged();
            } else {
                mAdapterSet = true;
                mArtistLayout.setAdapter(mAdapter);
            }
        }
    }
}