package org.omnirom.music.service;

import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Handles the playback of a list of songs
 */

public class PlaybackQueue extends ArrayList<Song> {

    public void addSong(Song s, boolean top) {
        if (top) {
            this.add(0, s);
        } else {
            this.add(s);
        }
    }

    public void addPlaylist(Playlist p, boolean top) {
        Iterator<String> songs = p.songs();
    }
}
