package org.omnirom.music.service;

import android.util.Log;

import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderCache;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Handles the playback of a list of songs
 */
public class PlaybackQueue extends ArrayList<Song> {

    private static final String TAG = "PlaybackQueue";

    /**
     * Adds a song to the queue
     * @param s The song to add
     * @param top If true, the song will be added at the top
     */
    public void addSong(Song s, boolean top) {
        if (top) {
            this.add(0, s);
        } else {
            this.add(s);
        }
    }

    /**
     * Adds a full playlist to the queue
     * @param p The playlist to add
     * @param top If true, the playlist will be added at the top.
     */
    public void addPlaylist(Playlist p, boolean top) {
        Iterator<String> songs = p.songs();
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();

        int index = 0;
        while (songs.hasNext()) {
            String songRef = songs.next();
            Song song = aggregator.retrieveSong(songRef, p.getProvider());
            if (song != null) {
                if (top) {
                    this.add(index, song);
                    index++;
                } else {
                    this.add(song);
                }
            } else {
                Log.w(TAG, "Cannot add playlist song " + songRef + " because it is not known" +
                        " by our cache");
            }
        }
    }
}
