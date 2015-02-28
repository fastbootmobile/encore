/*
 * Copyright (C) 2014 Fastboot Mobile, LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.omnirom.music.app;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Fragment;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.support.v7.app.ActionBar;
import android.support.v7.graphics.Palette;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;

import com.echonest.api.v4.EchoNestException;

import org.omnirom.music.api.echonest.AutoMixBucket;
import org.omnirom.music.api.echonest.AutoMixManager;
import org.omnirom.music.app.adapters.PlaybackQueueAdapter;
import org.omnirom.music.app.ui.MaterialTransitionDrawable;
import org.omnirom.music.app.ui.PlayPauseDrawable;
import org.omnirom.music.framework.ListenLogger;
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
import org.omnirom.music.utils.Utils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Activity showing the current playback queue
 */
public class PlaybackQueueActivity extends AppActivity {
    private static final String TAG = "PlaybackQueue";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playback_queue);
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.container, new SimpleFragment())
                    .commit();
        }

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        // getMenuInflater().inflate(R.menu.playback_queue, menu);
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
            supportFinishAfterTransition();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Simple fragment for the activity contents
     */
    public static class SimpleFragment extends Fragment {

        private View.OnClickListener mArtClickListener = new View.OnClickListener() {
            @Override
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            public void onClick(View view) {
                Bitmap hero = ((MaterialTransitionDrawable) ((ImageView) view).getDrawable())
                        .getFinalDrawable().getBitmap();
                Palette palette = Palette.generate(hero);

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
        private View.OnClickListener mAlbumArtClickListener;

        private static class PlaybackQueueHandler extends Handler {
            private WeakReference<SimpleFragment> mParent;

            public PlaybackQueueHandler(WeakReference<SimpleFragment> parent) {
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

        public SimpleFragment() {
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

                        AutoMixBucket bucket = AutoMixManager.getDefault().getCurrentPlayingBucket();
                        if (bucket != null) {
                            try {
                                bucket.notifyLike();
                            } catch (EchoNestException e) {
                                Log.e(TAG, "Unable to notify like event to EchoNest");
                            }
                        }

                        ((ImageView) v).setImageResource(R.drawable.ic_thumbs_up);
                    }
                }
            };

            mAlbumArtClickListener = new View.OnClickListener() {
                @Override
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                public void onClick(View v) {
                    PlaybackQueueAdapter.ViewHolder tag = (PlaybackQueueAdapter.ViewHolder) v.getTag();
                    Bitmap hero = ((MaterialTransitionDrawable) tag.ivAlbumArt.getDrawable()).getFinalDrawable().getBitmap();
                    Intent intent = AlbumActivity.craftIntent(getActivity(), hero, tag.song.getAlbum(),
                            tag.song.getProvider(), 0xFF333333);

                    if (Utils.hasLollipop()) {
                        ActivityOptions opts = ActivityOptions.makeSceneTransitionAnimation(getActivity(),
                                v, "itemImage");
                        startActivity(intent, opts.toBundle());
                    } else {
                        startActivity(intent);
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
                    mLikeClickListener, mAlbumArtClickListener);

            if (mListView != null) {
                mListView.setAdapter(mAdapter);
            }
        }

        @Override
        public void onResume() {
            super.onResume();

            // Attach this fragment as Playback Listener
            PlaybackProxy.addCallback(mPlaybackListener);
            mHandler.sendEmptyMessage(MSG_UPDATE_SEEKBAR);

            ProviderAggregator.getDefault().addUpdateCallback(mProviderCallback);

            if (!mHandler.hasMessages(MSG_UPDATE_QUEUE)) {
                mHandler.sendEmptyMessage(MSG_UPDATE_QUEUE);
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
            final List<Song> songs = new ArrayList<Song>(PlaybackProxy.getCurrentPlaybackQueue());
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
}
