package org.omnirom.music.app.fragments;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import org.omnirom.music.app.ArtistActivity;
import org.omnirom.music.app.MainActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.app.adapters.ArtistsAdapter;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ILocalCallback;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.ProviderAggregator;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
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
        GridView artistLayout =
                (GridView) root.findViewById(R.id.gvArtists);
        artistLayout.setAdapter(mAdapter);
        mAdapter.registerScrollListener(artistLayout);



        new Thread() {
            public void run() {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mAdapter.addAllUnique(ProviderAggregator.getDefault().getCache().getAllArtists());
                    }
                });
            }
        }.start();

        // Setup the click listener
        artistLayout.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(getActivity(), ArtistActivity.class);

                ArtistsAdapter.ViewHolder tag = (ArtistsAdapter.ViewHolder) view.getTag();
                ImageView ivCover = tag.ivCover;
                TextView tvTitle = tag.tvTitle;

                ((ViewGroup) tag.llRoot.getParent()).setTransitionGroup(false);

                intent.putExtra(ArtistActivity.EXTRA_ARTIST_NAME,
                        mAdapter.getItem(position).getName());

                intent.putExtra(ArtistActivity.EXTRA_BACKGROUND_COLOR,
                        ((ColorDrawable) view.getBackground()).getColor());

                Utils.queueBitmap(tag.srcBitmap);

                ActivityOptions opt = ActivityOptions.makeSceneTransitionAnimation(getActivity(),
                        new Pair<View, String>(ivCover, "itemImage"),
                        new Pair<View, String>(tvTitle, "artistName"));

                startActivity(intent, opt.toBundle());
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

    }

    @Override
    public void onArtistUpdate(Artist a) {

    }

    @Override
    public void onProviderConnected(IMusicProvider provider) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {

            }
        });
    }
}