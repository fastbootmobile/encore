package org.omnirom.music.app.fragments;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;

import org.omnirom.music.app.MainActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.adapters.AlbumsAdapter;
import org.omnirom.music.app.adapters.ArtistsAdapter;
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

import java.util.List;

/**
 * Created by h4o on 20/06/2014.
 */
public class AlbumsFragment extends AbstractRootFragment implements ILocalCallback {

    private AlbumsAdapter mAdapter;
    private Handler mHandler;
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
    public AlbumsFragment() {
        mAdapter = new AlbumsAdapter();
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
        View root = inflater.inflate(R.layout.fragment_albums, container, false);
        GridView albumLayout =
                (GridView) root.findViewById(R.id.gvAlbums);
        albumLayout.setAdapter(mAdapter);
       // albumLayout.setExpanded(true);


        new Thread() {
            public void run() {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        for(ProviderConnection providerConnection : PluginsLookup.getDefault().getAvailableProviders()) {
                            try {

                                List<Album> albums = providerConnection.getBinder().getAlbums();
                                mAdapter.addAllUnique(albums);
                            } catch (Exception e) {

                            }
                        }
                    }
                });
            }
        }.start();

        // Setup the search box
        setupSearchBox(root);

        // Setup the click listener
      /*  albumLayout.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                MainActivity act = (MainActivity) getActivity();
                //act.showFragment(PlaylistViewFragment.newInstance(mAdapter.getItem(position)), true);
            }
        });*/

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