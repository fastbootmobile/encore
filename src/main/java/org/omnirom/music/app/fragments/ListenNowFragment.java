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
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.h6ah4i.android.widget.advrecyclerview.animator.RefactoredDefaultItemAnimator;

import org.lucasr.twowayview.TwoWayView;
import org.lucasr.twowayview.widget.DividerItemDecoration;
import org.omnirom.music.app.MainActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.adapters.HistoryAdapter;
import org.omnirom.music.app.adapters.ListenNowAdapter;
import org.omnirom.music.framework.ListenLogger;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.BoundEntity;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.SearchResult;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ILocalCallback;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderConnection;
import org.omnirom.music.providers.ProviderIdentifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A simple {@link Fragment} subclass showing ideas of tracks and albums to listen to.
 * Use the {@link ListenNowFragment#newInstance} factory method to
 * create an instance of this fragment.
 *
 */
public class ListenNowFragment extends Fragment implements ILocalCallback {

    private static final String TAG = "ListenNowFragment";

    private static final ListenNowAdapter sAdapter = new ListenNowAdapter();
    private static boolean sWarmUp = false;

    private static final int TYPE_ARTIST = 0;
    private static final int TYPE_ALBUM = 1;
    private static final int TYPE_SONG = 2;

    private static final int MAX_SUGGESTIONS = 21;
    private static final int MAX_RECENTS = 4;

    private Handler mHandler;
    private TextView mTxtNoMusic;
    private int mWarmUpCount = 0;
    private boolean mFoundAnything;
    private List<Integer> mUpdateItems;

    private final Runnable mUpdateItemsRunnable = new Runnable() {
        @Override
        public void run() {
            for (int item : mUpdateItems) {
                sAdapter.notifyItemChanged(item);
            }
        }
    };

    /**
     * Runnable responsible of generating the entries to put in the grid
     */
    private final Runnable mGenerateEntries = new Runnable() {
        @Override
        public void run() {
            final ProviderAggregator aggregator = ProviderAggregator.getDefault();

            final List<Playlist> playlists = aggregator.getAllPlaylists();
            final List<String> chosenSongs = new ArrayList<>();
            final List<String> usedReferences = new ArrayList<>();

            int totalSongsCount = 0;

            sWarmUp = true;
            sAdapter.clearEntries();

            // If we don't have any playlists, retry in a short time and display either No Music
            // or Loading... depending on the number of tries, waiting for providers to start
            if (playlists.size() <= 0) {
                mTxtNoMusic.animate().setDuration(400).translationX(0).alpha(1.0f).start();
                if (mWarmUpCount < 2) {
                    mTxtNoMusic.setText(R.string.loading);
                } else if (PluginsLookup.getDefault().getAvailableProviders().size() == 0) {
                    mTxtNoMusic.setText(R.string.listen_now_no_providers);
                    mFoundAnything = false;
                } else {
                    mTxtNoMusic.setText(R.string.no_music_hint);
                    mFoundAnything = false;
                }

                mWarmUpCount++;

                mHandler.postDelayed(this, 1000);
                return;
            } else if (mTxtNoMusic != null) {
                mTxtNoMusic.animate().setDuration(400).translationX(100).alpha(0.0f).start();
                mFoundAnything = true;
            }

            for (Playlist p : playlists) {
                totalSongsCount += p.getSongsCount();
            }

            // We use a random algorithm (picking random tracks and albums and artists from
            // playlist) with a fixed layout:
            // - One big entry
            // - Six small entries
            // A total of MAX_SUGGESTIONS entries

            final Random random = new Random(SystemClock.uptimeMillis());
            final long startTime = SystemClock.uptimeMillis();
            for (int i = 0; i < MAX_SUGGESTIONS; i++) {
                // Watchdog timer
                if (SystemClock.uptimeMillis() - startTime > 1000) {
                    break;
                }

                // Make sure we haven't reached all our accessible data
                if (chosenSongs.size() >= totalSongsCount) {
                    break;
                }

                // Make sure we still have playlists
                if (playlists.size() <= 0) {
                    break;
                }

                // First, we determine the entity we want to show
                int type = random.nextInt(2);
                int playlistId = random.nextInt(playlists.size());

                Playlist playlist = playlists.get(playlistId);
                if (playlist.getSongsCount() <= 0) {
                    // Playlist is empty, skip to next one
                    i--;
                    continue;
                }

                int trackId = random.nextInt(playlist.getSongsCount());
                final ProviderIdentifier provider = playlist.getProvider();
                if (provider == null) {
                    Log.e(TAG, "Playlist has no identifier!");
                    continue;
                }

                String trackRef = playlist.songsList().get(trackId);
                if (chosenSongs.contains(trackRef)) {
                    // We already picked that song
                    i--;
                    continue;
                } else {
                    chosenSongs.add(trackRef);

                    // Remove the playlist from our selection if we picked all the songs from it
                    if (chosenSongs.containsAll(playlist.songsList())) {
                        playlists.remove(playlist);
                    }
                }

                Song track = aggregator.retrieveSong(trackRef, provider);
                if (track == null || !track.isLoaded()) {
                    // Some error while loading this track, or it's not loaded yet! Try another
                    i--;
                    continue;
                }

                // Let's see if this song has the type of entity we're looking for
                if ((type == TYPE_ARTIST && track.getArtist() == null)
                        || (type == TYPE_ALBUM && track.getAlbum() == null)) {
                    i--;
                    continue;
                }

                // Now that we have the entity, let's figure if it's a big or small entry
                boolean isLarge = ((i % 7) == 0);

                // And we make the entry!
                BoundEntity entity;
                switch (type) {
                    case TYPE_ARTIST:
                        String artistRef = track.getArtist();
                        entity = aggregator.retrieveArtist(artistRef, track.getProvider());
                        break;

                    case TYPE_ALBUM:
                        String albumRef = track.getAlbum();
                        entity = aggregator.retrieveAlbum(albumRef, track.getProvider());
                        ProviderConnection pc = PluginsLookup.getDefault()
                                .getProvider(provider);
                        if (pc != null) {
                            IMusicProvider binder = pc.getBinder();
                            try {
                                if (binder != null) {
                                    binder.fetchAlbumTracks(albumRef);
                                }
                            } catch (RemoteException e) {
                                // ignore
                            }
                        }
                        break;

                    case TYPE_SONG: // UNUSED
                        entity = track;
                        break;

                    default:
                        Log.e(TAG, "Unexpected entry type " + type);
                        entity = null;
                        break;
                }

                if (entity == null) {
                    i--;
                } else if (type == TYPE_ARTIST && ((Artist) entity).getName() == null) {
                    i--;
                    continue;
                } else if (type == TYPE_ALBUM && ((Album) entity).getName() == null) {
                    i--;
                    continue;
                }

                if (entity != null && usedReferences.contains(entity.getRef())) {
                    // Already shown that
                    i--;
                } else if (entity != null) {
                    ListenNowAdapter.ListenNowEntry entry = new ListenNowAdapter.ListenNowEntry(
                            isLarge ? ListenNowAdapter.ListenNowEntry.ENTRY_SIZE_LARGE
                                    : ListenNowAdapter.ListenNowEntry.ENTRY_SIZE_MEDIUM,
                            entity);
                    sAdapter.addEntry(entry);
                    sAdapter.notifyItemInserted(sAdapter.getItemCount() - 1);
                    usedReferences.add(entity.getRef());
                } else {
                    // Something bad happened while getting this entity, try something else
                    i--;
                }
            }

            // Generate 4 recent entries
            ListenLogger logger = new ListenLogger(getActivity());
            List<ListenLogger.LogEntry> entries = HistoryAdapter.sortByTime(logger.getEntries());
            List<String> knownReferences = new ArrayList<>();

            int countRecent = 0;
            for (ListenLogger.LogEntry entry : entries) {
                // Skip known elements
                if (knownReferences.contains(entry.getReference())) {
                    continue;
                }

                final Song song = aggregator.retrieveSong(entry.getReference(), entry.getIdentifier());

                // Keep only fully-featured elements
                if (song == null || song.getArtist() == null || song.getAlbum() == null) {
                    continue;
                }

                // Skip known elements (again)
                if (knownReferences.contains(song.getAlbum()) ||
                        knownReferences.contains(song.getArtist())) {
                    continue;
                }

                int type = random.nextInt(2);
                BoundEntity entity;
                switch (type) {
                    case TYPE_ARTIST:
                        entity = aggregator.retrieveArtist(song.getArtist(), song.getProvider());
                        break;

                    case TYPE_ALBUM:
                        entity = aggregator.retrieveAlbum(song.getAlbum(), song.getProvider());
                        break;

                    default:
                        throw new RuntimeException("Should not happen");
                }

                if (entity == null) {
                    // Entity cannot be null, something went wrong, try again with something else
                    continue;
                }


                // Store the references
                knownReferences.add(song.getRef());
                knownReferences.add(song.getAlbum());
                knownReferences.add(song.getArtist());

                // Create the item
                ListenNowAdapter.ListenNowEntry card = new ListenNowAdapter.ListenNowEntry(ListenNowAdapter.ListenNowEntry.ENTRY_SIZE_SMALL,
                        entity);
                sAdapter.addRecentEntry(card);
                sAdapter.notifyItemInserted(sAdapter.getItemCount() - 1);

                countRecent++;

                if (countRecent == 4) {
                    break;
                }
            }
        }
    };

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     * @return A new instance of fragment ListenNowFragment.
     */
    public static ListenNowFragment newInstance() {
        return new ListenNowFragment();
    }

    /**
     * Default empty constructor
     */
    public ListenNowFragment() {
        // Required empty public constructor
        mUpdateItems = new ArrayList<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler();

        if (sAdapter.getItemCount() == 2) {
            // Generate entries
            if (!sWarmUp) {
                mHandler.removeCallbacks(mGenerateEntries);
                mHandler.postDelayed(mGenerateEntries, 500);
            } else {
                mHandler.removeCallbacks(mGenerateEntries);
                mHandler.post(mGenerateEntries);
            }
        }
    }

    @Override
    public void onDestroy() {
        mHandler.removeCallbacks(mGenerateEntries);
        super.onDestroy();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        final FrameLayout root = (FrameLayout) inflater.inflate(R.layout.fragment_listen_now, container, false);
        TwoWayView twvRoot = (TwoWayView) root.findViewById(R.id.twvRoot);
        mTxtNoMusic = (TextView) root.findViewById(R.id.txtNoMusic);

        if (sAdapter.getItemCount() > 2) {
            mTxtNoMusic.setVisibility(View.GONE);
        } else {
            mTxtNoMusic.setTranslationX(-100);
            mTxtNoMusic.setAlpha(0.0f);
        }

        twvRoot.setAdapter(sAdapter);
        final Drawable divider = getResources().getDrawable(R.drawable.divider);
        twvRoot.addItemDecoration(new DividerItemDecoration(divider));
        twvRoot.setItemAnimator(new RefactoredDefaultItemAnimator());

        return root;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        MainActivity mainActivity = (MainActivity) activity;
        mainActivity.onSectionAttached(MainActivity.SECTION_LISTEN_NOW);
        mainActivity.setContentShadowTop(0);
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
    public void onSongUpdate(final List<Song> s) {
        int hasThisSong;
        for (Song song : s) {
            hasThisSong = sAdapter.contains(song);
            if (hasThisSong >= 0) {
                requestNotifyItemChanged(hasThisSong);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAlbumUpdate(final List<Album> a) {
        int hasThisAlbum;
        for (Album album : a) {
            hasThisAlbum = sAdapter.contains(album);
            if (hasThisAlbum >= 0) {
                requestNotifyItemChanged(hasThisAlbum);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPlaylistUpdate(List<Playlist> p) {
        if (!mFoundAnything || sAdapter.getItemCount() == 0) {
            mHandler.removeCallbacks(mGenerateEntries);
            mHandler.post(mGenerateEntries);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onArtistUpdate(final List<Artist> a) {
        int hasThisArtist;
        for (Artist artist : a) {
            hasThisArtist = sAdapter.contains(artist);
            if (hasThisArtist >= 0) {
                requestNotifyItemChanged(hasThisArtist);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onProviderConnected(IMusicProvider provider) {
        if (sWarmUp && sAdapter.getItemCount() < 2) {
            requestGenerateEntries();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSearchResult(List<SearchResult> searchResult) {
    }

    private void requestGenerateEntries() {
        mHandler.removeCallbacks(mGenerateEntries);
        mHandler.postDelayed(mGenerateEntries, 200);
    }

    private void requestNotifyItemChanged(int item) {
        if (!mUpdateItems.contains(item)) {
            mUpdateItems.add(item);

            mHandler.removeCallbacks(mUpdateItemsRunnable);
            mHandler.postDelayed(mUpdateItemsRunnable, 500);
        }
    }
}
