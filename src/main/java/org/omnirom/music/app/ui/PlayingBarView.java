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

package org.omnirom.music.app.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.v7.graphics.Palette;
import android.support.v7.graphics.PaletteItem;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.getbase.floatingactionbutton.FloatingActionButton;

import org.omnirom.music.app.AlbumActivity;
import org.omnirom.music.app.PlaybackQueueActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.SearchResult;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ILocalCallback;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.framework.PlaybackProxy;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.service.IPlaybackCallback;
import org.omnirom.music.service.IPlaybackService;
import org.omnirom.music.service.PlaybackService;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewGroup for the sticky bottom playing bar
 */
public class PlayingBarView extends RelativeLayout {

    private static final String TAG = "PlayingBarView";

    // Delay after which the seek bar is updated (30Hz)
    private static final int SEEK_BAR_UPDATE_DELAY = 1000 / 30;

    private Runnable mUpdateSeekBarRunnable = new Runnable() {
        @Override
        public void run() {
            int state = PlaybackProxy.getState();
            if (state == PlaybackService.STATE_PLAYING
                    || state == PlaybackService.STATE_PAUSING
                    || state == PlaybackService.STATE_PAUSED) {
                if (!mPlayInSeekMode) {
                    mProgressDrawable.setValue(PlaybackProxy.getCurrentTrackPosition());
                }

                // Restart ourselves
                mHandler.postDelayed(mUpdateSeekBarRunnable, SEEK_BAR_UPDATE_DELAY);
            } else if (state == PlaybackService.STATE_BUFFERING) {
                mProgressDrawable.setValue(0);
            } else {
                mProgressDrawable.setMax(1);
                mProgressDrawable.setValue(1);
            }
        }
    };

