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

package com.fastbootmobile.encore.app.fragments;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.CardView;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.echonest.api.v4.Biography;
import com.echonest.api.v4.EchoNestException;
import com.fastbootmobile.encore.api.echonest.EchoNest;
import com.fastbootmobile.encore.app.AppActivity;
import com.fastbootmobile.encore.app.ArtistActivity;
import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.app.adapters.ArtistsAdapter;
import com.fastbootmobile.encore.app.ui.AlbumArtImageView;
import com.fastbootmobile.encore.app.ui.MaterialTransitionDrawable;
import com.fastbootmobile.encore.app.ui.ObservableScrollView;
import com.fastbootmobile.encore.app.ui.ParallaxScrollView;
import com.fastbootmobile.encore.app.ui.PlayPauseDrawable;
import com.fastbootmobile.encore.app.ui.WrapContentHeightViewPager;
import com.fastbootmobile.encore.art.AlbumArtHelper;
import com.fastbootmobile.encore.art.RecyclingBitmapDrawable;
import com.fastbootmobile.encore.framework.PlaybackProxy;
import com.fastbootmobile.encore.framework.PluginsLookup;
import com.fastbootmobile.encore.framework.Suggestor;
import com.fastbootmobile.encore.model.Album;
import com.fastbootmobile.encore.model.Artist;
import com.fastbootmobile.encore.model.BoundEntity;
import com.fastbootmobile.encore.model.Playlist;
import com.fastbootmobile.encore.model.SearchResult;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.ILocalCallback;
import com.fastbootmobile.encore.providers.IMusicProvider;
import com.fastbootmobile.encore.providers.ProviderAggregator;
import com.fastbootmobile.encore.providers.ProviderConnection;
import com.fastbootmobile.encore.providers.ProviderIdentifier;
import com.fastbootmobile.encore.service.BasePlaybackCallback;
import com.fastbootmobile.encore.service.PlaybackService;
import com.fastbootmobile.encore.utils.Utils;
import com.getbase.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * Fragment showing artist information: Tracks, similar artists, and biography
 */
public class ArtistFragment extends Fragment implements ILocalCallback {
    private static final String TAG = "ArtistFragment";

    private static final int FRAGMENT_ID_TRACKS = 0;
    private static final int FRAGMENT_ID_SIMILAR = 1;
    private static final int FRAGMENT_ID_BIOGRAPHY = 2;
    private static final int FRAGMENT_COUNT = 3;

    private static final int ANIMATION_DURATION = 300;
    private static final DecelerateInterpolator mInterpolator = new DecelerateInterpolator();

    private Bitmap mHeroImage;
    private int mBackgroundColor;
    private Artist mArtist;
    private ParallaxScrollView mRootView;
    private Handler mHandler;
    private PlayPauseDrawable mFabDrawable;
    private boolean mFabShouldResume;
    private ArtistTracksFragment mArtistTracksFragment;
    private ArtistInfoFragment mArtistInfoFragment;
    private ArtistSimilarFragment mArtistSimilarFragment;
    private FloatingActionButton mFabPlay;
    private RecyclingBitmapDrawable mLogoBitmap;
    private ImageView mHeroImageView;

    private Runnable mUpdateAlbumsRunnable = new Runnable() {
        @Override
        public void run() {
            ProviderAggregator aggregator = ProviderAggregator.getDefault();
            mArtist = aggregator.retrieveArtist(mArtist.getRef(), mArtist.getProvider());

            mArtistTracksFragment.loadRecommendation();
            mArtistTracksFragment.loadAlbums(false);
        }
    };


