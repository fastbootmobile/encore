package org.omnirom.music.app.fragments;

import android.app.Fragment;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import org.omnirom.music.app.R;
import org.omnirom.music.app.adapters.SongsListAdapter;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.SearchResult;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ILocalCallback;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderConnection;

import java.util.List;

/**
 * Created by h4o on 19/06/2014.
 */
public class SongsFragment extends AbstractRootFragment implements ILocalCallback {
    private SongsListAdapter mSongsListAdapter;
    private Handler mHandler;
    private String TAG = "SongsFragment";
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mHandler = new Handler();
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_songs, container, false);
        assert root != null;
        ListView songsList = (ListView)root.findViewById(R.id.songsList);
        mSongsListAdapter = new SongsListAdapter(root.getContext());
        songsList.setAdapter(mSongsListAdapter);

                for(ProviderConnection providerConnection : PluginsLookup.getDefault().getAvailableProviders()){
                    try {
                        List<Song> Songs = providerConnection.getBinder().getSongs();
                        for(Song song : Songs){
                            mSongsListAdapter.put(song);
                        }
                    } catch (Exception e){
                        Log.d(TAG, e.toString());
                    }
                }

                mSongsListAdapter.sortAll();

        mSongsListAdapter.notifyDataSetChanged();
        songsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                // Play the song
                Song song = mSongsListAdapter.getItem(i);

                if (song != null) {
                    try {
                        PluginsLookup.getDefault().getPlaybackService().playSong(song);
                    } catch (RemoteException e) {
                        Log.e("TEST", "Unable to play song", e);
                    }
                } else {
                    Log.e(TAG, "Trying to play null song!");
                }
            }
        });
        setupSearchBox(root);
        return root;
    }

    public static SongsFragment newInstance() {
        SongsFragment fragment = new SongsFragment();
        return fragment;
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
    public void onArtistUpdate(List<Artist> a) {

    }

    @Override
    public void onProviderConnected(IMusicProvider provider) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                //mSongsListAdapter.addAllUnique(ProviderAggregator.getDefault().);
            }
        });
    }

    @Override
    public void onSearchResult(SearchResult searchResult) {

    }

}
