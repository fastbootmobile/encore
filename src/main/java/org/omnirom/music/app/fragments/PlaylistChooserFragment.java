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

package org.omnirom.music.app.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.util.Log;

import org.omnirom.music.app.R;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.BoundEntity;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderConnection;
import org.omnirom.music.providers.ProviderIdentifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Dialog fragment allowing the user to select a playlist to which add a song or an album
 */
public class PlaylistChooserFragment extends DialogFragment {
    private static final String TAG = "PlaylistChooserFragment";

    private static final String KEY_SONG = "song";
    private static final String KEY_ALBUM = "album";

    private Song mSong;
    private Album mAlbum;

    /**
     * Creates the fragment in the perspective of adding a song to a playlist
     * @param song The song to add
     * @return The fragment generated
     */
    public static PlaylistChooserFragment newInstance(Song song) {
        PlaylistChooserFragment fragment = new PlaylistChooserFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(KEY_SONG, song);
        fragment.setArguments(bundle);
        return fragment;
    }

    /**
     * Creates the fragment in the perspective of adding an album to a playlist
     * @param album The album of which tracks will be added
     * @return The fragment generated
     */
    public static PlaylistChooserFragment newInstance(Album album) {
        PlaylistChooserFragment fragment = new PlaylistChooserFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(KEY_ALBUM, album);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        Bundle args = getArguments();
        if (args == null) {
            throw new IllegalArgumentException("This fragment requires a song or album");
        }

        // Get the cached entity to have updated copy
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();

        if (args.containsKey(KEY_SONG)) {
            mSong = args.getParcelable(KEY_SONG);
            mSong = aggregator.retrieveSong(mSong.getRef(), mSong.getProvider());
        } else if (args.containsKey(KEY_ALBUM)) {
            mAlbum = args.getParcelable(KEY_ALBUM);
            mAlbum = aggregator.retrieveAlbum(mAlbum.getRef(), mAlbum.getProvider());
        } else {
            throw new IllegalArgumentException("Neither song or album parameters were found");
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstance) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();

        List<Playlist> playlistList = aggregator.getAllPlaylists();
        Collections.sort(playlistList, new Comparator<Playlist>() {
            @Override
            public int compare(Playlist lhs, Playlist rhs) {
                return lhs.getName().compareTo(rhs.getName());
            }
        });

        List<String> choices = new ArrayList<String>();
        choices.add(getString(R.string.new_playlist));
        final List<Playlist> playlistChoices = new ArrayList<Playlist>();
        final ProviderConnection mppp = PluginsLookup.getDefault().getMultiProviderPlaylistProvider();

        for (Playlist playlist : playlistList) {
            ProviderIdentifier providerIdentifier = playlist.getProvider();
            BoundEntity ent = (mSong != null ? mSong : mAlbum);
            if (ent.getProvider().equals(providerIdentifier)
                    || mppp.getIdentifier().equals(providerIdentifier)) {
                String decoration = "";
                choices.add(playlist.getName() + decoration);
                playlistChoices.add(playlist);
            }
        }

        builder.setTitle(getString(R.string.add_to))
                .setItems(choices.toArray(new String[choices.size()]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            if (mSong != null) {
                                NewPlaylistFragment fragment = NewPlaylistFragment.newInstance(mSong);
                                fragment.show(getFragmentManager(), mSong.getRef() + "-newplaylist");
                            } else {
                                NewPlaylistFragment fragment = NewPlaylistFragment.newInstance(mAlbum);
                                fragment.show(getFragmentManager(), mAlbum.getRef() + "-newplaylist");
                            }
                        } else {
                            final Playlist playlistChosen = playlistChoices.get(which - 1);
                            ProviderIdentifier playlistChosenId =
                                    aggregator.getCache().getRefProvider(playlistChosen.getRef());
                            try {
                                final IMusicProvider provider = PluginsLookup.getDefault().getProvider(playlistChosenId).getBinder();
                                if (mSong != null) {
                                    provider.addSongToPlaylist(mSong.getRef(), playlistChosen.getRef(), mSong.getProvider());
                                } else {
                                    Iterator<String> songs = mAlbum.songs();
                                    while (songs.hasNext()) {
                                        provider.addSongToPlaylist(songs.next(), playlistChosen.getRef(), mAlbum.getProvider());
                                    }
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Unable to add to playlist", e);
                            }
                        }
                    }
                });
        return builder.create();
    }
}
