package org.omnirom.music.app.fragments;

import android.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.Handler;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.support.v7.graphics.Palette;
import android.support.v7.graphics.PaletteItem;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.echonest.api.v4.Biography;
import com.echonest.api.v4.EchoNestException;

import org.lucasr.twowayview.ItemClickSupport;
import org.lucasr.twowayview.TwoWayLayoutManager;
import org.lucasr.twowayview.TwoWayView;
import org.omnirom.music.api.echonest.EchoNest;
import org.omnirom.music.app.ArtistActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.app.adapters.ArtistsAdapter;
import org.omnirom.music.app.ui.AlbumArtImageView;
import org.omnirom.music.app.ui.ExpandableHeightGridView;
import org.omnirom.music.app.ui.ObservableScrollView;
import org.omnirom.music.app.ui.ParallaxScrollView;
import org.omnirom.music.app.ui.PlayPauseDrawable;
import org.omnirom.music.app.ui.WrapContentHeightViewPager;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.framework.Suggestor;
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
import org.omnirom.music.service.IPlaybackService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * A fragment containing a simple view.
 */
public class ArtistFragment extends Fragment implements ILocalCallback {

    private static final String TAG = "ArtistFragment";
    private static final int FRAGMENT_ID_TRACKS = 0;
    private static final int FRAGMENT_ID_SIMILAR = 1;
    private static final int FRAGMENT_ID_BIOGRAPHY = 2;
    private static final int FRAGMENT_COUNT = 3;

    private static final int ANIMATION_DURATION = 500;
    private static DecelerateInterpolator mInterpolator = new DecelerateInterpolator();

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
    private ImageButton mFabPlay;

    private Runnable mUpdateAlbumsRunnable = new Runnable() {
        @Override
        public void run() {
            // FIXME: Artist object isn't getting updated with the new albums
            // Reason: Artist object is copied when serialized in the bundle. When retrieved
            // in the intent here, it's a copy with the existing attributes at that time
            ProviderAggregator aggregator = ProviderAggregator.getDefault();
            mArtist = aggregator.retrieveArtist(mArtist.getRef(), mArtist.getProvider());

            mArtistTracksFragment.loadRecommendation();
            mArtistTracksFragment.loadAlbums(false);
        }
    };

    private class ViewPagerAdapter extends FragmentPagerAdapter {

        private boolean mHasRosetta;

        public ViewPagerAdapter(FragmentManager fm) {
            super(fm);
            mHasRosetta = ProviderAggregator.getDefault().getRosettaStonePrefix().size() > 0;
        }

        @Override
        public Fragment getItem(int i) {
            switch (i) {
                case 0:
                    return mArtistTracksFragment;

                case 1:
                    if (mHasRosetta) {
                        return mArtistSimilarFragment;
                    } else {
                        return mArtistInfoFragment;
                    }

                case 2:
                    return mArtistInfoFragment;
            }

            // should never happen
            return null;
        }

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


    private static class AlbumArtLoadListener implements AlbumArtImageView.OnArtLoadedListener {
        private View mRootView;
        private Handler mHandler;

        public AlbumArtLoadListener(View rootView) {
            mRootView = rootView;
            mHandler = new Handler();
        }

