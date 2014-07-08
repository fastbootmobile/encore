package org.omnirom.music.framework;

import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderCache;
import org.omnirom.music.providers.ProviderConnection;

/**
 * Created by xplodwild on 7/8/14.
 */
public class Suggestor {

    private static final Suggestor INSTANCE = new Suggestor();

    public static Suggestor getInstance() {
        return INSTANCE;
    }

    private Suggestor() {

    }

    public Song suggestBestForArtist(Artist artist) {
        // TODO: Do a real algorithm
        if (artist.albums().hasNext()) {
            ProviderCache cache = ProviderAggregator.getDefault().getCache();
            Album album = cache.getAlbum(artist.albums().next());
            if (album.isLoaded() && album.getSongsCount() > 0) {
                return cache.getSong(album.songs().next());
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

}
