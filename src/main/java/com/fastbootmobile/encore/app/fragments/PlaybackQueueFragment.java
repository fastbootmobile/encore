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

package com.fastbootmobile.encore.app.fragments;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityOptions;
import android.support.v4.app.Fragment;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.support.v7.graphics.Palette;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Toast;

import com.echonest.api.v4.EchoNestException;

import com.fastbootmobile.encore.api.echonest.AutoMixBucket;
import com.fastbootmobile.encore.api.echonest.AutoMixManager;
import com.fastbootmobile.encore.app.AlbumActivity;
import com.fastbootmobile.encore.app.MainActivity;
import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.app.adapters.PlaybackQueueAdapter;
import com.fastbootmobile.encore.app.ui.MaterialTransitionDrawable;
import com.fastbootmobile.encore.app.ui.PlayPauseDrawable;
import com.fastbootmobile.encore.framework.ListenLogger;
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
import com.fastbootmobile.encore.service.PlaybackService;
import com.fastbootmobile.encore.utils.Utils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple fragment for the activity contents
 */
public class PlaybackQueueFragment extends Fragment {
    private static final String TAG = "PlaybackQueueFrag";

    private View.OnClickListener mArtClickListener = new View.OnClickListener() {
        @Override
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public void onClick(View view) {
            Bitmap hero = ((MaterialTransitionDrawable) ((ImageView) view).getDrawable())
                    .getFinalDrawable().getBitmap();
            Palette palette = Palette.from(hero).generate();

            Palette.Swatch darkVibrantColor = palette.getDarkVibrantSwatch();
            Palette.Swatch darkMutedColor = palette.getDarkMutedSwatch();

            int color;
            if (darkVibrantColor != null) {
                color = darkVibrantColor.getRgb();
            } else if (darkMutedColor != null) {
                color = darkMutedColor.getRgb();
            } else {
                color = getResources().getColor(R.color.default_album_art_background);
            }

            Song song = (Song) view.getTag();

            Intent intent = AlbumActivity.craftIntent(getActivity(), hero,
                    song.getAlbum(), song.getProvider(), color);

            if (Utils.hasLollipop()) {
                ActivityOptions opt = ActivityOptions.makeSceneTransitionAnimation(getActivity(),
                        view, "itemImage");

                getActivity().startActivity(intent, opt.toBundle());
            } else {
                getActivity().startActivity(intent);
            }
        }
    };

    private BasePlaybackCallback mPlaybackListener = new BasePlaybackCallback() {
        @Override
        public void onSongStarted(final boolean buffering, Song s) throws RemoteException {
            mHandler.sendEmptyMessage(MSG_UPDATE_SEEKBAR);
            mHandler.sendEmptyMessage(MSG_UPDATE_QUEUE);
            mHandler.obtainMessage(MSG_UPDATE_PLAYSTATE,
                    buffering ? PLAYSTATE_ARG1_BUFFERING : PLAYSTATE_ARG1_NOT_BUFFERING,
                    PlayPauseDrawable.SHAPE_PAUSE).sendToTarget();
        }

        @Override
        public void onPlaybackPause() throws RemoteException {
            mHandler.obtainMessage(MSG_UPDATE_PLAYSTATE,
                    PLAYSTATE_ARG1_NOT_BUFFERING, PlayPauseDrawable.SHAPE_PLAY).sendToTarget();
        }

        @Override
        public void onPlaybackResume() throws RemoteException {
            mHandler.sendEmptyMessage(MSG_UPDATE_SEEKBAR);
            mHandler.obtainMessage(MSG_UPDATE_PLAYSTATE,
                    PLAYSTATE_ARG1_NOT_BUFFERING, PlayPauseDrawable.SHAPE_PAUSE).sendToTarget();
        }

        @Override
        public void onPlaybackQueueChanged() throws RemoteException {
            mHandler.sendEmptyMessage(MSG_UPDATE_QUEUE);
        }
    };

    private ILocalCallback mProviderCallback = new ILocalCallback() {
        @Override
        public void onSongUpdate(List<Song> s) {
            boolean contains = false;
            final List<Song> playbackQueue = PlaybackProxy.getCurrentPlaybackQueue();
            for (Song song : s) {
                if (playbackQueue.contains(song)) {
                    contains = true;
                    break;
                }
            }

            if (contains) {
                mHandler.sendEmptyMessage(MSG_UPDATE_QUEUE);
            }
        }

        @Override
        public void onAlbumUpdate(List<Album> a) {
        }

        @Override
        public void onPlaylistUpdate(List<Playlist> p) {
        }

        @Override
        public void onPlaylistRemoved(String ref) {
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
                mHandler.sendEmptyMessage(MSG_UPDATE_QUEUE);
            }
        }

        @Override
        public void onProviderConnected(IMusicProvider provider) {
        }

