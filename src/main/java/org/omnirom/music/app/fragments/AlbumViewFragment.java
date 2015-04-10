/*
 * Copyright (C) 2014 Fastboot Mobile, LLC.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program;
 * if not, see <http://www.gnu.org/licenses>.
 */

package org.omnirom.music.app.fragments;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v7.graphics.Palette;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.LayoutAnimationController;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.TextView;

import com.getbase.floatingactionbutton.FloatingActionButton;

import org.omnirom.music.app.AlbumActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.adapters.SongsListAdapter;
import org.omnirom.music.app.ui.ParallaxScrollListView;
import org.omnirom.music.app.ui.PlayPauseDrawable;
import org.omnirom.music.art.RecyclingBitmapDrawable;
import org.omnirom.music.framework.PlaybackProxy;
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
import org.omnirom.music.utils.Utils;

import java.util.Iterator;
import java.util.List;

/**
 * Fragment for viewing an album's details
 */
public class AlbumViewFragment extends MaterialReelBaseFragment implements ILocalCallback {
    private static final String TAG = "AlbumViewFragment";

    private SongsListAdapter mAdapter;
    private ViewGroup mRootView;
    private Album mAlbum;
    private Handler mHandler;
    private Bitmap mHeroImage;
    private PlayPauseDrawable mFabDrawable;
    private int mBackgroundColor;
    private FloatingActionButton mPlayFab;
    private ParallaxScrollListView mListView;
    private boolean mFabShouldResume = false;
    private RecyclingBitmapDrawable mLogoBitmap;
    private View mOfflineView;
    private ImageView mIvHero;
    private TextView mTvAlbumName;
    private boolean mDidHaveMore = true;

