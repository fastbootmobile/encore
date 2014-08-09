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

    private final List<Artist> mDelayedUpdateList = new ArrayList<Artist>();
    private Runnable mDelayedUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            int initialCount = mAdapter.getCount();

            synchronized (mDelayedUpdateList) {
                mAdapter.addAllUnique(mDelayedUpdateList);
                mDelayedUpdateList.clear();
            }

            // Only notify and reload the gridview content if we actually have new elements
            if (initialCount != mAdapter.getCount()) {
                mAdapter.notifyDataSetChanged();
            }
        }
    };

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

        // Setup the click listener
        artistLayout.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(getActivity(), ArtistActivity.class);

                ArtistsAdapter.ViewHolder tag = (ArtistsAdapter.ViewHolder) view.getTag();
                AlbumArtImageView ivCover = tag.ivCover;
                TextView tvTitle = tag.tvTitle;

                intent.putExtra(ArtistActivity.EXTRA_ARTIST,
                        mAdapter.getItem(position).getRef());

                intent.putExtra(ArtistActivity.EXTRA_BACKGROUND_COLOR, tag.itemColor);

                Utils.queueBitmap(ArtistActivity.BITMAP_ARTIST_HERO, tag.srcBitmap);

                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                    /* ActivityOptions opt = ActivityOptions.makeSceneTransitionAnimation(getActivity(),
                            new Pair<View, String>(ivCover, "itemImage"),
                            new Pair<View, String>(tvTitle, "artistName"));

                    startActivity(intent, opt.toBundle()); */
                } else {
                    startActivity(intent);
                }
            }
        });

        return root;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        ProviderAggregator.getDefault().removeUpdateCallback(this);
    }

    private void postListUpdate() {
        mHandler.removeCallbacks(mDelayedUpdateRunnable);
        mHandler.post(mDelayedUpdateRunnable);
    }

    @Override
    public void onSongUpdate(List<Song> s) {
        for (Song song : s) {
            String artistRef = song.getArtist();
            Artist artist = ProviderAggregator.getDefault().getCache().getArtist(artistRef);

            if (artist != null) {
                synchronized (mDelayedUpdateList) {
                    if (!mDelayedUpdateList.contains(artist)) {
                        mDelayedUpdateList.add(artist);
                        postListUpdate();
                    }
                }
            }
        }
    }

    @Override
    public void onAlbumUpdate(List<Album> a) {

    }

    @Override
    public void onPlaylistUpdate(final List<Playlist> p) {

    }

    @Override
    public void onArtistUpdate(List<Artist> a) {
        synchronized (mDelayedUpdateList) {
            if (!mDelayedUpdateList.containsAll(a)) {
                mDelayedUpdateList.addAll(a);
                postListUpdate();
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