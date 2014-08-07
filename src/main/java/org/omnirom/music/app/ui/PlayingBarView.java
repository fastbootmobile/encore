package org.omnirom.music.app.ui;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v7.graphics.Palette;
import android.support.v7.graphics.PaletteItem;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.omnirom.music.app.AlbumActivity;
import org.omnirom.music.app.PlaybackQueueActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderCache;
import org.omnirom.music.service.IPlaybackCallback;
import org.omnirom.music.service.IPlaybackService;

import java.security.Provider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * ViewGroup for the sticky bottom playing bar
 */
public class PlayingBarView extends RelativeLayout {

    private static final String TAG = "PlayingBarView";

    // Delay after which the seek bar is updated (30Hz)
    private static final int SEEK_BAR_UPDATE_DELAY = 1000/30;

    private Runnable mUpdateSeekBarRunnable = new Runnable() {
        @Override
        public void run() {
            IPlaybackService playbackService = PluginsLookup.getDefault().getPlaybackService();
            if (playbackService != null) {
                try {
                    if (playbackService.isPlaying()) {
                        /*mScrobble.setMax(playbackService.getCurrentTrackLength())
                        mScrobble.setProgress(playbackService.getCurrentTrackPosition());*/

                        // Restart ourselves
                        mHandler.postDelayed(mUpdateSeekBarRunnable, SEEK_BAR_UPDATE_DELAY);
                    } else {
                        /*mScrobble.setMax(1);
                        mScrobble.setProgress(1);*/
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to update seek bar", e);
                }
            }
        }
    };

    private IPlaybackCallback.Stub mPlaybackCallback = new IPlaybackCallback.Stub() {

        @Override
        public void onSongStarted(Song s) throws RemoteException {
            mCurrentSong = s;

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    updatePlayingQueue();
/*
                    // Fill the album art
                    mExecutor.execute(mAlbumArtRunnable);

                    // Fill the views
                    mTitleView.setText(s.getTitle());

                    Artist artist = ProviderAggregator.getDefault().getCache().getArtist(s.getArtist());
                    if(artist != null)
                        mArtistView.setText(artist.getName());
                    else
                        mArtistView.setText("...");
                    //mScrobble.setMax(s.getDuration());
*/
                    // Set the visibility and button state
                    setPlayButtonState(false);
                    mIsPlaying = true;
                }
            });

