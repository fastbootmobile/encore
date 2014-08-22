package org.omnirom.music.app.fragments;

import android.app.Activity;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import org.omnirom.music.app.MainActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.app.adapters.PlaylistAdapter;
import org.omnirom.music.app.ui.PlayPauseDrawable;
import org.omnirom.music.app.ui.PlaylistListView;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.SearchResult;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ILocalCallback;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderCache;
import org.omnirom.music.service.IPlaybackService;

import java.util.Iterator;
import java.util.List;


/**
 * A simple {@link android.support.v4.app.Fragment} subclass.
 * Use the {@link org.omnirom.music.app.fragments.PlaylistViewFragment#newInstance} factory method to
 * create an instance of this fragment.
 *
 */
public class PlaylistViewFragment extends Fragment implements ILocalCallback {

    private static final String TAG = "PlaylistViewFragment";
    public static final String KEY_PLAYLIST = "playlist";

    private PlaylistAdapter mAdapter;
    private Playlist mPlaylist;
    private ImageButton mPlayFab;
    private PlayPauseDrawable mFabDrawable;
    private boolean mFabShouldResume;

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

        PlaylistListView lvPlaylistContents = (PlaylistListView) root.findViewById(R.id.lvPlaylistContents);
        mAdapter = new PlaylistAdapter(root.getContext());
        lvPlaylistContents.setAdapter(mAdapter);

        // Setup the parallaxed header
        View headerView = inflater.inflate(R.layout.songs_list_view_header, null);
        lvPlaylistContents.addParallaxedHeaderView(headerView);

        headerView.findViewById(R.id.pbAlbumLoading).setVisibility(View.GONE);

        ImageView ivHero = (ImageView) headerView.findViewById(R.id.ivHero);
        TextView tvAlbumName = (TextView) headerView.findViewById(R.id.tvAlbumName);

        tvAlbumName.setText(mPlaylist.getName());
        ivHero.setImageResource(R.drawable.album_placeholder);

        mPlayFab = (ImageButton) headerView.findViewById(R.id.fabPlay);
        Utils.setLargeFabOutline(new View[]{mPlayFab});
        Utils.setupBigFabShadow(mPlayFab);

        // Set source logo
        ImageView ivSource = (ImageView) headerView.findViewById(R.id.ivSourceLogo);
        ivSource.setImageBitmap(PluginsLookup.getDefault().getCachedLogo(mPlaylist));

        // Set the FAB animated drawable
        mFabDrawable = new PlayPauseDrawable(getResources());
        mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
        mFabDrawable.setPaddingDp(48);
        mPlayFab.setImageDrawable(mFabDrawable);
        mPlayFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mFabDrawable.getCurrentShape() == PlayPauseDrawable.SHAPE_PLAY) {
                    if (mFabShouldResume) {
                        try {
                            PluginsLookup.getDefault().getPlaybackService().play();
                            mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                        } catch (RemoteException e) {
                            Log.e(TAG, "Cannot resume playback", e);
                        }
                    } else {
                        try {
                            PluginsLookup.getDefault().getPlaybackService().playPlaylist(mPlaylist);
                            mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                        } catch (RemoteException e) {
                            Log.e(TAG, "Cannot start playing playlist " + mPlaylist.getRef(), e);
                        }
                    }
                } else {
                    mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                    mFabShouldResume = true;
                    try {
                        PluginsLookup.getDefault().getPlaybackService().pause();
                        mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Cannot pause playback", e);
                    }
                }
            }
        });

        // Fill the playlist
        Iterator<String> songIt = mPlaylist.songs();
        while (songIt.hasNext()) {
            String songRef = songIt.next();
            Song song = ProviderAggregator.getDefault().getCache().getSong(songRef);
            if (song == null) {
                song = ProviderAggregator.getDefault().retrieveSong(songRef, mPlaylist.getProvider());
            }
            mAdapter.addItem(song);
        }
        mAdapter.notifyDataSetChanged();
        mAdapter.setPlaylist(mPlaylist);

        // Set the list listener
        lvPlaylistContents.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                // Play the song
                Song song = mAdapter.getItem(i-1);

                if (song != null) {
                    IPlaybackService pbService = PluginsLookup.getDefault().getPlaybackService();
                    ProviderCache cache = ProviderAggregator.getDefault().getCache();
                    try {
                        pbService.playSong(song);

                        // queue remaining songs
                        Iterator<String> songsIt = mPlaylist.songs();
                        int itIndex = 0;
                        while (songsIt.hasNext()) {
                            String songRef = songsIt.next();
                            if (itIndex <= i) {
                                itIndex++;
                                continue;
                            } else {
                                itIndex++;
                            }

                            pbService.queueSong(cache.getSong(songRef), false);
                        }
                    } catch (RemoteException e) {
                        Log.e(TAG, "Unable to play song", e);
                    }
                } else {
                    Log.e(TAG, "Trying to play null song!");
                }
            }
        });

        return root;
    }


    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        ProviderAggregator.getDefault().addUpdateCallback(this);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        ProviderAggregator.getDefault().removeUpdateCallback(this);
    }

    @Override
    public void onSongUpdate(List<Song> s) {
        // We check if the song belongs to this playlist
        boolean hasPlaylist = false;
        Iterator<String> songsRef = mPlaylist.songs();
        while (songsRef.hasNext()) {
            String ref = songsRef.next();
            for (Song song : s) {
                if (song.getRef().equals(ref)) {
                    hasPlaylist = true;
                    break;
                }
            }

            if (hasPlaylist) {
                break;
            }
        }

        // It does, update the list then
        if (hasPlaylist) {
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onAlbumUpdate(List<Album> a) {

    }

    @Override
    public void onPlaylistUpdate(final List<Playlist> p) {
        // If the currently watched playlist is updated, update me
        if (p.equals(mPlaylist)) {
            mAdapter.notifyDataSetChanged();
        }
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
