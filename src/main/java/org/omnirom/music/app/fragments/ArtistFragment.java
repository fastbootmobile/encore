package org.omnirom.music.app.fragments;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.echonest.api.v4.Biography;
import com.echonest.api.v4.EchoNestException;

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

    private Runnable mUpdateAlbumsRunnable = new Runnable() {
        @Override
        public void run() {
            // FIXME: Artist object isn't getting updated with the new albums
            // Reason: Artist object is copied when serialized in the bundle. When retrieved
            // in the intent here, it's a copy with the existing attributes at that time
            ProviderCache cache = ProviderAggregator.getDefault().getCache();
            mArtist = cache.getArtist(mArtist.getRef());

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

        public AlbumArtLoadListener(View rootView) {
            mRootView = rootView;
        }

        @Override
        public void onArtLoaded(AlbumArtImageView view, BitmapDrawable drawable) {
            if (drawable == null) {
                return;
            }

            Palette palette = Palette.generate(drawable.getBitmap());
            PaletteItem vibrant = palette.getVibrantColor();

            if (vibrant != null && mRootView != null) {
                mRootView.setBackgroundColor(vibrant.getRgb());

                float luminance = vibrant.getHsl()[2];

                TextView tvArtist = (TextView) mRootView.findViewById(R.id.tvArtistSuggestionArtist);
                TextView tvTitle = (TextView) mRootView.findViewById(R.id.tvArtistSuggestionTitle);
                Button btnPlay = (Button) mRootView.findViewById(R.id.btnArtistSuggestionPlay);

                int color = 0xFF333333;
                if (luminance < 0.6f) {
                    color = 0xFFFFFFFF;
                }

                tvArtist.setTextColor(color);
                tvTitle.setTextColor(color);
                btnPlay.setTextColor(color);
            }
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

                mLastItemDivider.animate().alpha(0.0f).setDuration(500).start();
                mHeaderDivider.animate().alpha(0.0f).setDuration(500).start();
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
                mHeaderDivider.animate().alpha(1.0f).setDuration(500).start();

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
        mArtist = ProviderAggregator.getDefault().getCache().getArtist(artistRef);

        if (mArtist == null) {
            Log.e(TAG, "No artist found in cache for " + artistRef + "!");
        }

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
                                GradientDrawable shape = (GradientDrawable) mRootView.findViewById(R.id.fabPlay).getBackground();
                                shape.setColor(color.getRgb());
                            }
                        }
                    }
                });
            }
        });
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
                if (y >= tvArtist.getTop()) {
                    getActivity().getActionBar().hide();
                } else {
                    getActivity().getActionBar().show();
                }
            }
        });

        // Setup the source logo
        ImageView ivSource = (ImageView) mRootView.findViewById(R.id.ivSourceLogo);
        ivSource.setImageBitmap(PluginsLookup.getDefault().getCachedLogo(mArtist));

        // Outline is required for the FAB shadow to be actually oval
        ImageButton fabPlay = (ImageButton) mRootView.findViewById(R.id.fabPlay);
        setOutlines(fabPlay);

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
        fabPlay.setImageDrawable(mFabDrawable);
        fabPlay.setOnClickListener(new View.OnClickListener() {
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
        private ArtistFragment mParent;
        private HashMap<Song, View> mSongToViewMap = new HashMap<Song, View>();
        private HashMap<String, View> mAlbumToViewMap = new HashMap<String, View>();
        private View mPreviousSongGroup;
        private View mPreviousAlbumGroup;
        private Handler mHandler;

        private View.OnClickListener mSongClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Song song = (Song) view.getTag();

                try {
                    PluginsLookup.getDefault().getPlaybackService().playSong(song);
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to play song", e);
                    return;
                }

                boldPlayingTrack(song);
                updatePlayingAlbum(song.getAlbum());
            }
        };

        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            mRootView = inflater.inflate(R.layout.fragment_artist_tracks, container, false);
            mHandler = new Handler();

            // Load recommendation and albums
            loadRecommendation();
            loadAlbums(true);

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
                    ProviderCache cache = ProviderAggregator.getDefault().getCache();
                    pbService.queueAlbum(cache.getAlbum(mRecommendedSong.getAlbum()), false);
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to play recommended song", e);
                }
            }
        }

        private void loadRecommendation() {
            if (mRecommendationLoaded) {
                return;
            }

            Song recommended = Suggestor.getInstance().suggestBestForArtist(mParent.getArtist());
            if (recommended != null) {
                mRecommendedSong = recommended;
                Album album = ProviderAggregator.getDefault().getCache().getAlbum(recommended.getAlbum());

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

                ProviderCache cache = ProviderAggregator.getDefault().getCache();
                AlbumArtImageView ivCov = (AlbumArtImageView) mRootView.findViewById(R.id.ivArtistSuggestionCover);
                ivCov.setOnArtLoadedListener(new AlbumArtLoadListener(cvRec));
                ivCov.loadArtForAlbum(cache.getAlbum(recommended.getAlbum()));

                // If we were gone, animate in
                if (cvRec.getVisibility() == View.GONE) {
                    cvRec.setVisibility(View.VISIBLE);
                    cvRec.setAlpha(0.0f);
                    cvRec.animate().alpha(1.0f).setDuration(500).start();

                    View suggestionTitle = mRootView.findViewById(R.id.tvArtistSuggestionNote);
                    suggestionTitle.setVisibility(View.VISIBLE);
                    suggestionTitle.setAlpha(0.0f);
                    suggestionTitle.animate().alpha(1.0f).setDuration(500).start();
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
                            } catch (RemoteException e) {
                                Log.e(TAG, "Unable to fetch artist albums", e);
                                postToast(R.string.plugin_error);
                            }
                        } else {
                            showLoadingSpinner(false);
                            Log.e(TAG, "Provider is null, cannot fetch albums");
                            postToast(R.string.plugin_error);
                        }
                    } else {
                        showLoadingSpinner(false);
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
            llAlbums.removeAllViews();

            ProviderCache cache = ProviderAggregator.getDefault().getCache();

            Iterator<String> albumIt = mParent.getArtist().albums();
            List<Album> albums = new ArrayList<Album>();

            while (albumIt.hasNext()) {
                Album album = cache.getAlbum(albumIt.next());
                albums.add(album);
            }

            // Sort it from album names
            Collections.sort(albums, new Comparator<Album>() {
                @Override
                public int compare(Album album, Album album2) {
                    if (album.getYear() != album2.getYear()) {
                        return album.getYear() < album2.getYear() ? 1 : -1;
                    } else {
                        return album.getName().compareTo(album2.getName());
                    }
                }
            });

            // Then inflate views
            LayoutInflater inflater = getActivity().getLayoutInflater();
            for (final Album album : albums) {
                final View viewRoot = inflater.inflate(R.layout.expanded_albums_group, llAlbums, false);
                llAlbums.addView(viewRoot);

                TextView tvAlbumName = (TextView) viewRoot.findViewById(R.id.tvAlbumName);
                TextView tvAlbumYear = (TextView) viewRoot.findViewById(R.id.tvAlbumYear);
                AlbumArtImageView ivCover = (AlbumArtImageView) viewRoot.findViewById(R.id.ivCover);
                ImageView ivPlayAlbum = (ImageView) viewRoot.findViewById(R.id.ivPlayAlbum);

                if (album.isLoaded()) {
                    tvAlbumName.setText(album.getName());
                    if (album.getYear() > 0) {
                        tvAlbumYear.setVisibility(View.VISIBLE);
                        tvAlbumYear.setText(Integer.toString(album.getYear()));
                    } else {
                        tvAlbumYear.setVisibility(View.GONE);
                    }

                    AlbumGroupClickListener listener =
                            new AlbumGroupClickListener(album, llAlbums, viewRoot, this);
                    viewRoot.setOnClickListener(listener);

                    ivPlayAlbum.setVisibility(View.VISIBLE);
                    final PlayPauseDrawable drawable = new PlayPauseDrawable(getResources());
                    drawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
                    drawable.setColor(0xCC333333);

                    // Set play or pause based on if this album is playing
                    final IPlaybackService pbService = PluginsLookup.getDefault().getPlaybackService();
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

                    ivPlayAlbum.setImageDrawable(drawable);
                    ivPlayAlbum.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
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
                                } catch (RemoteException e) {
                                    e.printStackTrace();
                                }

                                // Bold the corresponding track
                                Iterator<String> songs = album.songs();
                                if (songs.hasNext()) {
                                    String songRef = songs.next();
                                    Song song = ProviderAggregator.getDefault().getCache().getSong(songRef);
                                    boldPlayingTrack(song);
                                }
                            }
                        }
                    });
                } else {
                    tvAlbumName.setText(getString(R.string.loading));
                    tvAlbumYear.setVisibility(View.GONE);
                    ivPlayAlbum.setVisibility(View.GONE);
                }

                // Load the album art
                ivCover.setOnArtLoadedListener(new AlbumArtImageView.OnArtLoadedListener() {
                    @Override
                    public void onArtLoaded(AlbumArtImageView view, BitmapDrawable drawable) {
                        if (drawable == null) {
                            return;
                        }
                        Palette palette = Palette.generate(drawable.getBitmap());
                        PaletteItem mutedBgColor = palette.getMutedColor();
                        if (mutedBgColor != null) {
                            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                            /* RippleDrawable bg = (RippleDrawable) viewRoot.getBackground();
                            bg.setColor(ColorStateList.valueOf(mutedBgColor.getRgb()));
                            viewRoot.setBackground(bg); */
                            }
                        }
                    }
                });
                ivCover.loadArtForAlbum(album);

                mAlbumToViewMap.put(album.getRef(), viewRoot);
            }

            showLoadingSpinner(false);
        }

        private void showAlbumTracks(Album album, LinearLayout container) {
            Iterator<String> songsIt = album.songs();

            LayoutInflater inflater = getActivity().getLayoutInflater();
            ProviderCache cache = ProviderAggregator.getDefault().getCache();

            while (songsIt.hasNext()) {
                final Song song = cache.getSong(songsIt.next());

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
                            PopupMenu popupMenu = new PopupMenu(getActivity(), ivOverflow);
                            popupMenu.inflate(R.menu.track_overflow);
                            popupMenu.show();

                            popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                                @Override
                                public boolean onMenuItemClick(MenuItem menuItem) {
                                    switch (menuItem.getItemId()) {
                                        case R.id.menu_add_to_playlist:
                                            PlaylistChooserFragment fragment = PlaylistChooserFragment.newInstance(song);
                                            fragment.show(getActivity().getSupportFragmentManager(), song.getRef());
                                            break;

                                        default:
                                            return false;
                                    }
                                    return true;
                                }
                            });
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
        private Artist mArtist;
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
                            mLoadingSpinner.setVisibility(View.GONE);

                            if (bio != null) {
                                mArtistInfo.setText(getString(R.string.biography_format,
                                        bio.getText(), bio.getSite(), bio.getURL(),
                                        bio.getLicenseType(), bio.getLicenseAttribution()));
                            } else {
                                mArtistInfo.setText(getString(R.string.no_bio_available));
                            }
                        }
                    });
                }
            } catch (EchoNestException e) {
                Log.e(TAG, "Unable to get artist information", e);
                Utils.shortToast(getActivity(), R.string.unable_fetch_artist_info);
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
        private ExpandableHeightGridView mArtistsGrid;
        private Artist mArtist;
        private boolean mSimilarLoaded;
        private ArtistsAdapter mAdapter;
        private Handler mHandler;

        public ArtistSimilarFragment() {
            mAdapter = new ArtistsAdapter();
            mHandler = new Handler();
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

        public void notifyArtistUpdate() {
            mAdapter.notifyDataSetChanged();
        }

        public void loadSimilarSync() {
            EchoNest echoNest = new EchoNest();
            try {
                com.echonest.api.v4.Artist enArtist = echoNest.searchArtistByName(mArtist.getName());
                if (enArtist != null) {
                    List<com.echonest.api.v4.Artist> similars = echoNest.getArtistSimilar(enArtist);

                    List<String> rosettas = ProviderAggregator.getDefault().getRosettaStonePrefix();
                    String rosettaPreferred = null;
                    ProviderIdentifier rosettaProvider = null;
                    if (rosettas != null && rosettas.size() > 0) {
                        rosettaPreferred = rosettas.get(0);
                        rosettaProvider = ProviderAggregator.getDefault().getRosettaStoneIdentifier(rosettaPreferred);
                    }

                    for (com.echonest.api.v4.Artist similar : similars) {
                        if (rosettaPreferred != null) {
                            String ref = echoNest.getArtistForeignID(similar, rosettaPreferred);
                            Artist artist = ProviderAggregator.getDefault().retrieveArtist(ref, rosettaProvider);
                            if (artist != null) {
                                mAdapter.addItemUnique(artist);
                            } else {
                                Log.e(TAG, "Null artist for similar");
                            }
                        }
                    }

                    mAdapter.notifyDataSetChanged();
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mArtistsGrid.setAdapter(mAdapter);
                        }
                    });
                }
            } catch (EchoNestException e) {
                Log.e(TAG, "Cannot get similar artist", e);
                Utils.shortToast(getActivity(), R.string.unable_fetch_artist_info);
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_artist_similar, container, false);
            mArtistsGrid = (ExpandableHeightGridView) rootView.findViewById(R.id.gvSimilarArtists);
            mArtistsGrid.setExpanded(true);
            mArtistsGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    Intent intent = new Intent(getActivity(), ArtistActivity.class);

                    ArtistsAdapter.ViewHolder tag = (ArtistsAdapter.ViewHolder) view.getTag();
                    AlbumArtImageView ivCover = tag.ivCover;
                    TextView tvTitle = tag.tvTitle;

                    intent.putExtra(ArtistActivity.EXTRA_ARTIST,
                            mAdapter.getItem(position).getRef());

                    intent.putExtra(ArtistActivity.EXTRA_BACKGROUND_COLOR, tag.itemColor);

                    Utils.queueBitmap(ArtistActivity.BITMAP_ARTIST_HERO, tag.srcBitmap);

                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                    /* ActivityOptions opt = ActivityOptions.makeSceneTransitionAnimation(getActivity(),
                            new Pair<View, String>(ivCover, "itemImage"),
                            new Pair<View, String>(tvTitle, "artistName"));

                    startActivity(intent, opt.toBundle()); */
                    } else {
                        startActivity(intent);
                    }
                }
            });
            return rootView;
        }
    }

    @Override
    public void onSongUpdate(List<Song> s) {
        mHandler.removeCallbacks(mUpdateAlbumsRunnable);
        mHandler.post(mUpdateAlbumsRunnable);
    }

    @Override
    public void onAlbumUpdate(List<Album> a) {
        mHandler.removeCallbacks(mUpdateAlbumsRunnable);
        mHandler.post(mUpdateAlbumsRunnable);
    }

    @Override
    public void onPlaylistUpdate(List<Playlist> p) {

    }

    @Override
    public void onArtistUpdate(List<Artist> a) {
        mArtistSimilarFragment.notifyArtistUpdate();
    }

    @Override
    public void onProviderConnected(IMusicProvider provider) {

    }

    @Override
    public void onSearchResult(SearchResult searchResult) {

    }
}
