package org.omnirom.music.framework;

import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;

import java.util.Iterator;

/**
 * Created by xplodwild on 7/8/14.
 */
public class Suggestor {

    private static final String TAG = "Suggestor";

    private static final Suggestor INSTANCE = new Suggestor();

    public static Suggestor getInstance() {
        return INSTANCE;
    }

    private Suggestor() {

    }

    public Song suggestBestForArtist(Artist artist) {
        // TODO: Do a real algorithm
        Iterator<String> albums = artist.albums();
        while (albums.hasNext()) {
            final ProviderAggregator aggregator = ProviderAggregator.getDefault();
            String albumRef = albums.next();
            Album album = aggregator.retrieveAlbum(albumRef, artist.getProvider());

            if (album != null && album.isLoaded() && album.getSongsCount() > 0) {
                Iterator<String> songs = album.songs();
                while (songs.hasNext()) {
                    String songRef = songs.next();
                    Song song = aggregator.retrieveSong(songRef, artist.getProvider());

                    if (song != null) {
                        if (artist.getRef().equals(song.getArtist())) {
                            return song;
                        }
                    }
                }
            }
        }

        return null;
    }

}
