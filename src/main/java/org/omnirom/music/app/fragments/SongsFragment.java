package org.omnirom.music.app.fragments;

import android.app.Activity;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.Handler;
import android.os.RemoteException;
import android.os.SystemClock;
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
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.ProviderConnection;
import org.omnirom.music.service.BasePlaybackCallback;
import org.omnirom.music.service.IPlaybackService;

import java.util.List;

/**
 * Created by h4o on 19/06/2014.
 */
public class SongsFragment extends Fragment {
    private static final String TAG = "SongsFragment";

    private SongsListAdapter mSongsListAdapter;
    private Handler mHandler;

    private BasePlaybackCallback mPlaybackCallback = new BasePlaybackCallback() {
        @Override
        public void onSongStarted(Song s) throws RemoteException {
            mHandler.post(new Runnable() {
                public void run() {
                    mSongsListAdapter.notifyDataSetChanged();
                }
            });
        }
    };

    private AdapterView.OnItemClickListener mItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
            // Play the song
            Song song = mSongsListAdapter.getItem(i);

            if (song != null) {
                try {
                    long clock = SystemClock.uptimeMillis();
                    PluginsLookup.getDefault().getPlaybackService().playSong(song);
                    Log.e(TAG, "playSong call took " + (SystemClock.uptimeMillis()-clock) + "ms");
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
        mHandler = new Handler();

        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_songs, container, false);
        assert root != null;
        ListView songsList = (ListView) root.findViewById(R.id.songsList);

        mSongsListAdapter = new SongsListAdapter(getActivity(), true);
        songsList.setAdapter(mSongsListAdapter);

        List<ProviderConnection> providers = PluginsLookup.getDefault().getAvailableProviders();
        for (ProviderConnection providerConnection : providers) {
            try {
                IMusicProvider provider = providerConnection.getBinder();
                if (provider != null) {
                    List<Song> songs = provider.getSongs();
                    for (Song song : songs) {
                        mSongsListAdapter.put(song);
                    }
                }
            } catch (DeadObjectException e) {
                Log.e(TAG, "Provider died while getting songs");
            } catch (RemoteException e) {
                Log.w(TAG, "Cannot get songs from a provider", e);
            }
        }

        mSongsListAdapter.sortAll();
        mSongsListAdapter.notifyDataSetChanged();

        songsList.setOnItemClickListener(mItemClickListener);
        return root;
    }

    public static SongsFragment newInstance() {
        return new SongsFragment();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            IPlaybackService service = PluginsLookup.getDefault().getPlaybackService();
            if (service != null) {
                service.addCallback(mPlaybackCallback);
            }
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
