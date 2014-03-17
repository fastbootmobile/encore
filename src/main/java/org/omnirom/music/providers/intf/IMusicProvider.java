package org.omnirom.music.providers.intf;

import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;

import java.util.List;

public interface IMusicProvider {
    /**
     * Authenticates the user against the provider.
     *
     * @param username Login username
     * @param password Login password
     * @return true if authentication succeeded, false otherwise
     */
    public boolean login(final String username, final String password);

    /**
     * @return true if this provider is authenticated and ready to be used, false otherwise
     */
    public boolean isAuthenticated();

    /**
     * Informs whether or not this provider is infinite (ie. it's a cloud provider that allows
     * you to access a virtually unlimited number of tracks, such as Spotify or Deezer ; the local
     * storage or a simple storage provider would return false).
     *
     * @return true if there's no defined number of tracks, false otherwise
     */
    public boolean isInfinite();

    /**
     * Returns the list of all albums
     * This method call is only valid when isInfinite returns false
     *
     * @return A list of all the albums available on the provider
     */
    public List<Album> getAlbums();

    /**
     * Returns the list of all artists
     * This method call is only valid when isInfinite returns false
     *
     * @return A list of all the artists available on the provider
     */
    public List<Artist> getArtists();

    /**
     * Returns the list of all songs
     * This method call is only valid when isInfinite returns false
     *
     * @return A list of all songs available on the provider
     */
    public List<Song> getSongs();

    /**
     * Returns the list of all playlists on this provider
     * This method is valid for both infinite and defined providers.
     *
     * @return A list of all playlists on this provider
     */
    public List<Playlist> getPlaylists();


}