    private View.OnClickListener mReelFabClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mBarDrawable.getCurrentShape() == PlayPauseDrawable.SHAPE_PLAY) {
                if (mFabShouldResume) {
                    PlaybackProxy.play();
                    mBarDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                } else {
                    PlaybackProxy.playAlbum(mAlbum);
                }
            } else {
                mFabShouldResume = true;
                PlaybackProxy.pause();
                mBarDrawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
            }
        }
    };

    private BasePlaybackCallback mPlaybackCallback = new BasePlaybackCallback() {
        @Override
        public void onSongStarted(final boolean buffering, final Song s) throws RemoteException {
            if (mAdapter.contains(s)) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mAdapter.notifyDataSetChanged();
                        setReelBarTitle(s.getTitle());
                        mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);

                        mFabDrawable.setBuffering(buffering);

                        mBarDrawable.setBuffering(buffering);
                        mBarDrawable.setShape(mFabDrawable.getRequestedShape());
                    }
                });
            }
        }

        @Override
        public void onPlaybackResume() throws RemoteException {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                    mFabDrawable.setBuffering(false);
                    mBarDrawable.setShape(mFabDrawable.getRequestedShape());
                    mBarDrawable.setBuffering(false);
                }
            });

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

            // Check whether or not the album is fully loaded on the provider's end
            if (mDidHaveMore) {
                if (provider != null) {
                    try {
                        mDidHaveMore = provider.fetchAlbumTracks(mAlbum.getRef());
                    } catch (RemoteException e) {
                        Log.e(TAG, "Error while fetching album tracks", e);
                    }
                }
            }

            // If we already have some songs, show them
            View loadingBar = findViewById(R.id.pbAlbumLoading);
            mListView.setVisibility(View.VISIBLE);
            if (mAlbum.getSongsCount() > 0) {
                // If there's not more tracks, hide the loading bar, otherwise keep on displaying it
                if (loadingBar.getVisibility() == View.VISIBLE) {
                    loadingBar.setVisibility(View.GONE);
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            showFab(true, true);
                        }
                    }, 400);
                }
                if (mOfflineView != null) {
                    mOfflineView.setVisibility(View.GONE);
                }

                // Load the songs into the adapter
                mAdapter.clear();
                final Iterator<String> songs = mAlbum.songs();
                while (songs.hasNext()) {
                    String songRef = songs.next();
                    Song song = aggregator.retrieveSong(songRef, mAlbum.getProvider());

                    if (song != null) {
                        mAdapter.put(song);
                    }
                }
                mAdapter.notifyDataSetChanged();
                mListView.startLayoutAnimation();
            } else if (aggregator.isOfflineMode()) {
                loadingBar.setVisibility(View.GONE);
                mListView.setVisibility(View.GONE);
                mOfflineView = LayoutInflater.from(getActivity())
                        .inflate(R.layout.offline, (ViewGroup) mRootView.getParent().getParent())
                        .findViewById(R.id.tvErrorMessage);
            } else {
                // No song loaded, show the loading spinner and hide the play FAB
                loadingBar.setVisibility(View.VISIBLE);
            }
        }
    };

    public AlbumViewFragment() {
        super();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mHandler = new Handler();

        // Inflate the layout for this fragment
        mRootView = (ViewGroup) inflater.inflate(R.layout.fragment_album_view, container, false);
        assert mRootView != null;

        setupList();

        setupHeader(inflater);

        // Start loading songs in another thread
        loadSongs();

        return mRootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        ProviderAggregator.getDefault().addUpdateCallback(this);
        PlaybackProxy.addCallback(mPlaybackCallback);
    }

    @Override
    public void onPause() {
        super.onPause();
        ProviderAggregator.getDefault().removeUpdateCallback(this);
        PlaybackProxy.removeCallback(mPlaybackCallback);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mHeroImage != null) {
            mHeroImage.recycle();
            mHeroImage = null;
        }
    }

    private void setupList() {
        // Setup the contents list view
        mListView = (ParallaxScrollListView) mRootView.findViewById(R.id.lvAlbumContents);
        mAdapter = new SongsListAdapter(false);
        mListView.setAdapter(mAdapter);

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                // We substract the header view
                position = position - 1;
                final Song song = mAdapter.getItem(position);

                if (Utils.canPlaySong(song)) {
                    // Play the song (ie. queue the album and play at the selected index)
                    PlaybackProxy.clearQueue();
                    PlaybackProxy.queueAlbum(mAlbum, false);
                    PlaybackProxy.playAtIndex(position);

                    mFabDrawable.setBuffering(true);
                    mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);

                    mBarDrawable.setBuffering(true);
                    mBarDrawable.setShape(mFabDrawable.getRequestedShape());

                    mFabShouldResume = true;

                    showMaterialReelBar(mPlayFab);
                }
            }
        });
    }

    private void setupHeader(LayoutInflater inflater) {
        // Load the header
        View headerView = inflater.inflate(R.layout.songs_list_view_header, mListView, false);
        mIvHero = (ImageView) headerView.findViewById(R.id.ivHero);
        mTvAlbumName = (TextView) headerView.findViewById(R.id.tvAlbumName);
        mTvAlbumName.setBackgroundColor(0xBBFFFFFF & mBackgroundColor);

        if (mAlbum != null) {
            mTvAlbumName.setText(mAlbum.getName());
        }

        if (Utils.hasLollipop()) {
            mTvAlbumName.setVisibility(View.INVISIBLE);
            mHandler.postDelayed(new Runnable() {
                @Override
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                public void run() {
                    if (mTvAlbumName.isAttachedToWindow()) {
                        Utils.animateHeadingReveal(mTvAlbumName);
                    }
                }
            }, 500);
        } else {
            mTvAlbumName.setVisibility(View.VISIBLE);
            mTvAlbumName.setAlpha(0.0f);
            mTvAlbumName.animate().alpha(1.0f).setDuration(AlbumActivity.BACK_DELAY).start();
        }

        // Hide download button by default
        headerView.findViewById(R.id.cpbOffline).setVisibility(View.GONE);

        mPlayFab = (FloatingActionButton) headerView.findViewById(R.id.fabPlay);

        // Set source logo
        ImageView ivSource = (ImageView) headerView.findViewById(R.id.ivSourceLogo);
        if (mAlbum != null) {
            mLogoBitmap = PluginsLookup.getDefault().getCachedLogo(getResources(), mAlbum);
            ivSource.setImageDrawable(mLogoBitmap);
        }

        // Set the FAB animated drawable
        mFabDrawable = new PlayPauseDrawable(getResources(), 1);
        mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
        mFabDrawable.setYOffset(6);

        mPlayFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mFabDrawable.getCurrentShape() == PlayPauseDrawable.SHAPE_PLAY) {
                    if (mFabShouldResume) {
                        PlaybackProxy.play();
                        mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                        mBarDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                    } else {
                        PlaybackProxy.playAlbum(mAlbum);
                    }
                } else {
                    mFabShouldResume = true;
                    PlaybackProxy.pause();
                    mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
                    mBarDrawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
                }

                if (Utils.hasLollipop()) {
                    // No Lollipop, no cool animation!
                    showMaterialReelBar(mPlayFab);
                }
            }
        });
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                showFab(false, false);
            }
        });
        mPlayFab.setImageDrawable(mFabDrawable);

        if (mHeroImage != null) {
            mIvHero.setImageBitmap(mHeroImage);
        } else {
            mIvHero.setImageResource(R.drawable.album_placeholder);
        }

        // Set the header view and adapter
        mListView.addParallaxedHeaderView(headerView);


        AlphaAnimation anim = new AlphaAnimation(0.f, 1.f);
        anim.setDuration(200);
        mListView.setLayoutAnimation(new LayoutAnimationController(anim));

        setupMaterialReelBar(mRootView, mReelFabClickListener);
    }

    public ImageView getHeroImageView() {
        return mIvHero;
    }

    /**
     * Sets the arguments for this fragment
     * @param hero The hero header image bitmap
     * @param extras The extras contained in the Intent that started the parent activity
     */
    public void setArguments(Bitmap hero, Bundle extras) {
        if (hero != null) {
            mHeroImage = hero.copy(hero.getConfig(), false);
        }
        mBackgroundColor = extras.getInt(AlbumActivity.EXTRA_BACKGROUND_COLOR, 0xFF333333);

        String albumRef = extras.getString(AlbumActivity.EXTRA_ALBUM);
        ProviderIdentifier provider
                = ProviderIdentifier.fromSerialized(extras.getString(AlbumActivity.EXTRA_PROVIDER));
        mAlbum = ProviderAggregator.getDefault().retrieveAlbum(albumRef, provider);

        if (mAlbum == null) {
            Log.e(TAG, "Album isn't in cache and provider returned null!");
        } else if (mHeroImage != null) {
            // Prepare the palette to colorize the FAB
            Palette.generateAsync(hero, new Palette.PaletteAsyncListener() {
                @Override
                public void onGenerated(final Palette palette) {
                    final Palette.Swatch normalColor = palette.getDarkMutedSwatch();
                    final Palette.Swatch pressedColor = palette.getDarkVibrantSwatch();
                    if (normalColor != null && mRootView != null) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mPlayFab.setNormalColor(normalColor.getRgb());
                                if (pressedColor != null) {
                                    mPlayFab.setPressedColor(pressedColor.getRgb());
                                } else {
                                    mPlayFab.setPressedColor(normalColor.getRgb());
                                }
                            }
                        });
                    }
                }
            });
        }
    }

    public void notifyReturnTransition() {
        showFab(true, false);
        if (Utils.hasLollipop()) {
            Utils.animateHeadingHiding(mTvAlbumName);
        }
    }

    /**
     * @return The {@link org.omnirom.music.model.Album} displayed by this fragment
     */
    public Album getAlbum() {
        return mAlbum;
    }

    /**
     * Returns the main artist of this album, based on the tracks container. This call is not valid
     * if the album's tracks aren't loaded.
     * @return The main artist of this album.
     */
    public String getArtist() {
        return Utils.getMainArtist(mAlbum);
    }

    /**
     * Shows or hide the play FAB
     * @param animate Whether or not to animate the transition
     * @param visible Whether or not to show the button
     */
    private void showFab(final boolean animate, final boolean visible) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Utils.animateScale(mPlayFab, animate, visible);
            }
        });
    }

    /**
     * Looks up and returns a view in this framgent
     * @param id The layout item ID to display
     * @return The view, or null if not found
     */
    public View findViewById(int id) {
        return mRootView.findViewById(id);
    }

    private void loadSongs() {
        mHandler.removeCallbacks(mLoadSongsRunnable);
        mHandler.post(mLoadSongsRunnable);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSongUpdate(List<Song> s) {
        for (Song song : s) {
            if (song.isLoaded() && song.getAlbum().equals(mAlbum.getRef())) {
                loadSongs();
                break;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAlbumUpdate(List<Album> a) {
        for (Album album : a) {
            if (album.getRef().equals(mAlbum.getRef())) {
                loadSongs();
                break;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPlaylistUpdate(List<Playlist> p) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onArtistUpdate(List<Artist> a) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onProviderConnected(IMusicProvider provider) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSearchResult(List<SearchResult> searchResult) {
    }
}
