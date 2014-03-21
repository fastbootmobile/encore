package org.omnirom.music.providers;

import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;

public interface ILocalCallback {

    void onSongUpdate(Song s);

    void onAlbumUpdate(Album a);

    void onPlaylistUpdate(Playlist p);

    void onArtistUpdate(Artist a);
}
