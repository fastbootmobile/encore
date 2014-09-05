package org.omnirom.music.app.fragments;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.GridView;

import org.omnirom.music.app.MainActivity;
import org.omnirom.music.app.PlaylistActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.app.adapters.PlaylistListAdapter;
import org.omnirom.music.app.ui.ExpandableHeightGridView;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.SearchResult;
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
public class PlaylistListFragment extends Fragment implements ILocalCallback {
    private static final String TAG = "PlaylistListFragment";

    private PlaylistListAdapter mAdapter;
    private Handler mHandler;
    private boolean mIsStandalone;
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
            Log.e(TAG, "Added playlists runned");
        }
    };

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param isStandalone Whether this fragment is embedded in My Songs or is the Playlists section
     * @return A new instance of fragment PlaylistListFragment.
     */
    public static PlaylistListFragment newInstance(boolean isStandalone) {
        PlaylistListFragment fragment = new PlaylistListFragment();
        fragment.setIsStandalone(isStandalone);
        return fragment;
    }
    public PlaylistListFragment() {
        mAdapter = new PlaylistListAdapter();
    }

    public void setIsStandalone(boolean isStandalone) {
        mIsStandalone = isStandalone;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_playlist, container, false);
        GridView playlistLayout = (GridView) root.findViewById(R.id.gvPlaylists);
        playlistLayout.setAdapter(mAdapter);

        // If we're not standalone, remove the huge padding
        if (!mIsStandalone) {
            int fourDp = Utils.dpToPx(getResources(), 4);
            root.setPadding(fourDp, fourDp, fourDp, fourDp);
        }

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

        // Setup the click listener
        playlistLayout.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                MainActivity act = (MainActivity) getActivity();
                act.startActivity(PlaylistActivity.craftIntent(act, mAdapter.getItem(position)));
            }
        });

        return root;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (mIsStandalone) {
            MainActivity mainActivity = (MainActivity) activity;
            mainActivity.onSectionAttached(MainActivity.SECTION_PLAYLISTS);
            mainActivity.setContentShadowTop(0);
        }

        ProviderAggregator.getDefault().addUpdateCallback(this);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        ProviderAggregator.getDefault().removeUpdateCallback(this);
    }

    @Override
    public void onSongUpdate(List<Song> s) {

    }

    @Override
    public void onAlbumUpdate(List<Album> a) {

    }

    @Override
    public void onPlaylistUpdate(final List<Playlist> p) {
        Log.e(TAG, "onPlaylistUpdate");

        synchronized (mPlaylistsUpdated) {
            mPlaylistsUpdated.addAll(p);
        }

        mHandler.removeCallbacks(mUpdateListRunnable);
        mHandler.post(mUpdateListRunnable);
    }

    @Override
    public void onArtistUpdate(List<Artist> a) {

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

    @Override
    public void onSearchResult(SearchResult searchResult) {

    }
}
