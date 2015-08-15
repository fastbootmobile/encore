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

package com.fastbootmobile.encore.app;

import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.fastbootmobile.encore.app.ui.AlbumArtImageView;
import com.fastbootmobile.encore.app.ui.PlayPauseDrawable;
import com.fastbootmobile.encore.framework.PlaybackProxy;
import com.fastbootmobile.encore.model.Album;
import com.fastbootmobile.encore.model.Artist;
import com.fastbootmobile.encore.model.Playlist;
import com.fastbootmobile.encore.model.SearchResult;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.ILocalCallback;
import com.fastbootmobile.encore.providers.IMusicProvider;
import com.fastbootmobile.encore.providers.ProviderAggregator;
import com.fastbootmobile.encore.service.BasePlaybackCallback;
import com.fastbootmobile.encore.service.NavHeadService;
import com.fastbootmobile.encore.service.PlaybackService;
import com.fastbootmobile.encore.utils.Utils;
import com.fastbootmobile.encore.voice.VoiceActionHelper;
import com.fastbootmobile.encore.voice.VoiceCommander;
import com.fastbootmobile.encore.voice.VoiceRecognizer;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;


/**
 *
 */
public class DriveModeActivity extends AppActivity implements ILocalCallback,
        View.OnClickListener, SeekBar.OnSeekBarChangeListener, GestureDetector.OnGestureListener {
    private static final String TAG = "DriveModeActivity";

    public static final String ACTION_FINISH = "com.fastbootmobile.encore.action.FINISH_DRIVE_MODE";
    private static final int DELAY_SEEKBAR_UPDATE = 1000 / 15;  // 15 Hz

    private static final int MSG_UPDATE_PLAYBACK_STATUS = 1;
    private static final int MSG_UPDATE_SEEKBAR = 2;
    private static final int MSG_UPDATE_TIME = 3;
    private static final int MSG_HIDE_SYSTEM_UI = 4;

    private static final String PREFS_DRIVE_MODE = "drive_mode";
    private static final String PREF_ONBOARDING_DONE = "onboarding_is_done";

    private boolean mBackPressed = false;
    private boolean mPausedForOnboarding = false;
    private DriveHandler mHandler;
    private DrivePlaybackCallback mPlaybackCallback;
    private View mDecorView;
    private PlayPauseDrawable mPlayDrawable;
    private ImageView mPlayButton;
    private ImageView mPreviousButton;
    private ImageView mSkipButton;
    private ImageView mVoiceButton;
    private ImageView mMapsButton;
    private TextView mTvTitle;
    private TextView mTvArtist;
    private TextView mTvAlbum;
    private TextView mTvCurrentTime;
    private AlbumArtImageView mIvAlbumArt;
    private SeekBar mSeek;
    private ProgressBar mPbVoiceLoading;
    private VoiceRecognizer mVoiceRecognizer;
    private VoiceCommander mVoiceCommander;
    private VoiceActionHelper mVoiceHelper;
    private GestureDetector mDetector;

    private BroadcastReceiver mBroadcastRcv = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_FINISH)) {
                finish();
            }
        }
    };

    private static class DriveHandler extends Handler {
        private WeakReference<DriveModeActivity> mParent;

        public DriveHandler(WeakReference<DriveModeActivity> parent) {
            mParent = parent;
        }

        @Override
        public void handleMessage(Message msg) {
            if (mParent.get() != null) {
                switch (msg.what) {
                    case MSG_UPDATE_PLAYBACK_STATUS:
                        mParent.get().updatePlaybackStatus();
                        break;

                    case MSG_UPDATE_SEEKBAR:
                        mParent.get().updateSeekBar();
                        break;

                    case MSG_UPDATE_TIME:
                        mParent.get().updateTime();
                        break;

                    case MSG_HIDE_SYSTEM_UI:
                        mParent.get().hideSystemUI();
                        break;
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        // Allow for BluetoothReceiver to kill the activity on BT disconnect
        registerReceiver(mBroadcastRcv, new IntentFilter(ACTION_FINISH));

        mDetector = new GestureDetector(this,this);

        mHandler = new DriveHandler(new WeakReference<>(this));
        mPlaybackCallback = new DrivePlaybackCallback();

        mVoiceCommander = new VoiceCommander(this);
        mVoiceRecognizer = new VoiceRecognizer(this);
        mVoiceHelper = new VoiceActionHelper(this);

        mVoiceRecognizer.setListener(new VoiceRecognizer.Listener() {
            @Override
            public void onReadyForSpeech() {
                setVoiceEmphasis(true, true);
                PlaybackProxy.pause();
            }

            @Override
            public void onBeginningOfSpeech() {

            }

            @Override
            public void onEndOfSpeech() {
                setVoiceEmphasis(false, true);
                PlaybackProxy.play();
                resetVoiceRms();
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mPbVoiceLoading.setVisibility(View.GONE);
                    }
                }, 1000);
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                setVoiceRms(rmsdB);
            }

            @Override
            public void onError(int error) {
                setVoiceEmphasis(false, true);
                resetVoiceRms();
                PlaybackProxy.play();
                mPbVoiceLoading.setVisibility(View.GONE);
                mTvArtist.setAlpha(1.0f);
                if (!mHandler.hasMessages(MSG_UPDATE_PLAYBACK_STATUS)) {
                    mHandler.sendEmptyMessage(MSG_UPDATE_PLAYBACK_STATUS);
                }
            }

            @Override
            public void onResults(List<String> results) {
                if (results != null && results.size() > 0) {
                    mTvArtist.setText(results.get(0));
                    mTvArtist.setAlpha(1.0f);
                    mVoiceCommander.processResult(results, mVoiceHelper);
                    mPbVoiceLoading.setVisibility(View.VISIBLE);

                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mPbVoiceLoading.setVisibility(View.GONE);
                            mHandler.sendEmptyMessage(MSG_UPDATE_PLAYBACK_STATUS);
                        }
                    }, 2000);
                }
            }

            @Override
            public void onPartialResults(List<String> results) {
                if (results != null && results.size() > 0) {
                    mTvArtist.setText(results.get(0));
                    mTvArtist.setAlpha(0.7f);
                    mPbVoiceLoading.setVisibility(View.VISIBLE);
                }
            }
        });

        setContentView(R.layout.activity_drive_mode);

        mDecorView = findViewById(R.id.rlDriveRoot);
        mPlayButton = (ImageView) findViewById(R.id.btnPlayPause);
        mPreviousButton = (ImageView) findViewById(R.id.btnPrevious);
        mSkipButton = (ImageView) findViewById(R.id.btnNext);
        mVoiceButton = (ImageView) findViewById(R.id.btnVoice);
        mMapsButton = (ImageView) findViewById(R.id.btnMaps);
        mTvTitle = (TextView) findViewById(R.id.tvTitle);
        mTvArtist = (TextView) findViewById(R.id.tvArtist);
        mTvAlbum = (TextView) findViewById(R.id.tvAlbum);
        mTvCurrentTime = (TextView) findViewById(R.id.tvCurrentTime);
        mIvAlbumArt = (AlbumArtImageView) findViewById(R.id.ivAlbumArt);
        mSeek = (SeekBar) findViewById(R.id.sbSeek);
        mPbVoiceLoading = (ProgressBar) findViewById(R.id.pbVoiceLoading);

        mPlayDrawable = new PlayPauseDrawable(getResources(), 1.5f, 1.6f);
        mPlayDrawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
        mPlayButton.setImageDrawable(mPlayDrawable);

        mPlayButton.setOnClickListener(this);
        mPreviousButton.setOnClickListener(this);
        mSkipButton.setOnClickListener(this);
        mMapsButton.setOnClickListener(this);
        mVoiceButton.setOnClickListener(this);
        mSeek.setOnSeekBarChangeListener(this);

        mHandler.sendEmptyMessage(MSG_UPDATE_PLAYBACK_STATUS);
        mHandler.sendEmptyMessageDelayed(MSG_UPDATE_SEEKBAR, DELAY_SEEKBAR_UPDATE);
        mHandler.sendEmptyMessage(MSG_UPDATE_TIME);

        SharedPreferences prefs = getSharedPreferences(PREFS_DRIVE_MODE, 0);
        if (!prefs.getBoolean(PREF_ONBOARDING_DONE, false)) {
            mPausedForOnboarding = true;
            prefs.edit().putBoolean(PREF_ONBOARDING_DONE, true).apply();
            startActivity(new Intent(this, DriveTutorialActivity.class));
        }
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
            final ProviderAggregator aggregator = ProviderAggregator.getDefault();

            mTvTitle.setText(currentTrack.getTitle());

            if (currentTrack.getArtist() != null) {
                Artist artist = aggregator.retrieveArtist(currentTrack.getArtist(), currentTrack.getProvider());

                if (artist != null && artist.getName() != null && !artist.getName().isEmpty()) {
                    mTvArtist.setText(artist.getName());
                } else if (artist != null && !artist.isLoaded()) {
                    mTvArtist.setText(R.string.loading);
                } else {
                    mTvArtist.setText(null);
                }
            } else {
                mTvArtist.setText(null);
            }

            if (currentTrack.getAlbum() != null) {
                Album album = aggregator.retrieveAlbum(currentTrack.getAlbum(), currentTrack.getProvider());

                if (album != null && album.getName() != null && !album.getName().isEmpty()) {
                    mTvAlbum.setText(album.getName());
                } else if (album != null && !album.isLoaded()) {
                    mTvAlbum.setText(R.string.loading);
                } else {
                    mTvAlbum.setText(null);
                }
            } else {
                mTvAlbum.setText(null);
            }

            mIvAlbumArt.loadArtForSong(currentTrack);
            mSeek.setMax(currentTrack.getDuration());
        } else if (currentTrack != null) {
            mTvTitle.setText(R.string.loading);
            mTvArtist.setText(null);
            mIvAlbumArt.setDefaultArt();
        } else {
            // TODO: No song playing
        }
    }

    private void updateSeekBar() {
        int state = PlaybackProxy.getState();

        if (state == PlaybackService.STATE_PLAYING) {
            int elapsedMs = PlaybackProxy.getCurrentTrackPosition();

            mSeek.setProgress(elapsedMs);
            mHandler.sendEmptyMessageDelayed(MSG_UPDATE_SEEKBAR, DELAY_SEEKBAR_UPDATE);
            mSeek.setVisibility(View.VISIBLE);
        } else {
            mSeek.setVisibility(View.INVISIBLE);
        }
    }

    private void updateTime() {
        Calendar cal = GregorianCalendar.getInstance();
        cal.setTime(new Date());
        mTvCurrentTime.setText(String.format("%02d:%02d",
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)));
        mHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, 5000);
    }

    private void setButtonDarker(ImageView button, boolean darker) {
        if (darker) {
            button.animate().alpha(0.3f).setDuration(300).start();
        } else {
            button.animate().alpha(1).setDuration(300).start();
        }
    }

    private void setVoiceEmphasis(boolean emphasis, boolean voice) {
        setButtonDarker(mVoiceButton, !voice);
        setButtonDarker(mPreviousButton, emphasis);
        setButtonDarker(mPlayButton, emphasis);
        setButtonDarker(mMapsButton, emphasis);
        setButtonDarker(mSkipButton, emphasis);
    }

    private void setVoiceRms(float rmsdB) {
        Drawable drawable = mVoiceButton.getDrawable();
        int red = (int) (((rmsdB + 2.0f) / 12.0f) * 255.0f);

        drawable.setColorFilter(((0xFFFF0000) | (red << 8) | red), PorterDuff.Mode.MULTIPLY);
    }

    private void resetVoiceRms() {
        Drawable drawable = mVoiceButton.getDrawable();
        drawable.setColorFilter(null);
    }

    @Override
    protected void onPause() {
        ProviderAggregator.getDefault().removeUpdateCallback(this);
        PlaybackProxy.removeCallback(mPlaybackCallback);

        final int state = PlaybackProxy.getState();
        if (!mBackPressed && !mPausedForOnboarding && state != PlaybackService.STATE_PAUSED
                && state != PlaybackService.STATE_STOPPED) {
            // Start NavHead for easy going back into Drive mode
            startService(new Intent(this, NavHeadService.class));
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        PlaybackProxy.removeCallback(mPlaybackCallback);

        // Hide NavHead as we're getting back into the app
        stopService(new Intent(this, NavHeadService.class));
        unregisterReceiver(mBroadcastRcv);

        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();

        ProviderAggregator.getDefault().addUpdateCallback(this);
        PlaybackProxy.addCallback(mPlaybackCallback);
        mHandler.sendEmptyMessageDelayed(MSG_HIDE_SYSTEM_UI, 1000);
        mHandler.sendEmptyMessage(MSG_UPDATE_PLAYBACK_STATUS);
        mHandler.sendEmptyMessageDelayed(MSG_UPDATE_SEEKBAR, DELAY_SEEKBAR_UPDATE);

        // Hide NavHead as we're getting back into the app
        stopService(new Intent(this, NavHeadService.class));
    }

    private void startMaps() {
        Intent mapIntent = new Intent(Intent.ACTION_VIEW);
        mapIntent.setPackage("com.google.android.apps.maps");
        try {
            startActivity(mapIntent);
        } catch (ActivityNotFoundException e) {
            // User doesn't have Google Maps
            Toast.makeText(this, R.string.toast_no_gmaps, Toast.LENGTH_SHORT).show();
        }
    }

    public void onClickClose(View v) {
        finish();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        mHandler.sendEmptyMessageDelayed(MSG_HIDE_SYSTEM_UI, 1000);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        mBackPressed = true;

        // Stop NavHead if needed
        stopService(new Intent(this, NavHeadService.class));
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
        } else if (v == mPreviousButton) {
            PlaybackProxy.previous();
        } else if (v == mSkipButton) {
            PlaybackProxy.next();
        } else if (v == mMapsButton) {
            startMaps();
        } else if (v == mVoiceButton) {
            setVoiceEmphasis(true, false);
            mVoiceRecognizer.startListening();
        }
    }

    @Override
    public void onSongUpdate(List<Song> s) {
        final Song currentTrack = PlaybackProxy.getCurrentTrack();

        if (s.contains(currentTrack)) {
            if (!mHandler.hasMessages(MSG_UPDATE_PLAYBACK_STATUS)) {
                mHandler.sendEmptyMessage(MSG_UPDATE_PLAYBACK_STATUS);
            }
        }

        mVoiceHelper.onSongUpdate(s);
    }

    @Override
    public void onAlbumUpdate(List<Album> a) {
        mVoiceHelper.onAlbumUpdate(a);
    }

    @Override
    public void onPlaylistUpdate(List<Playlist> p) {
    }

    @Override
    public void onPlaylistRemoved(String ref) {
    }

    @Override
    public void onArtistUpdate(List<Artist> a) {
        final Song currentTrack = PlaybackProxy.getCurrentTrack();

        if (currentTrack != null) {
            for (Artist artist : a) {
                if (artist.getRef().equals(currentTrack.getArtist())) {
                    if (!mHandler.hasMessages(MSG_UPDATE_PLAYBACK_STATUS)) {
                        mHandler.sendEmptyMessage(MSG_UPDATE_PLAYBACK_STATUS);
                    }
                    break;
                }
            }
        }

        mVoiceHelper.onArtistUpdate(a);
    }

    @Override
    public void onProviderConnected(IMusicProvider provider) {

    }

    @Override
    public void onSearchResult(List<SearchResult> searchResult) {
        mVoiceHelper.onSearchResult(searchResult);
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
                mHandler.sendEmptyMessageDelayed(MSG_UPDATE_SEEKBAR, DELAY_SEEKBAR_UPDATE);
            }
        }

        @Override
        public void onPlaybackResume() throws RemoteException {
            if (!mHandler.hasMessages(MSG_UPDATE_PLAYBACK_STATUS)) {
                mHandler.sendEmptyMessage(MSG_UPDATE_PLAYBACK_STATUS);
                mHandler.sendEmptyMessageDelayed(MSG_UPDATE_SEEKBAR, DELAY_SEEKBAR_UPDATE);
            }
        }
    }


    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
            PlaybackProxy.seek(progress);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        this.mDetector.onTouchEvent(event);

        return super.onTouchEvent(event);
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return true;
    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        int state = PlaybackProxy.getState();
        if (state == PlaybackService.STATE_BUFFERING || state == PlaybackService.STATE_PLAYING) {
            PlaybackProxy.pause();
        } else {
            PlaybackProxy.play();
        }
        return true;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return true;
    }

    @Override
    public void onLongPress(MotionEvent e) {

    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (Math.abs(velocityY) > Math.abs(velocityX)) {
            // Vertical gesture
            if (velocityY < -400) {
                startMaps();
            }
        } else {
            // Horizontal gesture
            if (velocityX < -500) {
                PlaybackProxy.previous();
            } else if (velocityX > 500) {
                PlaybackProxy.next();
            }
        }
        return true;
    }

}
