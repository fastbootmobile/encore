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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONException;
import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.app.ui.AlbumArtImageView;
import com.fastbootmobile.encore.framework.PlaylistOrderer;
import com.fastbootmobile.encore.model.BoundEntity;
import com.fastbootmobile.encore.model.Playlist;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Adapter to display a list of {@link com.fastbootmobile.encore.model.Playlist} in a GridView
 */
public class PlaylistGridAdapter extends BaseAdapter {
    private static final String TAG = "PlaylistGridAdapter";

    /**
     * ViewHolder for items
     */
    public static class ViewHolder {
        public AlbumArtImageView ivCover;
        public TextView tvTitle;
        public TextView tvSubTitle;
        public ViewGroup vRoot;
        public Playlist playlist;
        public ImageView ivOfflineStatus;
    }

    private List<Playlist> mPlaylists;
    private PlaylistOrderer mOrderer;

    /**
     * Default constructor
     */
    public PlaylistGridAdapter(Context context) {
        mPlaylists = new ArrayList<>();
        ensureOrderer(context);
    }

    private void ensureOrderer(Context context) {
        if (mOrderer == null && context != null) {
            mOrderer = new PlaylistOrderer(context);
        } else if (mOrderer == null) {
            throw new IllegalArgumentException("Orderer context is null and orderer is null too!");
        }
    }

    /**
     * Sorts the list alphabetically
     */
    private void sortList(Context context) throws JSONException {
        ensureOrderer(context);
        final Map<String, Integer> order = mOrderer.getOrder();

        if (order != null) {
            Collections.sort(mPlaylists, new PlaylistListAdapter.PlaylistSort(order));
        }
    }

    /**
     * Adds a playlist to the adapter
     * @param p The playlist to add
     */
    public void addItem(Playlist p) {
        mPlaylists.add(p);
    }

    /**
     * Adds the playlist to the adapter if it's not already there
     * @param p The playlist to add
     */
    public boolean addItemUnique(Playlist p) {
        if (!mPlaylists.contains(p)) {
            mPlaylists.add(p);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Add all the elements into the adapter if they're not already there
     * @param ps The collection of {@link com.fastbootmobile.encore.model.Playlist} to add
     */
    public boolean addAllUnique(Collection<Playlist> ps) {
        boolean didChange = false;
        for (Playlist p : ps) {
            if (p != null && !mPlaylists.contains(p)) {
                mPlaylists.add(p);
                didChange = true;
            }
        }

        if (didChange) {
            notifyDataSetChanged();
        }

        return didChange;
    }

    public void remove(String ref) {
        for (Playlist playlist : mPlaylists) {
            if (playlist.getRef().equals(ref)) {
                mPlaylists.remove(ref);
                break;
            }
        }
    }

    public void clear() {
        mPlaylists.clear();
    }

    /**
     * Returns whether or not the adapter contains the provided Playlist
     * @param p The playlist to check
     * @return True if the adapter already has the playlist, false otherwise
     */
    public boolean contains(Playlist p) {
        return mPlaylists.contains(p);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCount() {
        return mPlaylists.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Playlist getItem(int position) {
        return mPlaylists.get(position);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getItemId(int position) {
        if (position >= 0 && position < mPlaylists.size()) {
            return mPlaylists.get(position).getRef().hashCode();
        } else {
            return -1;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Context ctx = parent.getContext();
        assert ctx != null;

        View root = convertView;
        if (convertView == null) {
            // Create a new view (nothing to recycle)
            LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            root = inflater.inflate(R.layout.medium_card_two_lines, parent, false);
            assert root != null;

            ViewHolder holder = new ViewHolder();
            holder.vRoot = (ViewGroup) root;
            holder.ivCover = (AlbumArtImageView) root.findViewById(R.id.ivCover);
            holder.tvTitle = (TextView) root.findViewById(R.id.tvTitle);
            holder.tvSubTitle = (TextView) root.findViewById(R.id.tvSubTitle);
            holder.ivOfflineStatus = (ImageView) root.findViewById(R.id.ivOfflineStatus);

            root.setTag(holder);
        }

        // Fill in the fields
        final Playlist playlist = getItem(position);
        final ViewHolder tag = (ViewHolder) root.getTag();

        tag.playlist = playlist;

        if (playlist.isLoaded() || playlist.getName() != null) {
            tag.tvTitle.setText(playlist.getName());
            tag.tvSubTitle.setText(ctx.getResources().getQuantityString(R.plurals.songs_count, playlist.getSongsCount(), playlist.getSongsCount()));
            tag.ivCover.loadArtForPlaylist(playlist);

            tag.ivOfflineStatus.setVisibility(View.VISIBLE);
            switch (playlist.getOfflineStatus()) {
                case BoundEntity.OFFLINE_STATUS_NO:
                    tag.ivOfflineStatus.setVisibility(View.GONE);
                    break;

                case BoundEntity.OFFLINE_STATUS_DOWNLOADING:
                    tag.ivOfflineStatus.setImageResource(R.drawable.ic_sync_in_progress);
                    break;

                case BoundEntity.OFFLINE_STATUS_ERROR:
                    tag.ivOfflineStatus.setImageResource(R.drawable.ic_sync_problem);
                    break;

                case BoundEntity.OFFLINE_STATUS_PENDING:
                    tag.ivOfflineStatus.setImageResource(R.drawable.ic_track_download_pending);
                    break;

                case BoundEntity.OFFLINE_STATUS_READY:
                    tag.ivOfflineStatus.setImageResource(R.drawable.ic_track_downloaded);
                    break;
            }
        } else {
            tag.tvTitle.setText(R.string.loading);
            tag.tvSubTitle.setText(R.string.loading);
            tag.ivCover.setDefaultArt();
            tag.ivOfflineStatus.setVisibility(View.GONE);
        }

        return root;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void notifyDataSetChanged() {
        // We sort the list before showing it
        try {
            sortList(null);
        } catch (JSONException e) {
            Log.e(TAG, "Cannot sort items", e);
        }
        super.notifyDataSetChanged();
    }
}
