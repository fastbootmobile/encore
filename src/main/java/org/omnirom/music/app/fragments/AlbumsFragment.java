package org.omnirom.music.app.fragments;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ExpandableListView;
import android.widget.GridView;

import org.omnirom.music.app.MainActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.adapters.AlbumsAdapter;
import org.omnirom.music.app.adapters.ArtistsAdapter;
import org.omnirom.music.app.ui.ExpandableGridView;
import org.omnirom.music.app.ui.ExpandableHeightGridView;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ILocalCallback;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderConnection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by h4o on 20/06/2014.
 */
public class AlbumsFragment extends AbstractRootFragment implements ILocalCallback {

    private AlbumsAdapter mAdapter;
    private List<Album> mAlbums;
    private Handler mHandler;
    private static final String KEY_ALBUM_LIST = "album_list";
//    private final ArrayList<Playlist> mPlaylistsUpdated = new ArrayList<Playlist>();



    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment PlaylistListFragment.
     */
    public static AlbumsFragment newInstance() {
        AlbumsFragment fragment = new AlbumsFragment();
        return fragment;
    }
    public static AlbumsFragment newInstance(List<Album> albumList){
        AlbumsFragment fragment = new AlbumsFragment();
        Bundle bundle = new Bundle();
        Object[] objects = albumList.toArray();
        Album[] albums = Arrays.copyOf(objects,objects.length,Album[].class);
        bundle.putParcelableArray(KEY_ALBUM_LIST,albums);
        fragment.setArguments(bundle);
        return fragment;
    }
    public AlbumsFragment() {
        mAdapter = new AlbumsAdapter();
        mHandler = new Handler();

        ProviderAggregator.getDefault().addUpdateCallback(this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if(args != null) {
            Album[] albums = (Album[]) args.getParcelableArray(KEY_ALBUM_LIST);
            mAlbums = new ArrayList<Album>(Arrays.asList(albums));
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_albums, container, false);
        GridView albumLayout =
                (GridView) root.findViewById(R.id.gvAlbums);
        //albumLayout.setStretchMode(ExpandableGridView.AUTO_FIT);
        albumLayout.setAdapter(mAdapter);

       // albumLayout.setExpanded(true);

        if(mAlbums == null) {
            new Thread() {
                public void run() {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mAdapter.addAllUnique(ProviderAggregator.getDefault().getCache().getAllAlbums());
                        }
                    });
                }
            }.start();
        } else {
            mAdapter.addAllUnique(mAlbums);
        }
        // Setup the search box
        setupSearchBox(root);
        albumLayout.setOnScrollListener(mAdapter.onScrollListener);
        // Setup the click listener
      albumLayout.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                MainActivity act = (MainActivity) getActivity();
                act.showFragment(AlbumViewFragment.newInstance(mAdapter.getItem(position)), true);
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
        /*mHandler.post(new Runnable() {
            @Override
            public void run() {

            }
        });*/
    }
}