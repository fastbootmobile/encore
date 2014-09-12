package org.omnirom.music.app.fragments;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.support.v7.widget.DefaultItemAnimator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.lucasr.twowayview.TwoWayView;
import org.lucasr.twowayview.widget.DividerItemDecoration;
import org.omnirom.music.app.MainActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.adapters.ListenNowAdapter;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ListenNowFragment#newInstance} factory method to
 * create an instance of this fragment.
 *
 */
public class ListenNowFragment extends Fragment implements ILocalCallback {

    private static final String TAG = "ListenNowFragment";

    private TwoWayView mRoot;
    private ListenNowAdapter mAdapter;
    private Handler mHandler;
    private static boolean sWarmUp = false;

    /**
     * Runnable responsible of generating the entries to put in the grid
     */
    private Runnable mGenerateEntries = new Runnable() {
        @Override
        public void run() {
            final ProviderAggregator aggregator = ProviderAggregator.getDefault();

            final List<Playlist> playlists = aggregator.getAllPlaylists();
            final List<String> chosenSongs = new ArrayList<String>();

            int totalSongsCount = 0;

            if (playlists.size() <= 0) {
                mHandler.postDelayed(this, 1000);
                return;
            }

            for (Playlist p : playlists) {
                totalSongsCount += p.getSongsCount();
            }

            // We use a random algorithm (picking random tracks and albums and artists from
            // playlist) with a fixed layout:
            // - One big entry
            // - Six small entries
            // A total of 21 entries

            Random random = new Random(SystemClock.uptimeMillis());
            for (int i = 0; i < 21; i++) {
                // Make sure we haven't reached all our accessible data
                if (chosenSongs.size() >= totalSongsCount) {
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

                // Now that we have the entity, let's figure if it's a big or small entry
                boolean isLarge = ((i % 7) == 0);

                // And we make the entry!
                BoundEntity entity;
                switch (type) {
                    case 0: // Artist
                        String artistRef = track.getArtist();
                        entity = aggregator.retrieveArtist(artistRef, track.getProvider());
                        break;

                    case 1: // Album
                        String albumRef = track.getAlbum();
                        entity = aggregator.retrieveAlbum(albumRef, track.getProvider());
                        IMusicProvider binder = PluginsLookup.getDefault()
                                .getProvider(provider).getBinder();
                        try {
                            binder.fetchAlbumTracks(albumRef);
                        } catch (RemoteException e) {
                            // ignore
                        }
                        break;

                    case 2: // Song
                        entity = track;
                        break;

                    default:
                        Log.e(TAG, "Unexpected entry type " + type);
                        entity = null;
                        break;
                }

                ListenNowAdapter.ListenNowEntry entry = new ListenNowAdapter.ListenNowEntry(
                        isLarge ? ListenNowAdapter.ListenNowEntry.ENTRY_SIZE_LARGE
                                : ListenNowAdapter.ListenNowEntry.ENTRY_SIZE_MEDIUM,
                        entity);
                mAdapter.addEntry(entry);
                mAdapter.notifyItemInserted(mAdapter.getItemCount() - 1);
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

    public ListenNowFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler();
        mAdapter = new ListenNowAdapter();

        // Generate entries
        if (!sWarmUp) {
            sWarmUp = true;
            mHandler.postDelayed(mGenerateEntries, 1000);
        } else {
            mHandler.post(mGenerateEntries);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        mRoot = (TwoWayView) inflater.inflate(R.layout.fragment_listen_now, container, false);
        mRoot.setAdapter(mAdapter);
        final Drawable divider = getResources().getDrawable(R.drawable.divider);
        mRoot.addItemDecoration(new DividerItemDecoration(divider));
        mRoot.setItemAnimator(new DefaultItemAnimator());
        return mRoot;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        MainActivity mainActivity = (MainActivity) activity;
        mainActivity.onSectionAttached(MainActivity.SECTION_LISTEN_NOW);
        mainActivity.setContentShadowTop(0);
        ProviderAggregator.getDefault().addUpdateCallback(this);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        ProviderAggregator.getDefault().removeUpdateCallback(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onSongUpdate(final List<Song> s) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                int hasThisSong;
                for (Song song : s) {
                    hasThisSong = mAdapter.contains(song);
                    if (hasThisSong >= 0) {
                        mAdapter.notifyItemChanged(hasThisSong);
                    }
                }
            }
        });
    }

    @Override
    public void onAlbumUpdate(final List<Album> a) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                int hasThisAlbum;
                for (Album album : a) {
                    hasThisAlbum = mAdapter.contains(album);
                    if (hasThisAlbum >= 0) {
                        mAdapter.notifyItemChanged(hasThisAlbum);
                    }
                }
            }
        });
    }

    @Override
    public void onPlaylistUpdate(List<Playlist> p) {

    }

    @Override
    public void onArtistUpdate(final List<Artist> a) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                int hasThisArtist;
                for (Artist artist : a) {
                    hasThisArtist = mAdapter.contains(artist);
                    if (hasThisArtist >= 0) {
                        mAdapter.notifyItemChanged(hasThisArtist);
                    }
                }
            }
        });
    }

    @Override
    public void onProviderConnected(IMusicProvider provider) {

    }

    @Override
    public void onSearchResult(SearchResult searchResult) {

    }
}
