package org.omnirom.music.app.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;

import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderIdentifier;

import java.nio.ByteBuffer;
import java.security.Provider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by h4o on 27/06/2014.
 */
public class PlaylistChooserFragment extends DialogFragment {
    private String TAG = "PlaylistChooserFragment";
    private static final String KEY_SONG = "song";
    private Song mSong;
    public static  PlaylistChooserFragment newInstance(Song song){
        PlaylistChooserFragment fragment = new PlaylistChooserFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(KEY_SONG,song);
        fragment.setArguments(bundle);
        return fragment;
    }
    @Override
    public void onCreate(Bundle savedInstance){
        super.onCreate(savedInstance);
        Bundle args = getArguments();
        if(args == null){
            throw new IllegalArgumentException("This fragment requires a song");
        }
        mSong = args.getParcelable(KEY_SONG);

    }
    @Override
    public Dialog onCreateDialog(Bundle savedInstance){
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        final List<Playlist> playlistList = ProviderAggregator.getDefault().getAllPlaylists();

        Collections.sort(playlistList,new Comparator<Playlist>() {
            @Override
            public int compare(Playlist lhs, Playlist rhs) {
                return lhs.getName().compareTo(rhs.getName());
            }
        });
        List<String> choices = new ArrayList<String>();
        choices.add("new playlist");
        for(Playlist playlist : playlistList){
            ProviderIdentifier providerIdentifier = ProviderAggregator.getDefault().getCache().getRefProvider(playlist.getRef());
           // if(mSong.getProvider().equals(providerIdentifier))
                choices.add(playlist.getName());
        }


        builder.setTitle("Choose a playlist to add")
               .setItems(choices.toArray(new String[choices.size()]), new DialogInterface.OnClickListener() {
                   @Override
                   public void onClick(DialogInterface dialog, int which) {
                       if(which == 0){
                            NewPlaylistFragment fragment = NewPlaylistFragment.newInstance(mSong);
                            fragment.show(getFragmentManager(),mSong.getRef()+"-newplaylist");
                       }else {

                           ProviderIdentifier playlistChosenId = ProviderAggregator.getDefault().getCache().getRefProvider(playlistList.get(which-1).getRef());
                           try {
                               PluginsLookup.getDefault().getProvider(playlistChosenId).getBinder().addSongToPlaylist(mSong.getRef(), playlistList.get(which-1).getRef(), mSong.getProvider());
                           } catch (Exception e) {

                           }
                       }
                   }
               });
        return builder.create();
    }
}
