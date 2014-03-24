package org.omnirom.music.providers;

import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Caches information gotten by providers
 */
public class ProviderCache {
    private Map<String, Playlist> mPlaylists;
    private Map<String, Song> mSongs;
    private Map<String, Album> mAlbums;
    private Map<String, Artist> mArtists;

    public ProviderCache() {
        mPlaylists = new HashMap<String, Playlist>();
        mSongs = new HashMap<String, Song>();
        mAlbums = new HashMap<String, Album>();
        mArtists = new HashMap<String, Artist>();
    }

    public void putPlaylist(final Playlist pl) {
        mPlaylists.put(pl.getRef(), pl);
    }

    public Playlist getPlaylist(final String ref) {
        return mPlaylists.get(ref);
    }

    public List<Playlist> getAllPlaylists() {
        return new ArrayList<Playlist>(mPlaylists.values());
    }

    public void putSong(final Song song) {
        mSongs.put(song.getRef(), song);
    }

    public Song getSong(final String ref) {
        return mSongs.get(ref);
    }

    public void putAlbum(final Album album) {
        mAlbums.put(album.getRef(), album);
    }

    public Album getAlbum(final String ref) {
        return mAlbums.get(ref);
    }

    public void putArtist(final Artist artist) {
        mArtists.put(artist.getRef(), artist);
    }

    public Artist getArtist(final String ref) {
        return mArtists.get(ref);
    }
}
