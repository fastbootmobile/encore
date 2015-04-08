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

import android.content.Context;

import org.omnirom.music.app.R;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.providers.ProviderAggregator;

import java.util.List;

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
        playlist.setName(ctx.getString(R.string.favorites));
        playlist.setOfflineCapable(false);
        playlist.setOfflineStatus(Playlist.OFFLINE_STATUS_NO);
        playlist.setIsLoaded(true);

        for (ListenLogger.LogEntry like : likes) {
            // Ensure the songs are cached as they're coming from multiple providers
            aggregator.retrieveSong(like.getReference(), like.getIdentifier());
            playlist.addSong(like.getReference());
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

        List<ListenLogger.LogEntry> likes = logger.getEntries();

        Playlist playlist = new Playlist(REF_SPECIAL_FAVORITES);
        playlist.setName(ctx.getString(R.string.favorites));
        playlist.setOfflineCapable(false);
        playlist.setOfflineStatus(Playlist.OFFLINE_STATUS_NO);
        playlist.setIsLoaded(true);

        for (ListenLogger.LogEntry like : likes) {
            // Ensure the songs are cached as they're coming from multiple providers
            aggregator.retrieveSong(like.getReference(), like.getIdentifier());
            playlist.addSong(like.getReference());
        }

        return playlist;
    }
}
