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

package com.fastbootmobile.encore.app.tv;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.support.v17.leanback.app.BrowseFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v7.graphics.Palette;
import android.view.View;

import com.fastbootmobile.encore.api.common.Pair;
import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.app.adapters.HistoryAdapter;
import com.fastbootmobile.encore.framework.ListenLogger;
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
import com.fastbootmobile.encore.providers.ProviderIdentifier;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class TvActivity extends Activity {
    public static final String TAG = "TvActivity";

    private static final int MSG_NOTIFY_CHANGES = 1;

    private static final int TYPE_ALBUM = 0;
    private static final int TYPE_ARTIST = 1;

    private static final int ROW_RECENTS            = 0;
    private static final int ROW_RECOMMENDATIONS    = 1;
    private static final int ROW_LIBRARY            = 2;
    private static final int ROW_PLAYLISTS          = 3;
    private static final int ROW_SETTINGS           = 4;

    private ArrayObjectAdapter mRowsAdapter;
    private Handler mHandler;
    private int mNumSuggestions;

    protected BrowseFragment mBrowseFragment;
    private ILocalCallback mLocalCallback = new TvLocalCallback();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tv_browsefragment);

        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_NOTIFY_CHANGES:
                        if (mRowsAdapter != null) {
                            mRowsAdapter.notifyArrayItemRangeChanged(0, 2);
                        }
                        break;
                }
            }
        };

        final FragmentManager fragmentManager = getFragmentManager();
        mBrowseFragment = (BrowseFragment) fragmentManager.findFragmentById(
                R.id.browse_fragment);

        // Set display parameters for the BrowseFragment
        mBrowseFragment.setHeadersState(BrowseFragment.HEADERS_ENABLED);
        mBrowseFragment.setTitle(getString(R.string.app_name));
        mBrowseFragment.setBadgeDrawable(getResources().getDrawable(
                R.mipmap.ic_launcher));

        // Set search interface
        mBrowseFragment.setOnSearchClickedListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(TvActivity.this, TvSearchActivity.class);
                startActivity(intent);
            }
        });
        mBrowseFragment.setSearchAffordanceColor(getResources().getColor(R.color.primary_dark));

        // Setup event listeners
        mBrowseFragment.setOnItemViewClickedListener(new OnItemViewClickedListener() {
            @Override
            public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                      RowPresenter.ViewHolder rowViewHolder, Row row) {
                if (item instanceof Album) {
                    Album album = (Album) item;
                    int color = getResources().getColor(R.color.primary);
                    if (itemViewHolder.view.getTag() != null && itemViewHolder.view.getTag() instanceof Palette) {
                        color = ((Palette) itemViewHolder.view.getTag()).getDarkVibrantColor(color);
                    }

                    Intent intent = new Intent(TvActivity.this, TvAlbumDetailsActivity.class);
                    intent.putExtra(TvAlbumDetailsActivity.EXTRA_ALBUM, album);
                    intent.putExtra(TvAlbumDetailsActivity.EXTRA_COLOR, color);

                    Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                            TvActivity.this,
                            ((ImageCardView) itemViewHolder.view).getMainImageView(),
                            TvAlbumDetailsActivity.SHARED_ELEMENT_NAME).toBundle();
                    startActivity(intent, bundle);
                } else if (item instanceof Artist) {
                    Artist artist = (Artist) item;
                    int color = getResources().getColor(R.color.primary);
                    if (itemViewHolder.view.getTag() != null && itemViewHolder.view.getTag() instanceof Palette) {
                        color = ((Palette) itemViewHolder.view.getTag()).getDarkVibrantColor(color);
                    }

                    Intent intent = new Intent(TvActivity.this, TvArtistDetailsActivity.class);
                    intent.putExtra(TvArtistDetailsActivity.EXTRA_ARTIST, artist);
                    intent.putExtra(TvArtistDetailsActivity.EXTRA_COLOR, color);

                    Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                            TvActivity.this,
                            ((ImageCardView) itemViewHolder.view).getMainImageView(),
                            TvArtistDetailsActivity.SHARED_ELEMENT_NAME).toBundle();
                    startActivity(intent, bundle);
                } else if (item instanceof Playlist) {
                    Playlist playlist = (Playlist) item;
                    int color = getResources().getColor(R.color.primary);
                    if (itemViewHolder.view.getTag() != null && itemViewHolder.view.getTag() instanceof Palette) {
                        color = ((Palette) itemViewHolder.view.getTag()).getDarkVibrantColor(color);
                    }

                    Intent intent = new Intent(TvActivity.this, TvPlaylistDetailsActivity.class);
                    intent.putExtra(TvPlaylistDetailsActivity.EXTRA_PLAYLIST, playlist);
                    intent.putExtra(TvPlaylistDetailsActivity.EXTRA_COLOR, color);

                    Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                            TvActivity.this,
                            ((ImageCardView) itemViewHolder.view).getMainImageView(),
                            TvAlbumDetailsActivity.SHARED_ELEMENT_NAME).toBundle();
                    startActivity(intent, bundle);
                } else if (item instanceof SettingsItem) {
                    SettingsItem sitem = (SettingsItem) item;
                    switch (sitem.getType()) {
                        case SettingsItem.ITEM_PROVIDERS:
                            startActivity(new Intent(TvActivity.this, TvProvidersActivity.class));
                            break;

                        case SettingsItem.ITEM_EFFECTS:
                            Intent intent = new Intent(TvActivity.this, TvProvidersActivity.class);
                            intent.putExtra(TvProvidersActivity.EXTRA_DSP_MODE, true);
                            startActivity(intent);
                            break;
                    }
                } else if (item instanceof MyLibraryItem) {
                    MyLibraryItem libraryItem = (MyLibraryItem) item;
                    Intent intent = new Intent(TvActivity.this, TvEntityGridActivity.class);
                    switch (libraryItem.getType()) {
                        case MyLibraryItem.TYPE_ALBUMS:
                            intent.putExtra(TvEntityGridActivity.EXTRA_MODE, TvEntityGridActivity.MODE_ALBUM);
                            break;

                        case MyLibraryItem.TYPE_ARTISTS:
                            intent.putExtra(TvEntityGridActivity.EXTRA_MODE, TvEntityGridActivity.MODE_ARTIST);
                            break;
                    }

                    startActivity(intent);
                }
            }
        });

        // Build adapter items
        mHandler.postDelayed(new Runnable() {
            public void run() {
                buildRowsAdapter();
            }
        }, 1500);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ProviderAggregator.getDefault().addUpdateCallback(mLocalCallback);

        if (mNumSuggestions > 2) {
            requestAdapterUpdate();
        } else {
            mHandler.postDelayed(new Runnable() {
                public void run() {
                    buildRowsAdapter();
                }
            }, 1500);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        ProviderAggregator.getDefault().removeUpdateCallback(mLocalCallback);
    }

    private void requestAdapterUpdate() {
        mHandler.removeMessages(MSG_NOTIFY_CHANGES);
        mHandler.sendEmptyMessage(MSG_NOTIFY_CHANGES);
    }

    private void buildRowsAdapter() {
        ClassPresenterSelector selector = new ClassPresenterSelector();
        selector.addClassPresenter(ListRow.class, new ListRowPresenter());
        selector.addClassPresenter(ShadowlessListRow.class, ShadowlessListRow.createPresenter(this));

        mRowsAdapter = new ArrayObjectAdapter(selector);

        // Generate rows contents
        generateRecentlyPlayedRow();
        generateRecommendations();
        generateMyLibraryRow();
        generatePlaylistsRow();
        generateSettingsRow();

        mBrowseFragment.setAdapter(mRowsAdapter);
    }

    private void generateRecentlyPlayedRow() {
        final Random rand = new Random();
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();

        // First row: Recently played (10 items, randomly artist or album)
        ListenLogger logger = new ListenLogger(this);
        List<ListenLogger.LogEntry> logEntries = HistoryAdapter.sortByTime(logger.getEntries(25));
        ArrayObjectAdapter logEntriesRowAdapter = new ArrayObjectAdapter(new CardPresenter());
        int entriesCount = 0;

        for (ListenLogger.LogEntry logEntry : logEntries) {
            if (entriesCount == 10) break;

            // Get the song information
            Song song = aggregator.retrieveSong(logEntry.getReference(), logEntry.getIdentifier());

            if (song == null) {
                // That song might not exist anymore, keep moving
                continue;
            }

            final int type = rand.nextInt(2);
            if (type == TYPE_ALBUM && song.getAlbum() == null
                    || type == TYPE_ARTIST && song.getArtist() == null) {
                // We don't have the requested track info, so skip it
                continue;
            }

            if (type == TYPE_ALBUM) {
                Album album = aggregator.retrieveAlbum(song.getAlbum(), song.getProvider());
                logEntriesRowAdapter.add(album);
            } else if (type == TYPE_ARTIST) {
                Artist artist = aggregator.retrieveArtist(song.getArtist(), song.getProvider());
                logEntriesRowAdapter.add(artist);
            }
            ++entriesCount;
        }

        // Build Recently Played Leanback item
        HeaderItem header = new HeaderItem(ROW_RECENTS, "Recently played");
        ListRow row = new ListRow(header, logEntriesRowAdapter);
        if (mRowsAdapter.size() > ROW_RECENTS) {
            mRowsAdapter.replace(ROW_RECENTS, row);
        } else {
            mRowsAdapter.add(row);
        }
    }

    private void generateRecommendations() {
        final Random rand = new Random();
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();

        // Get all the available tracks to build Recommendations
        final List<Pair<String, ProviderIdentifier>> availableReferences = new ArrayList<>();
        final List<ProviderConnection> providers = PluginsLookup.getDefault().getAvailableProviders();

        final List<Playlist> playlists = aggregator.getAllPlaylists();
        for (Playlist p : playlists) {
            Iterator<String> it = p.songs();
            while (it.hasNext()) {
                String ref = it.next();
                Pair<String, ProviderIdentifier> pair = Pair.create(ref, p.getProvider());
                if (!availableReferences.contains(pair)) {
                    availableReferences.add(pair);
                }
            }
        }

        for (ProviderConnection provider : providers) {
            IMusicProvider binder = provider.getBinder();
            if (binder != null) {
                int limit = 50;
                int offset = 0;
                boolean goAhead = true;
                try {
                    while (goAhead) {
                        List<Song> songs = binder.getSongs(offset, limit);
                        if (songs == null) {
                            goAhead = false;
                            continue;
                        }

                        if (songs.size() < limit) {
                            goAhead = false;
                        }

                        offset += songs.size();

                        for (Song song : songs) {
                            Pair<String, ProviderIdentifier> pair = Pair.create(song.getRef(), song.getProvider());
                            if (!availableReferences.contains(pair)) {
                                availableReferences.add(pair);
                            }
                        }
                    }
                } catch (RemoteException ignore) {
                }
            }
        }

        // Randomly generate recommendations
        ArrayObjectAdapter recommendedRowAdapter = new ArrayObjectAdapter(new CardPresenter());
        int entriesCount = 0;
        List<String> knownRefs = new ArrayList<>();

        for (Pair<String, ProviderIdentifier> ref : availableReferences) {
            if (entriesCount == 20) break;

            // Get the song information
            Song song = aggregator.retrieveSong(ref.first, ref.second);

            if (song == null) {
                // That song might not exist anymore, keep moving
                continue;
            }

            final int type = rand.nextInt(2);
            if (type == TYPE_ALBUM && (song.getAlbum() == null || knownRefs.contains(song.getAlbum()))
                    || type == TYPE_ARTIST && (song.getArtist() == null || knownRefs.contains(song.getArtist()))) {
                // We don't have the requested track info, so skip it
                continue;
            }

            if (type == TYPE_ALBUM) {
                knownRefs.add(song.getAlbum());
                Album album = aggregator.retrieveAlbum(song.getAlbum(), song.getProvider());
                recommendedRowAdapter.add(album);
            } else if (type == TYPE_ARTIST) {
                knownRefs.add(song.getArtist());
                Artist artist = aggregator.retrieveArtist(song.getArtist(), song.getProvider());
                recommendedRowAdapter.add(artist);
            }
            ++entriesCount;
        }

        mNumSuggestions = recommendedRowAdapter.size();

        // Build Recommended Leanback item
        HeaderItem header = new HeaderItem(ROW_RECOMMENDATIONS, "Recommended for you");
        ListRow row = new ListRow(header, recommendedRowAdapter);
        if (mRowsAdapter.size() > ROW_RECOMMENDATIONS) {
            mRowsAdapter.replace(ROW_RECOMMENDATIONS, row);
        } else {
            mRowsAdapter.add(row);
        }
    }

    private void generateMyLibraryRow() {
        // Build My Library item
        ArrayObjectAdapter libraryAdapter = new ArrayObjectAdapter(new CardPresenter());
        libraryAdapter.add(new MyLibraryItem(MyLibraryItem.TYPE_ARTISTS));
        libraryAdapter.add(new MyLibraryItem(MyLibraryItem.TYPE_ALBUMS));

        HeaderItem header = new HeaderItem(ROW_LIBRARY, getString(R.string.title_section_my_songs));
        ListRow row = new ListRow(header, libraryAdapter);
        if (mRowsAdapter.size() > ROW_LIBRARY) {
            mRowsAdapter.replace(ROW_LIBRARY, row);
        } else {
            mRowsAdapter.add(row);
        }
    }

    private void generatePlaylistsRow() {
        // Build Playlists items
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();
        final List<Pair<String, ProviderIdentifier>> availableReferences = new ArrayList<>();
        final List<Playlist> playlists = aggregator.getAllPlaylists();
        for (Playlist p : playlists) {
            Iterator<String> it = p.songs();
            while (it.hasNext()) {
                String ref = it.next();
                Pair<String, ProviderIdentifier> pair = Pair.create(ref, p.getProvider());
                if (!availableReferences.contains(pair)) {
                    availableReferences.add(pair);
                }
            }
        }

        ArrayObjectAdapter playlistsAdapter = new ArrayObjectAdapter(new CardPresenter());
        playlistsAdapter.addAll(0, playlists);

        HeaderItem header = new HeaderItem(ROW_PLAYLISTS, getString(R.string.title_section_playlists));
        ListRow row = new ListRow(header, playlistsAdapter);
        if (mRowsAdapter.size() > ROW_PLAYLISTS) {
            mRowsAdapter.replace(ROW_PLAYLISTS, row);
        } else {
            mRowsAdapter.add(row);
        }
    }

    private void generateSettingsRow() {
        // Build Settings items
        ArrayObjectAdapter settingsAdapter = new ArrayObjectAdapter(new IconPresenter());
        settingsAdapter.add(new SettingsItem(SettingsItem.ITEM_PROVIDERS));
        settingsAdapter.add(new SettingsItem(SettingsItem.ITEM_EFFECTS));
        settingsAdapter.add(new SettingsItem(SettingsItem.ITEM_LICENSES));

        HeaderItem header = new HeaderItem(ROW_SETTINGS, getString(R.string.title_activity_settings));
        ShadowlessListRow row = new ShadowlessListRow(header, settingsAdapter);
        if (mRowsAdapter.size() > ROW_SETTINGS) {
            mRowsAdapter.replace(ROW_SETTINGS, row);
        } else {
            mRowsAdapter.add(row);
        }
    }

    private class TvLocalCallback implements ILocalCallback {
        @Override
        public void onSongUpdate(List<Song> s) {
            if (mRowsAdapter != null) {
                if (mNumSuggestions < 2) {
                    generateRecommendations();
                } else {
                    requestAdapterUpdate();
                }
            }
        }

        @Override
        public void onAlbumUpdate(List<Album> a) {
            if (mRowsAdapter != null) {
                requestAdapterUpdate();
            }
        }

        @Override
        public void onPlaylistUpdate(List<Playlist> p) {
            if (mRowsAdapter != null) {
                generatePlaylistsRow();
            }
        }

        @Override
        public void onPlaylistRemoved(String ref) {

        }

        @Override
        public void onArtistUpdate(List<Artist> a) {
            if (mRowsAdapter != null) {
                requestAdapterUpdate();
            }
        }

        @Override
        public void onProviderConnected(IMusicProvider provider) {
            if (mRowsAdapter != null) {
                requestAdapterUpdate();
            }
        }

        @Override
        public void onSearchResult(List<SearchResult> searchResult) {

        }
    }
}
