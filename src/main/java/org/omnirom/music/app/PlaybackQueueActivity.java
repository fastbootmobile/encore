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

import android.app.ActionBar;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v4.app.FragmentActivity;
import android.support.v7.graphics.Palette;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.getbase.floatingactionbutton.FloatingActionButton;

import org.omnirom.music.app.ui.AlbumArtImageView;
import org.omnirom.music.app.ui.MaterialTransitionDrawable;
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
import org.omnirom.music.service.IPlaybackCallback;
import org.omnirom.music.service.PlaybackService;

import java.util.ArrayList;
import java.util.List;

import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

/**
 * Activity showing the current playback queue
 */
public class PlaybackQueueActivity extends FragmentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playback_queue);
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.container, new SimpleFragment())
                    .commit();
        }

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(new CalligraphyContextWrapper(newBase));
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

                ProviderAggregator aggregator = ProviderAggregator.getDefault();
                Song song = (Song) view.getTag();

                Intent intent = AlbumActivity.craftIntent(getActivity(), hero,
                        aggregator.retrieveAlbum(song.getAlbum(), song.getProvider()), color);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ActivityOptions opt = ActivityOptions.makeSceneTransitionAnimation(getActivity(),
                            view, "itemImage");

                    getActivity().startActivity(intent, opt.toBundle());
                } else {
                    getActivity().startActivity(intent);
                }
            }
        };

        private Runnable mUpdateSeekBarRunnable = new Runnable() {
            @Override
            public void run() {
                int state = PlaybackProxy.getState();
                if (state == PlaybackService.STATE_PLAYING
                        || state == PlaybackService.STATE_PAUSING
                        || state == PlaybackService.STATE_PAUSED) {
                    if (mSeekBar != null) {
                        if (!mSeekBar.isPressed()) {
                            mSeekBar.setMax(PlaybackProxy.getCurrentTrackLength());
                            mSeekBar.setProgress(PlaybackProxy.getCurrentTrackPosition());
                        }
                    }
                    mHandler.postDelayed(this, SEEK_UPDATE_DELAY);
                }
            }
        };

        private IPlaybackCallback mPlaybackListener = new IPlaybackCallback.Stub() {
            @Override
            public void onSongStarted(boolean buffering, Song s) throws RemoteException {
                mHandler.post(mUpdateSeekBarRunnable);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mPlayDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                        updateQueueLayout();
                    }
                });
            }

            @Override
            public void onSongScrobble(int timeMs) throws RemoteException {
            }

            @Override
            public void onPlaybackPause() throws RemoteException {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mPlayDrawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
                    }
                });
            }

            @Override
            public void onPlaybackResume() throws RemoteException {
                mHandler.post(mUpdateSeekBarRunnable);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mPlayDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                    }
                });
            }

            @Override
            public void onPlaybackQueueChanged() throws RemoteException {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateQueueLayout();
                    }
                });
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
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            updateQueueLayout();
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
                            updateQueueLayout();
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


        private SeekBar.OnSeekBarChangeListener mSeekBarListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, final int progress, boolean fromUser) {
                if (fromUser) {
                    PlaybackProxy.seek(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        };

        private static final int SEEK_UPDATE_DELAY = 1000/30;
        private SeekBar mSeekBar;
        private Handler mHandler;
        private ScrollView mRootView;
        private PlayPauseDrawable mPlayDrawable;

        public SimpleFragment() {
            mHandler = new Handler();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            mRootView = (ScrollView) inflater.inflate(R.layout.fragment_playback_queue, container, false);
            updateQueueLayout();
            return mRootView;
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            // Attach this fragment as Playback Listener
            PlaybackProxy.addCallback(mPlaybackListener);
            mHandler.post(mUpdateSeekBarRunnable);

            ProviderAggregator.getDefault().addUpdateCallback(mProviderCallback);
        }

        @Override
        public void onDetach() {
            super.onDetach();
            // Detach this fragment as Playback Listener
            PlaybackProxy.removeCallback(mPlaybackListener);
            mHandler.removeCallbacks(mUpdateSeekBarRunnable);

            ProviderAggregator.getDefault().removeUpdateCallback(mProviderCallback);
        }

        public void updateQueueLayout() {
            final ProviderAggregator aggregator = ProviderAggregator.getDefault();
            ViewGroup tracksContainer = (ViewGroup) mRootView.findViewById(R.id.llRoot);

            // Load and inflate the playback queue
            // TODO: Recycle
            tracksContainer.removeAllViews();

            // We make a copy of the queue as we will modify it locally
            final List<Song> songs = new ArrayList<Song>(PlaybackProxy.getCurrentPlaybackQueue());
            final Song currentTrack = PlaybackProxy.getCurrentTrack();
            final int currentTrackIndex = PlaybackProxy.getCurrentTrackIndex();

            // We remove the first song as it's the currently playing song we displayed above
            if (songs.size() > 0) {
                mRootView.findViewById(R.id.txtEmptyQueue).setVisibility(View.GONE);
                mRootView.findViewById(R.id.llRoot).setVisibility(View.VISIBLE);

                int itemIndex = 0;
                int currentPlayingTop = 0;

                final LayoutInflater inflater = getActivity().getLayoutInflater();
                View itemView;
                for (final Song song : songs) {
                    if (currentTrackIndex == itemIndex) {
                        itemView = inflater.inflate(R.layout.item_playbackqueue_current, tracksContainer, false);

                        TextView tvCurrentTitle = (TextView) itemView.findViewById(R.id.tvCurrentTitle);
                        TextView tvCurrentArtist = (TextView) itemView.findViewById(R.id.tvCurrentArtist);
                        AlbumArtImageView ivCurrentPlayAlbumArt = (AlbumArtImageView) itemView.findViewById(R.id.ivCurrentPlayAlbumArt);
                        final ImageView ivOverflow = (ImageView) itemView.findViewById(R.id.ivOverflow);

                        tvCurrentTitle.setText(currentTrack.getTitle());

                        final String artistRef = currentTrack.getArtist();
                        Artist artist = aggregator.retrieveArtist(artistRef, currentTrack.getProvider());
                        if (artist == null) {
                            artist = ProviderAggregator.getDefault().retrieveArtist(artistRef, currentTrack.getProvider());
                        }
                        if (artist != null) {
                            tvCurrentArtist.setText(artist.getName());
                        } else {
                            tvCurrentArtist.setText(getString(R.string.loading));
                        }
                        ivCurrentPlayAlbumArt.loadArtForSong(currentTrack);

                        mSeekBar = (SeekBar) itemView.findViewById(R.id.sbSeek);
                        mSeekBar.setOnSeekBarChangeListener(mSeekBarListener);

                        ivOverflow.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                Utils.showCurrentSongOverflow((FragmentActivity) getActivity(),
                                        ivOverflow, currentTrack);
                            }
                        });

                        // Set play pause drawable
                        FloatingActionButton ivPlayPause = (FloatingActionButton) itemView.findViewById(R.id.ivPlayPause);
                        mPlayDrawable = new PlayPauseDrawable();
                        mPlayDrawable.setYOffset(6);
                        mPlayDrawable.setPaddingDp(64);
                        mPlayDrawable.setColor(getResources().getColor(R.color.white));
                        mPlayDrawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
                        ivPlayPause.setFixupInset(false);
                        ivPlayPause.setImageDrawable(mPlayDrawable);

                        ivPlayPause.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                if (mPlayDrawable.getRequestedShape() == PlayPauseDrawable.SHAPE_PLAY) {
                                    // We're paused, play
                                    PlaybackProxy.play();
                                } else {
                                    // We're playing, pause
                                    PlaybackProxy.pause();
                                }
                            }
                        });

                        int state = PlaybackProxy.getState();
                        if (state == PlaybackService.STATE_PLAYING) {
                            mPlayDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                            mPlayDrawable.setBuffering(false);
                        } else if (state == PlaybackService.STATE_BUFFERING) {
                            mPlayDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                            mPlayDrawable.setBuffering(true);
                        }

                        ImageView ivForward = (ImageView) itemView.findViewById(R.id.ivForward);
                        ivForward.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                PlaybackProxy.next();
                            }
                        });

                        ImageView ivPrevious = (ImageView) itemView.findViewById(R.id.ivPrevious);
                        ivPrevious.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                PlaybackProxy.previous();
                            }
                        });

                        final ImageView ivRepeat = (ImageView) itemView.findViewById(R.id.ivRepeat);
                        if (PlaybackProxy.isRepeatMode()) {
                            ivRepeat.setImageResource(R.drawable.ic_replay);
                        } else {
                            ivRepeat.setImageResource(R.drawable.ic_replay_gray);
                        }
                        ivRepeat.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                Drawable[] drawables = new Drawable[2];
                                final Resources res = getResources();
                                boolean enable;

                                if (PlaybackProxy.isRepeatMode()) {
                                    // Repeating, disable
                                    drawables[0] = res.getDrawable(R.drawable.ic_replay);
                                    drawables[1] = res.getDrawable(R.drawable.ic_replay_gray);
                                    enable = false;
                                } else {
                                    // Not repeating, enable
                                    drawables[0] = res.getDrawable(R.drawable.ic_replay_gray);
                                    drawables[1] = res.getDrawable(R.drawable.ic_replay);
                                    enable = true;
                                }

                                final TransitionDrawable drawable = new TransitionDrawable(drawables);
                                ivRepeat.setImageDrawable(drawable);
                                final AccelerateDecelerateInterpolator interpolator = new AccelerateDecelerateInterpolator();

                                mHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        drawable.startTransition(500);
                                        ivRepeat.setRotation(0);
                                        ivRepeat.animate().rotationBy(-360.0f).setDuration(500).setInterpolator(interpolator).start();
                                    }
                                }, 100);


                                PlaybackProxy.setRepeatMode(enable);
                            }
                        });
                    } else {
                        itemView = inflater.inflate(R.layout.item_playbar, tracksContainer, false);
                        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
                            itemView.setTransitionName("playbackqueue:" + itemIndex);
                        }
                        TextView tvTitle = (TextView) itemView.findViewById(R.id.tvTitle),
                                tvArtist = (TextView) itemView.findViewById(R.id.tvArtist);
                        AlbumArtImageView ivCover = (AlbumArtImageView) itemView.findViewById(R.id.ivAlbumArt);

                        Artist artist = aggregator.retrieveArtist(song.getArtist(), song.getProvider());

                        tvTitle.setText(song.getTitle());
                        if (artist != null) {
                            tvArtist.setText(artist.getName());
                        }
                        ivCover.loadArtForSong(song);

                        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
                            ivCover.setTransitionName("playbackqueue:" + itemIndex + ":cover:" + song.getRef());
                        }

                        ivCover.setTag(song);
                        ivCover.setOnClickListener(mArtClickListener);

                        final int itemIndexFinal = itemIndex;
                        itemView.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                // Play that song now
                                if (song.equals(PlaybackProxy.getCurrentTrack())) {
                                    // We're already playing that song, play it again
                                    PlaybackProxy.seek(0);
                                } else {
                                    PlaybackProxy.playAtIndex(itemIndexFinal);
                                }
                            }
                        });
                    }

                    tracksContainer.addView(itemView);
                    if (currentTrackIndex == itemIndex) {
                        currentPlayingTop =
                                getResources().getDimensionPixelSize(R.dimen.playing_bar_height) * itemIndex
                                - Utils.dpToPx(getResources(), 8);
                    }

                    itemIndex++;
                }

                final int finalPlayingTop = currentPlayingTop;
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mRootView.smoothScrollTo(0, finalPlayingTop);
                    }
                }, 500);

            } else {
                mRootView.findViewById(R.id.txtEmptyQueue).setVisibility(View.VISIBLE);
                mRootView.findViewById(R.id.llRoot).setVisibility(View.GONE);
            }

        }
    }
}
