package org.omnirom.music.app.fragments;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
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
import org.omnirom.music.service.BasePlaybackCallback;

import java.util.List;

/**
 * Created by h4o on 19/06/2014.
 */
public class SongsFragment extends Fragment {
    private SongsListAdapter mSongsListAdapter;
    private String TAG = "SongsFragment";

    private BasePlaybackCallback mPlaybackCallback = new BasePlaybackCallback() {
        @Override
        public void onSongStarted(Song s) throws RemoteException {
            mSongsListAdapter.notifyDataSetChanged();
        }
    };

    private AdapterView.OnItemClickListener mItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
            // Play the song
            Song song = mSongsListAdapter.getItem(i);

            if (song != null) {
                try {
                    PluginsLookup.getDefault().getPlaybackService().playSong(song);
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to play song", e);
                }
            } else {
                Log.e(TAG, "Trying to play null song!");
            }
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_songs, container, false);
        assert root != null;
        ListView songsList = (ListView) root.findViewById(R.id.songsList);

        mSongsListAdapter = new SongsListAdapter(getActivity(), true);
        songsList.setAdapter(mSongsListAdapter);

        List<ProviderConnection> providers = PluginsLookup.getDefault().getAvailableProviders();
        for (ProviderConnection providerConnection : providers) {
            try {
                List<Song> songs = providerConnection.getBinder().getSongs();
                for (Song song : songs) {
                    mSongsListAdapter.put(song);
                }
            } catch (Exception e) {
                Log.w(TAG, "Cannot get songs from a provider", e);
            }
        }

        mSongsListAdapter.sortAll();
        mSongsListAdapter.notifyDataSetChanged();

        songsList.setOnItemClickListener(mItemClickListener);
        return root;
    }

    public static SongsFragment newInstance() {
        SongsFragment fragment = new SongsFragment();
        return fragment;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            PluginsLookup.getDefault().getPlaybackService().addCallback(mPlaybackCallback);
        } catch (RemoteException e) {
            // ignore
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        try {
            PluginsLookup.getDefault().getPlaybackService().removeCallback(mPlaybackCallback);
        } catch (Exception e) {
            // ignore
        }
    }

}
