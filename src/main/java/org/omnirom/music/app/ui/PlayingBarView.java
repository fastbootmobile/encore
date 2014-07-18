package org.omnirom.music.app.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.RemoteException;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.framework.AlbumArtCache;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderCache;
import org.omnirom.music.service.IPlaybackCallback;
import org.omnirom.music.service.IPlaybackService;

import java.security.Provider;
import java.util.ArrayList;
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

    private Runnable mAlbumArtRunnable = new Runnable() {
        @Override
        public void run() {
            final ProviderCache cache = ProviderAggregator.getDefault().getCache();
            final Song startSong = mCurrentSong;

            // Prepare the placeholder/default
            BitmapDrawable drawable = (BitmapDrawable) getResources().getDrawable(R.drawable.album_list_default_bg);
            assert drawable != null;
            Bitmap bmp = drawable.getBitmap();

            // Download the art image
            String artKey = cache.getSongArtKey(mCurrentSong);
            String artUrl = null;

            if (artKey == null) {
                StringBuffer urlBuffer = new StringBuffer();
                artKey = AlbumArtCache.getDefault().getArtKey(mCurrentSong, urlBuffer);
                artUrl = urlBuffer.toString();
            }

            if (artKey != null && !artKey.equals(AlbumArtCache.DEFAULT_ART)) {
                bmp = AlbumArtCache.getOrDownloadArt(artKey, artUrl, bmp);
            }

            final Bitmap albumArtBitmap = bmp;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (startSong == mCurrentSong) {
                        // mAlbumArt.setImageBitmap(albumArtBitmap);
                    }
                }
            });
        }
    };

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

            mHandler.postDelayed(mUpdateSeekBarRunnable, SEEK_BAR_UPDATE_DELAY);
        }

        @Override
        public void onSongScrobble(int timeMs) throws RemoteException {
            /*mScrobble.setProgress(timeMs);*/
        }

        @Override
        public void onPlaybackPause() throws RemoteException {
            setPlayButtonState(true);
            mIsPlaying = false;
        }

        @Override
        public void onPlaybackResume() throws RemoteException {
            setPlayButtonState(false);
            mIsPlaying = true;
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
            int i = 0;
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            ProviderCache cache = ProviderAggregator.getDefault().getCache();
            for (Song song : queue) {
                if (i == MAX_PEEK_QUEUE_SIZE) {
                    break;
                }

                View itemRoot = inflater.inflate(R.layout.item_playbar, mTracksLayout, false);
                mTracksLayout.addView(itemRoot);

                TextView tvArtist = (TextView) itemRoot.findViewById(R.id.tvArtist);
                TextView tvTitle = (TextView) itemRoot.findViewById(R.id.tvTitle);
                ImageView ivAlbumArt = (ImageView) itemRoot.findViewById(R.id.ivAlbumArt);

                Artist artist = cache.getArtist(song.getArtist());
                if (artist != null) {
                    tvArtist.setText(artist.getName());
                } else {
                    tvArtist.setText(getContext().getString(R.string.loading));
                }

                tvTitle.setText(song.getTitle());

                // TODO: ivAlbumArt
                ivAlbumArt.setImageResource(R.drawable.album_placeholder);

                if (i == 0) {
                    itemRoot.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            if (mWrapped) {
                                setWrapped(false);
                            }
                        }
                    });
                }

                i++;
            }

            // Add the "View full queue" item entry
            View itemRoot = inflater.inflate(R.layout.item_playbar, mTracksLayout, false);
            mTracksLayout.addView(itemRoot);

            TextView tvArtist = (TextView) itemRoot.findViewById(R.id.tvArtist);
            TextView tvTitle = (TextView) itemRoot.findViewById(R.id.tvTitle);
            ImageView ivAlbumArt = (ImageView) itemRoot.findViewById(R.id.ivAlbumArt);

            tvArtist.setVisibility(View.GONE);
            tvTitle.setText("View full queue...");
            ivAlbumArt.setImageResource(R.drawable.ic_reduce);
            ivAlbumArt.setScaleType(ImageView.ScaleType.CENTER);

            ivAlbumArt.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    setWrapped(true);
                }
            });

            // Update wrap status
            setWrapped(mWrapped, false);
        } else {
            mWrapped = true;
            mTracksLayout.setVisibility(View.GONE);
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
