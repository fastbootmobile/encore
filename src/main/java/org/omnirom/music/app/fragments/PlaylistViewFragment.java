package org.omnirom.music.app.fragments;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.dd.CircularProgressButton;
import com.getbase.floatingactionbutton.FloatingActionButton;

import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.app.adapters.PlaylistAdapter;
import org.omnirom.music.app.ui.PlayPauseDrawable;
import org.omnirom.music.app.ui.PlaylistListView;
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
import org.omnirom.music.providers.ProviderIdentifier;
import org.omnirom.music.service.BasePlaybackCallback;
import org.omnirom.music.service.IPlaybackService;

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
    private FloatingActionButton mPlayFab;
    private PlayPauseDrawable mFabDrawable;
    private boolean mFabShouldResume;
    private Handler mHandler;
    private CircularProgressButton mOfflineBtn;

    private BasePlaybackCallback mPlaybackCallback = new BasePlaybackCallback() {
        @Override
        public void onSongStarted(boolean buffering, Song s) throws RemoteException {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mAdapter.notifyDataSetChanged();
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
        mPlaylist = args.getParcelable(KEY_PLAYLIST);
        mPlaylist = aggregator.retrievePlaylist(mPlaylist.getRef(), mPlaylist.getProvider());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_playlist_view, container, false);
        assert root != null;

        final ProviderAggregator aggregator = ProviderAggregator.getDefault();

        PlaylistListView lvPlaylistContents = (PlaylistListView) root.findViewById(R.id.lvPlaylistContents);
        mAdapter = new PlaylistAdapter(root.getContext());
        lvPlaylistContents.setAdapter(mAdapter);

        // Setup the parallaxed header
        View headerView = inflater.inflate(R.layout.songs_list_view_header, null);
        lvPlaylistContents.addParallaxedHeaderView(headerView);

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
        updateOfflineStatus();

        tvAlbumName.setText(mPlaylist.getName());
        ivHero.setImageResource(R.drawable.album_placeholder);

        mPlayFab = (FloatingActionButton) headerView.findViewById(R.id.fabPlay);

        // Set source logo
        ImageView ivSource = (ImageView) headerView.findViewById(R.id.ivSourceLogo);
        ivSource.setImageBitmap(PluginsLookup.getDefault().getCachedLogo(mPlaylist));

        // Set the FAB animated drawable
        mFabDrawable = new PlayPauseDrawable(getResources());
        mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
        mFabDrawable.setPaddingDp(52);
        mFabDrawable.setYOffset(6);

        mPlayFab.setImageDrawable(mFabDrawable);
        mPlayFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mFabDrawable.getCurrentShape() == PlayPauseDrawable.SHAPE_PLAY) {
                    if (mFabShouldResume) {
                        try {
                            PluginsLookup.getDefault().getPlaybackService().play();
                            mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                        } catch (RemoteException e) {
                            Log.e(TAG, "Cannot resume playback", e);
                        }
                    } else {
                        try {
                            PluginsLookup.getDefault().getPlaybackService().playPlaylist(mPlaylist);
                            mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                        } catch (RemoteException e) {
                            Log.e(TAG, "Cannot start playing playlist " + mPlaylist.getRef(), e);
                        }
                    }
                } else {
                    mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                    mFabShouldResume = true;
                    try {
                        PluginsLookup.getDefault().getPlaybackService().pause();
                        mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Cannot pause playback", e);
                    }
                }
            }
        });

        // Fill the playlist
        mAdapter.setPlaylist(mPlaylist);

        // Set the list listener
        lvPlaylistContents.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (mAdapter.getItem(i - 1).isAvailable()) {
                    IPlaybackService pbService = PluginsLookup.getDefault().getPlaybackService();
                    try {
                        pbService.getCurrentPlaybackQueue().clear();
                        pbService.queuePlaylist(mPlaylist, false);
                        pbService.playAtQueueIndex(i - 1);

                        // Update FAB
                        mFabShouldResume = true;
                        mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Unable to play song", e);
                    }
                }
            }
        });

        return root;
    }


    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        ProviderAggregator.getDefault().addUpdateCallback(this);
        try {
            PluginsLookup.getDefault().getPlaybackService().addCallback(mPlaybackCallback);
        } catch (RemoteException e) {
            // ignore
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();

        ProviderAggregator.getDefault().removeUpdateCallback(this);
        IPlaybackService service = PluginsLookup.getDefault().getPlaybackService();
        if (service != null) {
            try {
                service.addCallback(mPlaybackCallback);
            } catch (RemoteException e) {
                // ignore
            }
        }
    }

    private void updateOfflineStatus() {
        final int offlineStatus = mPlaylist.getOfflineStatus();
        Log.e(TAG, "Offline Status: " + offlineStatus);
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
                mOfflineBtn.setIndeterminateProgressMode(false);
                float numSyncTracks = getNumSyncTracks();
                float numTracksToSync = 0;

                // Count the number of tracks to sync (ie. num of tracks available)
                Iterator<String> songs = mPlaylist.songs();
                final ProviderAggregator aggregator = ProviderAggregator.getDefault();
                while (songs.hasNext()) {
                    String ref = songs.next();
                    Song s = aggregator.retrieveSong(ref, mPlaylist.getProvider());
                    if (s != null && s.isAvailable()) {
                        ++numTracksToSync;
                    }
                }

                mOfflineBtn.setProgress(Math.min(100, numSyncTracks * 100.0f / numTracksToSync + 0.1f));
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
