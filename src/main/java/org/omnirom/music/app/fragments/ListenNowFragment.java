package org.omnirom.music.app.fragments;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.lucasr.twowayview.TwoWayView;
import org.omnirom.music.app.MainActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.adapters.ListenNowAdapter;
import org.omnirom.music.model.BoundEntity;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderCache;
import org.omnirom.music.providers.ProviderIdentifier;

import java.util.List;
import java.util.Random;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ListenNowFragment#newInstance} factory method to
 * create an instance of this fragment.
 *
 */
public class ListenNowFragment extends Fragment {

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
            final ProviderCache cache = aggregator.getCache();

            List<Playlist> playlists = aggregator.getAllPlaylists();

            if (playlists.size() <= 0) {
                mHandler.postDelayed(this, 1000);
                return;
            }

            // We use a random algorithm (picking random tracks and albums and artists from
            // playlist) with a fixed layout:
            // - One big entry
            // - Six small entries
            // A total of 21 entries

            Random random = new Random(SystemClock.uptimeMillis());
            for (int i = 0; i < 21; i++) {
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
                Song track = aggregator.retrieveSong(trackRef, provider);

                if (track == null) {
                    // The track is not loaded...?
                    Log.e(TAG, "Track is not loaded, skipping one entry for now. TODO: Load!");
                } else {
                    // Now that we have the entity, let's figure if it's a big or small entry
                    boolean isLarge = ((i % 7) == 0);

                    // And we make the entry!
                    BoundEntity entity;
                    switch (type) {
                        case 0: // Artist
                            String artistRef = track.getArtist();
                            entity = cache.getArtist(artistRef);
                            if (entity == null) {
                                entity = aggregator.retrieveArtist(artistRef, provider);
                            }
                            break;

                        case 1: // Album
                            String albumRef = track.getAlbum();
                            entity = cache.getAlbum(albumRef);
                            if (entity == null) {
                                entity = aggregator.retrieveAlbum(albumRef, provider);
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
                }
            }
            mAdapter.notifyDataSetChanged();
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
        return mRoot;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        MainActivity mainActivity = (MainActivity) activity;
        mainActivity.onSectionAttached(MainActivity.SECTION_LISTEN_NOW);
        mainActivity.setContentShadowTop(0);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }
}