            mHandler.postDelayed(mUpdateSeekBarRunnable, SEEK_BAR_UPDATE_DELAY);
        }

        @Override
        public void onSongScrobble(int timeMs) throws RemoteException {
            /*mScrobble.setProgress(timeMs);*/
        }

        @Override
        public void onPlaybackPause() throws RemoteException {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    setPlayButtonState(true);
                    mIsPlaying = false;
                }
            });
        }

        @Override
        public void onPlaybackResume() throws RemoteException {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    setPlayButtonState(false);
                    mIsPlaying = true;
                }
            });
        }
    };

    private static final int MAX_PEEK_QUEUE_SIZE = 4;

    private Song mCurrentSong;
    private boolean mIsPlaying;
    private LinearLayout mTracksLayout;
    private ImageButton mPlayFab;
    private PlayPauseDrawable mPlayFabDrawable;
    private List<Song> mLastQueue;
    private Handler mHandler = new Handler();
    private final int mAnimationDuration;
    private boolean mWrapped;

    public PlayingBarView(Context context) {
        super(context);
        mAnimationDuration = getResources().getInteger(android.R.integer.config_mediumAnimTime);
    }

    public PlayingBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mAnimationDuration = getResources().getInteger(android.R.integer.config_mediumAnimTime);
    }

    public PlayingBarView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mAnimationDuration = getResources().getInteger(android.R.integer.config_mediumAnimTime);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        // Setup the Playback service callback
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                IPlaybackService playbackService = PluginsLookup.getDefault().getPlaybackService();
                if (playbackService != null) {
                    try {
                        playbackService.addCallback(mPlaybackCallback);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Unable to register the playbar as a callback of the playback", e);
                    }
                } else {
                    // Retry, playback connection not established yet
                    mHandler.post(this);
                }
            }
        });


        // Set FAB info
        mPlayFab = (ImageButton) findViewById(R.id.fabPlayBarButton);
        Utils.setSmallFabOutline(new View[]{mPlayFab});

        mPlayFabDrawable = new PlayPauseDrawable(getResources());
        mPlayFabDrawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
        mPlayFab.setImageDrawable(mPlayFabDrawable);
        mPlayFab.setVisibility(View.INVISIBLE);

        mPlayFab.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                IPlaybackService playbackService = PluginsLookup.getDefault().getPlaybackService();
                if (mIsPlaying) {
                    try {
                        playbackService.pause();
                    } catch (RemoteException e) {
                        Log.e(TAG, "Unable to pause playback", e);
                    }
                } else {
                    try {
                        playbackService.play();
                    } catch (RemoteException e) {
                        Log.e(TAG, "Unable to resume playback", e);
                    }
                }
            }
        });

        // Load playing queue
        mTracksLayout = (LinearLayout) findViewById(R.id.playBarTracksLayout);
        updatePlayingQueue();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        IPlaybackService playbackService = PluginsLookup.getDefault().getPlaybackService();
        try {
            if (playbackService != null) {
                playbackService.removeCallback(mPlaybackCallback);
            }
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to remove the playing bar callback from the playback", e);
        }
    }

    /**
     * Defines the visual state of the play/pause button
     * @param play true will set the image to a "play" image, false will set to "pause"
     */
    public void setPlayButtonState(boolean play) {
        if (play) {
            mPlayFabDrawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
        } else {
            mPlayFabDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
        }
    }

    public void updatePlayingQueue() {
        IPlaybackService playbackService = PluginsLookup.getDefault().getPlaybackService();

        if (playbackService == null) {
            // Service not bound yet
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    updatePlayingQueue();
                }
            });
            return;
        }

        List<Song> queue = null;
        try {
            queue = playbackService.getCurrentPlaybackQueue();
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to retrieve the current playback queue");
        }


        if (queue != null && queue.size() > 0) {
            mLastQueue = new ArrayList<Song>(queue);
            mTracksLayout.removeAllViews();
            mTracksLayout.setVisibility(View.VISIBLE);

            // Inflate views and make the list out of the first 4 items (or less)
            int shownCount = 0;
            View itemViews[] = new View[MAX_PEEK_QUEUE_SIZE];
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            ProviderCache cache = ProviderAggregator.getDefault().getCache();
            for (final Song song : queue) {
                if (shownCount == MAX_PEEK_QUEUE_SIZE) {
                    break;
                }

                View itemRoot = inflater.inflate(R.layout.item_playbar, mTracksLayout, false);
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                    // itemRoot.setViewName("playbackqueue:preview:" + shownCount);
                }
                mTracksLayout.addView(itemRoot);
                itemViews[shownCount] = itemRoot;

                TextView tvArtist = (TextView) itemRoot.findViewById(R.id.tvArtist);
                TextView tvTitle = (TextView) itemRoot.findViewById(R.id.tvTitle);
                AlbumArtImageView ivAlbumArt = (AlbumArtImageView) itemRoot.findViewById(R.id.ivAlbumArt);

                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                    //tvArtist.setViewName("playbackqueue:preview:" + shownCount + ":artist");
                    //tvTitle.setViewName("playbackqueue:preview:" + shownCount + ":title");
                    //ivAlbumArt.setViewName("playbackqueue:preview:" + shownCount + ":art");
                }
                Artist artist = cache.getArtist(song.getArtist());
                if (artist != null) {
                    tvArtist.setText(artist.getName());
                } else {
                    tvArtist.setText(getContext().getString(R.string.loading));
                }

                tvTitle.setText(song.getTitle());
                ivAlbumArt.loadArtForSong(song);
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                    // ivAlbumArt.setViewName(song.getRef() + shownCount);
                }

                ivAlbumArt.setOnClickListener(new View.OnClickListener() {
                    Palette mPalette;

                    @Override
                    public void onClick(View view) {
                        Bitmap hero = ((MaterialTransitionDrawable) ((ImageView) view).getDrawable()).getFinalDrawable().getBitmap();
                        if (mPalette == null) {
                            mPalette = Palette.generate(hero);
                        }
                        PaletteItem darkVibrantColor = mPalette.getDarkVibrantColor();
                        PaletteItem darkMutedColor = mPalette.getDarkMutedColor();

                        int color;
                        if (darkVibrantColor != null) {
                            color = darkVibrantColor.getRgb();
                        } else if (darkMutedColor != null) {
                            color = darkMutedColor.getRgb();
                        } else {
                            color = getResources().getColor(R.color.default_album_art_background);
                        }

                        ProviderCache cache = ProviderAggregator.getDefault().getCache();

                        Intent intent = AlbumActivity.craftIntent(getContext(), hero,
                                cache.getAlbum(song.getAlbum()), color);

                        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                            /* ActivityOptions opt = ActivityOptions.makeSceneTransitionAnimation((Activity) getContext(),
                                    view, "itemImage");

                            getContext().startActivity(intent, opt.toBundle()); */
                        } else {
                            getContext().startActivity(intent);
                        }
                    }
                });

                if (shownCount == 0) {
                    itemRoot.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            if (mWrapped) {
                                setWrapped(false);
                            }
                        }
                    });
                }

                shownCount++;
            }

            // Add the "View full queue" item entry, using the album art as "wrap" button
            final View itemRoot = inflater.inflate(R.layout.item_playbar, mTracksLayout, false);
            mTracksLayout.addView(itemRoot);

            TextView tvArtist = (TextView) itemRoot.findViewById(R.id.tvArtist);
            TextView tvTitle = (TextView) itemRoot.findViewById(R.id.tvTitle);
            ImageView ivAlbumArt = (ImageView) itemRoot.findViewById(R.id.ivAlbumArt);

            tvArtist.setVisibility(View.GONE);
            tvTitle.setText(getContext().getString(R.string.view_full_queue));
            ivAlbumArt.setImageResource(R.drawable.ic_reduce);
            ivAlbumArt.setScaleType(ImageView.ScaleType.CENTER);

            ivAlbumArt.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    setWrapped(true);
                }
            });

            final int finalShownCount = shownCount;
            final View[] finalItemViews = itemViews;
            itemRoot.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(getContext(), PlaybackQueueActivity.class);

                    List<Pair<View, String>> itemsTransition = new ArrayList<Pair<View, String>>();

                    // First item is processed differently.
                    for (int i = 1; i < finalShownCount; i++) {
                        itemsTransition.add(Pair.create(finalItemViews[i], "playbackqueue:" + i));
                    }

                    itemsTransition.add(Pair.create(finalItemViews[0].findViewById(R.id.tvTitle), "playback:firstitem:title"));
                    itemsTransition.add(Pair.create(finalItemViews[0].findViewById(R.id.tvArtist), "playback:firstitem:artist"));
                    itemsTransition.add(Pair.create(finalItemViews[0].findViewById(R.id.ivAlbumArt), "playback:firstitem:art"));

                    // FIXME: For some reason, List.toArray doesn't work for generic types... So
                    // we manually copy.
                    Pair<View, String>[] viewsToTransition = new Pair[itemsTransition.size()];
                    int i = 0;
                    for (Pair<View, String> pair : itemsTransition) {
                        viewsToTransition[i] = pair;
                        i++;
                    }

                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                        /* ActivityOptions opt = ActivityOptions.makeSceneTransitionAnimation((Activity) getContext(),
                                viewsToTransition);

                        getContext().startActivity(intent, opt.toBundle()); */
                    } else {
                        getContext().startActivity(intent);
                    }
                }
            });

            // Update wrap status
            setWrapped(mWrapped, false);
            setFabVisible(true);
        } else {
            mWrapped = true;
            mTracksLayout.setVisibility(View.GONE);
            setFabVisible(false);
        }
    }

    public void setFabVisible(boolean visible) {
        if (visible && mPlayFab.getVisibility() == View.VISIBLE
                || !visible && mPlayFab.getVisibility() == View.INVISIBLE) {
            return;
        }
        int startRadius;
        int finalRadius;

        // get the center for the clipping circle
        final int cx = mPlayFab.getMeasuredWidth() / 2;
        final int cy = mPlayFab.getMeasuredHeight() / 2;

        if (visible) {
            mPlayFab.setVisibility(View.VISIBLE);

            startRadius = 0;
            finalRadius = mPlayFab.getWidth();
        } else {
            startRadius = mPlayFab.getWidth();
            finalRadius = 0;
        }

        // create and start the animator for this view (the start radius is zero)
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
            /* ValueAnimator anim =
                    ViewAnimationUtils.createCircularReveal(mPlayFab, cx, cy, 0, finalRadius);
            anim.setInterpolator(new DecelerateInterpolator());

            if (!visible) {
                anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                        mPlayFab.setVisibility(View.INVISIBLE);
                    }
                });
            }

            anim.start(); */
        } else {
            // TODO: Kitkat animation
        }

    }

    public void setWrapped(boolean wrapped, boolean animation) {
        if (wrapped && mLastQueue != null) {
            final int itemHeight = getResources().getDimensionPixelSize(R.dimen.playing_bar_height);
            final int translationY = itemHeight * Math.min(mLastQueue.size(), MAX_PEEK_QUEUE_SIZE);
            if (animation) {
                animate().translationY(translationY)
                        .setDuration(mAnimationDuration)
                        .start();
            } else {
                setTranslationY(translationY);
            }
        } else {
            if (animation) {
                animate().translationY(0).setDuration(mAnimationDuration).start();
            } else {
                setTranslationY(0);
            }
        }

        mWrapped = wrapped;
    }

    public void setWrapped(boolean wrapped) {
        setWrapped(wrapped, true);
    }

    public boolean isWrapped() {
        return mWrapped;
    }
}
