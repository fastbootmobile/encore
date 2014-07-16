package org.omnirom.music.app.fragments;

import android.app.Fragment;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v7.graphics.Palette;
import android.support.v7.graphics.PaletteItem;
import android.support.v7.widget.CardView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.omnirom.music.app.ArtistActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.app.ui.PlayPauseDrawable;
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

    private Bitmap mHeroImage;
    private int mBackgroundColor;
    private Artist mArtist;
    private View mRootView;
    private Palette mPalette;
    private Handler mHandler;
    private View mPreviousSongGroup;
    private View mPreviousAlbumGroup;
    private boolean mRecommendationLoaded = false;
    private SongClickListener mSongClickListener = new SongClickListener();
    private HashMap<Song, View> mSongToViewMap = new HashMap<Song, View>();
    private HashMap<String, View> mAlbumToViewMap = new HashMap<String, View>();

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

    public class SongClickListener implements View.OnClickListener {
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
    }


    public ArtistFragment() {

    }

    public View findViewById(int id) {
        return mRootView.findViewById(id);
    }

    public void setArguments(Bitmap hero, Bundle extras) {
        mHeroImage = hero;
        mBackgroundColor = extras.getInt(ArtistActivity.EXTRA_BACKGROUND_COLOR, 0xFF333333);
        mArtist = extras.getParcelable(ArtistActivity.EXTRA_ARTIST);

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
        ImageButton fabPlay = (ImageButton) mRootView.findViewById(R.id.fabPlay);
        setOutlines(fabPlay);

        // Set the FAB animated drawable
        final PlayPauseDrawable drawable = new PlayPauseDrawable(getResources());
        drawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
        drawable.setPaddingDp(48);
        fabPlay.setImageDrawable(drawable);
        fabPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (drawable.getCurrentShape() == PlayPauseDrawable.SHAPE_PAUSE) {
                    drawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
                } else {
                    drawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                }
                Utils.shortToast(getActivity(), R.string.please_program_me);
            }
        });

        // Register for updates
        ProviderAggregator.getDefault().addUpdateCallback(this);

        // Load recommendation and albums
        loadRecommendation();
        loadAlbums(true);


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

    private void showLoadingSpinner(final boolean show) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ProgressBar pb = (ProgressBar) mRootView.findViewById(R.id.pbArtistLoading);
                pb.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        });
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

                View suggestionTitle = mRootView.findViewById(R.id.tvArtistSuggestionNote);
                suggestionTitle.setVisibility(View.VISIBLE);
                suggestionTitle.setAlpha(0.0f);
                suggestionTitle.animate().alpha(1.0f).setDuration(500).start();
            }

            mRecommendationLoaded = true;
        } else {
            mRootView.findViewById(R.id.cardArtistSuggestion).setVisibility(View.GONE);
            mRootView.findViewById(R.id.tvArtistSuggestionNote).setVisibility(View.GONE);
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
                            showLoadingSpinner(hasMore);
                        } catch (RemoteException e) {
                            Log.e(TAG, "Unable to fetch artist albums", e);
                        }
                    } else {
                        showLoadingSpinner(false);
                    }
                } else {
                    showLoadingSpinner(false);
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
        for (final Album album : albums) {
            final View viewRoot = inflater.inflate(R.layout.expanded_albums_group, llAlbums, false);
            llAlbums.addView(viewRoot);
            mAlbumToViewMap.put(album.getRef(), viewRoot);

            TextView tvAlbumName = (TextView) viewRoot.findViewById(R.id.tvAlbumName);
            TextView tvAlbumYear = (TextView) viewRoot.findViewById(R.id.tvAlbumYear);
            ImageView ivCover = (ImageView) viewRoot.findViewById(R.id.ivCover);
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
                        new AlbumGroupClickListener(album, llAlbums, viewRoot);
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

        showLoadingSpinner(false);
    }

    private void showAlbumTracks(Album album, LinearLayout container) {
        Iterator<String> songsIt = album.songs();

        LayoutInflater inflater = getActivity().getLayoutInflater();
        ProviderCache cache = ProviderAggregator.getDefault().getCache();

        while (songsIt.hasNext()) {
            Song song = cache.getSong(songsIt.next());

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
                    }
                });

                // Bold if already playing
                IPlaybackService pbService = PluginsLookup.getDefault().getPlaybackService();
                try {
                    if (pbService.isPlaying()) {
                        Song currentSong = pbService.getCurrentTrack();
                        if (currentSong != null && song.equals(currentSong)) {
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