        @Override
        public void onArtLoaded(AlbumArtImageView view, BitmapDrawable drawable) {
            if (drawable == null || drawable.getBitmap() == null) {
                return;
            }

            Palette.generateAsync(drawable.getBitmap(), new Palette.PaletteAsyncListener() {
                @Override
                public void onGenerated(Palette palette) {
                    PaletteItem vibrant = palette.getVibrantColor();

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

    private static class AlbumGroupClickListener implements View.OnClickListener {
        private Album mAlbum;
        private LinearLayout mContainer;
        private LinearLayout mItemHost;
        private View mHeader;
        private boolean mOpen;
        private View mHeaderDivider;
        private View mLastItemDivider;
        private Context mContext;
        private ArtistTracksFragment mTracksFragment;

        public AlbumGroupClickListener(Album a, LinearLayout container, View header,
                                       ArtistTracksFragment tracksFragment) {
            mAlbum = a;
            mContainer = container;
            mOpen = false;
            mHeader = header;
            mContext = header.getContext();
            mTracksFragment = tracksFragment;

            mHeaderDivider = header.findViewById(R.id.divider);
            mHeaderDivider.setVisibility(View.VISIBLE);
            mHeaderDivider.setAlpha(0.0f);
        }

        @Override
        public void onClick(View view) {
            toggle();
        }

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

                mOpen = true;
            }
        }
    }

    public ArtistFragment() {
        mFabShouldResume = false;
    }

    public View findViewById(int id) {
        return mRootView.findViewById(id);
    }

    public void setArguments(Bitmap hero, Bundle extras) {
        mHeroImage = hero;
        mBackgroundColor = extras.getInt(ArtistActivity.EXTRA_BACKGROUND_COLOR, 0xFF333333);
        String artistRef = extras.getString(ArtistActivity.EXTRA_ARTIST);
        mArtist = ProviderAggregator.getDefault().retrieveArtist(artistRef, null);

        if (mArtist == null) {
            Log.e(TAG, "No artist found in cache for " + artistRef + "!");
        }

        // Prepare the palette to colorize the FAB
        if (hero != null) {
            Palette.generateAsync(hero, new Palette.PaletteAsyncListener() {
                @Override
                public void onGenerated(final Palette palette) {
                    final PaletteItem color = palette.getDarkMutedColor();
                    if (color != null && mRootView != null) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Utils.colorFloatingButton(mFabPlay, color.getRgb(), true);
                            }
                        });
                    }
                }
            });
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mHandler = new Handler();
        final IPlaybackService playbackService = PluginsLookup.getDefault().getPlaybackService();

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
        ImageView heroImage = (ImageView) mRootView.findViewById(R.id.ivHero);
        heroImage.setImageBitmap(mHeroImage);

        final TextView tvArtist = (TextView) mRootView.findViewById(R.id.tvArtist);
        tvArtist.setBackgroundColor(mBackgroundColor);
        tvArtist.setText(mArtist.getName());

        // Setup the subfragments pager
        final WrapContentHeightViewPager pager = (WrapContentHeightViewPager) mRootView.findViewById(R.id.pagerArtist);
        pager.setAdapter(new ViewPagerAdapter(getActivity().getSupportFragmentManager()));
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
                pager.requestLayout();

                if (i == FRAGMENT_ID_BIOGRAPHY) {
                    mArtistInfoFragment.notifyActive();
                } else if (i == FRAGMENT_ID_SIMILAR) {
                    mArtistSimilarFragment.notifyActive();
                }
            }

