package org.omnirom.music.app.fragments;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.omnirom.music.app.MainActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.adapters.PlaylistAdapter;
import org.omnirom.music.app.adapters.PlaylistListAdapter;
import org.omnirom.music.app.ui.ExpandableHeightGridView;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ILocalCallback;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.ProviderAggregator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * A simple {@link android.support.v4.app.Fragment} subclass.
 * Use the {@link org.omnirom.music.app.fragments.PlaylistViewFragment#newInstance} factory method to
 * create an instance of this fragment.
 *
 */
public class PlaylistViewFragment extends Fragment implements ILocalCallback {

    private static final String KEY_PLAYLIST = "playlist";

    private PlaylistAdapter mAdapter;
    private Handler mHandler;
    private Playlist mPlaylist;


    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment PlaylistViewFragment.
     */
    public static PlaylistViewFragment newInstance(Playlist p) {
        PlaylistViewFragment fragment = new PlaylistViewFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(KEY_PLAYLIST, p);
        fragment.setArguments(bundle);
        return fragment;
    }
    public PlaylistViewFragment() {
        mHandler = new Handler();

        ProviderAggregator.getDefault().addUpdateCallback(this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args == null) {
            throw new IllegalArgumentException("This fragment must have a valid playlist");
        }

        // Get the playlist from the arguments, from the instantiation
        mPlaylist = args.getParcelable(KEY_PLAYLIST);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_playlist_view, container, false);
        assert root != null;

        ListView lvPlaylistContents = (ListView) root.findViewById(R.id.lvPlaylistContents);
        mAdapter = new PlaylistAdapter(root.getContext());
        lvPlaylistContents.setAdapter(mAdapter);

        // Fill the playlist
        Iterator<String> songIt = mPlaylist.songs();
        while (songIt.hasNext()) {
            String songRef = songIt.next();
            Song song = ProviderAggregator.getDefault().getCache().getSong(songRef);
            mAdapter.addItem(song);
        }

        // Fill the playlist information
        TextView tvPlaylistName = (TextView) root.findViewById(R.id.tvPlaylistName);
        TextView tvNumTracks = (TextView) root.findViewById(R.id.tvNumTracks);

        tvPlaylistName.setText(mPlaylist.getName());
        tvNumTracks.setText(getString(R.string.nb_tracks, mPlaylist.getSongsCount()));

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

    }
}
