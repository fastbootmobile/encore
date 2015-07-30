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

package com.fastbootmobile.encore.app.adapters;

import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.getbase.floatingactionbutton.FloatingActionButton;

import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.utils.Utils;
import com.fastbootmobile.encore.app.ui.AlbumArtImageView;
import com.fastbootmobile.encore.app.ui.PlayPauseDrawable;
import com.fastbootmobile.encore.framework.ListenLogger;
import com.fastbootmobile.encore.framework.PlaybackProxy;
import com.fastbootmobile.encore.model.Artist;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.ProviderAggregator;
import com.fastbootmobile.encore.service.PlaybackService;

import java.util.List;

/**
 * Adapter for playback queue
 */
public class PlaybackQueueAdapter extends BaseAdapter {
    private ListenLogger mListenLogger;
    private List<Song> mQueue;
    private ViewHolder mCurrentTrackTag;
    private View.OnClickListener mPlayFabClickListener;
    private View.OnClickListener mNextClickListener;
    private View.OnClickListener mPreviousClickListener;
    private View.OnClickListener mRepeatClickListener;
    private View.OnClickListener mShuffleClickListener;
    private View.OnClickListener mLikeClickListener;
    private View.OnClickListener mDislikeClickListener;
    private View.OnClickListener mOverflowClickListener;
    private View.OnClickListener mAlbumArtCLickListener;
    private SeekBar.OnSeekBarChangeListener mSeekListener;