    private BasePlaybackCallback mPlaybackCallback = new BasePlaybackCallback() {
        @Override
        public void onSongStarted(final boolean buffering, Song s) throws RemoteException {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                    mFabDrawable.setBuffering(buffering);
                }
            });
        }

        @Override
        public void onPlaybackPause() throws RemoteException {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
                    mFabDrawable.setBuffering(false);
                }
            });
        }

        @Override
        public void onPlaybackResume() throws RemoteException {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                    mFabDrawable.setBuffering(false);
                }
            });
        }
    };

    /**
     * Class handling the ViewPager for the tabs
     */
    private class ViewPagerAdapter extends FragmentPagerAdapter {
        private boolean mHasRosetta;

        public ViewPagerAdapter(FragmentManager fm) {
            super(fm);
            // Depending on whether we have a rosetta-enabled provider or not, we will
            // show or not the Similar tab.
            mHasRosetta = ProviderAggregator.getDefault().getRosettaStonePrefix().size() > 0;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Fragment getItem(int i) {
            // We're deliberately not using constants in this switch because values may be two
            // different things.
            switch (i) {
                case 0: // Tracks tab
                    return mArtistTracksFragment;

                case 1: // Either similar, or biography tab
                    if (mHasRosetta) {
                        return mArtistSimilarFragment;
                    } else {
                        return mArtistInfoFragment;
                    }

                case 2: // Biography tab in case similar is enabled
                    return mArtistInfoFragment;
            }

            // should never happen
            throw new IllegalStateException("We should never be here, i=" + i);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public CharSequence getPageTitle(int position) {
            if (position == 0) {
                return getString(R.string.tracks).toUpperCase();
            } else if (position == 1) {
                if (mHasRosetta) {
                    return getString(R.string.Similar).toUpperCase();
                } else {
                    return getString(R.string.biography).toUpperCase();
                }
            } else if (position == 2) {
                return getString(R.string.biography).toUpperCase();
            }

            return "Error";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getCount() {
            // Similar only works if we have one provider supporting Rosetta Stone
            if (mHasRosetta) {
                return FRAGMENT_COUNT;
            } else {
                return FRAGMENT_COUNT - 1;
            }
        }
    }

    /**
     * Album art helper for each entry in the Tracks view
     */
    private static class AlbumArtLoadListener implements AlbumArtImageView.OnArtLoadedListener {
        private View mRootView;
        private Handler mHandler;

        public AlbumArtLoadListener(View rootView) {
            mRootView = rootView;
            mHandler = new Handler();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onArtLoaded(AlbumArtImageView view, BitmapDrawable drawable) {
            if (drawable == null || drawable.getBitmap() == null) {
                return;
            }

            Palette.from(drawable.getBitmap()).generate(new Palette.PaletteAsyncListener() {
                @Override
                public void onGenerated(Palette palette) {
                    Palette.Swatch vibrant = palette.getVibrantSwatch();

                    if (vibrant != null && mRootView != null) {
                        mRootView.setBackgroundColor(vibrant.getRgb());

                        float luminance = vibrant.getHsl()[2];

                        final TextView tvArtist = (TextView) mRootView.findViewById(R.id.tvArtistSuggestionArtist);
                        final TextView tvTitle = (TextView) mRootView.findViewById(R.id.tvArtistSuggestionTitle);
                        final Button btnPlay = (Button) mRootView.findViewById(R.id.btnArtistSuggestionPlay);

                        final int color = luminance < 0.6f ? 0xFFFFFFFF : 0xFF333333;

                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                tvArtist.setTextColor(color);
                                tvTitle.setTextColor(color);
                                btnPlay.setTextColor(color);
                            }
                        });
                    }
                }
            });

        }
    }

    /**
     * Click listener on Album group entries
     */
    private static class AlbumGroupClickListener implements View.OnClickListener {
        private Album mAlbum;
        private LinearLayout mContainer;
        private LinearLayout mItemHost;
        private View mHeader;
        private boolean mOpen;
        private View mHeaderDivider;
        private View mLastItemDivider;
        private ParallaxScrollView mRootView;
        private Context mContext;
        private ArtistTracksFragment mTracksFragment;
        private Handler mHandler;

        public AlbumGroupClickListener(Album a, ParallaxScrollView rootView,
                                       LinearLayout container, View header,
                                       ArtistTracksFragment tracksFragment) {
            mAlbum = a;
            mRootView = rootView;
            mContainer = container;
            mOpen = false;
            mHeader = header;
            mContext = header.getContext();
            mTracksFragment = tracksFragment;
            mHandler = new Handler();

            mHeaderDivider = header.findViewById(R.id.divider);
            mHeaderDivider.setVisibility(View.VISIBLE);
            mHeaderDivider.setAlpha(0.0f);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onClick(View view) {
            toggle();
        }

        /**
         * Toggles an album group visibility
         */
        public void toggle() {
            if (mOpen) {
                mItemHost.startAnimation(Utils.animateExpand(mItemHost, false));
                mOpen = false;

                mLastItemDivider.animate().alpha(0.0f).setDuration(ANIMATION_DURATION)
                        .setInterpolator(mInterpolator).start();
                mHeaderDivider.animate().alpha(0.0f).setDuration(ANIMATION_DURATION)
                        .setInterpolator(mInterpolator).start();
            } else {
                if (mItemHost == null) {
                    mItemHost = new LinearLayout(mContext);
                    mItemHost.setOrientation(LinearLayout.VERTICAL);

                    // We insert the view below the group
                    int index = ((LinearLayout) mHeader.getParent()).indexOfChild(mHeader);
                    mContainer.addView(mItemHost, index + 1);

                    mTracksFragment.showAlbumTracks(mAlbum, mItemHost);

                    // Add the divider at the end
                    LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    mLastItemDivider = inflater.inflate(R.layout.divider, mItemHost, false);
                    mItemHost.addView(mLastItemDivider);
                }
                mItemHost.startAnimation(Utils.animateExpand(mItemHost, true));
                mHeaderDivider.animate().alpha(1.0f).setDuration(ANIMATION_DURATION)
                        .setInterpolator(mInterpolator).start();

                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mRootView.smoothScrollBy(0, Utils.dpToPx(mContext.getResources(), 240));
                    }
                }, 300);


                mOpen = true;
            }
        }
    }

    /**
     * Default constructor
     */
    public ArtistFragment() {
        mFabShouldResume = false;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void notifyClosing() {
        Utils.animateScale(mFabPlay, true, false);
        final TextView tvArtist = (TextView) mRootView.findViewById(R.id.tvArtist);
        final PagerTabStrip strip = (PagerTabStrip) mRootView.findViewById(R.id.pagerArtistStrip);

        if (!Utils.hasLollipop()) {
            tvArtist.animate().alpha(0.0f).setStartDelay(0).setDuration(ArtistActivity.BACK_DELAY).start();
        }
        strip.animate().alpha(0.0f).setStartDelay(0).translationY(-20).setDuration(ArtistActivity.BACK_DELAY).start();
    }

    public void scrollToTop() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mRootView.smoothScrollTo(0, 0);
            }
        });
    }

    /**
     * Returns a view in the fragment
     *
     * @param id The layout item ID
     * @return The view if found, null otherwise
     */
    public View findViewById(int id) {
        return mRootView.findViewById(id);
    }

    /**
     * Sets the main arguments for the fragment
     *
     * @param hero   The hero header image bitmap
     * @param extras The intent bundle extras
     */
    public void setArguments(Bitmap hero, Bundle extras) {
        mHeroImage = hero;
        mBackgroundColor = extras.getInt(ArtistActivity.EXTRA_BACKGROUND_COLOR, 0xFF333333);
        final String artistRef = extras.getString(ArtistActivity.EXTRA_ARTIST);
        final ProviderIdentifier provider = extras.getParcelable(ArtistActivity.EXTRA_PROVIDER);
        mArtist = ProviderAggregator.getDefault().retrieveArtist(artistRef, provider);

        if (mArtist == null) {
            Log.e(TAG, "No cache entry or provider hit for " + artistRef + "!");
            throw new IllegalStateException("Artist is null in ArtistFragment arguments!");
        }

        // Prepare the palette to colorize the FAB
        if (mHeroImage != null) {
            generateHeroPalette();
        }
    }

    private void generateHeroPalette() {
        if (mHeroImage != null && !mHeroImage.isRecycled()) {
            Palette.from(mHeroImage).generate(new Palette.PaletteAsyncListener() {
                @Override
                public void onGenerated(final Palette palette) {
                    final Palette.Swatch normalColor = palette.getDarkMutedSwatch();
                    if (normalColor != null && mRootView != null) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (mRootView != null) {
                                    final Palette.Swatch pressedColor = palette.getDarkVibrantSwatch();
                                    mFabPlay.setNormalColor(normalColor.getRgb());
                                    if (pressedColor != null) {
                                        mFabPlay.setPressedColor(pressedColor.getRgb());
                                    } else {
                                        mFabPlay.setPressedColor(normalColor.getRgb());
                                    }
                                }
                            }
                        });
                    }
                }
            });
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mHandler = new Handler();

        // Setup the inside fragments
        mArtistTracksFragment = new ArtistTracksFragment();
        mArtistTracksFragment.setParentFragment(this);

        mArtistInfoFragment = new ArtistInfoFragment();
        mArtistInfoFragment.setArguments(mArtist);

        mArtistSimilarFragment = new ArtistSimilarFragment();
        mArtistSimilarFragment.setArguments(mArtist);

        // Inflate the main fragment view
        mRootView = (ParallaxScrollView) inflater.inflate(R.layout.fragment_artist, container, false);

        // Set the hero image and artist from arguments
        mHeroImageView = (ImageView) mRootView.findViewById(R.id.ivHero);
        if (mHeroImage != null) {
            mHeroImageView.setImageBitmap(mHeroImage);

            // The hero image that comes from a transition might be low in quality, so load
            // the higher quality and fade it in
            loadArt(false);
        } else {
            // Display placeholder and try to get the real art
            mHeroImageView.setImageResource(R.drawable.album_placeholder);
            loadArt(true);
        }


        final TextView tvArtist = (TextView) mRootView.findViewById(R.id.tvArtist);
        tvArtist.setBackgroundColor(mBackgroundColor);
        tvArtist.setText(mArtist.getName());

        final PagerTabStrip strip = (PagerTabStrip) mRootView.findViewById(R.id.pagerArtistStrip);
        strip.setDrawFullUnderline(false);
        strip.setAlpha(0.0f);
        strip.setTranslationY(-20);
        strip.animate().alpha(1.0f).setDuration(ANIMATION_DURATION).setStartDelay(500).translationY(0).start();

        if (!Utils.hasLollipop()) {
            tvArtist.setAlpha(0);
            tvArtist.animate().alpha(1).setDuration(ANIMATION_DURATION).setStartDelay(500).start();
        }

        // Setup the subfragments pager
        final WrapContentHeightViewPager pager = (WrapContentHeightViewPager) mRootView.findViewById(R.id.pagerArtist);
        pager.setAdapter(new ViewPagerAdapter(getChildFragmentManager()));
        pager.setOffscreenPageLimit(FRAGMENT_COUNT);


        pager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int i) {
                if (mRootView.getScrollY() > tvArtist.getTop()) {
                    mRootView.smoothScrollTo(0, tvArtist.getTop());
                }
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        pager.setMinimumHeight(500);
                        pager.requestLayout();
                    }
                });


                boolean hasRosetta = ProviderAggregator.getDefault().getRosettaStonePrefix().size() > 0;

                if (hasRosetta) {
                    if (i == FRAGMENT_ID_BIOGRAPHY) {
                        mArtistInfoFragment.notifyActive();
                    } else if (i == FRAGMENT_ID_SIMILAR) {
                        mArtistSimilarFragment.notifyActive();
                    }
                } else {
                    if (i == FRAGMENT_ID_SIMILAR) {
                        // This is actually BIOGRAPHY if rosetta is not available
                        mArtistInfoFragment.notifyActive();
                    }
                }
            }

            @Override
            public void onPageScrollStateChanged(int i) {
            }
        });

        mRootView.setOnScrollListener(new ObservableScrollView.ScrollViewListener() {
            @Override
            public void onScroll(ObservableScrollView scrollView, int x, int y, int oldx, int oldy) {
                final ActionBar ab = ((AppActivity) getActivity()).getSupportActionBar();
                if (ab != null) {
                    if (y >= tvArtist.getTop()) {
                        ab.hide();
                    } else {
                        ab.show();
                    }
                }
            }
        });

        // Setup the source logo
        final ImageView ivSource = (ImageView) mRootView.findViewById(R.id.ivSourceLogo);
        mLogoBitmap = PluginsLookup.getDefault().getCachedLogo(getResources(), mArtist);
        ivSource.setImageDrawable(mLogoBitmap);

        // Outline is required for the FAB shadow to be actually oval
        mFabPlay = (FloatingActionButton) mRootView.findViewById(R.id.fabPlay);
        showFab(false, false);

        // Set the FAB animated drawable
        mFabDrawable = new PlayPauseDrawable(getResources(), 1);
        mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
        mFabDrawable.setYOffset(6);

        final Song currentTrack = PlaybackProxy.getCurrentTrack();
        if (currentTrack != null && currentTrack.getArtist() != null
                && currentTrack.getArtist().equals(mArtist.getRef())) {
            int state = PlaybackProxy.getState();
            if (state == PlaybackService.STATE_PLAYING) {
                mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
            } else if (state == PlaybackService.STATE_PAUSED) {
                mFabShouldResume = true;
            } else if (state == PlaybackService.STATE_BUFFERING) {
                mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
                mFabDrawable.setBuffering(true);
                mFabShouldResume = true;
            } else if (state == PlaybackService.STATE_PAUSING) {
                mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                mFabDrawable.setBuffering(true);
                mFabShouldResume = true;
            }
        }

        mFabPlay.setImageDrawable(mFabDrawable);
        mFabPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mFabDrawable.getCurrentShape() == PlayPauseDrawable.SHAPE_PLAY) {
                    if (mFabShouldResume) {
                        PlaybackProxy.play();
                    } else {
                        mArtistTracksFragment.playRecommendation();
                    }
                } else {
                    mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                    mFabShouldResume = true;
                    PlaybackProxy.pause();
                }
            }
        });

        return mRootView;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onResume() {
        super.onResume();

        // Register for updates
        PlaybackProxy.addCallback(mPlaybackCallback);
        ProviderAggregator.getDefault().addUpdateCallback(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPause() {
        super.onPause();

        mHandler.removeCallbacks(mUpdateAlbumsRunnable);

        // Unregister callbacks
        PlaybackProxy.removeCallback(mPlaybackCallback);
        ProviderAggregator.getDefault().removeUpdateCallback(this);
    }

    /**
     * Shows or hides the play Floating Action Button
     *
     * @param animate Whether or not to animate the transition
     * @param visible Whether or not to display the button
     */
    private void showFab(final boolean animate, final boolean visible) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Utils.animateScale(mFabPlay, animate, visible);
            }
        });
    }

    /**
     * Sets the play FAB drawable shape
     *
     * @param shape The shape to display (one of {@link com.fastbootmobile.encore.app.ui.PlayPauseDrawable}
     *              constants
     */
    private void setFabShape(int shape) {
        mFabDrawable.setShape(shape);
    }

    /**
     * Sets whether or not tapping the FAB in "Play" shape will start playing from scratch or resume
     * the current playback.
     *
     * @param shouldResume True to resume, false to play from scratch
     */
    private void setFabShouldResume(boolean shouldResume) {
        mFabShouldResume = shouldResume;
    }

    /**
     * @return The artist displayed by this fragment
     */
    public Artist getArtist() {
        return mArtist;
    }

    private void loadArt(final boolean materialTransition) {
        AlbumArtHelper.retrieveAlbumArt(getResources(), new AlbumArtHelper.AlbumArtListener() {
            @Override
            public void onArtLoaded(RecyclingBitmapDrawable output, BoundEntity request) {
                try {
                    if (output != null && !isDetached()) {
                        mHeroImage = output.getBitmap();

                        if (materialTransition) {
                            MaterialTransitionDrawable mtd = new MaterialTransitionDrawable(
                                    (BitmapDrawable) getResources().getDrawable(R.drawable.ic_cloud_offline),
                                    (BitmapDrawable) getResources().getDrawable(R.drawable.album_placeholder));
                            mtd.transitionTo(output);

                            mHeroImageView.setImageDrawable(mtd);
                        } else {
                            final TransitionDrawable transition = new TransitionDrawable(new Drawable[]{
                                    mHeroImageView.getDrawable(),
                                    output
                            });

                            // Make sure the transition happens after the activity animation is done,
                            // otherwise weird sliding occurs.
                            mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    mHeroImageView.setImageDrawable(transition);
                                    transition.startTransition(500);
                                }
                            }, 600);
                        }

                        generateHeroPalette();
                    }
                } catch (IllegalStateException ignore) {
                    // We might have left the activity, so go on
                }
            }
        }, mArtist, -1, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSongUpdate(List<Song> s) {
        boolean hasThisArtist = false;
        for (Song song : s) {
            if (mArtist.getRef().equals(song.getArtist())) {
                hasThisArtist = true;
                break;
            }
        }

        if (hasThisArtist) {
            // TODO: Instead of updating everything, update only the relevant entries
            mHandler.removeCallbacks(mUpdateAlbumsRunnable);
            mHandler.postDelayed(mUpdateAlbumsRunnable, 300);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAlbumUpdate(List<Album> a) {
        boolean hasThisArtist = false;
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();
        for (Album album : a) {
            Iterator<String> songs = album.songs();
            while (songs.hasNext()) {
                String songRef = songs.next();
                Song song = aggregator.retrieveSong(songRef, album.getProvider());
                if (song != null && mArtist != null && mArtist.getRef().equals(song.getArtist())) {
                    hasThisArtist = true;
                    break;
                }
            }

            if (hasThisArtist) {
                break;
            }
        }

        if (hasThisArtist) {
            // TODO: Instead of updating everything, update only the relevant entries
            mHandler.removeCallbacks(mUpdateAlbumsRunnable);
            mHandler.postDelayed(mUpdateAlbumsRunnable, 300);
        }
    }

    @Override
    public void onPlaylistUpdate(List<Playlist> p) {
    }

    @Override
    public void onPlaylistRemoved(String ref) {
    }

    @Override
    public void onArtistUpdate(final List<Artist> a) {
        if (a.contains(mArtist)) {
            mHandler.removeCallbacks(mUpdateAlbumsRunnable);
            mHandler.postDelayed(mUpdateAlbumsRunnable, 300);
        }
        mArtistSimilarFragment.notifyArtistUpdate(a);
    }

    @Override
    public void onProviderConnected(IMusicProvider provider) {
    }

    @Override
    public void onSearchResult(List<SearchResult> searchResult) {
    }

    /**
     * Class representing the inner fragment displaying albums and tracks
     */
    public static class ArtistTracksFragment extends Fragment {
        private Song mRecommendedSong;
        private boolean mRecommendationLoaded = false;
        private View mRootView;
        private ArtistFragment mParent;
        private HashMap<Song, View> mSongToViewMap = new HashMap<>();
        private HashMap<String, View> mAlbumToViewMap = new HashMap<>();
        private View mPreviousSongGroup;
        private View mPreviousAlbumGroup;
        private TextView mOfflineView;
        private Handler mHandler;


        private Comparator<Album> mComparator = new Comparator<Album>() {
            @Override
            public int compare(Album album, Album album2) {
                if (album.getYear() != album2.getYear()) {
                    return album.getYear() < album2.getYear() ? 1 : -1;
                } else if (album.getName() != null && album2.getName() != null) {
                    return album.getName().compareTo(album2.getName());
                } else {
                    return album.getRef().compareTo(album2.getRef());
                }
            }
        };

        private View.OnClickListener mPlayAlbumClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final ProviderAggregator aggregator = ProviderAggregator.getDefault();
                final AlbumViewHolder tag = (AlbumViewHolder) view.getTag();
                final Album album = tag.album;
                final PlayPauseDrawable drawable = (PlayPauseDrawable) tag.ivPlayAlbum.getDrawable();

                if (mPreviousAlbumGroup != null) {
                    ImageView ivPlayAlbum = (ImageView) mPreviousAlbumGroup.findViewById(R.id.ivPlayAlbum);
                    PlayPauseDrawable existingDrawable = (PlayPauseDrawable) ivPlayAlbum.getDrawable();
                    existingDrawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
                }

                mPreviousAlbumGroup = tag.vRoot;

                if (drawable.getRequestedShape() == PlayPauseDrawable.SHAPE_STOP) {
                    drawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
                    mParent.setFabShape(PlayPauseDrawable.SHAPE_PLAY);
                    PlaybackProxy.pause();
                } else {
                    drawable.setShape(PlayPauseDrawable.SHAPE_STOP);
                    mParent.setFabShape(PlayPauseDrawable.SHAPE_PAUSE);
                    PlaybackProxy.playAlbum(album);

                    // Bold the corresponding track
                    final Iterator<String> songs = album.songs();
                    if (songs.hasNext()) {
                        final String songRef = songs.next();
                        final Song s = aggregator.retrieveSong(songRef, album.getProvider());
                        boldPlayingTrack(s);
                    }
                }
            }
        };

        /**
         * View holder for items in this class
         */
        private class AlbumViewHolder {
            TextView tvAlbumName;
            TextView tvAlbumYear;
            AlbumArtImageView ivCover;
            ImageView ivPlayAlbum;
            Album album;
            View vRoot;

            AlbumViewHolder(View viewRoot) {
                vRoot = viewRoot;
                tvAlbumName = (TextView) viewRoot.findViewById(R.id.tvAlbumName);
                tvAlbumYear = (TextView) viewRoot.findViewById(R.id.tvAlbumYear);
                ivCover = (AlbumArtImageView) viewRoot.findViewById(R.id.ivCover);
                ivPlayAlbum = (ImageView) viewRoot.findViewById(R.id.ivPlayAlbum);
            }
        }

        private View.OnClickListener mSongClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final ProviderAggregator aggregator = ProviderAggregator.getDefault();

                final Song song = (Song) view.getTag();
                Album album = null;
                if (song.getAlbum() != null) {
                    album = aggregator.retrieveAlbum(song.getAlbum(), song.getProvider());
                }

                if (Utils.canPlaySong(song)) {
                    // Queue the full album, if possible
                    PlaybackProxy.clearQueue();
                    if (album != null) {
                        PlaybackProxy.queueAlbum(album, false);

                        // Find the clicked song index and play it
                        Iterator<String> songs = album.songs();
                        int i = 0;
                        while (songs.hasNext()) {
                            String songRef = songs.next();
                            if (songRef.equals(song.getRef())) {
                                break;
                            } else {
                                ++i;
                            }
                        }

                        PlaybackProxy.playAtIndex(i);
                    } else {
                        // We have no album information, just play the song
                        PlaybackProxy.playSong(song);
                    }

                    // Update UI
                    boldPlayingTrack(song);
                    updatePlayingAlbum(song.getAlbum());
                }
            }
        };

        /**
         * {@inheritDoc}
         */
        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            mRootView = inflater.inflate(R.layout.fragment_artist_tracks, container, false);
            mHandler = new Handler();

            mOfflineView = (TextView) mRootView.findViewById(R.id.tvErrorMessage);
            mOfflineView.setText(R.string.error_artist_unavailable_offline);
            mOfflineView.setVisibility(View.GONE);

            return mRootView;
        }

        @Override
        public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            // Load recommendation and albums for the first
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    loadRecommendation();
                    loadAlbums(true);
                }
            });
        }

        /**
         * Sets the parent fragment
         *
         * @param parent The parent fragment
         */
        public void setParentFragment(ArtistFragment parent) {
            mParent = parent;
        }

        /**
         * Sets whether or not to show the loading spinner
         *
         * @param show True to show, false otherwise
         */
        private void showLoadingSpinner(final boolean show) {
            final ProgressBar pb = (ProgressBar) mRootView.findViewById(R.id.pbArtistLoading);
            if (show && pb.getVisibility() != View.VISIBLE
                    || !show && pb.getVisibility() != View.GONE) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        pb.setVisibility(show ? View.VISIBLE : View.GONE);
                    }
                });
            }
        }

        /**
         * Play the artist radio
         */
        private void playRecommendation() {
            if (mRecommendationLoaded && mRecommendedSong != null) {
                // Generate radio tracks
                List<Song> tracks = Suggestor.getInstance().buildArtistRadio(mParent.getArtist());
                PlaybackProxy.clearQueue();

                // Add the recommended song itself first, then the generated tracks
                PlaybackProxy.queueSong(mRecommendedSong, true);

                for (Song song : tracks) {
                    PlaybackProxy.queueSong(song, false);
                }

                // And play!
                PlaybackProxy.playAtIndex(0);
                mParent.setFabShape(PlayPauseDrawable.SHAPE_PAUSE);
                mParent.setFabShouldResume(true);

                // Update UI indicators
                boldPlayingTrack(mRecommendedSong);
                updatePlayingAlbum(mRecommendedSong.getAlbum());
            }
        }

        /**
         * Load the recommended track
         */
        private synchronized void loadRecommendation() {
            if (mRecommendationLoaded || mParent == null || mRootView == null) {
                // Already loaded or parent not loaded yet or view not loaded yet
                return;
            }

            final ProviderAggregator aggregator = ProviderAggregator.getDefault();

            // Load a song from the Suggestor and display it
            Song recommended = Suggestor.getInstance().suggestBestForArtist(mParent.getArtist());
            if (recommended != null) {
                mRecommendedSong = recommended;
                Album album = aggregator.retrieveAlbum(recommended.getAlbum(), recommended.getProvider());

                CardView cvRec = (CardView) mRootView.findViewById(R.id.cardArtistSuggestion);
                TextView tvTitle = (TextView) mRootView.findViewById(R.id.tvArtistSuggestionTitle);
                TextView tvArtist = (TextView) mRootView.findViewById(R.id.tvArtistSuggestionArtist);
                Button btnPlayNow = (Button) mRootView.findViewById(R.id.btnArtistSuggestionPlay);
                tvTitle.setText(recommended.getTitle());

                if (album != null) {
                    tvArtist.setText(getString(R.string.from_the_album, album.getName()));
                } else {
                    tvArtist.setText("");
                }

                AlbumArtImageView ivCov = (AlbumArtImageView) mRootView.findViewById(R.id.ivArtistSuggestionCover);
                ivCov.setOnArtLoadedListener(new AlbumArtLoadListener(cvRec));
                if (recommended.getAlbum() != null) {
                    ivCov.loadArtForAlbum(aggregator.retrieveAlbum(recommended.getAlbum(), recommended.getProvider()));
                }

                // If we were gone, animate in
                if (cvRec.getVisibility() == View.GONE) {
                    cvRec.setVisibility(View.VISIBLE);
                    cvRec.setAlpha(0.0f);
                    cvRec.animate().alpha(1.0f).setDuration(ANIMATION_DURATION)
                            .setInterpolator(mInterpolator).start();

                    View suggestionTitle = mRootView.findViewById(R.id.tvArtistSuggestionNote);
                    suggestionTitle.setVisibility(View.VISIBLE);
                    suggestionTitle.setAlpha(0.0f);
                    suggestionTitle.animate().alpha(1.0f).setDuration(ANIMATION_DURATION)
                            .setInterpolator(mInterpolator).start();
                }

                btnPlayNow.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        playRecommendation();
                    }
                });

                mRecommendationLoaded = true;
            } else {
                mRootView.findViewById(R.id.cardArtistSuggestion).setVisibility(View.GONE);
                mRootView.findViewById(R.id.tvArtistSuggestionNote).setVisibility(View.GONE);
                mRecommendationLoaded = false;
            }
        }

        /**
         * Displays a toast
         *
         * @param string A string resource of the text to display
         */
        private void postToast(@StringRes final int string) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Utils.shortToast(getActivity(), string);
                }
            });
        }

        /**
         * Fetch the albums from the provider
         */
        private void fetchAlbums() {
            new Thread() {
                public void run() {
                    ProviderIdentifier pi = mParent.getArtist().getProvider();
                    ProviderConnection pc = PluginsLookup.getDefault().getProvider(pi);
                    if (pc != null) {
                        IMusicProvider provider = pc.getBinder();
                        if (provider != null) {
                            try {
                                boolean hasMore = provider.fetchArtistAlbums(mParent.getArtist().getRef());
                                showLoadingSpinner(hasMore && !ProviderAggregator.getDefault().isOfflineMode());
                                mParent.showFab(true, !hasMore);
                            } catch (RemoteException e) {
                                Log.e(TAG, "Unable to fetch artist albums", e);
                                postToast(R.string.plugin_error);
                            }
                        } else {
                            showLoadingSpinner(false);
                            mParent.showFab(true, true);
                            Log.e(TAG, "Provider is null, cannot fetch albums");
                            postToast(R.string.plugin_error);
                        }
                    } else {
                        showLoadingSpinner(false);
                        mParent.showFab(true, true);
                        Log.e(TAG, "ProviderConnection is null, cannot fetch albums");
                        postToast(R.string.plugin_error);
                    }
                }
            }.start();
        }

        /**
         * Load and display albums
         *
         * @param request Whether or not to fetch albums from the provider
         */
        private void loadAlbums(final boolean request) {
            if (request) {
                // Make sure we loaded all the albums for that artist
                fetchAlbums();
            }

            if (mRootView == null && mHandler != null) {
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        loadAlbums(request);
                    }
                }, 200);
                return;
            }

            // Check if we're offline, and if we have nothing to show, then show the offline error
            final ProviderAggregator aggregator = ProviderAggregator.getDefault();
            final LinearLayout llAlbums = (LinearLayout) mRootView.findViewById(R.id.llAlbums);
            llAlbums.removeAllViews();

            Iterator<String> albumIt = new ArrayList<>(mParent.getArtist().getAlbums()).iterator();
            List<Album> albums = new ArrayList<>();


            if (aggregator.isOfflineMode() && !albumIt.hasNext()) {
                mOfflineView.setVisibility(View.VISIBLE);
                mRootView.findViewById(R.id.tvHeaderAlbums).setVisibility(View.GONE);
            } else {
                mOfflineView.setVisibility(View.GONE);
                mRootView.findViewById(R.id.tvHeaderAlbums).setVisibility(View.VISIBLE);

                while (albumIt.hasNext()) {
                    String albumRef = albumIt.next();
                    Album album = aggregator.retrieveAlbum(albumRef, mParent.getArtist().getProvider());

                    if (album != null) {
                        ProviderConnection conn = PluginsLookup.getDefault().getProvider(album.getProvider());
                        if (conn != null) {
                            IMusicProvider provider = conn.getBinder();
                            try {
                                if (provider != null) {
                                    provider.fetchAlbumTracks(albumRef);
                                }
                            } catch (RemoteException e) {
                                Log.e(TAG, "Remote exception while trying to fetch album tracks", e);
                            }
                            albums.add(album);
                        }
                    }
                }
            }

            // Sort it from album names
            try {
                Collections.sort(albums, mComparator);
            } catch (IllegalArgumentException ignore) {
            }

            // Then inflate views
            final LayoutInflater inflater = getActivity().getLayoutInflater();
            for (final Album album : albums) {
                AlbumViewHolder holder;
                View viewRoot;
                if (mAlbumToViewMap.containsKey(album.getRef())) {
                    viewRoot = mAlbumToViewMap.get(album.getRef());
                    holder = (AlbumViewHolder) viewRoot.getTag();
                    holder.album = album;
                    llAlbums.addView(viewRoot);

                    AlbumGroupClickListener listener =
                            new AlbumGroupClickListener(album, mParent.mRootView, llAlbums, viewRoot, this);
                    viewRoot.setOnClickListener(listener);
                } else {
                    viewRoot = inflater.inflate(R.layout.exp_group_albums, llAlbums, false);
                    llAlbums.addView(viewRoot);

                    holder = new AlbumViewHolder(viewRoot);
                    holder.album = album;
                    viewRoot.setTag(holder);
                    holder.ivPlayAlbum.setTag(holder);

                    AlbumGroupClickListener listener =
                            new AlbumGroupClickListener(album, mParent.mRootView, llAlbums, viewRoot, this);
                    viewRoot.setOnClickListener(listener);

                    final PlayPauseDrawable drawable = new PlayPauseDrawable(getResources(), 1);
                    drawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
                    drawable.setColor(0xCC333333);
                    holder.ivPlayAlbum.setImageDrawable(drawable);
                    holder.ivPlayAlbum.setOnClickListener(mPlayAlbumClickListener);
                }

                // If the album is loaded, show its metadata
                if (album.isLoaded()) {
                    holder.tvAlbumName.setText(album.getName());
                    if (album.getYear() > 0) {
                        holder.tvAlbumYear.setVisibility(View.VISIBLE);
                        holder.tvAlbumYear.setText(Integer.toString(album.getYear()));
                    } else {
                        holder.tvAlbumYear.setVisibility(View.GONE);
                    }

                    holder.ivPlayAlbum.setVisibility(View.VISIBLE);

                    // Set play or pause based on if this album is playing
                    int state = PlaybackProxy.getState();
                    if (state == PlaybackService.STATE_PLAYING) {
                        Song currentSong = PlaybackProxy.getCurrentTrack();
                        if (currentSong != null && album.getRef().equals(currentSong.getAlbum())) {
                            updatePlayingAlbum(currentSong.getAlbum());
                        }
                    }
                } else {
                    // Album isn't loaded, show "Loading"
                    holder.tvAlbumName.setText(getString(R.string.loading));
                    holder.tvAlbumYear.setVisibility(View.GONE);
                    holder.ivPlayAlbum.setVisibility(View.GONE);
                }

                // Load the album art
                holder.ivCover.loadArtForAlbum(album);

                // Cache the view
                mAlbumToViewMap.put(album.getRef(), viewRoot);
            }

            // Hide loading spinner and show the play FAB as we now have stuff
            showLoadingSpinner(false);
            mParent.showFab(true, true);
        }

        /**
         * Shows the album tracks of the provided album
         *
         * @param album     The album of which display tracks
         * @param container The container in which expand views
         */
        private void showAlbumTracks(Album album, LinearLayout container) {
            Iterator<String> songsIt = album.songs();

            final LayoutInflater inflater = getActivity().getLayoutInflater();
            final ProviderAggregator aggregator = ProviderAggregator.getDefault();

            while (songsIt.hasNext()) {
                final Song song = aggregator.retrieveSong(songsIt.next(), album.getProvider());
                if (song == null) {
                    Log.w(TAG, "Null song in album!");
                    continue;
                }

                View itemRoot = inflater.inflate(R.layout.exp_item_albums, container, false);
                container.addView(itemRoot);
                itemRoot.setTag(song);

                // Set alpha based on offline availability and mode
                if ((aggregator.isOfflineMode()
                        && (song.getOfflineStatus() != BoundEntity.OFFLINE_STATUS_READY)
                        || (!song.isAvailable()))) {
                    Utils.setChildrenAlpha((ViewGroup) itemRoot,
                            Float.parseFloat(getString(R.string.unavailable_track_alpha)));
                } else {
                    Utils.setChildrenAlpha((ViewGroup) itemRoot, 1.0f);
                }

                mSongToViewMap.put(song, itemRoot);

                TextView tvTrackName = (TextView) itemRoot.findViewById(R.id.tvTrackName);
                TextView tvTrackDuration = (TextView) itemRoot.findViewById(R.id.tvTrackDuration);
                final ImageView ivOverflow = (ImageView) itemRoot.findViewById(R.id.ivOverflow);
                final ImageView ivOffline = (ImageView) itemRoot.findViewById(R.id.ivOffline);
                ivOffline.setVisibility(View.VISIBLE);

                switch (song.getOfflineStatus()) {
                    case BoundEntity.OFFLINE_STATUS_DOWNLOADING:
                        ivOffline.setImageResource(R.drawable.ic_sync_in_progress);
                        break;

                    case BoundEntity.OFFLINE_STATUS_ERROR:
                        ivOffline.setImageResource(R.drawable.ic_sync_problem);
                        break;

                    case BoundEntity.OFFLINE_STATUS_NO:
                        ivOffline.setVisibility(View.GONE);
                        break;

                    case BoundEntity.OFFLINE_STATUS_READY:
                        ivOffline.setImageResource(R.drawable.ic_track_downloaded);
                        break;

                    case BoundEntity.OFFLINE_STATUS_PENDING:
                        ivOffline.setImageResource(R.drawable.ic_track_download_pending);
                        break;
                }

                if (song.isLoaded()) {
                    tvTrackName.setText(song.getTitle());
                    tvTrackDuration.setText(Utils.formatTrackLength(song.getDuration()));
                    ivOverflow.setVisibility(View.VISIBLE);

                    // Set song click listener if playable
                    if (song.isAvailable()) {
                        itemRoot.setOnClickListener(mSongClickListener);
                    }

                    // Set overflow popup
                    ivOverflow.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Utils.showSongOverflow(getActivity(), ivOverflow, song, true);
                        }
                    });

                    // Bold if already playing
                    int state = PlaybackProxy.getState();
                    if (state == PlaybackService.STATE_PLAYING) {
                        Song currentSong = PlaybackProxy.getCurrentTrack();
                        if (currentSong != null && song.getRef().equals(currentSong.getRef())) {
                            boldPlayingTrack(currentSong);
                        }
                    }
                } else {
                    tvTrackName.setText(getString(R.string.loading));
                    tvTrackDuration.setText("");
                    ivOverflow.setVisibility(View.GONE);
                }
            }
        }

        /**
         * Updates the current playing album
         *
         * @param albumRef The new album being played
         */
        private void updatePlayingAlbum(String albumRef) {
            View view = mAlbumToViewMap.get(albumRef);
            ImageView ivPlayAlbum;

            if (mPreviousAlbumGroup != null) {
                ivPlayAlbum = (ImageView) mPreviousAlbumGroup.findViewById(R.id.ivPlayAlbum);
                PlayPauseDrawable drawable = (PlayPauseDrawable) ivPlayAlbum.getDrawable();
                drawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
            }

            if (view != null) {
                ivPlayAlbum = (ImageView) view.findViewById(R.id.ivPlayAlbum);
                PlayPauseDrawable drawable = (PlayPauseDrawable) ivPlayAlbum.getDrawable();
                drawable.setShape(PlayPauseDrawable.SHAPE_STOP);
            }

            mPreviousAlbumGroup = view;
        }

        /**
         * Finds and bolds the track that is currently playing, if we have it
         *
         * @param s The song that is currently playing
         */
        private void boldPlayingTrack(Song s) {
            View view = mSongToViewMap.get(s);

            TextView tvTrackName, tvTrackDuration;

            if (mPreviousSongGroup != null) {
                tvTrackName = (TextView) mPreviousSongGroup.findViewById(R.id.tvTrackName);
                tvTrackDuration = (TextView) mPreviousSongGroup.findViewById(R.id.tvTrackDuration);
                tvTrackName.setTypeface(null, Typeface.NORMAL);
                tvTrackDuration.setTypeface(null, Typeface.NORMAL);
            }

            if (view != null) {
                tvTrackName = (TextView) view.findViewById(R.id.tvTrackName);
                tvTrackDuration = (TextView) view.findViewById(R.id.tvTrackDuration);
                tvTrackName.setTypeface(null, Typeface.BOLD);
                tvTrackDuration.setTypeface(null, Typeface.BOLD);
            }

            mPreviousSongGroup = view;
        }
    }

    /**
     * Fragment showing the Artist's biography
     */
    public static class ArtistInfoFragment extends Fragment {
        private static Artist mArtist;
        private Handler mHandler;
        private TextView mArtistInfo;
        private TextView mOfflineView;
        private boolean mInfoLoaded;
        private ProgressBar mLoadingSpinner;

        /**
         * Default constructor
         */
        public ArtistInfoFragment() {
            mHandler = new Handler();
            mInfoLoaded = false;
        }

        /**
         * Sets the active artist for which get the biography
         *
         * @param artist The artist displayed
         */
        public void setArguments(Artist artist) {
            mArtist = artist;
        }

        /**
         * Notifies that the fragment is currently active, and that data should be populated.
         */
        public void notifyActive() {
            if (!mInfoLoaded) {
                if (ProviderAggregator.getDefault().isOfflineMode()) {
                    mOfflineView.setVisibility(View.VISIBLE);
                    mLoadingSpinner.setVisibility(View.GONE);
                } else {
                    mOfflineView.setVisibility(View.GONE);
                    mLoadingSpinner.setVisibility(View.VISIBLE);
                    mInfoLoaded = true;
                    new Thread() {
                        public void run() {
                            loadBiographySync();
                        }
                    }.start();
                }
            }
        }

        /**
         * Loads synchronously the biography data
         */
        private void loadBiographySync() {
            final EchoNest echoNest = new EchoNest();
            try {
                com.echonest.api.v4.Artist enArtist = echoNest.searchArtistByName(mArtist.getName());
                if (enArtist != null) {
                    final Biography bio = echoNest.getArtistBiography(enArtist);
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mLoadingSpinner != null && mArtistInfo != null && !isDetached()) {
                                mLoadingSpinner.setVisibility(View.GONE);

                                if (bio != null) {
                                    mArtistInfo.setText(getString(R.string.biography_format,
                                            bio.getText(), bio.getSite(), bio.getURL(),
                                            bio.getLicenseType(), bio.getLicenseAttribution()));
                                } else {
                                    mArtistInfo.setText(getString(R.string.no_bio_available));
                                }
                            }
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Unable to get artist information", e);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Utils.shortToast(getActivity(), R.string.unable_fetch_artist_info);
                    }
                });
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_artist_info, container, false);
            mArtistInfo = (TextView) rootView.findViewById(R.id.tvArtistInfo);
            mLoadingSpinner = (ProgressBar) rootView.findViewById(R.id.pbArtistInfo);
            mOfflineView = (TextView) rootView.findViewById(R.id.tvErrorMessage);
            mOfflineView.setText(R.string.error_biography_unavailable_offline);
            return rootView;
        }
    }

    /**
     * Fragment showing similar artists
     */
    public static class ArtistSimilarFragment extends Fragment {
        private RecyclerView mArtistsGrid;
        private Artist mArtist;
        private boolean mSimilarLoaded;
        private ArtistsAdapter mAdapter;
        private Handler mHandler;
        private List<Artist> mSimilarArtists;
        private ProgressBar mArtistsSpinner;
        private TextView mOfflineView;

        /**
         * Default constructor
         */
        public ArtistSimilarFragment() {
            mAdapter = new ArtistsAdapter();
            mHandler = new Handler();
            mSimilarArtists = new ArrayList<>();
        }

        /**
         * Sets the active artist to display
         *
         * @param artist The artist displayed
         */
        public void setArguments(Artist artist) {
            mArtist = artist;
        }

        /**
         * Notify that the fragment is active and that data should be populated
         */
        public void notifyActive() {
            Log.e(TAG, "Notify Active Similar, similarLoaded=" + mSimilarLoaded);
            if (!mSimilarLoaded) {
                if (ProviderAggregator.getDefault().isOfflineMode()) {
                    if (mOfflineView != null) mOfflineView.setVisibility(View.VISIBLE);
                    if (mArtistsSpinner != null) mArtistsSpinner.setVisibility(View.GONE);
                } else {
                    if (mOfflineView != null) mOfflineView.setVisibility(View.GONE);
                    if (mArtistsSpinner != null) mArtistsSpinner.setVisibility(View.VISIBLE);
                    mSimilarLoaded = true;
                    new Thread() {
                        public void run() {
                            loadSimilarSync();
                        }
                    }.start();
                }
            } else {
                ensureSimilar();
            }
        }

        /**
         * Called when the host fragment notifies that artists have been updated by a provider
         *
         * @param artists The artists who got updated
         */
        public void notifyArtistUpdate(final List<Artist> artists) {
            for (Artist artist : artists) {
                final int artistIndex = mSimilarArtists.indexOf(artist);
                if (artistIndex >= 0) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mAdapter.notifyDataSetChanged();
                        }
                    });
                }
            }
        }

        /**
         * Load synchronously the similar artists
         */
        public void loadSimilarSync() {
            EchoNest echoNest = new EchoNest();
            try {
                com.echonest.api.v4.Artist enArtist = echoNest.searchArtistByName(mArtist.getName());
                if (enArtist != null) {
                    List<com.echonest.api.v4.Artist> similars = echoNest.getArtistSimilar(enArtist);

                    // Retrieve the rosetta stone prefix
                    String rosettaPreferred = ProviderAggregator.getDefault().getPreferredRosettaStonePrefix();
                    ProviderIdentifier rosettaProvider = null;
                    if (rosettaPreferred != null) {
                        rosettaProvider = ProviderAggregator.getDefault().getRosettaStoneIdentifier(rosettaPreferred);
                    }

                    // For each similar artist, get the rosetta stone ID, and add it to the adapter
                    for (com.echonest.api.v4.Artist similar : similars) {
                        if (rosettaPreferred != null) {
                            String ref = echoNest.getArtistForeignID(similar, rosettaPreferred);
                            if (ref != null) {
                                Artist artist = ProviderAggregator.getDefault().retrieveArtist(ref, rosettaProvider);
                                if (artist != null) {
                                    mSimilarArtists.add(artist);
                                } else {
                                    Log.e(TAG, "Null artist for similar");
                                }
                            }
                        }
                    }

                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            ensureSimilar();
                        }
                    });
                }
            } catch (EchoNestException e) {
                Log.e(TAG, "Cannot get similar artist", e);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mOfflineView.setVisibility(View.VISIBLE);
                    }
                });

            }
        }

        private void ensureSimilar() {
            mAdapter.addAllUnique(mSimilarArtists);
            mAdapter.notifyDataSetChanged();

            if (mArtistsGrid != null && mArtistsSpinner != null) {
                mArtistsGrid.setAdapter(mAdapter);
                mArtistsSpinner.setVisibility(View.GONE);
                mArtistsGrid.setVisibility(View.VISIBLE);
                mOfflineView.setVisibility(View.GONE);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_artist_similar, container, false);
        }

        @Override
        public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            View rootView = getView();
            if (rootView != null) {
                mArtistsSpinner = (ProgressBar) rootView.findViewById(R.id.pbSimilarArtists);

                mArtistsGrid = (RecyclerView) rootView.findViewById(R.id.gridSimilarArtists);
                mArtistsGrid.setHasFixedSize(true);
                mArtistsGrid.setLayoutManager(new GridLayoutManager(view.getContext(), 2));
                mOfflineView = (TextView) rootView.findViewById(R.id.tvErrorMessage);
                mOfflineView.setText(R.string.error_similar_unavailable_offline);
            }
        }
    }
}
