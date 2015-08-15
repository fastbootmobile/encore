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

import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.fastbootmobile.encore.framework.PluginsLookup;
import com.fastbootmobile.encore.model.Playlist;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.IMusicProvider;
import com.fastbootmobile.encore.providers.ProviderAggregator;
import com.fastbootmobile.encore.providers.ProviderConnection;
import com.fastbootmobile.encore.providers.ProviderIdentifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter showing playlists songs in a ListView. This is a derivative
 * of {@link com.fastbootmobile.encore.app.adapters.SongsListAdapter} as the core display is similar,
 * except PlaylistAdapter supports drag, drop and deletion of items.
 */
public class PlaylistAdapter extends SongsListAdapter {
    private static final String TAG = "PlaylistAdapter";

    private List<Integer> mVisible;
    private List<Integer> mIds;
    private Playlist mPlaylist;

    /**
     * Default constructor
     */
    public PlaylistAdapter() {
        super(true);
        mVisible = new ArrayList<>();
        mIds = new ArrayList<>();
    }

    /**
     * Sets the playlist to display. This will call automatically {@link #notifyDataSetChanged()}.
     * @param playlist The playlist to display
     */
    public void setPlaylist(Playlist playlist) {
        mPlaylist = playlist;
        notifyDataSetChanged();
    }

    /**
     * Calls the proper handler to update the playlist order
     * @param oldPosition The old/current position of the item to move
     * @param newPosition The new position of the item
     */
    public void updatePlaylist(int oldPosition, int newPosition) {
        final ProviderIdentifier providerIdentifier = mPlaylist.getProvider();
        try {
            ProviderConnection connection = PluginsLookup.getDefault().getProvider(providerIdentifier);
            if (connection != null) {
                IMusicProvider binder = connection.getBinder();
                if (binder != null) {
                    binder.onUserSwapPlaylistItem(oldPosition, newPosition, mPlaylist.getRef());
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Error: " + e.getMessage());
        }
    }

    /**
     * Removes the provided item from the playlist on the view and from the playlist on the provider
     * @param id The ID of the playlist
     */
    public void delete(int id) {
        final ProviderIdentifier providerIdentifier = mPlaylist.getProvider();
        try {
            ProviderConnection connection = PluginsLookup.getDefault().getProvider(providerIdentifier);
            if (connection != null) {
                IMusicProvider binder = connection.getBinder();
                if (binder != null) {
                    binder.deleteSongFromPlaylist(id, mPlaylist.getRef());
                }
            }

            mSongs.remove(id);
            mIds.remove(id);
            mVisible.remove(id);
            resetIds();
        } catch (RemoteException e) {
            Log.e(TAG, "Error: " + e.getMessage());
        }
    }

    private void resetIds() {
        for (int i = 0; i < mIds.size(); i++) {
            mIds.set(i, i);
        }
    }

    /**
     * Swaps two elements and their properties
     * @param original The original position of the element
     * @param newPosition The new position of the item
     */
    public void swap(int original, int newPosition) {
        Song temp = mSongs.get(original);
        Song newSong = mSongs.get(newPosition);
        mSongs.set(original, newSong);
        mSongs.set(newPosition, temp);

        int tempVis = mVisible.get(original);
        mVisible.set(original, mVisible.get(newPosition));
        mVisible.set(newPosition, tempVis);

        int tempId = mIds.get(original);
        mIds.set(original, mIds.get(newPosition));
        mIds.set(newPosition, tempId);

        super.notifyDataSetChanged();
    }

    /**
     * Sets the visibility of a selected element to visibility
     * Save the visibility to remember when the view is recycled
     * @param position The position of the item
     * @param visibility The visibility flag (View)
     */
    public void setVisibility(int position, int visibility) {
        if (position >= 0 && position < mVisible.size()) {
            mVisible.set(position, visibility);
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void notifyDataSetChanged() {
        // We reload the songs from the playlist associated with this adapter
        mSongs.clear();
        mIds.clear();
        mVisible.clear();

        final List<String> it = new ArrayList<>(mPlaylist.songsList());
        final ProviderIdentifier id = mPlaylist.getProvider();
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();

        for (String songRef : it) {
            Song s = aggregator.retrieveSong(songRef, id);
            if (s == null) {
                Log.e(TAG, "Retrieved a null song from the playlist!");
            } else {
                put(s);
            }
        }

        // And we notify the list that something changed
        super.notifyDataSetChanged();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void put(Song p) {
        super.put(p);

        mVisible.add(View.VISIBLE);
        mIds.add(mSongs.size() - 1);
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
    public Song getItem(int position) {
        return mSongs.get(position);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getItemId(int position) {
        if (position >= 0 && position < mIds.size()) {
            return mIds.get(position);
        }

        Log.e(TAG, "Item ID out of bounds: " + position);
        return -1;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("ResourceType")
    @Override
    public View getView(int position, View convertView, final ViewGroup parent) {
        View root = super.getView(position, convertView, parent);
        root.setVisibility(mVisible.get(position));
        return root;
    }

}
