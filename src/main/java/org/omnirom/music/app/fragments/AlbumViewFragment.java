package org.omnirom.music.app.fragments;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.support.v7.graphics.Palette;
import android.support.v7.graphics.PaletteItem;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import org.omnirom.music.app.AlbumActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.app.adapters.SongsListAdapter;
import org.omnirom.music.app.ui.ParallaxScrollListView;
import org.omnirom.music.app.ui.PlayPauseDrawable;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.SearchResult;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ILocalCallback;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderIdentifier;
import org.omnirom.music.service.BasePlaybackCallback;
import org.omnirom.music.service.IPlaybackService;

import java.util.Iterator;
import java.util.List;

import omnimusic.Plugin;

/**
 * Created by h4o on 26/06/2014.
 */
public class AlbumViewFragment extends Fragment implements ILocalCallback {

    private static final String TAG = "AlbumViewFragment";
    private SongsListAdapter mAdapter;
    private View mRootView;
    private Album mAlbum;
    private Handler mHandler;
    private Bitmap mHeroImage;
    private PlayPauseDrawable mFabDrawable;
    private int mBackgroundColor;
    private ImageButton mPlayFab;
    private boolean mFabShouldResume = false;

    private BasePlaybackCallback mPlaybackCallback = new BasePlaybackCallback() {
        @Override
        public void onSongStarted(boolean buffering, Song s) throws RemoteException {
            if (mAdapter.contains(s)) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mAdapter.notifyDataSetChanged();
                    }
                });
            }
        }
    };

    private Runnable mLoadSongsRunnable = new Runnable() {
        @Override
        public void run() {
            final ProviderAggregator aggregator = ProviderAggregator.getDefault();

            ProviderIdentifier pi = mAlbum.getProvider();
            if (pi == null) {
                Log.e(TAG, "Album provider for " + mAlbum.getRef() + " is null!");
                return;
            }

            IMusicProvider provider = PluginsLookup.getDefault().getProvider(pi).getBinder();

            boolean hasMore = false;
            if (provider != null) {
                try {
                    hasMore = provider.fetchAlbumTracks(mAlbum.getRef());
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            if (mAlbum.getSongsCount() > 0) {
                View loadingBar = findViewById(R.id.pbAlbumLoading);
                if (loadingBar.getVisibility() == View.VISIBLE && !hasMore) {
                    loadingBar.setVisibility(View.GONE);
                    showFab(true, true);
                }

                Iterator<String> songs = mAlbum.songs();
                mAdapter.clear();

                while (songs.hasNext()) {
                    String songRef = songs.next();
                    Song song = aggregator.retrieveSong(songRef, mAlbum.getProvider());

                    if (song != null) {
                        mAdapter.put(song);
                    }
                }
                mAdapter.notifyDataSetChanged();
                mRootView.invalidate();
            } else {
                findViewById(R.id.pbAlbumLoading).setVisibility(View.VISIBLE);
                showFab(false, false);
            }
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState){
        mHandler = new Handler();

        // Inflate the layout for this fragment
        mRootView = inflater.inflate(R.layout.fragment_album_view, container, false);
        assert mRootView != null;
        View headerView = inflater.inflate(R.layout.songs_list_view_header, null);
        ImageView ivHero = (ImageView) headerView.findViewById(R.id.ivHero);
        TextView tvAlbumName = (TextView) headerView.findViewById(R.id.tvAlbumName);
        tvAlbumName.setBackgroundColor(mBackgroundColor);
        tvAlbumName.setText(mAlbum.getName());

        // Hide download button
        headerView.findViewById(R.id.cpbOffline).setVisibility(View.GONE);

        mPlayFab = (ImageButton) headerView.findViewById(R.id.fabPlay);
        Utils.setLargeFabOutline(new View[]{mPlayFab});
        Utils.setupLargeFabShadow(mPlayFab);

        // Set source logo
        ImageView ivSource = (ImageView) headerView.findViewById(R.id.ivSourceLogo);
        ivSource.setImageBitmap(PluginsLookup.getDefault().getCachedLogo(mAlbum));

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
                            PluginsLookup.getDefault().getPlaybackService().playAlbum(mAlbum);
                            mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                        } catch (RemoteException e) {
                            Log.e(TAG, "Cannot start playing album " + mAlbum.getRef(), e);
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

        ivHero.setImageBitmap(mHeroImage);

        ParallaxScrollListView listView =
                (ParallaxScrollListView) mRootView.findViewById(R.id.lvAlbumContents);
        mAdapter = new SongsListAdapter(getActivity(), false);
        listView.addParallaxedHeaderView(headerView);
        listView.setAdapter(mAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                final ProviderAggregator aggregator = ProviderAggregator.getDefault();
                // We substract the header view
                position = position - 1;

                // Play the song
                Song song = mAdapter.getItem(position);

                if (song != null) {
                    try {
                        IPlaybackService pbService = PluginsLookup.getDefault().getPlaybackService();
                        pbService.playSong(song);

                        // Add the remaining songs of the album to the playback queue
                        Iterator<String> it = mAlbum.songs();
                        for (int i = 0; i < position + 1; i++) {
                            it.next();
                        }
                        while (it.hasNext()) {
                            song = aggregator.retrieveSong(it.next(), mAlbum.getProvider());
                            pbService.queueSong(song, false);
                        }

                        pbService.play();
                        mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                        mFabShouldResume = true;
                    } catch (RemoteException e) {
                        Log.e(TAG, "Unable to play song", e);
                    }
                } else {
                    Log.e(TAG, "Trying to play null song!");
                }
            }
        });

        loadSongs();

        return mRootView;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        ProviderAggregator.getDefault().addUpdateCallback(this);
        try {
            PluginsLookup.getDefault().getPlaybackService().addCallback(mPlaybackCallback);
        } catch (RemoteException e) {
            Log.e(TAG, "Error while adding playback callback", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ProviderAggregator.getDefault().removeUpdateCallback(this);
        try {
            PluginsLookup.getDefault().getPlaybackService().removeCallback(mPlaybackCallback);
        } catch (RemoteException e) {
            Log.e(TAG, "Error while adding playback callback", e);
        }
    }

    public void setArguments(Bitmap hero, Bundle extras) {
        mHeroImage = hero;
        mBackgroundColor = extras.getInt(AlbumActivity.EXTRA_BACKGROUND_COLOR, 0xFF333333);
        mAlbum = extras.getParcelable(AlbumActivity.EXTRA_ALBUM);

        // Use cache item instead of parceled item (otherwise updates pushed to the cache won't
        // propagate here)
        mAlbum = ProviderAggregator.getDefault().retrieveAlbum(mAlbum.getRef(), mAlbum.getProvider());

        // Prepare the palette to colorize the FAB
        Palette.generateAsync(hero, new Palette.PaletteAsyncListener() {
            @Override
            public void onGenerated(final Palette palette) {
                final PaletteItem color = palette.getDarkMutedColor();
                if (color != null && mRootView != null) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Utils.colorFloatingButton(mPlayFab, color.getRgb(), true);
                        }
                    });
                }
            }
        });
    }

    public Album getAlbum() {
        return mAlbum;
    }

    private void showFab(final boolean animate, final boolean visible) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Utils.animateScale(mPlayFab, animate, visible);
            }
        });
    }

    public View findViewById(int id) {
        return mRootView.findViewById(id);
    }

    private void loadSongs() {
        mHandler.post(mLoadSongsRunnable);
    }

    public String getArtist() {
        return Utils.getMainArtist(mAlbum);
    }

    @Override
    public void onSongUpdate(List<Song> s) {
        for (Song song : s) {
            if (song.isLoaded() && song.getAlbum().equals(mAlbum.getRef())) {
                loadSongs();
                break;
            }
        }
    }

    @Override
    public void onAlbumUpdate(List<Album> a) {
        for (Album album : a) {
            if (album.getRef().equals(mAlbum.getRef())) {
                loadSongs();
                break;
            }
        }
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
