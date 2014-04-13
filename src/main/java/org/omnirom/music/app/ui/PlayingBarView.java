package org.omnirom.music.app.ui;

import android.content.Context;
import android.os.Handler;
import android.os.RemoteException;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.omnirom.music.app.R;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.service.IPlaybackCallback;
import org.omnirom.music.service.IPlaybackService;

/**
 * ViewGroup for the sticky bottom playing bar
 */
public class PlayingBarView extends RelativeLayout {

    private static final String TAG = "PlayingBarView";

    private IPlaybackCallback.Stub mPlaybackCallback = new IPlaybackCallback.Stub() {

        @Override
        public void onSongStarted(Song s) throws RemoteException {
            mTitleView.setText(s.getTitle());

            Artist artist = ProviderAggregator.getDefault().getCache().getArtist(s.getArtist());
            mArtistView.setText(artist.getName());

            mScrobble.setMax(s.getDuration());

            setPlayButtonState(false);
            animateVisibility(true);
            mIsPlaying = true;
        }

        @Override
        public void onSongScrobble(int timeMs) throws RemoteException {
            mScrobble.setProgress(timeMs);
        }

        @Override
        public void onPlaybackPause() throws RemoteException {
            setPlayButtonState(true);
            mIsPlaying = false;
        }

        @Override
        public void onPlaybackResume() throws RemoteException {
            setPlayButtonState(false);
            animateVisibility(true);
            mIsPlaying = true;
        }
    };

    private boolean mIsPlaying;
    private ProgressBar mScrobble;
    private ImageView mPlayPauseButton;
    private ImageView mAlbumArt;
    private TextView mArtistView;
    private TextView mTitleView;
    private Handler mHandler = new Handler();
    private final int mAnimationDuration;

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
                        Log.e(TAG, "Unable to register the main activity as a callback of the playback", e);
                    }
                } else {
                    // Retry, playback connection not established yet
                    mHandler.post(this);
                }
            }
        });

        mScrobble           = (ProgressBar) findViewById(R.id.pbScrobble);
        mPlayPauseButton    = (ImageView)   findViewById(R.id.btnPlay);
        mAlbumArt           = (ImageView)   findViewById(R.id.ivAlbumArt);
        mArtistView         = (TextView)    findViewById(R.id.tvArtist);
        mTitleView          = (TextView)    findViewById(R.id.tvTitle);

        // Setup click listeners
        mPlayPauseButton.setOnClickListener(new OnClickListener() {
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
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        try {
            PluginsLookup.getDefault().getPlaybackService().removeCallback(mPlaybackCallback);
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
            mPlayPauseButton.setImageResource(R.drawable.ic_btn_play);
        } else {
            mPlayPauseButton.setImageResource(R.drawable.ic_btn_pause);
        }
    }

    public void animateVisibility(boolean visible) {
        if (visible) {
            animate().translationY(0)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .setDuration(mAnimationDuration).start();
        } else {
            animate().translationY(getMeasuredHeight())
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .setDuration(mAnimationDuration).start();
        }
    }
}
