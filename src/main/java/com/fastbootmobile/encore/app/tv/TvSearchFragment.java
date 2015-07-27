/*
 * Copyright (C) 2015 Fastboot Mobile, LLC.
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

package com.fastbootmobile.encore.app.tv;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v17.leanback.app.SearchFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.ObjectAdapter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v17.leanback.widget.SpeechRecognitionCallback;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v7.graphics.Palette;
import android.text.TextUtils;
import android.util.Log;

import com.fastbootmobile.encore.app.BuildConfig;
import com.fastbootmobile.encore.app.R;
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

public class TvSearchFragment extends SearchFragment
        implements SearchFragment.SearchResultProvider {
    private static final String TAG = "TvSearchFragment";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static final int SEARCH_DELAY_MS = 1000;
    private static final boolean FINISH_ON_RECOGNIZER_CANCELED = true;
    private static final int REQUEST_SPEECH = 0x00000010;

    private ArrayObjectAdapter mRowsAdapter;
    private String mQuery;
    private Handler mHandler = new Handler();
    private final Runnable mDelayedLoad = new Runnable() {
        @Override
        public void run() {
            loadRows();
        }
    };
    private final Runnable mUpdateAdapter = new Runnable() {
        @Override
        public void run() {
            final int numSubs = mRowsAdapter.size();
            for (int i = 0; i < numSubs; ++i) {
                ListRow row = (ListRow) mRowsAdapter.get(i);
                ArrayObjectAdapter adapter = (ArrayObjectAdapter) row.getAdapter();
                adapter.notifyArrayItemRangeChanged(0, adapter.size());
            }
        }
    };
    private ILocalCallback mCallback = new SearchLocalCallback();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        setSearchResultProvider(this);
        setOnItemViewClickedListener(new OnItemViewClickedListener() {
            @Override
            public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
                if (item instanceof Album) {
                    Album album = (Album) item;
                    int color = getResources().getColor(R.color.primary);
                    if (itemViewHolder.view.getTag() != null && itemViewHolder.view.getTag() instanceof Palette) {
                        color = ((Palette) itemViewHolder.view.getTag()).getDarkVibrantColor(color);
                    }

                    Intent intent = new Intent(getActivity(), TvAlbumDetailsActivity.class);
                    intent.putExtra(TvAlbumDetailsActivity.EXTRA_ALBUM, album);
                    intent.putExtra(TvAlbumDetailsActivity.EXTRA_COLOR, color);

                    Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                            getActivity(),
                            ((ImageCardView) itemViewHolder.view).getMainImageView(),
                            TvAlbumDetailsActivity.SHARED_ELEMENT_NAME).toBundle();
                    startActivity(intent, bundle);
                } else if (item instanceof Artist) {
                    Artist artist = (Artist) item;
                    int color = getResources().getColor(R.color.primary);
                    if (itemViewHolder.view.getTag() != null && itemViewHolder.view.getTag() instanceof Palette) {
                        color = ((Palette) itemViewHolder.view.getTag()).getDarkVibrantColor(color);
                    }

                    Intent intent = new Intent(getActivity(), TvArtistDetailsActivity.class);
                    intent.putExtra(TvArtistDetailsActivity.EXTRA_ARTIST, artist);
                    intent.putExtra(TvArtistDetailsActivity.EXTRA_COLOR, color);

                    Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                            getActivity(),
                            ((ImageCardView) itemViewHolder.view).getMainImageView(),
                            TvArtistDetailsActivity.SHARED_ELEMENT_NAME).toBundle();
                    startActivity(intent, bundle);
                } else if (item instanceof Playlist) {
                    Playlist playlist = (Playlist) item;
                    int color = getResources().getColor(R.color.primary);
                    if (itemViewHolder.view.getTag() != null && itemViewHolder.view.getTag() instanceof Palette) {
                        color = ((Palette) itemViewHolder.view.getTag()).getDarkVibrantColor(color);
                    }

                    Intent intent = new Intent(getActivity(), TvPlaylistDetailsActivity.class);
                    intent.putExtra(TvPlaylistDetailsActivity.EXTRA_PLAYLIST, playlist);
                    intent.putExtra(TvPlaylistDetailsActivity.EXTRA_COLOR, color);

                    Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                            getActivity(),
                            ((ImageCardView) itemViewHolder.view).getMainImageView(),
                            TvAlbumDetailsActivity.SHARED_ELEMENT_NAME).toBundle();
                    startActivity(intent, bundle);
                } else if (item instanceof Song) {
                    PlaybackProxy.playSong((Song) item);
                }
            }
        });

        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            // SpeechRecognitionCallback is not required and if not provided recognition will be handled
            // using internal speech recognizer, in which case you must have RECORD_AUDIO permission
            setSpeechRecognitionCallback(new SpeechRecognitionCallback() {
                @Override
                public void recognizeSpeech() {
                    if (DEBUG) Log.v(TAG, "recognizeSpeech");
                    try {
                        startActivityForResult(getRecognizerIntent(), REQUEST_SPEECH);
                    } catch (ActivityNotFoundException e) {
                        Log.e(TAG, "Cannot find activity for speech recognizer", e);
                    }
                }
            });
        }

    }

    @Override
    public void onPause() {
        mHandler.removeCallbacksAndMessages(null);
        ProviderAggregator.getDefault().removeUpdateCallback(mCallback);
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        ProviderAggregator.getDefault().addUpdateCallback(mCallback);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (DEBUG) Log.v(TAG, "onActivityResult requestCode=" + requestCode +
                " resultCode=" + resultCode +
                " data=" + data);
        switch (requestCode) {
            case REQUEST_SPEECH:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        setSearchQuery(data, true);
                        break;
                    case Activity.RESULT_CANCELED:
                        // Once recognizer canceled, user expects the current activity to process
                        // the same BACK press as user doesn't know about overlay activity.
                        // However, you may not want this behaviour as it makes harder to
                        // fall back to keyboard input.
                        if (FINISH_ON_RECOGNIZER_CANCELED) {
                            if (!hasResults()) {
                                if (DEBUG) Log.v(TAG, "Delegating BACK press from recognizer");
                                getActivity().onBackPressed();
                            }
                        }
                        break;
                    // the rest includes various recognizer errors, see {@link RecognizerIntent}
                }
                break;
        }
    }

    @Override
    public ObjectAdapter getResultsAdapter() {
        return mRowsAdapter;
    }

    @Override
    public boolean onQueryTextChange(String newQuery) {
        Log.i(TAG, String.format("Search Query Text Change %s", newQuery));
        loadQuery(newQuery);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        Log.i(TAG, String.format("Search Query Text Submit %s", query));
        loadQuery(query);
        return true;
    }

    private void loadRows() {
        // offload processing from the UI thread
        new AsyncTask<String, Void, ListRow>() {
            private final String query = mQuery;

            @Override
            protected void onPreExecute() {
                mRowsAdapter.clear();
            }

            @Override
            protected ListRow doInBackground(String... params) {
                ProviderAggregator.getDefault().startSearch(query);
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private boolean hasPermission(final String permission) {
        final Context context = getActivity();
        return PackageManager.PERMISSION_GRANTED == context.getPackageManager().checkPermission(
                permission, context.getPackageName());
    }

    public boolean hasResults() {
        return mRowsAdapter.size() > 0;
    }

    private void loadQuery(String query) {
        mHandler.removeCallbacks(mDelayedLoad);
        if (!TextUtils.isEmpty(query) && !query.equals("nil")) {
            mQuery = query;
            mHandler.postDelayed(mDelayedLoad, SEARCH_DELAY_MS);
        }
    }

    private class SearchLocalCallback implements ILocalCallback {

        @Override
        public void onSongUpdate(List<Song> s) {
            if (mRowsAdapter != null) {
                mHandler.post(mUpdateAdapter);
            }
        }

        @Override
        public void onAlbumUpdate(List<Album> a) {
            if (mRowsAdapter != null) {
                mHandler.post(mUpdateAdapter);
            }
        }

        @Override
        public void onPlaylistUpdate(List<Playlist> p) {
            if (mRowsAdapter != null) {
                mHandler.post(mUpdateAdapter);
            }
        }

        @Override
        public void onPlaylistRemoved(String ref) {
        }

        @Override
        public void onArtistUpdate(List<Artist> a) {
            if (mRowsAdapter != null) {
                mHandler.post(mUpdateAdapter);
            }
        }

        @Override
        public void onProviderConnected(IMusicProvider provider) {

        }

        @Override
        public void onSearchResult(List<SearchResult> searchResult) {
            final ArrayObjectAdapter artistRowAdapter = new ArrayObjectAdapter(new CardPresenter());
            final ArrayObjectAdapter albumRowAdapter = new ArrayObjectAdapter(new CardPresenter());
            final ArrayObjectAdapter songsRowAdapter = new ArrayObjectAdapter(new CardPresenter());
            final ArrayObjectAdapter playlistsRowAdapter = new ArrayObjectAdapter(new CardPresenter());

            final ProviderAggregator aggregator = ProviderAggregator.getDefault();

            for (SearchResult result : searchResult) {
                for (String ref : result.getArtistList()) {
                    Artist artist = aggregator.retrieveArtist(ref, result.getIdentifier());
                    if (artist != null) {
                        artistRowAdapter.add(artist);
                    }
                }

                for (String ref : result.getAlbumsList()) {
                    Album album = aggregator.retrieveAlbum(ref, result.getIdentifier());
                    if (album != null) {
                        albumRowAdapter.add(album);
                    }
                }

                for (String ref : result.getSongsList()) {
                    Song song = aggregator.retrieveSong(ref, result.getIdentifier());
                    if (song != null) {
                        songsRowAdapter.add(song);
                    }
                }

                for (String ref : result.getPlaylistList()) {
                    Playlist playlist = aggregator.retrievePlaylist(ref, result.getIdentifier());
                    if (playlist != null) {
                        playlistsRowAdapter.add(playlist);
                    }
                }
            }

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mRowsAdapter.clear();

                    HeaderItem header = new HeaderItem(getString(R.string.artist));
                    mRowsAdapter.add(new ListRow(header, artistRowAdapter));

                    header = new HeaderItem(getString(R.string.albums));
                    mRowsAdapter.add(new ListRow(header, albumRowAdapter));

                    header = new HeaderItem(getString(R.string.songs));
                    mRowsAdapter.add(new ListRow(header, songsRowAdapter));

                    header = new HeaderItem(getString(R.string.tab_playlists));
                    mRowsAdapter.add(new ListRow(header, playlistsRowAdapter));

                    mRowsAdapter.notifyArrayItemRangeChanged(0, mRowsAdapter.size());
                }
            });
        }
    }
}