    private IPlaybackCallback.Stub mPlaybackCallback = new IPlaybackCallback.Stub() {

        @Override
        public void onSongStarted(final boolean buffering, Song s) throws RemoteException {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    updatePlayingQueue();
                    mProgressDrawable.setMax(PlaybackProxy.getCurrentTrackLength());

                    // Set the visibility and button state
                    setPlayButtonState(buffering, false);
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
                    setPlayButtonState(false, true);
                    mIsPlaying = false;
                }
            });
        }

        @Override
        public void onPlaybackResume() throws RemoteException {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    setPlayButtonState(false, false);
                    mIsPlaying = true;
                }
            });
        }

        @Override
        public void onPlaybackQueueChanged() throws RemoteException {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    updatePlayingQueue();
                }
            });
        }
    };

    private ILocalCallback mProviderCallback = new ILocalCallback() {
        @Override
        public void onSongUpdate(List<Song> s) {
            boolean contains = false;
            List<Song> playbackQueue = PlaybackProxy.getCurrentPlaybackQueue();
            for (Song song : s) {
                if (playbackQueue.contains(song)) {
                    contains = true;
                    break;
                }
            }

            if (contains) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        updatePlayingQueue();
                    }
                });

            }
        }

        @Override
        public void onAlbumUpdate(List<Album> a) {
        }

        @Override
        public void onPlaylistUpdate(List<Playlist> p) {
        }

        @Override
        public void onArtistUpdate(List<Artist> a) {
            boolean contains = false;
            List<Song> playbackQueue = PlaybackProxy.getCurrentPlaybackQueue();
            for (Song song : playbackQueue) {
                for (Artist artist : a) {
                    if (artist.getRef().equals(song.getArtist())) {
                        contains = true;
                        break;
                    }
                }

                if (contains) {
                    break;
                }
            }

            if (contains) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        updatePlayingQueue();
                    }
                });

            }
        }

        @Override
        public void onProviderConnected(IMusicProvider provider) {
        }

        @Override
        public void onSearchResult(SearchResult searchResult) {
        }
    };

    private GestureDetector mGestureDetector;
    private GestureDetector.SimpleOnGestureListener mGestureListener =
            new GestureDetector.SimpleOnGestureListener() {
                public boolean onSingleTapUp(MotionEvent ev) {
                    return false;
                }

                public void onLongPress(MotionEvent ev) {
                }

                public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
                                        float distanceY) {
                    return false;
                }

                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                                       float velocityY) {
                    if (velocityY > 0) {
                        setWrapped(true);
                    } else {
                        setWrapped(false);
                    }
                    return true;
                }
            };

    private static final int MAX_PEEK_QUEUE_SIZE = 3;

    private boolean mIsPlaying;
    private LinearLayout mTracksLayout;
    private ImageButton mPlayFab;
    private PlayPauseDrawable mPlayFabDrawable;
    private CircularProgressDrawable mProgressDrawable;
    private List<Song> mLastQueue;
    private Handler mHandler = new Handler();
    private final int mAnimationDuration;
    private boolean mWrapped;
    private boolean mPlayInSeekMode;
    private boolean mSkipFabAction;

    public PlayingBarView(Context context) {
        super(context);
        mAnimationDuration = getResources().getInteger(android.R.integer.config_mediumAnimTime);
        mGestureDetector = new GestureDetector(getContext(), mGestureListener);
    }

    public PlayingBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mAnimationDuration = getResources().getInteger(android.R.integer.config_mediumAnimTime);
        mGestureDetector = new GestureDetector(getContext(), mGestureListener);
    }

    public PlayingBarView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mAnimationDuration = getResources().getInteger(android.R.integer.config_mediumAnimTime);
        mGestureDetector = new GestureDetector(getContext(), mGestureListener);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        ProviderAggregator.getDefault().addUpdateCallback(mProviderCallback);

        // Setup the Playback service callback
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                PlaybackProxy.addCallback(mPlaybackCallback);
            }
        }, 200);


        // Set FAB info
        mPlayFab = (FloatingActionButton) findViewById(R.id.fabPlayBarButton);

        mProgressDrawable = new CircularProgressDrawable();
        mProgressDrawable.setValue(0);
        mProgressDrawable.setColor(getResources().getColor(R.color.white));
        mProgressDrawable.setAlpha(120);
        mProgressDrawable.setPadding(Utils.dpToPx(getResources(), 12));

        mPlayFabDrawable = new PlayPauseDrawable(getResources());
        mPlayFabDrawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
        mPlayFabDrawable.setYOffset(6);

        // Set the original state
        if (PlaybackProxy.getState() == PlaybackService.STATE_PLAYING) {
            mPlayFabDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
            mIsPlaying = true;
            mProgressDrawable.setMax(PlaybackProxy.getCurrentTrackLength());
            mHandler.postDelayed(mUpdateSeekBarRunnable, SEEK_BAR_UPDATE_DELAY);
        } else {
            mPlayFabDrawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
            mIsPlaying = false;
        }

        LayerDrawable drawable = new LayerDrawable(new Drawable[]{
                mProgressDrawable, mPlayFabDrawable
        });
        mPlayFab.setImageDrawable(drawable);
        mPlayFab.setVisibility(View.INVISIBLE);

        mPlayFab.setOnTouchListener(new OnTouchListener() {
            private boolean mIsDragging = false;
            private float mStartY;
            private float mSeekValue;

            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                boolean result = false;
                boolean isPlaying = (PlaybackProxy.getState() == PlaybackService.STATE_PLAYING);

                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN && isPlaying) {
                    mIsDragging = true;
                    mStartY = motionEvent.getY();
                } else if (motionEvent.getAction() == MotionEvent.ACTION_MOVE && mIsDragging && isPlaying) {
                    float deltaStart = motionEvent.getY() - mStartY;
                    if (Math.abs(deltaStart) > view.getMeasuredHeight() / 2.0f) {
                        // We're dragging the play button, start seek mode
                        mPlayInSeekMode = true;
                        mSeekValue = Math.max(0,
                                Math.min(-deltaStart * 500.0f, mProgressDrawable.getMax()));
                        mProgressDrawable.setValue(mSeekValue);
                    }
                    result = true;
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    if (mPlayInSeekMode) {
                        mPlayInSeekMode = false;
                        PlaybackProxy.seek((long) mSeekValue);

                        // If we consume the up action, we cannot "unselect"/"unfocus" the FAB
                        // and it remains in a "selected" state after we lift our finger. We
                        // work around this issue by skipping the FAB action once
                        mSkipFabAction = true;
                    }
                }

                return result;
            }
        });

        mPlayFab.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mSkipFabAction) {
                    mSkipFabAction = false;
                } else {
                    if (mIsPlaying) {
                        PlaybackProxy.pause();
                    } else {
                        PlaybackProxy.play();
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
        PlaybackProxy.removeCallback(mPlaybackCallback);
        ProviderAggregator.getDefault().removeUpdateCallback(mProviderCallback);
    }

    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent ev) {
        mGestureDetector.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    /**
     * Defines the visual state of the play/pause button
     *
     * @param play true will set the image to a "play" image, false will set to "pause"
     */
    public void setPlayButtonState(boolean buffering, boolean play) {
        if (play) {
            mPlayFabDrawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
        } else {
            mPlayFabDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
        }

        mPlayFabDrawable.setBuffering(buffering);
    }

    public void updatePlayingQueue() {
        List<Song> queue;
        int currentIndex;

        // Do a copy
        queue = new ArrayList<Song>(PlaybackProxy.getCurrentPlaybackQueue());
        currentIndex = PlaybackProxy.getCurrentTrackIndex();

        if (queue.size() > 0) {
            mLastQueue = new ArrayList<Song>(queue);
            mTracksLayout.removeAllViews();
            mTracksLayout.setVisibility(View.VISIBLE);

            // Inflate views and make the list out of the first 4 items (or less)
            int shownCount = 0;
            View itemViews[] = new View[MAX_PEEK_QUEUE_SIZE];
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            final ProviderAggregator aggregator = ProviderAggregator.getDefault();

            for (int i = 0; i < currentIndex; ++i) {
                queue.remove(0);
                mLastQueue.remove(0);
            }
            final int removedCount = currentIndex;

            for (final Song song : queue) {
                if (shownCount == MAX_PEEK_QUEUE_SIZE) {
                    break;
                }

                final int itemIndex = shownCount;
                View itemRoot = inflater.inflate(R.layout.item_playbar, mTracksLayout, false);
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                    // itemRoot.setViewName("playbackqueue:preview:" + shownCount);
                }
                mTracksLayout.addView(itemRoot);
                itemViews[shownCount] = itemRoot;

                TextView tvArtist = (TextView) itemRoot.findViewById(R.id.tvArtist);
                TextView tvTitle = (TextView) itemRoot.findViewById(R.id.tvTitle);
                AlbumArtImageView ivAlbumArt = (AlbumArtImageView) itemRoot.findViewById(R.id.ivAlbumArt);
                ivAlbumArt.setCrossfade(true);

                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                    //tvArtist.setViewName("playbackqueue:preview:" + shownCount + ":artist");
                    //tvTitle.setViewName("playbackqueue:preview:" + shownCount + ":title");
                    //ivAlbumArt.setViewName("playbackqueue:preview:" + shownCount + ":art");
                }

                if (song.isLoaded() && song.getArtist() != null) {
                    Artist artist = aggregator.retrieveArtist(song.getArtist(), song.getProvider());
                    if (artist != null) {
                        tvArtist.setText(artist.getName());
                    } else {
                        tvArtist.setText(getContext().getString(R.string.loading));
                    }
                } else if (song.isLoaded()) {
                    tvArtist.setText(null);
                } else {
                    tvArtist.setText(R.string.loading);
                }

                tvTitle.setText(song.getTitle());
                ivAlbumArt.loadArtForSong(song);
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                    // ivAlbumArt.setViewName(song.getRef() + shownCount);
                }

                // Set root view click listener
                itemRoot.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        // Play that song now
                        if (song.equals(PlaybackProxy.getCurrentTrack())) {
                            // We're already playing that song, play it again
                            PlaybackProxy.seek(0);
                        } else {
                            PlaybackProxy.playAtIndex(itemIndex + removedCount);
                        }
                    }
                });

                // Set album art click listener
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

                        Album album = aggregator.retrieveAlbum(song.getAlbum(), song.getProvider());
                        if (album != null) {
                            Intent intent = AlbumActivity.craftIntent(getContext(), hero, album, color);

                            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                            /* ActivityOptions opt = ActivityOptions.makeSceneTransitionAnimation((Activity) getContext(),
                                    view, "itemImage");

                            getContext().startActivity(intent, opt.toBundle()); */
                            } else {
                                getContext().startActivity(intent);
                            }
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
            final View itemRoot = inflater.inflate(R.layout.item_playbar_action, mTracksLayout, false);
            mTracksLayout.addView(itemRoot);

            TextView tvArtist = (TextView) itemRoot.findViewById(R.id.tvArtist);
            TextView tvTitle = (TextView) itemRoot.findViewById(R.id.tvTitle);
            ImageView ivActionIcon = (ImageView) itemRoot.findViewById(R.id.ivActionIcon);

            tvArtist.setVisibility(View.GONE);
            tvTitle.setText(getContext().getString(R.string.view_full_queue));
            ivActionIcon.setImageResource(R.drawable.ic_reduce);
            ivActionIcon.setScaleType(ImageView.ScaleType.CENTER);

            ivActionIcon.setOnClickListener(new OnClickListener() {
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

                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
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

    public boolean isVisible() {
        return mTracksLayout.getVisibility() == View.VISIBLE;
    }
}
