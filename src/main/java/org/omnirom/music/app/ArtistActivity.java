package org.omnirom.music.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v7.graphics.Palette;
import android.support.v7.graphics.PaletteItem;
import android.support.v7.widget.CardView;
import android.transition.Transition;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.omnirom.music.framework.AlbumArtCache;
import org.omnirom.music.framework.ImageCache;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.framework.Suggestor;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ILocalCallback;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderCache;
import org.omnirom.music.providers.ProviderConnection;
import org.omnirom.music.providers.ProviderIdentifier;

import java.security.Provider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class ArtistActivity extends Activity {

    private static final String TAG = "ArtistActivity";
    private static final String TAG_FRAGMENT = "fragment_inner";

    public static final String EXTRA_ARTIST = "artist";
    public static final String EXTRA_BACKGROUND_COLOR = "background_color";
    public static final String BITMAP_ARTIST_HERO = "artist_hero";
    private static final String EXTRA_RESTORE_INTENT = "restore_intent";

    private InnerFragment mActiveFragment;
    private Bundle mInitialIntent;
    private Bitmap mHero;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_artist);

        FragmentManager fm = getFragmentManager();
        mActiveFragment = (InnerFragment) fm.findFragmentByTag(TAG_FRAGMENT);

        if (savedInstanceState == null) {
            mHero = Utils.dequeueBitmap(BITMAP_ARTIST_HERO);
            mInitialIntent = getIntent().getExtras();
        } else {
            mHero = Utils.dequeueBitmap(BITMAP_ARTIST_HERO);
            mInitialIntent = savedInstanceState.getBundle(EXTRA_RESTORE_INTENT);
        }

        if (mActiveFragment == null) {
            mActiveFragment = new InnerFragment();
            fm.beginTransaction()
                    .add(R.id.container, mActiveFragment, TAG_FRAGMENT)
                    .commit();
        }

        mActiveFragment.setArguments(mHero, mInitialIntent);

        // Remove the activity title as we don't want it here
        getActionBar().setTitle("");
        getActionBar().setDisplayHomeAsUpEnabled(true);

        getWindow().getEnterTransition().addListener(new Transition.TransitionListener() {
            @Override
            public void onTransitionStart(Transition transition) {
                View fab = mActiveFragment.findViewById(R.id.fabPlay);
                fab.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onTransitionEnd(Transition transition) {
                View fab = mActiveFragment.findViewById(R.id.fabPlay);
                fab.setVisibility(View.VISIBLE);

                // get the center for the clipping circle
                int cx = fab.getMeasuredWidth() / 2;
                int cy = fab.getMeasuredHeight() / 2;

                // get the final radius for the clipping circle
                final int finalRadius = fab.getWidth();

                // create and start the animator for this view
                // (the start radius is zero)
                ValueAnimator anim =
                        ViewAnimationUtils.createCircularReveal(fab, cx, cy, 0, finalRadius);
                anim.setInterpolator(new DecelerateInterpolator());
                anim.start();

                fab.setTranslationX(-fab.getMeasuredWidth() / 4.0f);
                fab.setTranslationY(-fab.getMeasuredHeight() / 4.0f);
                fab.animate().translationX(0.0f).translationY(0.0f)
                        .setDuration(getResources().getInteger(android.R.integer.config_shortAnimTime))
                        .setInterpolator(new DecelerateInterpolator())
                        .start();

                getWindow().getEnterTransition().removeListener(this);
            }

            @Override
            public void onTransitionCancel(Transition transition) {

            }

            @Override
            public void onTransitionPause(Transition transition) {

            }

            @Override
            public void onTransitionResume(Transition transition) {

            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBundle(EXTRA_RESTORE_INTENT, mInitialIntent);
        Utils.queueBitmap(BITMAP_ARTIST_HERO, mHero);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.artist, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        } else if (id == android.R.id.home) {
            finishAfterTransition();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private static class BackgroundAsyncTask extends AsyncTask<Album, Void, BitmapDrawable> {
        private Album mAlbum;
        private Context mContext;
        private ImageView mImageView;
        private CardView mRootView;
        private Palette mPalette;

        public BackgroundAsyncTask(Context context, CardView cv, ImageView iv) {
            mContext = context;
            mImageView = iv;
            mRootView = cv;
        }

        @Override
        protected BitmapDrawable doInBackground(Album... params) {
            mAlbum = params[0];

            if (mAlbum == null) {
                return null;
            }
            final Resources res = mContext.getResources();
            assert res != null;

            final ProviderCache cache = ProviderAggregator.getDefault().getCache();

            // Prepare the placeholder/default
            BitmapDrawable drawable = (BitmapDrawable) res.getDrawable(R.drawable.album_placeholder);
            assert drawable != null;
            Bitmap bmp = drawable.getBitmap();

            String artKey = cache.getAlbumArtKey(mAlbum);
            Bitmap cachedImage = null;
            if (artKey != null) {
                cachedImage = ImageCache.getDefault().get(artKey);
            }
            if (cachedImage != null) {
                bmp = cachedImage;
            } else {
                String artUrl = null;

                if (artKey == null) {
                    StringBuffer urlBuffer = new StringBuffer();
                    artKey = AlbumArtCache.getArtKey(mAlbum, urlBuffer);
                    artUrl = urlBuffer.toString();
                }

                if (artKey != null && !artKey.equals(AlbumArtCache.DEFAULT_ART)) {
                    bmp = AlbumArtCache.getOrDownloadArt(artKey, artUrl, bmp);
                }
            }

            BitmapDrawable output = new BitmapDrawable(res, bmp);

            cache.putAlbumArtKey(mAlbum, artKey);

            mPalette = Palette.generate(bmp);

            return output;
        }

        @Override
        protected void onPostExecute(BitmapDrawable result) {
            super.onPostExecute(result);

            if (result != null) {
                mImageView.setImageDrawable(result);
                PaletteItem vibrant = mPalette.getVibrantColor();

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
    }

    /**
     * A fragment containing a simple view.
     */
    public static class InnerFragment extends Fragment implements ILocalCallback {

        private Bitmap mHeroImage;
        private int mBackgroundColor;
        private Artist mArtist;
        private View mRootView;
        private Palette mPalette;
        private Handler mHandler;
        private boolean mRecommendationLoaded = false;

        private Runnable mUpdateAlbumsRunnable = new Runnable() {
            @Override
            public void run() {
                // FIXME: Artist object isn't getting updated with the new albums
                // Reason: Artist object is copied when serialized in the bundle. When retrieved
                // in the intent here, it's a copy with the existing attributes at that time
                ProviderCache cache = ProviderAggregator.getDefault().getCache();
                mArtist = cache.getArtist(mArtist.getRef());

                if (!mRecommendationLoaded) {
                    loadRecommendation();
                }

                loadAlbums(false);
            }
        };

        private class AlbumGroupClickListener implements View.OnClickListener {
            private Album mAlbum;
            private LinearLayout mContainer;
            private LinearLayout mItemHost;
            private View mHeader;
            private boolean mOpen;
            private View mHeaderDivider;
            private View mLastItemDivider;

            public AlbumGroupClickListener(Album a, LinearLayout container, View header) {
                mAlbum = a;
                mContainer = container;
                mOpen = false;
                mHeader = header;

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
                        mItemHost = new LinearLayout(getActivity());
                        mItemHost.setOrientation(LinearLayout.VERTICAL);

                        // We insert the view below the group
                        int index = ((LinearLayout) mHeader.getParent()).indexOfChild(mHeader);
                        mContainer.addView(mItemHost, index + 1);

                        showAlbumTracks(mAlbum, mItemHost);

                        // Add the divider at the end
                        LayoutInflater inflater = getActivity().getLayoutInflater();
                        mLastItemDivider = inflater.inflate(R.layout.divider, mItemHost, false);
                        mItemHost.addView(mLastItemDivider);
                    }
                    mItemHost.startAnimation(Utils.animateExpand(mItemHost, true));
                    mHeaderDivider.animate().alpha(1.0f).setDuration(500).start();

                    mOpen = true;
                }
            }
        }


        public InnerFragment() {

        }

        public View findViewById(int id) {
            return mRootView.findViewById(id);
        }

        public void setArguments(Bitmap hero, Bundle extras) {
            mHeroImage = hero;
            mBackgroundColor = extras.getInt(EXTRA_BACKGROUND_COLOR, 0xFF333333);
            mArtist = extras.getParcelable(EXTRA_ARTIST);

            // Prepare the palette to colorize the FAB
            Palette.generateAsync(hero, new Palette.PaletteAsyncListener() {
                @Override
                public void onGenerated(final Palette palette) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mPalette = palette;
                            PaletteItem color = palette.getDarkMutedColor();
                            if (color != null && mRootView != null) {
                                RippleDrawable ripple = (RippleDrawable) mRootView.findViewById(R.id.fabPlay).getBackground();
                                GradientDrawable back = (GradientDrawable) ripple.getDrawable(0);
                                back.setColor(color.getRgb());
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

            mRootView = inflater.inflate(R.layout.fragment_artist, container, false);

            ImageView heroImage = (ImageView) mRootView.findViewById(R.id.ivHero);
            heroImage.setImageBitmap(mHeroImage);

            TextView tvArtist = (TextView) mRootView.findViewById(R.id.tvArtist);
            tvArtist.setBackgroundColor(mBackgroundColor);
            tvArtist.setText(mArtist.getName());

            // Outline is required for the FAB shadow to be actually oval
            setOutlines(mRootView.findViewById(R.id.fabPlay));

            loadRecommendation();
            loadAlbums(true);

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
            int size = getResources().getDimensionPixelSize(R.dimen.floating_button_size);

            Outline outline = new Outline();
            outline.setOval(0, 0, size, size);

            v.setOutline(outline);
        }

        private void loadRecommendation() {
            Song recommended = Suggestor.getInstance().suggestBestForArtist(mArtist);
            if (recommended != null) {
                Album album = ProviderAggregator.getDefault().getCache().getAlbum(recommended.getAlbum());

                TextView tvTitle = (TextView) mRootView.findViewById(R.id.tvArtistSuggestionTitle);
                TextView tvArtist = (TextView) mRootView.findViewById(R.id.tvArtistSuggestionArtist);
                tvTitle.setText(recommended.getTitle());

                if (album != null) {
                    tvArtist.setText(getString(R.string.from_the_album, album.getName()));
                } else {
                    tvArtist.setText("");
                }

                ImageView ivCov = (ImageView) mRootView.findViewById(R.id.ivArtistSuggestionCover);
                ivCov.setImageResource(R.drawable.album_placeholder);

                CardView cvRec = (CardView) mRootView.findViewById(R.id.cardArtistSuggestion);

                BackgroundAsyncTask task = new BackgroundAsyncTask(getActivity(), cvRec, ivCov);
                ProviderCache cache = ProviderAggregator.getDefault().getCache();
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, cache.getAlbum(recommended.getAlbum()));

                // If we were gone, animate in
                if (cvRec.getVisibility() == View.GONE) {
                    cvRec.setVisibility(View.VISIBLE);
                    cvRec.setAlpha(0.0f);
                    cvRec.animate().alpha(1.0f).setDuration(500).start();
                }

                mRecommendationLoaded = true;
            } else {
                mRootView.findViewById(R.id.cardArtistSuggestion).setVisibility(View.GONE);
                mRecommendationLoaded = false;
            }
        }

        private void fetchAlbums() {
            new Thread() {
                public void run() {
                    ProviderIdentifier pi = mArtist.getProvider();
                    ProviderConnection pc = PluginsLookup.getDefault().getProvider(pi);
                    if (pc != null) {
                        IMusicProvider provider = pc.getBinder();
                        if (provider != null) {
                            try {
                                boolean hasMore = provider.fetchArtistAlbums(mArtist.getRef());
                                Log.e("XPLOD", "Have more? " + hasMore);
                            } catch (RemoteException e) {
                                Log.e(TAG, "Unable to fetch artist albums", e);
                            }
                        }
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

            Iterator<String> albumIt = mArtist.albums();
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
            for (Album album : albums) {
                final View viewRoot = inflater.inflate(R.layout.expanded_albums_group, llAlbums, false);
                llAlbums.addView(viewRoot);

                TextView tvAlbumName = (TextView) viewRoot.findViewById(R.id.tvAlbumName);
                TextView tvAlbumYear = (TextView) viewRoot.findViewById(R.id.tvAlbumYear);
                ImageView ivCover = (ImageView) viewRoot.findViewById(R.id.ivCover);

                if (album.isLoaded()) {
                    tvAlbumName.setText(album.getName());
                    if (album.getYear() > 0) {
                        tvAlbumYear.setVisibility(View.VISIBLE);
                        tvAlbumYear.setText(Integer.toString(album.getYear()));
                    } else {
                        tvAlbumYear.setVisibility(View.GONE);
                    }

                    AlbumGroupClickListener listener =
                            new AlbumGroupClickListener(album, llAlbums, viewRoot);
                    viewRoot.setOnClickListener(listener);
                } else {
                    tvAlbumName.setText(getString(R.string.loading));
                    tvAlbumYear.setVisibility(View.GONE);
                }

                // TODO: Refactor that in a proper asynchronous task that sets both the ripple
                // based on the album art, and fetches the album art for the item
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mPalette != null) {
                            PaletteItem mutedBgColor = mPalette.getMutedColor();
                            if (mutedBgColor != null) {
                                RippleDrawable bg = (RippleDrawable) viewRoot.getBackground();
                                bg.setColor(ColorStateList.valueOf(mutedBgColor.getRgb()));
                                viewRoot.setBackground(bg);
                            }
                        } else {
                            // Palette not generated yet, go ahead again
                            mHandler.postDelayed(this, 100);
                        }
                    }
                });

                BackgroundAsyncTask task = new BackgroundAsyncTask(getActivity(), null, ivCover);
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, album);
            }
        }

        private void showAlbumTracks(Album album, LinearLayout container) {
            Iterator<String> songsIt = album.songs();

            LayoutInflater inflater = getActivity().getLayoutInflater();
            ProviderCache cache = ProviderAggregator.getDefault().getCache();

            while (songsIt.hasNext()) {
                View itemRoot = inflater.inflate(R.layout.expanded_albums_item, container, false);
                container.addView(itemRoot);

                TextView tvTrackName = (TextView) itemRoot.findViewById(R.id.tvTrackName);
                TextView tvTrackDuration = (TextView) itemRoot.findViewById(R.id.tvTrackDuration);

                Song song = cache.getSong(songsIt.next());

                if (song != null && song.isLoaded()) {
                    tvTrackName.setText(song.getTitle());
                    tvTrackDuration.setText(Utils.formatTrackLength(song.getDuration()));
                } else {
                    tvTrackName.setText(getString(R.string.loading));
                    tvTrackDuration.setText("");
                }
            }
        }

        @Override
        public void onSongUpdate(Song s) {
            mHandler.removeCallbacks(mUpdateAlbumsRunnable);
            mHandler.postDelayed(mUpdateAlbumsRunnable, 200);
        }

        @Override
        public void onAlbumUpdate(Album a) {
            mHandler.removeCallbacks(mUpdateAlbumsRunnable);
            mHandler.postDelayed(mUpdateAlbumsRunnable, 200);
        }

        @Override
        public void onPlaylistUpdate(Playlist p) {

        }

        @Override
        public void onArtistUpdate(Artist a) {

        }

        @Override
        public void onProviderConnected(IMusicProvider provider) {

        }
    }
}
