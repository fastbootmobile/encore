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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.framework.PluginsLookup;
import com.fastbootmobile.encore.model.Album;
import com.fastbootmobile.encore.model.Playlist;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.IMusicProvider;
import com.fastbootmobile.encore.providers.ProviderConnection;
import com.fastbootmobile.encore.providers.ProviderIdentifier;
import com.fastbootmobile.encore.utils.Utils;

import java.util.Iterator;

/**
 * Dialog Fragment allowing creation of a new playlist
 */
public class NewPlaylistFragment extends DialogFragment {
    private static final String TAG = "NewPlaylistFragment";

    private static final String KEY_SONG = "song";
    private static final String KEY_ALBUM = "album";
    private static final String KEY_PLAYLIST = "playlist";

    private Song mSong;
    private Album mAlbum;
    private Playlist mPlaylist;

    /**
     * Creates a new instance of the New Playlist dialog fragment to create a new playlist and
     * add a song to it.
     *
     * @param song The song to add to the playlist
     * @return The fragment generated
     */
    public static NewPlaylistFragment newInstance(Song song) {
        NewPlaylistFragment fragment = new NewPlaylistFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(KEY_SONG, song);
        fragment.setArguments(bundle);
        return fragment;
    }

    /**
     * Creates a new instance of the New Playlist dialog fragment to create a new playlist and
     * add all tracks of an album to it.
     *
     * @param album The album to add to the playlist
     * @return The fragment generated
     */
    public static NewPlaylistFragment newInstance(Album album) {
        NewPlaylistFragment fragment = new NewPlaylistFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(KEY_ALBUM, album);
        fragment.setArguments(bundle);
        return fragment;
    }

    /**
     * Creates a new instance of the New Playlist dialog fragment to create a new playlist and
     * add all tracks of an existing playlist to it.
     *
     * @param playlist The playlist to append to the new playlist
     * @return The fragment generated
     */
    public static NewPlaylistFragment newInstance(Playlist playlist) {
        NewPlaylistFragment fragment = new NewPlaylistFragment();
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
            throw new IllegalArgumentException("This fragment requires a song or an album");
        }

        if (args.containsKey(KEY_SONG)) {
            mSong = args.getParcelable(KEY_SONG);
        } else if (args.containsKey(KEY_ALBUM)) {
            mAlbum = args.getParcelable(KEY_ALBUM);
        } else if (args.containsKey(KEY_PLAYLIST)) {
            mPlaylist = args.getParcelable(KEY_PLAYLIST);
        }

    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstance) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View root = inflater.inflate(R.layout.dialog_new_playlist, null);
        final TextView playlistName = (TextView) root.findViewById(R.id.et_playlist_name);
        final CheckBox multiProviderPlaylist = (CheckBox) root.findViewById(R.id.cb_provider_specific);
        builder.setView(root)
                .setPositiveButton(getString(R.string.create), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String playlistNameStr = playlistName.getText().toString().trim();
                        if (!playlistNameStr.isEmpty()) {
                            Log.d(TAG, "Adding new playlist named '" + playlistNameStr + "'");

                            try {
                                ProviderConnection connection;
                                if (multiProviderPlaylist.isChecked()) {
                                    connection = PluginsLookup.getDefault().getMultiProviderPlaylistProvider();
                                } else {
                                    ProviderIdentifier identifier;
                                    if (mSong != null) {
                                        identifier = mSong.getProvider();
                                    } else if (mAlbum != null) {
                                        identifier = mAlbum.getProvider();
                                    } else if (mPlaylist != null) {
                                        identifier = mPlaylist.getProvider();
                                    } else {
                                        throw new IllegalStateException("Song, Album and Playlist are all null, cannot determine provider!");
                                    }

                                    connection = PluginsLookup.getDefault().getProvider(identifier);
                                }

                                IMusicProvider binder = connection.getBinder();
                                String playlistRef = binder.addPlaylist(playlistName.getText().toString());

                                if (playlistRef != null) {
                                    if (mSong != null) {
                                        binder.addSongToPlaylist(mSong.getRef(), playlistRef, mSong.getProvider());
                                    } else if (mAlbum != null) {
                                        Iterator<String> songs = mAlbum.songs();
                                        while (songs.hasNext()) {
                                            binder.addSongToPlaylist(songs.next(), playlistRef, mAlbum.getProvider());
                                        }
                                    } else if (mPlaylist != null) {
                                        Iterator<String> songs = mPlaylist.songs();
                                        while (songs.hasNext()) {
                                            // TODO: This might cause issues if we add a playlist
                                            // from a multi-provider playlist to another one
                                            binder.addSongToPlaylist(songs.next(), playlistRef, mPlaylist.getProvider());
                                        }
                                    }
                                } else {
                                    throw new IllegalStateException("Playlist reference returned by the provider is null!");
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Unable to add playlist", e);
                                Toast.makeText(getActivity(), getString(R.string.toast_playlist_track_add_error, playlistNameStr), Toast.LENGTH_SHORT).show();
                            }

                        } else {
                            Utils.shortToast(getActivity(), R.string.enter_name);
                        }
                    }

                })
                .setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                }).setTitle(getString(R.string.new_playlist));
        return builder.create();

    }
}
