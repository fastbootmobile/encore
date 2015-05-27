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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.widget.Toast;

import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.framework.PluginsLookup;
import com.fastbootmobile.encore.model.Album;
import com.fastbootmobile.encore.model.BoundEntity;
import com.fastbootmobile.encore.model.Playlist;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.IMusicProvider;
import com.fastbootmobile.encore.providers.ProviderAggregator;
import com.fastbootmobile.encore.providers.ProviderConnection;
import com.fastbootmobile.encore.providers.ProviderIdentifier;

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
    private static final String KEY_PLAYLIST = "playlist";

    private Song mSong;
    private Album mAlbum;
    private Playlist mPlaylist;

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

    /**
     * Creates the fragment in the perspective of appending a playlist to another one
     * @param playlist The playlist that should be appended to the selection
     * @return The fragment generated
     */
    public static PlaylistChooserFragment newInstance(Playlist playlist) {
        PlaylistChooserFragment fragment = new PlaylistChooserFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(KEY_PLAYLIST, playlist);
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
        } else if (args.containsKey(KEY_PLAYLIST)) {
            mPlaylist = args.getParcelable(KEY_PLAYLIST);
            mPlaylist = aggregator.retrievePlaylist(mPlaylist.getRef(), mPlaylist.getProvider());
        } else {
            throw new IllegalArgumentException("No song, album or playlist parameters were found");
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstance) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();

        // Get and sort the playlists available
        List<Playlist> playlistList = aggregator.getAllPlaylists();
        Collections.sort(playlistList, new Comparator<Playlist>() {
            @Override
            public int compare(Playlist lhs, Playlist rhs) {
                if (lhs.getName() != null && rhs.getName() != null) {
                    return lhs.getName().compareTo(rhs.getName());
                } else if (lhs.getName() == null && rhs.getName() == null) {
                    return 0;
                } else if (lhs.getName() == null) {
                    return 1;
                } else {
                    return -1;
                }
            }
        });

        List<String> choices = new ArrayList<>();
        choices.add(getString(R.string.new_playlist));
        final List<Playlist> playlistChoices = new ArrayList<>();
        final ProviderConnection mppp = PluginsLookup.getDefault().getMultiProviderPlaylistProvider();

        // Decide what entity we are using
        BoundEntity ent;
        if (mSong != null) {
            ent = mSong;
        } else if (mAlbum != null) {
            ent = mAlbum;
        } else if (mPlaylist != null) {
            ent = mPlaylist;
        } else {
            throw new RuntimeException("No entity attached for source checking");
        }

        for (Playlist playlist : playlistList) {
            ProviderIdentifier providerIdentifier = playlist.getProvider();

            // Allow adding to a playlist from the same provider, or from the MultiProvider
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
                            NewPlaylistFragment fragment;
                            String ref;
                            if (mSong != null) {
                                fragment = NewPlaylistFragment.newInstance(mSong);
                                ref = mSong.getRef();
                            } else if (mAlbum != null) {
                                fragment = NewPlaylistFragment.newInstance(mAlbum);
                                ref = mAlbum.getRef();
                            } else if (mPlaylist != null) {
                                fragment = NewPlaylistFragment.newInstance(mPlaylist);
                                ref = mPlaylist.getRef();
                            } else {
                                throw new RuntimeException("Shouldn't be here");
                            }

                            fragment.show(getFragmentManager(), ref + "-newplaylist");
                        } else {
                            final Playlist playlistChosen = playlistChoices.get(which - 1);
                            ProviderIdentifier playlistChosenId =
                                    aggregator.getCache().getRefProvider(playlistChosen.getRef());
                            try {
                                final IMusicProvider provider = PluginsLookup.getDefault().getProvider(playlistChosenId).getBinder();

                                int totalCount = 0;
                                int successCount = 0;

                                if (mSong != null) {
                                    ++totalCount;
                                    if (provider.addSongToPlaylist(mSong.getRef(), playlistChosen.getRef(), mSong.getProvider())) {
                                        ++successCount;
                                    }
                                } else if (mAlbum != null) {
                                    Iterator<String> songs = mAlbum.songs();
                                    while (songs.hasNext()) {
                                        if (provider.addSongToPlaylist(songs.next(), playlistChosen.getRef(), mAlbum.getProvider())) {
                                            ++successCount;
                                        }
                                        ++totalCount;
                                    }
                                } else if (mPlaylist != null) {
                                    Iterator<String> songs = mPlaylist.songs();
                                    while (songs.hasNext()) {
                                        // TODO: This might cause issues if we add a playlist
                                        // from a multi-provider playlist to another one
                                        ++totalCount;
                                        if (provider.addSongToPlaylist(songs.next(), playlistChosen.getRef(), mPlaylist.getProvider())) {
                                            ++successCount;
                                        }
                                    }
                                }

                                if (totalCount == successCount) {
                                    Toast.makeText(getActivity(), getString(R.string.toast_playlist_track_add_success,
                                            successCount, playlistChosen.getName()), Toast.LENGTH_SHORT).show();
                                } else if (successCount > 0) {
                                    Toast.makeText(getActivity(), getString(R.string.toast_playlist_track_add_partial,
                                            successCount, totalCount, playlistChosen.getName()), Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(getActivity(), getString(R.string.toast_playlist_track_add_error,
                                            playlistChosen.getName()), Toast.LENGTH_SHORT).show();
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Unable to add to playlist", e);
                                Toast.makeText(getActivity(), getString(R.string.toast_playlist_track_add_error,
                                        playlistChosen.getName()), Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
        return builder.create();
    }
}
