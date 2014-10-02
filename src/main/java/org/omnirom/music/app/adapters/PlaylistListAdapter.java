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

package org.omnirom.music.app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import org.omnirom.music.app.R;
import org.omnirom.music.app.ui.AlbumArtImageView;
import org.omnirom.music.model.Playlist;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Adapter to display a list of {@link org.omnirom.music.model.Playlist} in a GridView
 */
public class PlaylistListAdapter extends BaseAdapter {
    /**
     * ViewHolder for items
     */
    private static class ViewHolder {
        public AlbumArtImageView ivCover;
        public TextView tvTitle;
        public TextView tvSubTitle;
        public Playlist playlist;
    }

    private List<Playlist> mPlaylists;

    private Comparator<Playlist> mComparator = new Comparator<Playlist>() {
        @Override
        public int compare(Playlist playlist, Playlist playlist2) {
            if (playlist != null && playlist2 != null && playlist.getName() != null
                    && playlist2.getName() != null) {
                return playlist.getName().compareTo(playlist2.getName());
            } else if (playlist != null && playlist2 != null) {
                return playlist.getRef().compareTo(playlist2.getRef());
            } else {
                return 0;
            }
        }
    };

    /**
     * Default constructor
     */
    public PlaylistListAdapter() {
        mPlaylists = new ArrayList<Playlist>();
    }

    /**
     * Sorts the list alphabetically
     */
    private void sortList() {
        Collections.sort(mPlaylists, mComparator);
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
    public void addItemUnique(Playlist p) {
        if (!mPlaylists.contains(p)) {
            mPlaylists.add(p);
        }
    }

    /**
     * Add all the elements into the adapter if they're not already there
     * @param ps The collection of {@link org.omnirom.music.model.Playlist} to add
     */
    public void addAllUnique(Collection<Playlist> ps) {
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
            holder.ivCover = (AlbumArtImageView) root.findViewById(R.id.ivCover);
            holder.tvTitle = (TextView) root.findViewById(R.id.tvTitle);
            holder.tvSubTitle = (TextView) root.findViewById(R.id.tvSubTitle);

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
        } else {
            tag.tvTitle.setText(R.string.loading);
            tag.tvSubTitle.setText(R.string.loading);
            tag.ivCover.setDefaultArt();
        }

        return root;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void notifyDataSetChanged() {
        // We sort the list before showing it
        sortList();
        super.notifyDataSetChanged();
    }
}
