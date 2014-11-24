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
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v7.graphics.Palette;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.TextView;

import org.omnirom.music.app.AlbumActivity;
import org.omnirom.music.app.ArtistActivity;
import org.omnirom.music.app.PlaylistActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.app.adapters.SearchAdapter;
import org.omnirom.music.app.ui.MaterialTransitionDrawable;
import org.omnirom.music.framework.PlaybackProxy;
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
 * Fragment displaying search results
 */
public class SearchFragment extends Fragment implements ILocalCallback {
    private static final String TAG = "SearchFragment";

    private static SearchResult sSearchResult;

    private SearchAdapter mAdapter;
    private Handler mHandler;

    /**
     * Default constructor
     */
    public SearchFragment() {
        ProviderAggregator.getDefault().addUpdateCallback(this);
        mHandler = new Handler();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View root = inflater.inflate(R.layout.fragment_search, container, false);
        assert root != null;

        getActivity().setProgressBarIndeterminate(true);
        getActivity().setProgressBarIndeterminateVisibility(true);

        mAdapter = new SearchAdapter();

        ExpandableListView listView = (ExpandableListView) root.findViewById(R.id.expandablelv_search);
        listView.setAdapter(mAdapter);
        listView.setGroupIndicator(null);
        for (int i = 0; i < 4; i++) {
            listView.expandGroup(i, false);
        }

        listView.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView expandableListView, View view, int i, int i2, long l) {
                if (sSearchResult != null) {
                    switch (i) {
                        case SearchAdapter.ARTIST:
                            onArtistClick(i2, view);
                            break;

                        case SearchAdapter.ALBUM:
                            onAlbumClick(i2, view);
                            break;

                        case SearchAdapter.SONG:
                            onSongClick(i2);
                            break;

                        case SearchAdapter.PLAYLIST:
                            onPlaylistClick(i2, view);
                            break;

                        default:
                            Log.e(TAG, "Unknown child group id " + i);
                            break;
                    }

                    return true;
                } else {
                    return false;
                }
            }
        });

        // Restore previous search results, in case we're rotating
        // TODO: Persist the adapter instead
        if (sSearchResult != null) {
            mAdapter.appendResults(sSearchResult);
            mAdapter.notifyDataSetChanged();
        }

        return root;
    }

    public void resetResults() {
        if (mAdapter != null) {
            mAdapter.clear();
        }
    }

    private void onSongClick(int i) {
        SearchAdapter.SearchEntry entry = mAdapter.getChild(SearchAdapter.SONG, i);
        final int numSongEntries = mAdapter.getChildrenCount(SearchAdapter.SONG);
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();

        if (i == Math.max(10, numSongEntries + 1)) {
            // More
        } else {
            Song song = aggregator.retrieveSong(entry.ref, entry.identifier);
            if (song != null) {
                PlaybackProxy.playSong(song);
            }
        }

    }

    private void onAlbumClick(int i, View v) {
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();
        SearchAdapter.ViewHolder holder = (SearchAdapter.ViewHolder) v.getTag();
        Bitmap hero = ((MaterialTransitionDrawable) holder.albumArtImageView.getDrawable()).getFinalDrawable().getBitmap();
        int color = 0xffffff;
        if (hero != null) {
            Palette palette = Palette.generate(hero);
            Palette.Swatch darkVibrantColor = palette.getDarkVibrantSwatch();
            Palette.Swatch darkMutedColor = palette.getDarkMutedSwatch();

            if (darkVibrantColor != null) {
                color = darkVibrantColor.getRgb();
            } else if (darkMutedColor != null) {
                color = darkMutedColor.getRgb();
            } else {
                color = getResources().getColor(R.color.default_album_art_background);
            }
        }
        SearchAdapter.SearchEntry entry = mAdapter.getChild(SearchAdapter.ALBUM, i);
        Album album = aggregator.retrieveAlbum(entry.ref, entry.identifier);
        Intent intent = AlbumActivity.craftIntent(getActivity(), hero, album, color);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ImageView ivCover = holder.albumArtImageView;
            ActivityOptions opt = ActivityOptions.makeSceneTransitionAnimation(getActivity(),
                    new Pair<View, String>(ivCover, "itemGroup"));
            getActivity().startActivity(intent, opt.toBundle());
        } else {
            startActivity(intent);
        }
    }

    private void onArtistClick(int i, View v) {
        SearchAdapter.ViewHolder holder = (SearchAdapter.ViewHolder) v.getTag();
        ImageView ivCover = holder.albumArtImageView;
        Bitmap hero = ((MaterialTransitionDrawable) ivCover.getDrawable()).getFinalDrawable().getBitmap();
        int color = 0xffffff;
        if (hero != null) {
            Palette palette = Palette.generate(hero);
            Palette.Swatch darkVibrantColor = palette.getDarkVibrantSwatch();
            Palette.Swatch darkMutedColor = palette.getDarkMutedSwatch();

            if (darkVibrantColor != null) {
                color = darkVibrantColor.getRgb();
            } else if (darkMutedColor != null) {
                color = darkMutedColor.getRgb();
            } else {
                color = getResources().getColor(R.color.default_album_art_background);
            }
        }
        Intent intent = new Intent(getActivity(), ArtistActivity.class);
        intent.putExtra(ArtistActivity.EXTRA_ARTIST, mAdapter.getChild(SearchAdapter.ARTIST, i).ref);
        intent.putExtra(ArtistActivity.EXTRA_BACKGROUND_COLOR,
                color);
        Utils.queueBitmap(ArtistActivity.BITMAP_ARTIST_HERO, hero);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ActivityOptions opt = ActivityOptions.makeSceneTransitionAnimation(getActivity(),
                    ivCover, "itemImage");
            getActivity().startActivity(intent, opt.toBundle());
        } else {
            startActivity(intent);
        }
    }

    private void onPlaylistClick(int i, View v) {
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();
        SearchAdapter.SearchEntry entry = mAdapter.getChild(SearchAdapter.PLAYLIST, i);
        Playlist playlist = aggregator.retrievePlaylist(entry.ref, entry.identifier);
        if (playlist != null) {
            Intent intent = PlaylistActivity.craftIntent(getActivity(), playlist,
                    ((BitmapDrawable) getResources().getDrawable(R.drawable.album_placeholder)).getBitmap());
            startActivity(intent);
        }
    }

    @Override
    public void onSongUpdate(List<Song> s) {
        for (Song song : s) {
            if (mAdapter.contains(song)) {
                mAdapter.notifyDataSetChanged();
                return;
            }
        }
    }

    @Override
    public void onAlbumUpdate(List<Album> a) {
        for (Album album : a) {
            if (mAdapter.contains(album)) {
                mAdapter.notifyDataSetChanged();
                return;
            }
        }
    }

    @Override
    public void onPlaylistUpdate(List<Playlist> p) {
        for (Playlist playlist : p) {
            if (mAdapter.contains(playlist)) {
                mAdapter.notifyDataSetChanged();
                return;
            }
        }
    }

    @Override
    public void onArtistUpdate(List<Artist> a) {
        for (Artist artist : a) {
            if (mAdapter.contains(artist)) {
                mAdapter.notifyDataSetChanged();
                return;
            }
        }
    }

    @Override
    public void onProviderConnected(IMusicProvider provider) {
    }

    @Override
    public void onSearchResult(final SearchResult searchResult) {
        Log.d(TAG, "search result received " + searchResult + " (" + searchResult.getIdentifier()
                + " / " + searchResult.mIdentifier + ")");

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (getActivity() != null) {
                    getActivity().setTitle("'" + searchResult.getQuery() + "'");
                    getActivity().setProgressBarIndeterminateVisibility(false);
                } else {
                    mHandler.post(this);
                }
            }
        });

        sSearchResult = searchResult;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mAdapter != null) {
                    if (searchResult.getIdentifier() == null) {
                        Log.e(TAG, "Search provider identifier is null!");
                    } else {
                        mAdapter.appendResults(searchResult);
                        mAdapter.notifyDataSetChanged();
                    }
                }
            }
        });
    }
}
