package org.omnirom.music.app.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.support.v4.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderConnection;
import org.omnirom.music.providers.ProviderIdentifier;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Created by h4o on 27/06/2014.
 */
public class NewPlaylistFragment extends DialogFragment {
    private String TAG = "NewPlaylistFragment";
    private static final String KEY_SONG = "song";
    private static final String KEY_ALBUM = "album";

    private Song mSong;
    private Album mAlbum;

    public static NewPlaylistFragment newInstance(Song song) {
        NewPlaylistFragment fragment = new NewPlaylistFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(KEY_SONG, song);
        fragment.setArguments(bundle);
        return fragment;
    }

    public static NewPlaylistFragment newInstance(Album album) {
        NewPlaylistFragment fragment = new NewPlaylistFragment();
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
            throw new IllegalArgumentException("This fragment requires a song");
        }

        if (args.containsKey(KEY_SONG)) {
            mSong = args.getParcelable(KEY_SONG);
        } else if (args.containsKey(KEY_ALBUM)) {
            mAlbum = args.getParcelable(KEY_ALBUM);
        }

    }

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
                            Log.d(TAG, "Adding new playlist with name" + playlistNameStr);

                            try {
                                ProviderConnection connection;
                                if (multiProviderPlaylist.isChecked()) {
                                    connection = PluginsLookup.getDefault().getMultiProviderPlaylistProvider();
                                } else {
                                    connection = PluginsLookup.getDefault().getProvider(mSong.getProvider());
                                }

                                IMusicProvider binder = connection.getBinder();
                                String playlistRef = binder.addPlaylist(playlistName.getText().toString());

                                if (playlistRef != null) {
                                    if (mSong != null) {
                                        binder.addSongToPlaylist(mSong.getRef(), playlistRef, mSong.getProvider());
                                    } else {
                                        Iterator<String> songs = mAlbum.songs();
                                        while (songs.hasNext()) {
                                            binder.addSongToPlaylist(songs.next(), playlistRef, mAlbum.getProvider());
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Unable to add playlist", e);
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
