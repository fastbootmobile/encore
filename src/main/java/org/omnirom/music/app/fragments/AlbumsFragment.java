package org.omnirom.music.app.fragments;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import org.omnirom.music.app.AlbumActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.adapters.AlbumsAdapter;
import org.omnirom.music.app.ui.MaterialTransitionDrawable;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.SearchResult;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ILocalCallback;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.ProviderAggregator;

import java.util.List;

/**
 * Created by h4o on 20/06/2014.
 */
public class AlbumsFragment extends Fragment implements ILocalCallback {

    private static final String TAG = "AlbumsFragment";

    private AlbumsAdapter mAdapter;
    private Handler mHandler;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment AlbumsFragment.
     */
    public static AlbumsFragment newInstance() {
        return new AlbumsFragment();
    }

    public AlbumsFragment() {
        mAdapter = new AlbumsAdapter();


        ProviderAggregator.getDefault().addUpdateCallback(this);
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
        View root = inflater.inflate(R.layout.fragment_albums, container, false);
        GridView albumLayout = (GridView) root.findViewById(R.id.gvAlbums);
        albumLayout.setAdapter(mAdapter);

        List<Album> allAlbums = ProviderAggregator.getDefault().getCache().getAllAlbums();
        mAdapter.addAllUnique(allAlbums);
        mAdapter.notifyDataSetChanged();

        // Setup the click listener
        albumLayout.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AlbumsAdapter.ViewHolder tag = (AlbumsAdapter.ViewHolder) view.getTag();
                ImageView ivCover = tag.ivCover;
                TextView tvTitle = tag.tvTitle;

                Bitmap hero = ((MaterialTransitionDrawable) tag.ivCover.getDrawable()).getFinalDrawable().getBitmap();
                Intent intent = AlbumActivity.craftIntent(getActivity(), hero,
                        mAdapter.getItem(position), tag.itemColor);

                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                    /* ActivityOptions opt = ActivityOptions.makeSceneTransitionAnimation(getActivity(),
                            new Pair<View, String>(ivCover, "itemImage"),
                            new Pair<View, String>(tvTitle, "albumName"));

                    startActivity(intent, opt.toBundle()); */
                } else {
                    startActivity(intent);
                }
            }
        });

        return root;
    }

    @Override
    public void onSongUpdate(List<Song> s) {

    }

    @Override
    public void onAlbumUpdate(final List<Album> a) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mAdapter.addAllUnique(a)) {
                    mAdapter.notifyDataSetChanged();
                }
            }
        });

    }

    @Override
    public void onPlaylistUpdate(List<Playlist> p) {

    }

    @Override
    public void onArtistUpdate(List<Artist> a) {

    }

    @Override
    public void onProviderConnected(IMusicProvider provider) {

    }

    @Override
    public void onSearchResult(SearchResult searchResult) {

    }
}