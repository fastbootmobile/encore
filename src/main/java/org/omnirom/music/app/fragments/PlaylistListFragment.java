package org.omnirom.music.app.fragments;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import org.omnirom.music.app.MainActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.adapters.PlaylistListAdapter;
import org.omnirom.music.app.ui.ExpandableHeightGridView;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ILocalCallback;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.ProviderAggregator;

import java.util.ArrayList;
import java.util.List;


/**
 * A simple {@link android.support.v4.app.Fragment} subclass.
 * Use the {@link PlaylistListFragment#newInstance} factory method to
 * create an instance of this fragment.
 *
 */
public class PlaylistListFragment extends AbstractRootFragment implements ILocalCallback {

    private PlaylistListAdapter mAdapter;
    private Handler mHandler;
    private final ArrayList<Playlist> mPlaylistsUpdated = new ArrayList<Playlist>();

    private Runnable mUpdateListRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (mPlaylistsUpdated) {
                for (Playlist p : mPlaylistsUpdated) {
                    mAdapter.addItemUnique(p);
                }

                mPlaylistsUpdated.clear();
            }
            mAdapter.notifyDataSetChanged();
        }
    };

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment PlaylistListFragment.
     */
    public static PlaylistListFragment newInstance() {
        PlaylistListFragment fragment = new PlaylistListFragment();
        return fragment;
    }
    public PlaylistListFragment() {
        mAdapter = new PlaylistListAdapter();
        mHandler = new Handler();

        ProviderAggregator.getDefault().addUpdateCallback(this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_playlist, container, false);
        ExpandableHeightGridView playlistLayout =
                (ExpandableHeightGridView) root.findViewById(R.id.gvPlaylists);
        playlistLayout.setAdapter(mAdapter);
        playlistLayout.setExpanded(true);

        // Set the initial playlists
        new Thread() {
            public void run() {
                final List<Playlist> playlists = ProviderAggregator.getDefault().getAllPlaylists();
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mAdapter.addAllUnique(playlists);
                    }
                });
            }
        }.start();

        // Setup the search box
        setupSearchBox(root);

        // Setup the click listener
        playlistLayout.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                MainActivity act = (MainActivity) getActivity();
                act.showFragment(PlaylistViewFragment.newInstance(mAdapter.getItem(position)), true);
            }
        });

        return root;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        ((MainActivity) activity).onSectionAttached(MainActivity.SECTION_PLAYLISTS);
    }

    @Override
    public void onSongUpdate(Song s) {

    }

    @Override
    public void onAlbumUpdate(Album a) {

    }

    @Override
    public void onPlaylistUpdate(final Playlist p) {
        synchronized (mPlaylistsUpdated) {
            mPlaylistsUpdated.add(p);
        }

        mHandler.removeCallbacks(mUpdateListRunnable);
        mHandler.postDelayed(mUpdateListRunnable, 500);
    }

    @Override
    public void onArtistUpdate(Artist a) {

    }

    @Override
    public void onProviderConnected(IMusicProvider provider) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mAdapter.addAllUnique(ProviderAggregator.getDefault().getAllPlaylists());
            }
        });
    }
}
