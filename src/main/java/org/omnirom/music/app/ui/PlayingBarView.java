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

import android.animation.Animator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.v7.graphics.Palette;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.getbase.floatingactionbutton.FloatingActionButton;

import org.omnirom.music.app.AlbumActivity;
import org.omnirom.music.app.PlaybackQueueActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.utils.Utils;
import org.omnirom.music.framework.PlaybackProxy;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.SearchResult;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ILocalCallback;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.service.BasePlaybackCallback;
import org.omnirom.music.service.PlaybackService;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * ViewGroup for the sticky bottom playing bar
 */
public class PlayingBarView extends RelativeLayout {
    private static final String TAG = "PlayingBarView";

    // Delay after which the seek bar is updated (10Hz)
    private static final int SEEK_BAR_UPDATE_DELAY = 1000 / 10;

    private static class PlayingBarHandler extends Handler {
        private WeakReference<PlayingBarView> mParent;

        public PlayingBarHandler(WeakReference<PlayingBarView> parent) {
            mParent = parent;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_QUEUE:
                    mParent.get().mProgressDrawable.setMax(PlaybackProxy.getCurrentTrackLength());
                    mParent.get().updatePlayingQueue();
                    break;

                case MSG_UPDATE_SEEK:
                    mParent.get().updateSeekBar();
                    break;

                case MSG_UPDATE_FAB:
                    mParent.get().updatePlayFab();
                    break;
            }
        }
    }

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
        public void onSearchResult(List<SearchResult> searchResult) {
        }
    };

    private BasePlaybackCallback mPlaybackCallback = new BasePlaybackCallback() {
        @Override
        public void onSongStarted(final boolean buffering, Song s) throws RemoteException {
            mHandler.sendEmptyMessage(MSG_UPDATE_FAB);
            mHandler.sendEmptyMessageDelayed(MSG_UPDATE_SEEK, SEEK_BAR_UPDATE_DELAY);
        }

        @Override
        public void onPlaybackPause() throws RemoteException {
            mHandler.sendEmptyMessage(MSG_UPDATE_FAB);
        }

        @Override
        public void onPlaybackResume() throws RemoteException {
            mHandler.sendEmptyMessage(MSG_UPDATE_FAB);
        }

        @Override
        public void onPlaybackQueueChanged() throws RemoteException {
            mHandler.sendEmptyMessage(MSG_UPDATE_QUEUE);
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

    private static final int MSG_UPDATE_QUEUE = 1;
    private static final int MSG_UPDATE_SEEK = 2;
    private static final int MSG_UPDATE_FAB = 3;

    private boolean mIsPlaying;
    private LinearLayout mTracksLayout;
    private ImageButton mPlayFab;
    private PlayPauseDrawable mPlayFabDrawable;
    private CircularProgressDrawable mProgressDrawable;
    private List<Song> mLastQueue;
    private PlayingBarHandler mHandler;
    private int mAnimationDuration;
    private boolean mWrapped;
    private boolean mPlayInSeekMode;
    private boolean mSkipFabAction;

    public PlayingBarView(Context context) {
        super(context);
        init();
    }

    public PlayingBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PlayingBarView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        mAnimationDuration = getResources().getInteger(android.R.integer.config_mediumAnimTime);
        mGestureDetector = new GestureDetector(getContext(), mGestureListener);
        mHandler = new PlayingBarHandler(new WeakReference<>(this));
    }

    public void onPause() {
        mHandler.removeMessages(MSG_UPDATE_QUEUE);
        mHandler.removeMessages(MSG_UPDATE_SEEK);
        mHandler.removeMessages(MSG_UPDATE_FAB);

        PlaybackProxy.removeCallback(mPlaybackCallback);
        ProviderAggregator.getDefault().removeUpdateCallback(mProviderCallback);
    }

    public void onResume() {
        ProviderAggregator.getDefault().addUpdateCallback(mProviderCallback);

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                PlaybackProxy.addCallback(mPlaybackCallback);

                // We delay check if we have a queue and/or are playing to leave time to the
                // playback service to get up
                if (mTracksLayout != null) {
                    mHandler.sendEmptyMessage(MSG_UPDATE_QUEUE);
                    mHandler.sendEmptyMessage(MSG_UPDATE_FAB);
                    mHandler.sendEmptyMessageDelayed(MSG_UPDATE_SEEK, SEEK_BAR_UPDATE_DELAY);
                }
            }
        }, 200);

    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        // Set FAB info
        mPlayFab = (FloatingActionButton) findViewById(R.id.fabPlayBarButton);

        mProgressDrawable = new CircularProgressDrawable();
        mProgressDrawable.setValue(0);
        mProgressDrawable.setColor(getResources().getColor(R.color.white));
        mProgressDrawable.setAlpha(120);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mProgressDrawable.setPadding(Utils.dpToPx(getResources(), 1));
        } else {
            mProgressDrawable.setPadding(Utils.dpToPx(getResources(), 12));
        }

        mPlayFabDrawable = new PlayPauseDrawable(getResources(), 0.70f);
        mPlayFabDrawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
        mPlayFabDrawable.setYOffset(6);

        LayerDrawable drawable = new LayerDrawable(new Drawable[]{
                mProgressDrawable, mPlayFabDrawable
        });
        mPlayFab.setImageDrawable(drawable);
        mPlayFab.setVisibility(View.GONE);

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
        onResume();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        onPause();
    }

    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent ev) {
        mGestureDetector.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    public void updateSeekBar() {
        int state = PlaybackProxy.getState();
        if (state == PlaybackService.STATE_PLAYING
                || state == PlaybackService.STATE_PAUSING
                || state == PlaybackService.STATE_PAUSED) {
            if (!mPlayInSeekMode) {
                mProgressDrawable.setValue(PlaybackProxy.getCurrentTrackPosition());
            }

            // Restart ourselves
            mHandler.sendEmptyMessageDelayed(MSG_UPDATE_SEEK, SEEK_BAR_UPDATE_DELAY);
        } else if (state == PlaybackService.STATE_BUFFERING) {
            mProgressDrawable.setValue(0);
        } else {
            mProgressDrawable.setMax(1);
            mProgressDrawable.setValue(1);
        }
    }

    public void updatePlayingQueue() {
        List<Song> queue;
        int currentIndex;

        // Do a copy
        queue = new ArrayList<>(PlaybackProxy.getCurrentPlaybackQueue());
        currentIndex = PlaybackProxy.getCurrentTrackIndex();

        if (queue.size() > 0) {
            mLastQueue = new ArrayList<>(queue);
            mTracksLayout.removeAllViews();
            mTracksLayout.setVisibility(View.VISIBLE);

            // Inflate views and make the list out of the first 4 items (or less)
            int shownCount = 0;
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    itemRoot.setTransitionName("playbackqueue:preview:" + shownCount);
                }
                mTracksLayout.addView(itemRoot);

                TextView tvArtist = (TextView) itemRoot.findViewById(R.id.tvArtist);
                TextView tvTitle = (TextView) itemRoot.findViewById(R.id.tvTitle);
                AlbumArtImageView ivAlbumArt = (AlbumArtImageView) itemRoot.findViewById(R.id.ivAlbumArt);
                ivAlbumArt.setCrossfade(true);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    tvArtist.setTransitionName("playbackqueue:preview:" + shownCount + ":artist");
                    tvTitle.setTransitionName("playbackqueue:preview:" + shownCount + ":title");
                    ivAlbumArt.setTransitionName("playbackqueue:preview:" + shownCount + ":art");
                }

                if (song != null && song.isLoaded() && song.getArtist() != null) {
                    Artist artist = aggregator.retrieveArtist(song.getArtist(), song.getProvider());
                    if (artist != null) {
                        tvArtist.setText(artist.getName());
                    } else {
                        tvArtist.setText(getContext().getString(R.string.loading));
                    }
                } else if (song != null && song.isLoaded()) {
                    tvArtist.setText(null);
                } else {
                    tvArtist.setText(R.string.loading);
                }

                if (song != null) {
                    tvTitle.setText(song.getTitle());

                    ivAlbumArt.loadArtForSong(song);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        ivAlbumArt.setTransitionName(song.getRef() + shownCount);
                    }
                }

                // Set root view click listener
                itemRoot.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        // Play that song now
                        if (song != null && song.equals(PlaybackProxy.getCurrentTrack())) {
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
                        if (song == null || song.getAlbum() == null) {
                            Toast.makeText(view.getContext(),
                                    R.string.toast_song_no_album, Toast.LENGTH_SHORT).show();
                            return;
                        }


                        Bitmap hero = ((MaterialTransitionDrawable) ((ImageView) view).getDrawable()).getFinalDrawable().getBitmap();
                        if (mPalette == null) {
                            mPalette = Palette.generate(hero);
                        }
                        Palette.Swatch darkVibrantColor = mPalette.getDarkVibrantSwatch();
                        Palette.Swatch darkMutedColor = mPalette.getDarkMutedSwatch();

                        int color;
                        if (darkVibrantColor != null) {
                            color = darkVibrantColor.getRgb();
                        } else if (darkMutedColor != null) {
                            color = darkMutedColor.getRgb();
                        } else {
                            color = getResources().getColor(R.color.default_album_art_background);
                        }


                        Intent intent = AlbumActivity.craftIntent(getContext(), hero, song.getAlbum(),
                                song.getProvider(), color);

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            ActivityOptions opt = ActivityOptions.makeSceneTransitionAnimation((Activity) getContext(),
                                view, "itemImage");

                            getContext().startActivity(intent, opt.toBundle());
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
            final View itemRoot = inflater.inflate(R.layout.item_playbar_action, mTracksLayout, false);
            mTracksLayout.addView(itemRoot);

            TextView tvTitle = (TextView) itemRoot.findViewById(R.id.tvTitle);
            tvTitle.setText(getContext().getString(R.string.view_full_queue));

            itemRoot.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(getContext(), PlaybackQueueActivity.class);
                    getContext().startActivity(intent);
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

    public void updatePlayFab() {
        int state = PlaybackProxy.getState();

        switch (state) {
            case PlaybackService.STATE_PAUSED:
            case PlaybackService.STATE_STOPPED:
                mPlayFabDrawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
                mPlayFabDrawable.setBuffering(false);
                mIsPlaying = false;
                break;

            case PlaybackService.STATE_BUFFERING:
            case PlaybackService.STATE_PAUSING:
                mPlayFabDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                mPlayFabDrawable.setBuffering(true);
                mIsPlaying = true;
                break;

            case PlaybackService.STATE_PLAYING:
                mPlayFabDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                mPlayFabDrawable.setBuffering(false);
                mIsPlaying = true;
                break;
        }
    }

    public void setFabVisible(boolean visible) {
        if (visible && mPlayFab.getVisibility() == View.VISIBLE
                || !visible && mPlayFab.getVisibility() == View.GONE) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (mPlayFab.isAttachedToWindow()) {
                Animator anim =
                        ViewAnimationUtils.createCircularReveal(mPlayFab, cx, cy, startRadius, finalRadius);
                anim.setInterpolator(new DecelerateInterpolator());

                if (!visible) {
                    anim.addListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            mPlayFab.setVisibility(View.GONE);
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {

                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {

                        }

                        @Override
                        public void onAnimationRepeat(Animator animation) {

                        }
                    });
                }

                anim.start();
            }
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
