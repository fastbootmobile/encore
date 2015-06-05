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

import android.content.Context;

import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.model.Playlist;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.ProviderAggregator;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * This class generates automatic playlists from the listen logger
 */
public class AutoPlaylistHelper {
    public static final String REF_SPECIAL_FAVORITES = "__omni:playlist:special:favorites";
    public static final String REF_SPECIAL_MOST_PLAYED = "__omni:playlist:special:mostplayed";

    /**
     * Generates and returns a playlist containing all the liked entries
     * @param ctx The context
     * @return A playlist containing the songs that have been favorited/liked
     */
    public static Playlist getFavoritesPlaylist(Context ctx) {
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();
        final ListenLogger logger = new ListenLogger(ctx);

        List<ListenLogger.LogEntry> likes = logger.getLikedEntries();

        Playlist playlist = new Playlist(REF_SPECIAL_FAVORITES);
        playlist.setName(ctx.getString(R.string.favorite_songs));
        playlist.setOfflineCapable(false);
        playlist.setOfflineStatus(Playlist.OFFLINE_STATUS_NO);
        playlist.setIsLoaded(true);

        for (ListenLogger.LogEntry like : likes) {
            // Ensure the songs are cached as they're coming from multiple providers
            if (aggregator.retrieveSong(like.getReference(), like.getIdentifier()) != null) {
                playlist.addSong(like.getReference());
            }
        }

        return playlist;
    }

    /**
     * Generates and returns a playlist containing the 100 most played songs
     * @param ctx The context
     * @return A playlist containing the most 100 played songs
     */
    public static Playlist getMostPlayedPlaylist(Context ctx) {
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();
        final ListenLogger logger = new ListenLogger(ctx);

        List<ListenLogger.LogEntry> likes = logger.getEntries(0);

        Playlist playlist = new Playlist(REF_SPECIAL_MOST_PLAYED);
        playlist.setName(ctx.getString(R.string.most_played));
        playlist.setOfflineCapable(false);
        playlist.setOfflineStatus(Playlist.OFFLINE_STATUS_NO);
        playlist.setIsLoaded(true);

        HashMap<String, Integer> occurrences = new HashMap<>();
        for (ListenLogger.LogEntry like : likes) {
            final String ref = like.getReference();

            // Ensure the songs are cached as they're coming from multiple providers
            Song song = aggregator.retrieveSong(ref, like.getIdentifier());

            // A null song indicates either the song has gone unavailable or the provider has been
            // removed. We should not add it.
            if (song != null) {
                if (occurrences.containsKey(like.getReference())) {
                    occurrences.put(ref, occurrences.get(ref) + 1);
                } else {
                    occurrences.put(ref, 1);
                }
            }
        }

        int number = 0;
        TreeMap<String, Integer> sortedSongs = new TreeMap<>(new ValueComparator(occurrences));
        sortedSongs.putAll(occurrences);

        Set<String> references = sortedSongs.keySet();
        for (String ref : references) {
            if (ref != null) {
                playlist.addSong(ref);
                ++number;

                if (number == 100) {
                    break;
                }
            }
        }

        return playlist;
    }

    private static class ValueComparator implements Comparator<String> {
        private Map<String, Integer> mBase;

        public ValueComparator(Map<String, Integer> base) {
            mBase = base;
        }

        // Note: this comparator imposes orderings that are inconsistent with equals.
        public int compare(String a, String b) {
            if (mBase.get(a) >= mBase.get(b)) {
                return -1;
            } else {
                return 1;
            } // returning 0 would merge keys
        }
    }
}