            @Override
            public void onPageScrollStateChanged(int i) {

            }
        });

        PagerTabStrip strip = (PagerTabStrip) mRootView.findViewById(R.id.pagerArtistStrip);
        strip.setBackgroundColor(mBackgroundColor);

        mRootView.setOnScrollListener(new ObservableScrollView.ScrollViewListener() {
            @Override
            public void onScroll(ObservableScrollView scrollView, int x, int y, int oldx, int oldy) {
                final ActionBar ab = getActivity().getActionBar();
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
        ImageView ivSource = (ImageView) mRootView.findViewById(R.id.ivSourceLogo);
        ivSource.setImageBitmap(PluginsLookup.getDefault().getCachedLogo(mArtist));

        // Outline is required for the FAB shadow to be actually oval
        mFabPlay = (ImageButton) mRootView.findViewById(R.id.fabPlay);
        setOutlines(mFabPlay);
        Utils.setupLargeFabShadow(mFabPlay);
        showFab(false, false);

        // Set the FAB animated drawable
        mFabDrawable = new PlayPauseDrawable(getResources());
        mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PLAY);

        try {
            Song currentTrack = playbackService.getCurrentTrack();
            if (currentTrack != null && currentTrack.getArtist().equals(mArtist.getRef())) {
                if (playbackService.isPlaying()) {
                    mFabDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                } else {
                    mFabShouldResume = true;
                }
            }
        } catch (RemoteException e) {
            // ignore
        }


        mFabDrawable.setPaddingDp(48);
        mFabPlay.setImageDrawable(mFabDrawable);
        mFabPlay.setOnClickListener(new View.OnClickListener() {
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
                        mArtistTracksFragment.playRecommendation();
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

        // Register for updates
        ProviderAggregator.getDefault().addUpdateCallback(this);

        return mRootView;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ProviderAggregator.getDefault().removeUpdateCallback(this);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mHandler.removeCallbacks(mUpdateAlbumsRunnable);
    }

    private void showFab(final boolean animate, final boolean visible) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Utils.animateScale(mFabPlay, animate, visible);
            }
        });
    }

    private void setOutlines(View v) {
        Utils.setLargeFabOutline(new View[]{v});
    }

    private void setFabShape(int shape) {
        mFabDrawable.setShape(shape);
    }

    private void setFabShouldResume(boolean shouldResume) {
        mFabShouldResume = shouldResume;
    }

    public Artist getArtist() {
        return mArtist;
    }

    public static class ArtistTracksFragment extends Fragment {
        private Song mRecommendedSong;
        private boolean mRecommendationLoaded = false;
        private View mRootView;
        private static ArtistFragment mParent;
        private HashMap<Song, View> mSongToViewMap = new HashMap<Song, View>();
        private HashMap<String, View> mAlbumToViewMap = new HashMap<String, View>();
        private View mPreviousSongGroup;
        private View mPreviousAlbumGroup;
        private Handler mHandler;

        private class AlbumViewHolder {
            TextView tvAlbumName;
            TextView tvAlbumYear;
            AlbumArtImageView ivCover;
            ImageView ivPlayAlbum;

            AlbumViewHolder(View viewRoot) {
                tvAlbumName = (TextView) viewRoot.findViewById(R.id.tvAlbumName);
                tvAlbumYear = (TextView) viewRoot.findViewById(R.id.tvAlbumYear);
                ivCover = (AlbumArtImageView) viewRoot.findViewById(R.id.ivCover);
                ivPlayAlbum = (ImageView) viewRoot.findViewById(R.id.ivPlayAlbum);
            }
        }

        private View.OnClickListener mSongClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Song song = (Song) view.getTag();

                IPlaybackService pbService = PluginsLookup.getDefault().getPlaybackService();
                try {
                    pbService.playSong(song);
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to play song", e);
                    return;
                }

                // Queue remaining album tracks
                try {
                    final ProviderAggregator aggregator = ProviderAggregator.getDefault();
                    Album album = aggregator.retrieveAlbum(song.getAlbum(), song.getProvider());

                    Iterator<String> songs = album.songs();
                    boolean foundCurrent = false;
                    while (songs.hasNext()) {
                        String songRef = songs.next();

                        if (foundCurrent) {
                            pbService.queueSong(aggregator.retrieveSong(songRef, song.getProvider()), false);
                        } else if (songRef.equals(song.getRef())) {
                            foundCurrent = true;
                        }
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to queue album tracks", e);
                }

                // Update UI
                boldPlayingTrack(song);
                updatePlayingAlbum(song.getAlbum());
            }
        };

        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            mRootView = inflater.inflate(R.layout.fragment_artist_tracks, container, false);
            mHandler = new Handler();

            // Load recommendation and albums
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    loadRecommendation();
                    loadAlbums(true);
                }
            });

            return mRootView;
        }

        public void setParentFragment(ArtistFragment parent) {
            mParent = parent;
        }

        private void showLoadingSpinner(final boolean show) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ProgressBar pb = (ProgressBar) mRootView.findViewById(R.id.pbArtistLoading);
                    pb.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            });
        }

        private void playRecommendation() {
            if (mRecommendationLoaded && mRecommendedSong != null) {
                try {
                    IPlaybackService pbService = PluginsLookup.getDefault().getPlaybackService();
                    pbService.playSong(mRecommendedSong);
                    mParent.setFabShape(PlayPauseDrawable.SHAPE_PAUSE);
                    mParent.setFabShouldResume(true);

                    boldPlayingTrack(mRecommendedSong);
                    updatePlayingAlbum(mRecommendedSong.getAlbum());

                    // TODO: Figure out a better algorithm to find things to play from an artist
                    final ProviderAggregator aggregator = ProviderAggregator.getDefault();
                    pbService.queueAlbum(aggregator.retrieveAlbum(mRecommendedSong.getAlbum(), mRecommendedSong.getProvider()), false);
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to play recommended song", e);
                }
            }
        }

        private void loadRecommendation() {
            if (mRecommendationLoaded) {
                return;
            }

            final ProviderAggregator aggregator = ProviderAggregator.getDefault();

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
                ivCov.loadArtForAlbum(aggregator.retrieveAlbum(recommended.getAlbum(), recommended.getProvider()));

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

        private void postToast(final int string) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Utils.shortToast(getActivity(), string);
                }
            });
        }

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
                                showLoadingSpinner(hasMore);
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

        private void loadAlbums(boolean request) {
            if (request) {
                // Make sure we loaded all the albums for that artist
                fetchAlbums();
            }

            final LinearLayout llAlbums = (LinearLayout) mRootView.findViewById(R.id.llAlbums);
            // TODO: Recycle!
            llAlbums.removeAllViews();

            final ProviderAggregator aggregator = ProviderAggregator.getDefault();
            Iterator<String> albumIt = mParent.getArtist().albums();
            List<Album> albums = new ArrayList<Album>();

            while (albumIt.hasNext()) {
                String albumRef = albumIt.next();
                Album album = aggregator.retrieveAlbum(albumRef, mParent.getArtist().getProvider());

                if (album != null) {
                    IMusicProvider provider = PluginsLookup.getDefault().getProvider(album.getProvider()).getBinder();
                    try {
                        provider.fetchAlbumTracks(albumRef);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Remote exception while trying to fetch album tracks", e);
                    }
                    albums.add(album);
                }
            }

            // Sort it from album names
            Collections.sort(albums, new Comparator<Album>() {
                @Override
                public int compare(Album album, Album album2) {
                    if (album.getYear() != album2.getYear()) {
                        return album.getYear() < album2.getYear() ? 1 : -1;
                    } else if (album.getName() != null && album2.getName() != null) {
                        return album.getName().compareTo(album2.getName());
                    } else {
                        return 0;
                    }
                }
            });

            // Then inflate views
            final LayoutInflater inflater = getActivity().getLayoutInflater();
            final IPlaybackService pbService = PluginsLookup.getDefault().getPlaybackService();
            for (final Album album : albums) {
                AlbumViewHolder holder;
                View viewRoot;
                if (mAlbumToViewMap.containsKey(album.getRef())) {
                    viewRoot = mAlbumToViewMap.get(album.getRef());
                    holder = (AlbumViewHolder) viewRoot.getTag();
                    llAlbums.addView(viewRoot);
                } else {
                    viewRoot = inflater.inflate(R.layout.expanded_albums_group, llAlbums, false);
                    llAlbums.addView(viewRoot);

                    holder = new AlbumViewHolder(viewRoot);
                    viewRoot.setTag(holder);

                    AlbumGroupClickListener listener =
                            new AlbumGroupClickListener(album, llAlbums, viewRoot, this);
                    viewRoot.setOnClickListener(listener);

                    final PlayPauseDrawable drawable = new PlayPauseDrawable(getResources());
                    drawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
                    drawable.setColor(0xCC333333);
                    holder.ivPlayAlbum.setImageDrawable(drawable);

                    holder.ivPlayAlbum.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            final ProviderAggregator aggregator = ProviderAggregator.getDefault();
                            if (drawable.getRequestedShape() == PlayPauseDrawable.SHAPE_STOP) {
                                drawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
                                mParent.setFabShape(PlayPauseDrawable.SHAPE_PLAY);
                                new Thread() {
                                    public void run() {
                                        try {
                                            pbService.pause();
                                        } catch (RemoteException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }.start();
                            } else {
                                drawable.setShape(PlayPauseDrawable.SHAPE_STOP);
                                mParent.setFabShape(PlayPauseDrawable.SHAPE_PAUSE);
                                try {
                                    pbService.playAlbum(album);
                                } catch (DeadObjectException e) {
                                    Log.e(TAG, "Provider died while trying to play album");
                                } catch (RemoteException e) {
                                    Log.e(TAG, "Unable to play album", e);
                                }

                                // Bold the corresponding track
                                Iterator<String> songs = album.songs();
                                if (songs.hasNext()) {
                                    String songRef = songs.next();
                                    Song s = aggregator.retrieveSong(songRef, album.getProvider());
                                    boldPlayingTrack(s);
                                }
                            }
                        }
                    });
                }

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
                    try {
                        if (pbService.isPlaying()) {
                            Song currentSong = pbService.getCurrentTrack();
                            if (currentSong != null && album.getRef().equals(currentSong.getAlbum())) {
                                updatePlayingAlbum(currentSong.getAlbum());
                            }
                        }
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }


                } else {
                    holder.tvAlbumName.setText(getString(R.string.loading));
                    holder.tvAlbumYear.setVisibility(View.GONE);
                    holder.ivPlayAlbum.setVisibility(View.GONE);
                }

                // Load the album art
                holder.ivCover.loadArtForAlbum(album);

                mAlbumToViewMap.put(album.getRef(), viewRoot);
            }

            showLoadingSpinner(false);
            mParent.showFab(true, true);
        }

        private void showAlbumTracks(Album album, LinearLayout container) {
            Iterator<String> songsIt = album.songs();

            final LayoutInflater inflater = getActivity().getLayoutInflater();
            final ProviderAggregator aggregator = ProviderAggregator.getDefault();

            while (songsIt.hasNext()) {
                final Song song = aggregator.retrieveSong(songsIt.next(), album.getProvider());

                View itemRoot = inflater.inflate(R.layout.expanded_albums_item, container, false);
                container.addView(itemRoot);
                itemRoot.setTag(song);

                mSongToViewMap.put(song, itemRoot);

                TextView tvTrackName = (TextView) itemRoot.findViewById(R.id.tvTrackName);
                TextView tvTrackDuration = (TextView) itemRoot.findViewById(R.id.tvTrackDuration);
                final ImageView ivOverflow = (ImageView) itemRoot.findViewById(R.id.ivOverflow);

                if (song != null && song.isLoaded()) {
                    tvTrackName.setText(song.getTitle());
                    tvTrackDuration.setText(Utils.formatTrackLength(song.getDuration()));
                    ivOverflow.setVisibility(View.VISIBLE);

                    // Set song click listener
                    itemRoot.setOnClickListener(mSongClickListener);

                    // Set overflow popup
                    ivOverflow.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Utils.showSongOverflow(getActivity(), ivOverflow, song);
                        }
                    });

                    // Bold if already playing
                    IPlaybackService pbService = PluginsLookup.getDefault().getPlaybackService();
                    try {
                        if (pbService.isPlaying()) {
                            Song currentSong = pbService.getCurrentTrack();
                            if (currentSong != null && song.getRef().equals(currentSong.getRef())) {
                                boldPlayingTrack(currentSong);
                            }
                        }
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                } else {
                    tvTrackName.setText(getString(R.string.loading));
                    tvTrackDuration.setText("");
                    ivOverflow.setVisibility(View.GONE);
                }
            }
        }

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
            } else {
                // This is perfectly normal, if the user hasn't unwrapped an album, the views won't be
                // created.
                Log.d(TAG, "No view for track " + s.getRef());
            }

            mPreviousSongGroup = view;
        }
    }

    public static class ArtistInfoFragment extends Fragment {
        private static Artist mArtist;
        private Handler mHandler;
        private TextView mArtistInfo;
        private boolean mInfoLoaded;
        private ProgressBar mLoadingSpinner;

        public ArtistInfoFragment() {
            mHandler = new Handler();
            mInfoLoaded = false;
        }

        public void setArguments(Artist artist) {
            mArtist = artist;
        }

        public void notifyActive() {
            if (!mInfoLoaded) {
                mInfoLoaded = true;
                new Thread() {
                    public void run() {
                        loadBiographySync();
                    }
                }.start();
            }
        }

        private void loadBiographySync() {
            final EchoNest echoNest = new EchoNest();
            try {
                com.echonest.api.v4.Artist enArtist = echoNest.searchArtistByName(mArtist.getName());
                if (enArtist != null) {
                    final Biography bio = echoNest.getArtistBiography(enArtist);
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mLoadingSpinner != null && mArtistInfo != null) {
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
            } catch (EchoNestException e) {
                Log.e(TAG, "Unable to get artist information", e);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Utils.shortToast(getActivity(), R.string.unable_fetch_artist_info);
                    }
                });
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_artist_info, container, false);
            mArtistInfo = (TextView) rootView.findViewById(R.id.tvArtistInfo);
            mLoadingSpinner = (ProgressBar) rootView.findViewById(R.id.pbArtistInfo);

            EchoNest echoNest = new EchoNest();
            if (echoNest.hasArtistInCache(mArtist.getName())) {
                try {
                    com.echonest.api.v4.Artist artist = echoNest.searchArtistByName(mArtist.getName());
                    if (echoNest.hasArtistBiographyCached(artist)) {
                        loadBiographySync();
                    }
                } catch (EchoNestException e) {
                    // should not happen as we take from cache
                }
            }

            return rootView;
        }
    }

    public static class ArtistSimilarFragment extends Fragment {
        private TwoWayView mArtistsGrid;
        private Artist mArtist;
        private boolean mSimilarLoaded;
        private ArtistsAdapter mAdapter;
        private Handler mHandler;
        private List<Artist> mSimilarArtists;
        private ProgressBar mArtistsSpinner;
        private final ItemClickSupport.OnItemClickListener mItemClickListener = new ItemClickSupport.OnItemClickListener() {
            @Override
            public void onItemClick(RecyclerView parent, View view, int position, long id) {
                final ArtistsAdapter.ViewHolder tag = (ArtistsAdapter.ViewHolder) view.getTag();
                final Context ctx = view.getContext();
                String artistRef = mAdapter.getItem(tag.position).getRef();
                Intent intent = ArtistActivity.craftIntent(ctx, tag.srcBitmap, artistRef, tag.itemColor);

                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                        /*AlbumArtImageView ivCover = tag.ivCover;
                        TextView tvTitle = tag.tvTitle;
                        ActivityOptions opt = ActivityOptions.makeSceneTransitionAnimation(getActivity(),
                            new Pair<View, String>(ivCover, "itemImage"),
                            new Pair<View, String>(tvTitle, "artistName"));

                        ctx.startActivity(intent, opt.toBundle()); */
                } else {
                    ctx.startActivity(intent);
                }
            }
        };

        public ArtistSimilarFragment() {
            mAdapter = new ArtistsAdapter();
            mHandler = new Handler();
            mSimilarArtists = new ArrayList<Artist>();
        }

        public void setArguments(Artist artist) {
            mArtist = artist;
        }

        public void notifyActive() {
            if (!mSimilarLoaded) {
                mSimilarLoaded = true;
                new Thread() {
                    public void run() {
                        loadSimilarSync();
                    }
                }.start();
            }
        }

        public void notifyArtistUpdate(final List<Artist> artists) {
            for (Artist artist : artists) {
                int artistIndex = mSimilarArtists.indexOf(artist);
                if (artistIndex >= 0) {
                    mAdapter.notifyItemChanged(artistIndex);
                }
            }
        }

        public void loadSimilarSync() {
            EchoNest echoNest = new EchoNest();
            try {
                com.echonest.api.v4.Artist enArtist = echoNest.searchArtistByName(mArtist.getName());
                if (enArtist != null) {
                    List<com.echonest.api.v4.Artist> similars = echoNest.getArtistSimilar(enArtist);

                    String rosettaPreferred = ProviderAggregator.getDefault().getPreferredRosettaStonePrefix();
                    ProviderIdentifier rosettaProvider = null;
                    if (rosettaPreferred != null) {
                        rosettaProvider = ProviderAggregator.getDefault().getRosettaStoneIdentifier(rosettaPreferred);
                    }

                    for (com.echonest.api.v4.Artist similar : similars) {
                        if (rosettaPreferred != null) {
                            String ref = echoNest.getArtistForeignID(similar, rosettaPreferred);
                            Artist artist = ProviderAggregator.getDefault().retrieveArtist(ref, rosettaProvider);
                            if (artist != null) {
                                mAdapter.addItemUnique(artist);
                                mSimilarArtists.add(artist);
                                mAdapter.notifyItemInserted(mSimilarArtists.size() - 1);
                            } else {
                                Log.e(TAG, "Null artist for similar");
                            }
                        }
                    }

                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mArtistsGrid != null && mArtistsSpinner != null) {
                                mArtistsGrid.setAdapter(mAdapter);
                                mArtistsSpinner.setVisibility(View.GONE);
                            }
                        }
                    });
                }
            } catch (EchoNestException e) {
                Log.e(TAG, "Cannot get similar artist", e);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Utils.shortToast(getActivity(), R.string.unable_fetch_artist_info);
                    }
                });

            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_artist_similar, container, false);
            mArtistsSpinner = (ProgressBar) rootView.findViewById(R.id.pbSimilarArtists);
            mArtistsGrid = (TwoWayView) rootView.findViewById(R.id.twvSimilarArtists);
            final ItemClickSupport itemClick = ItemClickSupport.addTo(mArtistsGrid);
            itemClick.setOnItemClickListener(mItemClickListener);
            return rootView;
        }
    }

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
            mHandler.removeCallbacks(mUpdateAlbumsRunnable);
            mHandler.post(mUpdateAlbumsRunnable);
        }
    }

    @Override
    public void onAlbumUpdate(List<Album> a) {
        boolean hasThisArtist = false;
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();
        for (Album album : a) {
            Iterator<String> songs = album.songs();
            while (songs.hasNext()) {
                String songRef = songs.next();
                Song song = aggregator.retrieveSong(songRef, album.getProvider());
                if (song != null && mArtist.getRef().equals(song.getArtist())) {
                    hasThisArtist = true;
                    break;
                }
            }

            if (hasThisArtist) {
                break;
            }
        }

        if (hasThisArtist) {
            mHandler.removeCallbacks(mUpdateAlbumsRunnable);
            mHandler.post(mUpdateAlbumsRunnable);
        }
    }

    @Override
    public void onPlaylistUpdate(List<Playlist> p) {

    }

    @Override
    public void onArtistUpdate(final List<Artist> a) {
        if (a.contains(mArtist)) {
            mHandler.removeCallbacks(mUpdateAlbumsRunnable);
            mHandler.post(mUpdateAlbumsRunnable);
            mArtistSimilarFragment.notifyArtistUpdate(a);
        }
    }

    @Override
    public void onProviderConnected(IMusicProvider provider) {

    }

    @Override
    public void onSearchResult(SearchResult searchResult) {

    }
}