    public PlaybackQueueAdapter(View.OnClickListener playFabClickListener,
                                View.OnClickListener nextClickListener,
                                View.OnClickListener previousClickListener,
                                SeekBar.OnSeekBarChangeListener seekListener,
                                View.OnClickListener repeatClickListener,
                                View.OnClickListener likeClickListener,
                                View.OnClickListener dislikeClickListener,
                                View.OnClickListener albumArtClickListener,
                                View.OnClickListener shuffleClickListener) {
        mPlayFabClickListener = playFabClickListener;
        mNextClickListener = nextClickListener;
        mPreviousClickListener = previousClickListener;
        mSeekListener = seekListener;
        mRepeatClickListener = repeatClickListener;
        mLikeClickListener = likeClickListener;
        mDislikeClickListener = dislikeClickListener;
        mAlbumArtCLickListener = albumArtClickListener;
        mShuffleClickListener = shuffleClickListener;
        mOverflowClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ViewHolder tag = (ViewHolder) v.getTag();
                Utils.showCurrentSongOverflow(tag.vRoot.getContext(), v, tag.song, true);
            }
        };
    }

    public ViewHolder getCurrentTrackTag() {
        return mCurrentTrackTag;
    }

    public void setPlaybackQueue(List<Song> queue) {
        synchronized (this) {
            mQueue = queue;
            notifyDataSetChanged();
        }
    }

    @Override
    public int getCount() {
        synchronized (this) {
            if (mQueue != null) {
                return mQueue.size();
            } else {
                return 0;
            }
        }
    }

    @Override
    public Song getItem(int position) {
        synchronized (this) {
            final ProviderAggregator aggregator = ProviderAggregator.getDefault();
            Song copy = mQueue.get(position);

            if (copy != null) {
                return aggregator.retrieveSong(copy.getRef(), copy.getProvider());
            } else {
                return null;
            }
        }
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (mListenLogger == null) {
            mListenLogger = new ListenLogger(parent.getContext());
        }

        final ProviderAggregator aggregator = ProviderAggregator.getDefault();
        final Resources res = parent.getResources();
        final Song item;
        synchronized (this) {
             item = getItem(position);
        }
        final boolean isCurrent = position == PlaybackProxy.getCurrentTrackIndex();

        ViewHolder tag;

        if (convertView == null || ((ViewHolder) convertView.getTag()).isCurrent != isCurrent) {
            final LayoutInflater inflater = LayoutInflater.from(parent.getContext());

            if (isCurrent) {
                convertView = inflater.inflate(R.layout.item_playbackqueue_current, parent, false);
            } else {
                convertView = inflater.inflate(R.layout.item_playbar, parent, false);
            }

            tag = new ViewHolder();
            tag.vRoot = (ViewGroup) convertView;
            tag.tvTitle = (TextView) convertView.findViewById(R.id.tvTitle);
            tag.tvArtist = (TextView) convertView.findViewById(R.id.tvArtist);
            tag.ivAlbumArt = (AlbumArtImageView) convertView.findViewById(R.id.ivAlbumArt);
            tag.ivAlbumArt.setOnClickListener(mAlbumArtCLickListener);
            tag.ivAlbumArt.setTag(tag);

            tag.vRoot.setClickable(false);

            if (isCurrent) {
                // Lookup views
                tag.sbSeek = (SeekBar) convertView.findViewById(R.id.sbSeek);
                tag.btnNext = (ImageView) convertView.findViewById(R.id.btnForward);
                tag.btnPrevious = (ImageView) convertView.findViewById(R.id.btnPrevious);
                tag.btnRepeat = (ImageView) convertView.findViewById(R.id.btnRepeat);
                tag.btnShuffle = (ImageView) convertView.findViewById(R.id.btnShuffle);
                tag.btnThumbs = (ImageView) convertView.findViewById(R.id.btnThumbs);
                tag.btnThumbsDown = (ImageView) convertView.findViewById(R.id.btnThumbsDown);
                tag.btnOverflow = (ImageView) convertView.findViewById(R.id.btnOverflow);
                tag.fabPlay = (FloatingActionButton) convertView.findViewById(R.id.fabPlay);

                // Setup some initial states
                if (PlaybackProxy.isRepeatMode()) {
                    tag.btnRepeat.setImageResource(R.drawable.ic_replay);
                } else {
                    tag.btnRepeat.setImageResource(R.drawable.ic_replay_gray);
                }
                if (PlaybackProxy.isShuffleMode()) {
                    tag.btnShuffle.setImageResource(R.drawable.ic_shuffle);
                } else {
                    tag.btnShuffle.setImageResource(R.drawable.ic_shuffle_gray);
                }
                tag.btnOverflow.setTag(tag);
                tag.btnThumbs.setTag(tag);
                tag.btnThumbsDown.setTag(tag);

                // Play FAB drawable
                tag.fabPlay.setFixupInset(false);
                tag.fabPlayDrawable = new PlayPauseDrawable(res, 1.2f, 1.1f);
                tag.fabPlayDrawable.setYOffset(6);
                tag.fabPlayDrawable.setColor(res.getColor(R.color.white));
                updatePlaystate(tag.fabPlayDrawable);
                tag.fabPlay.setImageDrawable(tag.fabPlayDrawable);

                // Click listeners
                tag.fabPlay.setOnClickListener(mPlayFabClickListener);
                tag.btnPrevious.setOnClickListener(mPreviousClickListener);
                tag.btnNext.setOnClickListener(mNextClickListener);
                tag.sbSeek.setOnSeekBarChangeListener(mSeekListener);
                tag.btnRepeat.setOnClickListener(mRepeatClickListener);
                tag.btnThumbs.setOnClickListener(mLikeClickListener);
                tag.btnThumbsDown.setOnClickListener(mDislikeClickListener);
                tag.btnOverflow.setOnClickListener(mOverflowClickListener);
                tag.btnShuffle.setOnClickListener(mShuffleClickListener);
            }

            convertView.setTag(tag);
        } else {
            tag = (ViewHolder) convertView.getTag();
        }

        tag.isCurrent = isCurrent;
        tag.song = item;

        if (isCurrent) {
            mCurrentTrackTag = tag;
        }

        if (tag.btnThumbs != null && mListenLogger.isLiked(item.getRef())) {
            tag.btnThumbs.setImageResource(R.drawable.ic_thumbs_up);
        } else if (tag.btnThumbs != null) {
            tag.btnThumbs.setImageResource(R.drawable.ic_thumbs_up_gray);
        }

        if (tag.btnThumbsDown != null && mListenLogger.isDisliked(item.getRef())) {
            tag.btnThumbsDown.setImageResource(R.drawable.ic_thumb_down);
        } else if (tag.btnThumbsDown != null) {
            tag.btnThumbsDown.setImageResource(R.drawable.ic_thumb_down_gray);
        }

        if (item != null && item.isLoaded()) {
            tag.tvTitle.setText(item.getTitle());

            Artist artist = aggregator.retrieveArtist(item.getArtist(), item.getProvider());
            if (artist != null && artist.getName() != null && !artist.getName().isEmpty()) {
                tag.tvArtist.setText(artist.getName());
            } else {
                tag.tvArtist.setText("...");
            }

            tag.ivAlbumArt.loadArtForSong(item);
        } else {
            tag.tvTitle.setText(R.string.loading);
            tag.tvArtist.setText(null);
            tag.ivAlbumArt.setDefaultArt();
        }

        if (item == null || !item.isAvailable()) {
            tag.vRoot.setAlpha(0.5f);
        } else {
            tag.vRoot.setAlpha(1.0f);
        }

        return convertView;
    }

    private void updatePlaystate(PlayPauseDrawable drawable) {
        boolean buffering;
        int shape;

        switch (PlaybackProxy.getState()) {
            case PlaybackService.STATE_PLAYING:
                shape = PlayPauseDrawable.SHAPE_PAUSE;
                buffering = false;
                break;

            case PlaybackService.STATE_BUFFERING:
                shape = PlayPauseDrawable.SHAPE_PAUSE;
                buffering = true;
                break;

            case PlaybackService.STATE_PAUSING:
                shape = PlayPauseDrawable.SHAPE_PLAY;
                buffering = true;
                break;


            case PlaybackService.STATE_PAUSED:
            case PlaybackService.STATE_STOPPED:
            default:
                shape = PlayPauseDrawable.SHAPE_PLAY;
                buffering = false;
                break;
        }

        drawable.setShape(shape);
        drawable.setBuffering(buffering);
    }


    public static class ViewHolder {
        public boolean isCurrent;
        public ViewGroup vRoot;
        public TextView tvTitle;
        public TextView tvArtist;
        public AlbumArtImageView ivAlbumArt;
        public SeekBar sbSeek;
        public FloatingActionButton fabPlay;
        public PlayPauseDrawable fabPlayDrawable;
        public ImageView btnNext;
        public ImageView btnPrevious;
        public ImageView btnRepeat;
        public ImageView btnThumbs;
        public ImageView btnOverflow;
        public ImageView btnShuffle;
        public ImageView btnThumbsDown;
        public Song song;
    }
}
