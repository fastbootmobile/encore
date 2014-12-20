package org.omnirom.music.app;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.omnirom.music.app.ui.AlbumArtImageView;
import org.omnirom.music.app.ui.PlayPauseDrawable;
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

import java.util.List;


/**
 *
 */
public class DriveModeActivity extends AppActivity implements ILocalCallback, View.OnClickListener {
    private static final String TAG = "DriveModeActivity";

    private static final int MSG_UPDATE_PLAYBACK_STATUS = 1;

    private Handler mHandler;
    private DrivePlaybackCallback mPlaybackCallback;
    private View mDecorView;
    private PlayPauseDrawable mPlayDrawable;
    private ImageView mPlayButton;
    private ImageView mPreviousButton;
    private ImageView mSkipButton;
    private ImageView mVoiceButton;
    private ImageView mThumbsButton;
    private TextView mTvTitle;
    private TextView mTvArtist;
    private AlbumArtImageView mIvAlbumArt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MSG_UPDATE_PLAYBACK_STATUS) {
                    updatePlaybackStatus();
                }
            }
        };
        mPlaybackCallback = new DrivePlaybackCallback();

        setContentView(R.layout.activity_drive_mode);

        mDecorView = findViewById(R.id.rlDriveRoot);
        mPlayButton = (ImageView) findViewById(R.id.btnPlayPause);
        mPreviousButton = (ImageView) findViewById(R.id.btnPrevious);
        mSkipButton = (ImageView) findViewById(R.id.btnNext);
        mVoiceButton = (ImageView) findViewById(R.id.btnVoice);
        mThumbsButton = (ImageView) findViewById(R.id.btnThumbs);
        mTvTitle = (TextView) findViewById(R.id.tvTitle);
        mTvArtist = (TextView) findViewById(R.id.tvArtist);
        mIvAlbumArt = (AlbumArtImageView) findViewById(R.id.ivAlbumArt);

        mPlayDrawable = new PlayPauseDrawable(getResources(), 1.5f, 1.6f);
        mPlayDrawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
        mPlayButton.setImageDrawable(mPlayDrawable);

        mPlayButton.setOnClickListener(this);
        mPreviousButton.setOnClickListener(this);
        mSkipButton.setOnClickListener(this);
        mThumbsButton.setOnClickListener(this);
        mVoiceButton.setOnClickListener(this);

        hideSystemUI();
        mHandler.sendEmptyMessage(MSG_UPDATE_PLAYBACK_STATUS);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void hideSystemUI() {
        int stickyFlag = 0;
        if (Utils.hasKitKat()) {
            stickyFlag = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }

        mDecorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                        | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                        | stickyFlag);
    }

    private void showSystemUI() {
        mDecorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    private void updatePlaybackStatus() {
        int state = PlaybackProxy.getState();

        switch (state) {
            case PlaybackService.STATE_STOPPED:
            case PlaybackService.STATE_PAUSED:
                mPlayDrawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
                mPlayDrawable.setBuffering(false);
                break;

            case PlaybackService.STATE_BUFFERING:
                mPlayDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                mPlayDrawable.setBuffering(true);
                break;

            case PlaybackService.STATE_PAUSING:
                mPlayDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                mPlayDrawable.setBuffering(true);
                break;

            case PlaybackService.STATE_PLAYING:
                mPlayDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                mPlayDrawable.setBuffering(false);
                break;
        }

        Song currentTrack = PlaybackProxy.getCurrentTrack();

        if (currentTrack != null && currentTrack.isLoaded()) {
            mTvTitle.setText(currentTrack.getTitle());
            Artist artist = ProviderAggregator.getDefault().retrieveArtist(currentTrack.getArtist(),
                    currentTrack.getProvider());

            if (artist != null && artist.getName() != null && !artist.getName().isEmpty()) {
                mTvArtist.setText(artist.getName());
            } else {
                mTvArtist.setText(R.string.loading);
            }
            mIvAlbumArt.loadArtForSong(currentTrack);
        } else if (currentTrack != null) {
            mTvTitle.setText(R.string.loading);
            mTvArtist.setText(null);
            mIvAlbumArt.setDefaultArt();
        } else {
            // TODO: No song playing
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        ProviderAggregator.getDefault().removeUpdateCallback(this);
        PlaybackProxy.removeCallback(mPlaybackCallback);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ProviderAggregator.getDefault().addUpdateCallback(this);
        PlaybackProxy.addCallback(mPlaybackCallback);
    }

    @Override
    public void onClick(View v) {
        if (v == mPlayButton) {
            final int state = PlaybackProxy.getState();
            switch (state) {
                case PlaybackService.STATE_PLAYING:
                case PlaybackService.STATE_BUFFERING:
                    PlaybackProxy.pause();
                    break;

                default:
                    PlaybackProxy.play();
                    break;
            }
        }
    }

    @Override
    public void onSongUpdate(List<Song> s) {

    }

    @Override
    public void onAlbumUpdate(List<Album> a) {

    }

    @Override
    public void onPlaylistUpdate(List<Playlist> p) {

    }

    @Override
    public void onArtistUpdate(List<Artist> a) {

    }

    @Override
    public void onProviderConnected(IMusicProvider provider) {

    }

    @Override
    public void onSearchResult(SearchResult searchResult) {

    }

    private class DrivePlaybackCallback extends BasePlaybackCallback {
        @Override
        public void onPlaybackPause() throws RemoteException {
            if (!mHandler.hasMessages(MSG_UPDATE_PLAYBACK_STATUS)) {
                mHandler.sendEmptyMessage(MSG_UPDATE_PLAYBACK_STATUS);
            }
        }

        @Override
        public void onSongStarted(final boolean buffering, Song s) throws RemoteException {
            if (!mHandler.hasMessages(MSG_UPDATE_PLAYBACK_STATUS)) {
                mHandler.sendEmptyMessage(MSG_UPDATE_PLAYBACK_STATUS);
            }
        }

        @Override
        public void onPlaybackResume() throws RemoteException {
            if (!mHandler.hasMessages(MSG_UPDATE_PLAYBACK_STATUS)) {
                mHandler.sendEmptyMessage(MSG_UPDATE_PLAYBACK_STATUS);
            }
        }
    }
}
