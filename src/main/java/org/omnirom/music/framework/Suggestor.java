/*
 * Copyright (C) 2014 Fastboot Mobile, LLC.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program;
 * if not, see <http://www.gnu.org/licenses>.
 */

package org.omnirom.music.framework;

import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;

import java.util.Iterator;

/**
 * Class generating listening suggestions
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
        final Iterator<String> albums = artist.albums();
        while (albums.hasNext()) {
            final ProviderAggregator aggregator = ProviderAggregator.getDefault();
            final String albumRef = albums.next();
            Album album = aggregator.retrieveAlbum(albumRef, artist.getProvider());

            if (album != null && album.isLoaded() && album.getSongsCount() > 0) {
                final Iterator<String> songs = album.songs();
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
