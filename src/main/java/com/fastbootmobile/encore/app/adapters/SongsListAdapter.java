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

import android.content.Context;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.app.ui.AlbumArtImageView;
import com.fastbootmobile.encore.framework.PlaybackProxy;
import com.fastbootmobile.encore.model.Artist;
import com.fastbootmobile.encore.model.BoundEntity;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.ProviderAggregator;
import com.fastbootmobile.encore.utils.Utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Adapter allowing to display a list of songs in a ListView
 */
public class SongsListAdapter extends BaseAdapter {
    /**
     * ViewHolder for the list items
     */
    public static class ViewHolder {
        public TextView tvTitle;
        public TextView tvArtist;
        public TextView tvDuration;
        public ImageView ivOverflow;
        public AlbumArtImageView ivAlbumArt;
        public ImageView ivOffline;
        public ViewGroup vRoot;
        public int position;
        public Song song;
        public View vCurrentIndicator;
    }

    protected List<Song> mSongs;
    private boolean mShowAlbumArt;
    private RotateAnimation mSyncRotateAnimation;

    private View.OnClickListener mOverflowClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            final ViewHolder tag = (ViewHolder) v.getTag();
            final Context context = tag.vRoot.getContext();
            Utils.showSongOverflow((FragmentActivity) context, tag.ivOverflow, tag.song, false);
        }
    };

    private Comparator<Song> mComparator = new Comparator<Song>() {
        @Override
        public int compare(Song lhs, Song rhs) {
            if (lhs.isLoaded() && rhs.isLoaded()) {
                return lhs.getTitle().compareTo(rhs.getTitle());
            } else {
                return lhs.getRef().compareTo(rhs.getRef());
            }
        }
    };

    /**
     * Default constructor
     * @param showAlbumArt Whether or not to show album art in front of each item
     */
    public SongsListAdapter(boolean showAlbumArt) {
        mSongs = new ArrayList<>();
        mShowAlbumArt = showAlbumArt;
    }

    /**
     * Clear the current displayed songs
     */
    public void clear() {
        mSongs.clear();
    }

    public List<Song> getItems() {
        return mSongs;
    }

    /**
     * Adds a song to the adapter
     * @param song The song to add
     */
    public void put(Song song) {
        if (!mSongs.contains(song)) {
            mSongs.add(song);
        }
    }

    public void putAll(Collection<Song> songs) {
        for (Song song : songs) {
            put(song);
        }
    }

    /**
     * Sorts songs alphabetically
     */
    public void sortAll() {
        Collections.sort(mSongs, mComparator);
    }

    /**
     * Returns whether or not this adapter contains the provided song
     * @param s The song to check
     * @return true if the adapter contains the song, false otherwise
     */
    public boolean contains(Song s) {
        return mSongs.contains(s);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCount() {
        return mSongs.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Song getItem(int i) {
        return mSongs.get(i);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getItemId(int i) {
        return mSongs.get(i).getRef().hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getView(final int position, final View convertView, ViewGroup parent) {
        final Context ctx = parent.getContext();
        assert ctx != null;
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();

        View root = convertView;
        if (convertView == null) {
            // Recycle the existing view
            LayoutInflater inflater = LayoutInflater.from(ctx);
            root = inflater.inflate(R.layout.item_playlist_view, parent, false);
            assert root != null;

            ViewHolder holder = new ViewHolder();
            holder.tvTitle = (TextView) root.findViewById(R.id.tvTitle);
            holder.tvArtist = (TextView) root.findViewById(R.id.tvArtist);
            holder.tvDuration = (TextView) root.findViewById(R.id.tvDuration);
            holder.ivOverflow = (ImageView) root.findViewById(R.id.ivOverflow);
            holder.ivAlbumArt = (AlbumArtImageView) root.findViewById(R.id.ivAlbumArt);
            holder.ivOffline = (ImageView) root.findViewById(R.id.ivOffline);
            holder.vCurrentIndicator = root.findViewById(R.id.currentSongIndicator);
            holder.vRoot = (ViewGroup) root;

            holder.vRoot.setLayerType(View.LAYER_TYPE_HARDWARE, null);

            if (mShowAlbumArt) {
                // Fixup some style stuff
                holder.ivAlbumArt.setVisibility(View.VISIBLE);
            } else {
                holder.ivAlbumArt.setVisibility(View.GONE);
            }

            holder.ivOverflow.setOnClickListener(mOverflowClickListener);
            holder.ivOverflow.setTag(holder);
            root.setTag(holder);
        }
        final Song song = getItem(position);
        final ViewHolder tag = (ViewHolder) root.getTag();

        // Update tag
        tag.position = position;
        tag.song = song;
        root.setTag(tag);

        // Fill fields
        if (song != null && song.isLoaded()) {
            tag.tvTitle.setText(song.getTitle());
            tag.tvDuration.setText(Utils.formatTrackLength(song.getDuration()));

            if (mShowAlbumArt) {
                tag.ivAlbumArt.loadArtForSong(song);
            }

            if (song.getArtist() == null) {
                tag.tvArtist.setText(null);
            } else {
                Artist artist = aggregator.retrieveArtist(song.getArtist(), song.getProvider());
                if (artist != null) {
                    tag.tvArtist.setText(artist.getName());
                } else {
                    tag.tvArtist.setText("...");
                }
            }
        } else {
            tag.tvTitle.setText("...");
            tag.tvDuration.setText("...");
            tag.tvArtist.setText("...");

            if (mShowAlbumArt) {
                tag.ivAlbumArt.setDefaultArt();
            }
        }

        // Set current song indicator
        tag.vCurrentIndicator.setVisibility(View.INVISIBLE);

        Song currentSong = PlaybackProxy.getCurrentTrack();
        if (currentSong != null && currentSong.equals(tag.song)) {
            tag.vCurrentIndicator.setVisibility(View.VISIBLE);
        }

        if (song != null) {
            // Set alpha based on offline availability and mode
            if ((aggregator.isOfflineMode()
                    && song.getOfflineStatus() != BoundEntity.OFFLINE_STATUS_READY)
                    || !song.isAvailable()) {
                Utils.setChildrenAlpha(tag.vRoot,
                        Float.parseFloat(ctx.getResources().getString(R.string.unavailable_track_alpha)));
            } else {
                Utils.setChildrenAlpha(tag.vRoot, 1.0f);
            }

            // Show offline indicator in any case
            tag.ivOffline.setVisibility(View.VISIBLE);
            tag.ivOffline.clearAnimation();
            if (song.getOfflineStatus() == BoundEntity.OFFLINE_STATUS_READY) {
                tag.ivOffline.setImageResource(R.drawable.ic_track_downloaded);
            } else if (song.getOfflineStatus() == BoundEntity.OFFLINE_STATUS_DOWNLOADING) {
                tag.ivOffline.setImageResource(R.drawable.ic_sync_in_progress);

                if (mSyncRotateAnimation == null && tag.ivOffline.getMeasuredWidth() != 0) {
                    mSyncRotateAnimation = new RotateAnimation(0, -360,
                            tag.ivOffline.getMeasuredWidth() / 2.0f,
                            tag.ivOffline.getMeasuredHeight() / 2.0f);
                    mSyncRotateAnimation.setRepeatMode(Animation.INFINITE);
                    mSyncRotateAnimation.setRepeatCount(Animation.INFINITE);
                    mSyncRotateAnimation.setDuration(1000);
                    mSyncRotateAnimation.setInterpolator(new LinearInterpolator());
                }

                if (mSyncRotateAnimation != null) {
                    tag.ivOffline.startAnimation(mSyncRotateAnimation);
                }
            } else if (song.getOfflineStatus() == BoundEntity.OFFLINE_STATUS_ERROR) {
                tag.ivOffline.setImageResource(R.drawable.ic_sync_problem);
            } else if (song.getOfflineStatus() == BoundEntity.OFFLINE_STATUS_PENDING) {
                tag.ivOffline.setImageResource(R.drawable.ic_track_download_pending);
            } else {
                tag.ivOffline.setVisibility(View.GONE);
            }
        }

        return root;
    }

}
