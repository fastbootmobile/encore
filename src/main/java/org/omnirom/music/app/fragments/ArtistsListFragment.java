package org.omnirom.music.app.fragments;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.TextView;

import org.lucasr.twowayview.TwoWayView;
import org.omnirom.music.app.ArtistActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.app.adapters.ArtistsAdapter;
import org.omnirom.music.app.ui.AlbumArtImageView;
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
 * Created by h4o on 20/06/2014.
 */
public class ArtistsListFragment extends AbstractRootFragment implements ILocalCallback {

    private ArtistsAdapter mAdapter;
    private Handler mHandler;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment PlaylistListFragment.
     */
    public static ArtistsListFragment newInstance() {
        ArtistsListFragment fragment = new ArtistsListFragment();
        return fragment;
    }
    public ArtistsListFragment() {
        mAdapter = new ArtistsAdapter();
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
        View root = inflater.inflate(R.layout.fragment_artists, container, false);
        TwoWayView artistLayout = (TwoWayView) root.findViewById(R.id.twvArtists);
        artistLayout.setAdapter(mAdapter);

        new Thread() {
            public void run() {
                final List<Artist> artists = ProviderAggregator.getDefault().getCache().getAllArtists();
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mAdapter.addAllUnique(artists);
                        mAdapter.notifyDataSetChanged();
                    }
                });
            }
        }.start();

        return root;
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
    }

    @Override
    public void onArtistUpdate(List<Artist> artists) {
        for (Artist a : artists) {
            int index = mAdapter.indexOf(a);
            if (index >= 0) {
                mAdapter.notifyItemChanged(index);
            }
        }
    }

    @Override
    public void onProviderConnected(IMusicProvider provider) {
    }

    @Override
    public void onSearchResult(SearchResult searchResult) {
    }
}