        @Override
        public void onSearchResult(List<SearchResult> searchResult) {
        }
    };

    private static final int SEEK_UPDATE_DELAY = 1000/15;

    private static final int MSG_UPDATE_SEEKBAR = 1;
    private static final int MSG_UPDATE_QUEUE = 2;
    private static final int MSG_UPDATE_PLAYSTATE = 3;

    private static final int PLAYSTATE_ARG1_NOT_BUFFERING = 0;
    private static final int PLAYSTATE_ARG1_BUFFERING = 1;

    private PlaybackQueueHandler mHandler;
    private boolean mLockSeekBarUpdate;
    private FrameLayout mRootView;
    private ListView mListView;
    private PlaybackQueueAdapter mAdapter;
    private View.OnClickListener mPlayFabClickListener;
    private View.OnClickListener mNextClickListener;
    private View.OnClickListener mPreviousClickListener;
    private SeekBar.OnSeekBarChangeListener mSeekListener;
    private View.OnClickListener mRepeatClickListener;
    private View.OnClickListener mLikeClickListener;
    private View.OnClickListener mDislikeClickListener;
    private View.OnClickListener mAlbumArtClickListener;
    private View.OnClickListener mShuffleClickListener;

    private static class PlaybackQueueHandler extends Handler {
        private WeakReference<PlaybackQueueFragment> mParent;

        public PlaybackQueueHandler(WeakReference<PlaybackQueueFragment> parent) {
            mParent = parent;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_QUEUE:
                    mParent.get().updateQueueLayout();
                    break;

                case MSG_UPDATE_SEEKBAR:
                    mParent.get().updateSeekbar();
                    break;

                case MSG_UPDATE_PLAYSTATE:
                    mParent.get().updatePlaystate(msg.arg1, msg.arg2);
                    break;
            }
        }
    }

    public static PlaybackQueueFragment newInstance() {
        return new PlaybackQueueFragment();
    }

    public PlaybackQueueFragment() {
        mHandler = new PlaybackQueueHandler(new WeakReference<>(this));

        mPlayFabClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (PlaybackProxy.getState()) {
                    case PlaybackService.STATE_PAUSED:
                    case PlaybackService.STATE_STOPPED:
                    case PlaybackService.STATE_PAUSING:
                        PlaybackProxy.play();
                        break;

                    case PlaybackService.STATE_BUFFERING:
                    case PlaybackService.STATE_PLAYING:
                        PlaybackProxy.pause();
                        break;

                }
            }
        };

        mNextClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlaybackProxy.next();
            }
        };

        mPreviousClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlaybackProxy.previous();
            }
        };

        mSeekListener = new SeekBar.OnSeekBarChangeListener() {
            private int mStartProgress;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mLockSeekBarUpdate = true;
                mStartProgress = seekBar.getProgress();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mLockSeekBarUpdate = false;
                int endProgress = seekBar.getProgress();

                if (endProgress != mStartProgress) {
                    PlaybackProxy.seek(endProgress);
                }
            }
        };

        mRepeatClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean currentMode = PlaybackProxy.isRepeatMode();
                boolean newMode = !currentMode;

                PlaybackProxy.setRepeatMode(newMode);
                v.animate().rotationBy(-360).setDuration(600).start();

                if (newMode) {
                    ((ImageView) v).setImageResource(R.drawable.ic_replay);
                } else {
                    ((ImageView) v).setImageResource(R.drawable.ic_replay_gray);
                }
            }
        };

        mShuffleClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean currentMode = PlaybackProxy.isShuffleMode();
                boolean newMode = !currentMode;

                PlaybackProxy.setShuffleMode(newMode);

                if (newMode) {
                    ((ImageView) v).setImageResource(R.drawable.ic_shuffle);
                } else {
                    ((ImageView) v).setImageResource(R.drawable.ic_shuffle_gray);
                }
            }
        };

        mLikeClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ListenLogger logger = new ListenLogger(getActivity());
                PlaybackQueueAdapter.ViewHolder tag = (PlaybackQueueAdapter.ViewHolder) v.getTag();
                boolean isLiked = logger.isLiked(tag.song.getRef());

                if (isLiked) {
                    logger.removeLike(tag.song);
                    ((ImageView) v).setImageResource(R.drawable.ic_thumbs_up_gray);
                } else {
                    logger.addLike(tag.song);

                    final AutoMixBucket bucket = AutoMixManager.getDefault().getCurrentPlayingBucket();
                    if (bucket != null) {
                        new Thread() {
                            public void run() {
                                try {
                                    bucket.notifyLike();
                                } catch (EchoNestException e) {
                                    Log.e(TAG, "Unable to notify like event to EchoNest");
                                }
                            }
                        }.start();
                    }

                    ((ImageView) v).setImageResource(R.drawable.ic_thumbs_up);
                }
            }
        };

        mDislikeClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ListenLogger logger = new ListenLogger(getActivity());
                PlaybackQueueAdapter.ViewHolder tag = (PlaybackQueueAdapter.ViewHolder) v.getTag();
                boolean isDisliked = logger.isDisliked(tag.song.getRef());

                if (isDisliked) {
                    logger.removeDislike(tag.song);
                    ((ImageView) v).setImageResource(R.drawable.ic_thumb_down_gray);
                } else {
                    logger.addDislike(tag.song);

                    final AutoMixBucket bucket = AutoMixManager.getDefault().getCurrentPlayingBucket();
                    if (bucket != null) {
                        new Thread() {
                            public void run() {
                                try {
                                    bucket.notifyDislike();
                                } catch (EchoNestException e) {
                                    Log.e(TAG, "Unable to notify dislike event to EchoNest");
                                }
                            }
                        }.start();
                    }

                    ((ImageView) v).setImageResource(R.drawable.ic_thumb_down);
                }
            }
        };

        mAlbumArtClickListener = new View.OnClickListener() {
            @Override
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            public void onClick(View v) {
                PlaybackQueueAdapter.ViewHolder tag = (PlaybackQueueAdapter.ViewHolder) v.getTag();
                if (tag.song.getAlbum() != null) {
                    Bitmap hero = ((MaterialTransitionDrawable) tag.ivAlbumArt.getDrawable()).getFinalDrawable().getBitmap();
                    Intent intent = AlbumActivity.craftIntent(getActivity(), hero, tag.song.getAlbum(),
                            tag.song.getProvider(), 0xFF333333);

                    if (Utils.hasLollipop()) {
                        ActivityOptions opts = ActivityOptions.makeSceneTransitionAnimation(getActivity(),
                                v, "itemImage");
                        getActivity().startActivity(intent, opts.toBundle());
                    } else {
                        startActivity(intent);
                    }
                } else {
                    Toast.makeText(getActivity(), R.string.toast_song_no_album, Toast.LENGTH_SHORT).show();
                }
            }
        };
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mRootView = (FrameLayout) inflater.inflate(R.layout.fragment_playback_queue, container,
                false);
        mListView = (ListView) mRootView.findViewById(R.id.lvPlaybackQueue);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                PlaybackProxy.playAtIndex(position);
            }
        });

        if (mAdapter != null) {
            mListView.setAdapter(mAdapter);
        }

        updateQueueLayout();

        return mRootView;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        mAdapter = new PlaybackQueueAdapter(mPlayFabClickListener, mNextClickListener,
                mPreviousClickListener, mSeekListener, mRepeatClickListener,
                mLikeClickListener, mDislikeClickListener, mAlbumArtClickListener, mShuffleClickListener);

        if (mListView != null) {
            mListView.setAdapter(mAdapter);
        }

        if (activity instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) activity;
            mainActivity.onSectionAttached(MainActivity.SECTION_NOW_PLAYING);
        }
    }



    @Override
    public void onResume() {
        super.onResume();

        // Attach this fragment as Playback Listener
        PlaybackProxy.addCallback(mPlaybackListener);
        mHandler.sendEmptyMessageDelayed(MSG_UPDATE_SEEKBAR, 1000);

        ProviderAggregator.getDefault().addUpdateCallback(mProviderCallback);

        if (!mHandler.hasMessages(MSG_UPDATE_QUEUE)) {
            mHandler.sendEmptyMessage(MSG_UPDATE_QUEUE);
            mHandler.sendEmptyMessageDelayed(MSG_UPDATE_QUEUE, 500);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        // Remove callback on various places
        PlaybackProxy.removeCallback(mPlaybackListener);
        ProviderAggregator.getDefault().removeUpdateCallback(mProviderCallback);

        // Stop updating the seekbar
        mHandler.removeMessages(MSG_UPDATE_SEEKBAR);
    }

    public void updateQueueLayout() {
        final List<Song> songs = new ArrayList<>(PlaybackProxy.getCurrentPlaybackQueue());
        mAdapter.setPlaybackQueue(songs);

        final int trackIndex = PlaybackProxy.getCurrentTrackIndex();
        if (trackIndex >= 0) {
            mListView.smoothScrollToPosition(trackIndex + 1);
        }

        if (songs.size() <= 0) {
            mRootView.findViewById(R.id.txtEmptyQueue).setVisibility(View.VISIBLE);
        } else {
            mRootView.findViewById(R.id.txtEmptyQueue).setVisibility(View.GONE);
        }
    }

    public void updateSeekbar() {
        PlaybackQueueAdapter.ViewHolder tag = mAdapter.getCurrentTrackTag();

        if (tag != null && tag.sbSeek != null) {
            int state = PlaybackProxy.getState();
            if (state == PlaybackService.STATE_PLAYING
                    || state == PlaybackService.STATE_PAUSING
                    || state == PlaybackService.STATE_PAUSED) {
                if (!mLockSeekBarUpdate) {
                    tag.sbSeek.setMax(PlaybackProxy.getCurrentTrackLength());
                    tag.sbSeek.setProgress(PlaybackProxy.getCurrentTrackPosition());
                }

                mHandler.sendEmptyMessageDelayed(MSG_UPDATE_SEEKBAR, SEEK_UPDATE_DELAY);
            }
        }
    }

    public void updatePlaystate(int arg1, int arg2) {
        PlaybackQueueAdapter.ViewHolder tag = mAdapter.getCurrentTrackTag();

        if (tag != null && tag.fabPlayDrawable != null) {
            tag.fabPlayDrawable.setBuffering(arg1 == PLAYSTATE_ARG1_BUFFERING);
            tag.fabPlayDrawable.setShape(arg2);
        }
    }
}
