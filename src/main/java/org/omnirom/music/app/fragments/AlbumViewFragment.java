package org.omnirom.music.app.fragments;

import android.app.Activity;
import android.graphics.drawable.StateListDrawable;
import android.support.v4.app.Fragment;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v7.graphics.Palette;
import android.support.v7.graphics.PaletteItem;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.manuelpeinado.fadingactionbar.FadingActionBarHelper;

import org.omnirom.music.app.AlbumActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.app.adapters.SongsListAdapter;
import org.omnirom.music.app.ui.MaterialTransitionDrawable;
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
import org.omnirom.music.providers.ProviderCache;
import org.omnirom.music.providers.ProviderConnection;
import org.omnirom.music.providers.ProviderIdentifier;

import java.util.Iterator;
import java.util.List;

/**
 * Created by h4o on 26/06/2014.
 */
public class AlbumViewFragment extends Fragment implements ILocalCallback {

    private static final String TAG = "AlbumViewFragment";
    private SongsListAdapter mAdapter;
    private ParallaxScrollListView listView;
    private View mRootView;
    private Album mAlbum;
    private Handler mHandler;
    private Bitmap mHeroImage;
    private PlayPauseDrawable mFabDrawable;
    private int mBackgroundColor;
    private ImageButton mPlayFab;
    private boolean mFabShouldResume = false;
    private FadingActionBarHelper mFadingHelper;

    private Runnable mLoadSongsRunnable = new Runnable() {
        @Override
        public void run() {
            final ProviderCache cache = ProviderAggregator.getDefault().getCache();

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
                    Song song = cache.getSong(songRef);

                    // If the song isn't loaded, try to get it from the provider, it might be loaded
                    // there but not cached for various reasons. For instance, Spotify loads the
                    // albums tracks info, but we're not tracking them in metadata_callback, so
                    // they're not actually pushed to the app's cache
                    if (song == null) {
                        song = ProviderAggregator.getDefault().retrieveSong(songRef, mAlbum.getProvider());

                        if (song == null) {
                            // Song is still unknown, we skip!
                            continue;
                        }
                    }

                    mAdapter.put(song);
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
        View headerView = inflater.inflate(R.layout.album_view_header, null);
        ImageView ivHero = (ImageView) headerView.findViewById(R.id.ivHero);
        TextView tvAlbumName = (TextView) headerView.findViewById(R.id.tvAlbumName);
        tvAlbumName.setBackgroundColor(mBackgroundColor);
        tvAlbumName.setText(mAlbum.getName());

        mPlayFab = (ImageButton) headerView.findViewById(R.id.fabPlay);
        Utils.setLargeFabOutline(new View[]{mPlayFab});

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

        listView =  (ParallaxScrollListView) mRootView.findViewById(R.id.lvAlbumContents);
        mAdapter = new SongsListAdapter(getActivity(), false);
        listView.addParallaxedHeaderView(headerView);
        listView.setAdapter(mAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                // Play the song
                Song song = mAdapter.getItem(i);

                if (song != null) {
                    try {
                        PluginsLookup.getDefault().getPlaybackService().playSong(song);
                        // TODO: Add everything else from the album to the queue
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
       /* mFadingHelper = new FadingActionBarHelper()
                .actionBarBackground(mBackgroundColor)
                .headerLayout(R.layout.header_light)
                .contentLayout(R.layout.activity_scrollview)
                .lightActionBar(mBackgroundColor);
        mFadingHelper.initActionBar(activity);*/
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ProviderAggregator.getDefault().removeUpdateCallback(this);
    }

    public void setArguments(Bitmap hero, Bundle extras) {
        mHeroImage = hero;
        mBackgroundColor = extras.getInt(AlbumActivity.EXTRA_BACKGROUND_COLOR, 0xFF333333);
        mAlbum = extras.getParcelable(AlbumActivity.EXTRA_ALBUM);

        // Use cache item instead of parceled item (otherwise updates pushed to the cache won't
        // propagate here)
        mAlbum = ProviderAggregator.getDefault().getCache().getAlbum(mAlbum.getRef());

        // Prepare the palette to colorize the FAB
        Palette.generateAsync(hero, new Palette.PaletteAsyncListener() {
            @Override
            public void onGenerated(final Palette palette) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        PaletteItem color = palette.getDarkMutedColor();
                        if (color != null && mRootView != null) {
                            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                                // RippleDrawable ripple = (RippleDrawable) mRootView.findViewById(R.id.fabPlay).getBackground();
                                // GradientDrawable back = (GradientDrawable) ripple.getDrawable(0);
                                // back.setColor(color.getRgb());
                            } else {
                                StateListDrawable drawable = (StateListDrawable) mRootView.findViewById(R.id.fabPlay).getBackground();
                                GradientDrawable shape = (GradientDrawable) drawable.getCurrent();
                                shape.setColor(color.getRgb());
                            }
                        }
                    }
                });
            }
        });
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
        mLoadSongsRunnable.run();
    }

    public String getArtist() {
        return Utils.getMainArtist(mAlbum);
    }

    @Override
    public void onSongUpdate(List<Song> s) {
        for (Song song : s) {
            if (song.getAlbum().equals(mAlbum.getRef())) {
                loadSongs();
                break;
            }
        }
    }

    @Override
    public void onAlbumUpdate(List<Album> a) {
        for (Album album : a) {
            if (album.getRef().equals(mAlbum.getRef())) {
                Log.e(TAG, "onAlbumUpdate");
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
