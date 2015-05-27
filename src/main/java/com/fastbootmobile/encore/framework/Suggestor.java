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

package com.fastbootmobile.encore.framework;

import android.util.Log;

import com.fastbootmobile.encore.model.Album;
import com.fastbootmobile.encore.model.Artist;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.ProviderAggregator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

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

    /**
     * Builds an artist radio, which means a list of 100 songs (or less if less available) from the
     * specified artist
     * @param artist The artist from which we want to get a radio
     * @return A list of songs
     */
    public List<Song> buildArtistRadio(Artist artist) {
        final List<Song> output = new ArrayList<>();
        final List<Song> allSongs = new ArrayList<>();
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();



        // List all tracks from all albums, get 100 random
        final List<String> albumReferences = artist.getAlbums();
        for (String albumRef : albumReferences) {
            Album album = aggregator.retrieveAlbum(albumRef, artist.getProvider());
            if (album != null && album.isLoaded()) {
                Iterator<String> songsIt = album.songs();
                while (songsIt.hasNext()) {
                    String songRef = songsIt.next();
                    Song song = aggregator.retrieveSong(songRef, artist.getProvider());
                    allSongs.add(song);
                }
            }
        }

        long seed = System.nanoTime();
        Collections.shuffle(allSongs, new Random(seed));

        final int numTracks = Math.min(100, allSongs.size());
        for (int i = 0; i < numTracks; ++i) {
            output.add(allSongs.get(i));
        }

        Log.d(TAG, "Building artist radio for " + artist.getName() + ": "
                + artist.getAlbums().size() + " albums, " + output.size() + " songs chosen");

        return output;
    }

    /**
     * Suggests the best song from an artist
     * @param artist The artist from which we want to suggest a song
     * @return A song, or null if the artist has no songs
     */
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
