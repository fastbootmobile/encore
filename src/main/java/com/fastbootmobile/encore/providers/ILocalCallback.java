package com.fastbootmobile.encore.providers;

import com.fastbootmobile.encore.model.Album;
import com.fastbootmobile.encore.model.Artist;
import com.fastbootmobile.encore.model.Playlist;
import com.fastbootmobile.encore.model.SearchResult;
import com.fastbootmobile.encore.model.Song;

import java.util.List;

/**
 * Local callback interface called on providers updates
 */
public interface ILocalCallback {
    /**
     * Called when a song metadata has been updated
     * @param s The list of songs updated
     */
    void onSongUpdate(List<Song> s);

    /**
     * Called when an album metadata has been updated
     * @param a The list of albums updated
     */
    void onAlbumUpdate(List<Album> a);

    /**
     * Called when playlist metadata has been updated
     * @param p The list of playlists updated
     */
    void onPlaylistUpdate(List<Playlist> p);

    /**
     * Called when a playlist has been removed
     * @param ref The reference of the playlist that has been removed
     */
    void onPlaylistRemoved(String ref);

    /**
     * Called when artist metadata has been updated
     * @param a The list of artists updated
     */
    void onArtistUpdate(List<Artist> a);

    /**
     * Called when a provider has connected
     * @param provider The provider that connected
     */
    void onProviderConnected(IMusicProvider provider);

    /**
     * Called when a provider returns a search result
     * @param searchResult The result
     */
    void onSearchResult(List<SearchResult> searchResult);
}
