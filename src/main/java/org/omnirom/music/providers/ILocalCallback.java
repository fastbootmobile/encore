package org.omnirom.music.providers;

import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;

import java.util.List;

public interface ILocalCallback {

    void onSongUpdate(List<Song> s);

    void onAlbumUpdate(List<Album> a);

    void onPlaylistUpdate(List<Playlist> p);

    void onArtistUpdate(List<Artist> a);

    void onProviderConnected(IMusicProvider provider);

}
