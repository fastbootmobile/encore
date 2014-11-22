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
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.LayoutAnimationController;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.dd.CircularProgressButton;
import com.getbase.floatingactionbutton.FloatingActionButton;

import org.omnirom.music.app.PlaylistActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.app.adapters.PlaylistAdapter;
import org.omnirom.music.app.ui.PlayPauseDrawable;
import org.omnirom.music.app.ui.PlaylistListView;
import org.omnirom.music.framework.PlaybackProxy;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.framework.RefCountedBitmap;
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
import org.omnirom.music.service.BasePlaybackCallback;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * A simple {@link android.support.v4.app.Fragment} subclass.
 * Use the {@link org.omnirom.music.app.fragments.PlaylistViewFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class PlaylistViewFragment extends Fragment implements ILocalCallback {

    private static final String TAG = "PlaylistViewFragment";
    public static final String KEY_PLAYLIST = "playlist";

    private PlaylistAdapter mAdapter;
    private Playlist mPlaylist;
    private PlaylistListView mListViewContents;
    private FloatingActionButton mPlayFab;
    private PlayPauseDrawable mFabDrawable;
    private boolean mFabShouldResume;
    private Handler mHandler;
    private CircularProgressButton mOfflineBtn;
    private RefCountedBitmap mLogoBitmap;

    private BasePlaybackCallback mPlaybackCallback = new BasePlaybackCallback() {
        @Override
        public void onSongStarted(final boolean buffering, Song s) throws RemoteException {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mAdapter.notifyDataSetChanged();
                    mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                    mFabDrawable.setBuffering(buffering);
                }
            });
        }

        @Override
        public void onPlaybackResume() throws RemoteException {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                    mFabDrawable.setBuffering(false);
                }
            });
        }

        @Override
        public void onPlaybackPause() throws RemoteException {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
                    mFabDrawable.setBuffering(false);
                }
            });
        }
    };

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment PlaylistViewFragment.
     */
    public static PlaylistViewFragment newInstance(Playlist p) {
        PlaylistViewFragment fragment = new PlaylistViewFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(KEY_PLAYLIST, p);
        fragment.setArguments(bundle);
        return fragment;
    }

    public PlaylistViewFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler();

        Bundle args = getArguments();
        if (args == null) {
            throw new IllegalArgumentException("This fragment must have a valid playlist");
        }

        // Get the playlist from the arguments, from the instantiation, and from the cache
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();
        String playlistRef = args.getString(KEY_PLAYLIST);
        mPlaylist = aggregator.retrievePlaylist(playlistRef, null);

        if (mPlaylist == null) {
            Log.e(TAG, "Playlist is null (not in cache, aborting)");
            // TODO: Wait for playlist to be loaded
            Activity act = getActivity();
            if (act != null) {
                act.finish();
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_playlist_view, container, false);
        assert root != null;

        mListViewContents = (PlaylistListView) root.findViewById(R.id.lvPlaylistContents);

        // Setup the parallaxed header
        View headerView = inflater.inflate(R.layout.songs_list_view_header, mListViewContents, false);
        mListViewContents.addParallaxedHeaderView(headerView);

        mAdapter = new PlaylistAdapter();
        mListViewContents.setAdapter(mAdapter);

        headerView.findViewById(R.id.pbAlbumLoading).setVisibility(View.GONE);

        ImageView ivHero = (ImageView) headerView.findViewById(R.id.ivHero);
        TextView tvAlbumName = (TextView) headerView.findViewById(R.id.tvAlbumName);

        // Download button
        mOfflineBtn = (CircularProgressButton) headerView.findViewById(R.id.cpbOffline);
        mOfflineBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ProviderIdentifier pi = mPlaylist.getProvider();
                IMusicProvider provider = PluginsLookup.getDefault().getProvider(pi).getBinder();
                try {
                    if (mPlaylist.getOfflineStatus() == BoundEntity.OFFLINE_STATUS_NO) {
                        provider.setPlaylistOfflineMode(mPlaylist.getRef(), true);
                        mOfflineBtn.setIndeterminateProgressMode(true);
                        mOfflineBtn.setProgress(1);

                        if (ProviderAggregator.getDefault().isOfflineMode()) {
                            Toast.makeText(getActivity(), R.string.toast_offline_playlist_sync,
                                    Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        provider.setPlaylistOfflineMode(mPlaylist.getRef(), false);
                        mOfflineBtn.setProgress(0);
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Cannot set this playlist to offline mode", e);
                    mOfflineBtn.setProgress(-1);
                }
            }
        });
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateOfflineStatus();
            }
        }, 1000);

        tvAlbumName.setText(mPlaylist.getName());

        Bitmap hero = Utils.dequeueBitmap(PlaylistActivity.BITMAP_PLAYLIST_HERO);
        ivHero.setImageBitmap(hero);

        mPlayFab = (FloatingActionButton) headerView.findViewById(R.id.fabPlay);

        // Set source logo
        ImageView ivSource = (ImageView) headerView.findViewById(R.id.ivSourceLogo);
        mLogoBitmap = PluginsLookup.getDefault().getCachedLogo(mPlaylist);
        mLogoBitmap.acquire();

        ivSource.setImageBitmap(mLogoBitmap.get());

        // Set the FAB animated drawable
        mFabDrawable = new PlayPauseDrawable();
        mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
        mFabDrawable.setPaddingDp(52);
        mFabDrawable.setYOffset(6);

        mPlayFab.setImageDrawable(mFabDrawable);
        mPlayFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mFabDrawable.getCurrentShape() == PlayPauseDrawable.SHAPE_PLAY) {
                    if (mFabShouldResume) {
                        PlaybackProxy.play();
                        mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                        mFabDrawable.setBuffering(true);
                    } else {
                        playNow();
                    }
                } else {
                    mFabShouldResume = true;
                    PlaybackProxy.pause();
                    mFabDrawable.setBuffering(true);
                }
            }
        });

        // Fill the playlist
        mAdapter.setPlaylist(mPlaylist);

        // Set the list listener
        mListViewContents.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Song song = mAdapter.getItem(i - 1);
                if (Utils.canPlaySong(song)) {
                    PlaybackProxy.clearQueue();
                    PlaybackProxy.queuePlaylist(mPlaylist, false);
                    PlaybackProxy.playAtIndex(i - 1);

                    // Update FAB
                    mFabShouldResume = true;
                    mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                    mFabDrawable.setBuffering(true);
                }
            }
        });

        // Set the display animation
        AlphaAnimation anim = new AlphaAnimation(0.f, 1.f);
        anim.setDuration(200);
        mListViewContents.setLayoutAnimation(new LayoutAnimationController(anim));

        return root;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        ProviderAggregator.getDefault().addUpdateCallback(this);
        PlaybackProxy.addCallback(mPlaybackCallback);
    }

    @Override
    public void onDetach() {
        super.onDetach();

        ProviderAggregator.getDefault().removeUpdateCallback(this);
        PlaybackProxy.removeCallback(mPlaybackCallback);
    }

    @Override
    public void onDestroy() {
        if (mLogoBitmap != null) {
            mLogoBitmap.release();
        }
        super.onDestroy();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.playlist, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_play_now) {
            playNow();
            return true;
        } else if (item.getItemId() == R.id.menu_add_to_queue) {
            PlaybackProxy.queuePlaylist(mPlaylist, false);
            return true;
        } else if (item.getItemId() == R.id.menu_remove_duplicates) {
            try {
                removeDuplicates();
            } catch (RemoteException e) {
                Log.e(TAG, "Cannot remove duplicates", e);
            }
            return true;
        }

        return false;
    }

    private void playNow() {
        PlaybackProxy.playPlaylist(mPlaylist);
        mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
        mFabDrawable.setBuffering(true);
    }

    private void removeDuplicates() throws RemoteException {
        // Process each track and look for the same track
        Iterator<String> songsIt = mPlaylist.songs();
        List<String> knownTracks = new ArrayList<String>();

        // Only process if the provider is up
        ProviderConnection conn = PluginsLookup.getDefault().getProvider(mPlaylist.getProvider());
        if (conn != null) {
            IMusicProvider provider = conn.getBinder();
            if (provider != null) {
                int position = 0;
                while (songsIt.hasNext()) {
                    String songRef = songsIt.next();

                    // If we know the track, remove it (it's the second occurrence of the track).
                    // Else, add it to the known list and move on.
                    if (knownTracks.contains(songRef)) {
                        // Delete the song and restart the process
                        provider.deleteSongFromPlaylist(position, mPlaylist.getRef());
                        removeDuplicates();
                        return;
                    } else {
                        knownTracks.add(songRef);
                    }

                    ++position;
                }
            }
        }

    }

    private void updateOfflineStatus() {
        if (mPlaylist == null) {
            Log.e(TAG, "Calling updateOfflineStatus when mPlaylist is null!");
            return;
        }

        // We bias the playlist status based on the current offline mode
        ProviderAggregator aggregator = ProviderAggregator.getDefault();
        final int offlineStatus = mPlaylist.getOfflineStatus();

        switch (offlineStatus) {
            case BoundEntity.OFFLINE_STATUS_NO:
                mOfflineBtn.setProgress(0);
                break;

            case BoundEntity.OFFLINE_STATUS_READY:
                mOfflineBtn.setProgress(100);
                break;

            case BoundEntity.OFFLINE_STATUS_ERROR:
                mOfflineBtn.setProgress(-1);
                break;

            case BoundEntity.OFFLINE_STATUS_PENDING:
                mOfflineBtn.setProgress(50);
                mOfflineBtn.setIndeterminateProgressMode(true);
                break;

            case BoundEntity.OFFLINE_STATUS_DOWNLOADING:
                if (aggregator.isOfflineMode()) {
                    mOfflineBtn.setProgress(50);
                    mOfflineBtn.setIndeterminateProgressMode(true);
                } else {
                    mOfflineBtn.setIndeterminateProgressMode(false);
                    float numSyncTracks = getNumSyncTracks();
                    float numTracksToSync = 0;

                    // Count the number of tracks to sync (ie. num of tracks available)
                    final Iterator<String> songs = mPlaylist.songs();
                    while (songs.hasNext()) {
                        String ref = songs.next();
                        Song s = aggregator.retrieveSong(ref, mPlaylist.getProvider());
                        if (s != null && s.isAvailable()) {
                            ++numTracksToSync;
                        }
                    }

                    mOfflineBtn.setProgress(Math.min(100, numSyncTracks * 100.0f / numTracksToSync + 0.1f));
                }
                break;
        }

        mOfflineBtn.setVisibility((mPlaylist.isLoaded() && mPlaylist.isOfflineCapable()) ? View.VISIBLE : View.GONE);
    }

    private int getNumSyncTracks() {
        final Iterator<String> it = mPlaylist.songs();
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();

        int numSync = 0;
        while (it.hasNext()) {
            final String songRef = it.next();
            Song song = aggregator.retrieveSong(songRef, mPlaylist.getProvider());
            if (song.getOfflineStatus() == BoundEntity.OFFLINE_STATUS_READY) {
                numSync++;
            }
        }

        Log.d(TAG, "Num sync tracks: " + numSync);

        return numSync;
    }

    @Override
    public void onSongUpdate(List<Song> s) {
        // We check if the song belongs to this playlist
        boolean hasPlaylist = false;
        Iterator<String> songsRef = mPlaylist.songs();
        while (songsRef.hasNext()) {
            String ref = songsRef.next();
            for (Song song : s) {
                if (song.getRef().equals(ref)) {
                    hasPlaylist = true;
                    break;
                }
            }

            if (hasPlaylist) {
                break;
            }
        }

        // It does, update the list then
        if (hasPlaylist) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    updateOfflineStatus();
                    mAdapter.notifyDataSetChanged();
                }
            });
        }
    }

    @Override
    public void onAlbumUpdate(List<Album> a) {
    }

    @Override
    public void onPlaylistUpdate(final List<Playlist> p) {
        // If the currently watched playlist is updated, update me
        if (p.contains(mPlaylist)) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    updateOfflineStatus();

                    // Make sure we're using the new/cached entity
                    mAdapter.setPlaylist(p.get(p.indexOf(mPlaylist)));
                    mAdapter.notifyDataSetChanged();
                }
            });
        }
    }

    @Override
    public void onArtistUpdate(List<Artist> a) {
        // We check if the artists belongs to this playlist
        boolean hasPlaylist = false;
        Iterator<String> songsRef = mPlaylist.songs();
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();
        while (songsRef.hasNext()) {
            String ref = songsRef.next();
            Song song = aggregator.retrieveSong(ref, mPlaylist.getProvider());
            for (Artist artist : a) {
                if (artist.getRef().equals(song.getArtist())) {
                    hasPlaylist = true;
                    break;
                }
            }

            if (hasPlaylist) {
                break;
            }
        }

        // It does, update the list then
        if (hasPlaylist) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mAdapter.notifyDataSetChanged();
                }
            });
        }
    }

    @Override
    public void onProviderConnected(IMusicProvider provider) {
    }

    @Override
    public void onSearchResult(SearchResult searchResult) {
    }
}
