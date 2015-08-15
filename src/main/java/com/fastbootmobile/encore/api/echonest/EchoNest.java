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

package com.fastbootmobile.encore.api.echonest;

import android.os.SystemClock;
import android.util.Log;

import com.echonest.api.v4.Artist;
import com.echonest.api.v4.Biography;
import com.echonest.api.v4.CatalogUpdater;
import com.echonest.api.v4.DynamicPlaylistParams;
import com.echonest.api.v4.DynamicPlaylistSession;
import com.echonest.api.v4.EchoNestAPI;
import com.echonest.api.v4.EchoNestException;
import com.echonest.api.v4.GeneralCatalog;
import com.echonest.api.v4.IdentifySongParams;
import com.echonest.api.v4.Params;
import com.echonest.api.v4.PlaylistParams;
import com.echonest.api.v4.SongCatalogItem;

import com.fastbootmobile.encore.api.common.APIKeys;
import com.fastbootmobile.encore.model.Album;
import com.fastbootmobile.encore.model.Playlist;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.ProviderAggregator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * EchoNest Glue class between jEN and the data we use in OmniMusic
 * TODO: Cache artist info on disk
 */
public class EchoNest {
    private static final String TAG = "EchoNest";
    private static final boolean DEBUG = false;

    private EchoNestAPI mEchoNest;
    private static final Map<String, Artist> sArtistSearchCache = new HashMap<>();
    private static final Map<Artist, Biography> sArtistBiographyCache = new HashMap<>();
    private static final Map<Artist, Map<String, String>> sArtistUrlsCache = new HashMap<>();
    private static final Map<Artist, List<Artist>> sArtistSimilarCache = new HashMap<>();
    private static final Map<Artist, Map<String, String>> sArtistForeignIDs = new HashMap<>();

    /**
     * Initializes an EchoNest API client with the EchoNest API key
     */
    public EchoNest() {
        mEchoNest = new EchoNestAPI(APIKeys.KEY_ECHONEST);
        mEchoNest.setTraceRecvs(DEBUG);
        mEchoNest.setTraceSends(DEBUG);
    }

    /**
     * @return The {@link com.echonest.api.v4.EchoNestAPI} handle
     */
    public EchoNestAPI getApi() {
        return mEchoNest;
    }

    /**
     * Returns whether or not the provided artist name is in cache for searchArtistByName
     * @param name The artist name
     * @return True if the artist is in cache and searchArtistByName won't do any network
     * query, false otherwise
     */
    public boolean hasArtistInCache(String name) {
        synchronized (sArtistSearchCache) {
            return sArtistSearchCache.containsKey(name);
        }
    }

    /**
     * Searches an EchoNest {@link com.echonest.api.v4.Artist} by name
     * @param name The artist name
     * @return An {@link com.echonest.api.v4.Artist} or null if none found
     * @throws EchoNestException In case of error (not found, network error, etc).
     */
    public Artist searchArtistByName(String name) throws EchoNestException {
        // First look in the cache
        Artist result;
        synchronized (sArtistSearchCache) {
            result = sArtistSearchCache.get(name);
        }

        if (result == null) {
            // We don't have this artist cached, so let's look it up on EchoNest
            Params p = new Params();
            p.add("name", name);
            p.add("results", 1);

            List<Artist> results = mEchoNest.searchArtists(p);
            if (results.size() > 0) {
                result = results.get(0);
                synchronized (sArtistSearchCache) {
                    sArtistSearchCache.put(name, result);
                }
            }
        }

        return result;
    }

    /**
     * Returns whether or not the biography for the provided artist exists in the cache (ie.
     * a call to {@link #getArtistBiography(com.echonest.api.v4.Artist)} won't do any network
     * operation.
     * @param artist The artist for which we want the biography
     * @return True if the biography is cached, false otherwise
     */
    public boolean hasArtistBiographyCached(Artist artist) {
        synchronized (sArtistBiographyCache) {
            return sArtistBiographyCache.containsKey(artist);
        }
    }

    /**
     * Fetches and return the artist biography for the provided artist. This method is doing
     * network operations if the biography is not already cached.
     * @param artist The artist for which we want the biography
     * @return A {@link com.echonest.api.v4.Biography}, or null if none available
     * @throws EchoNestException
     */
    public Biography getArtistBiography(Artist artist) throws EchoNestException {
        Biography result;

        // First, look in the cache
        synchronized (sArtistBiographyCache) {
            result = sArtistBiographyCache.get(artist);
        }

        if (result == null) {
            List<Biography> results = artist.getBiographies(0, 10);

            // We prefer wikipedia, and otherwise the longest one
            for (Biography bio : results) {
                if (bio.getSite().equals("wikipedia")) {
                    result = bio;
                    break;
                } else if (result == null || result.getText().length() < bio.getText().length()) {
                    result = bio;
                }
            }

            // Cache it
            synchronized (sArtistBiographyCache) {
                sArtistBiographyCache.put(artist, result);
            }
        }

        return result;
    }

    /**
     * Returns a map of Artist URLs (artists websites, etc). for the provided artists
     * @param artist The artist for which we want URLs
     * @return A map of [Site Name, Site URL]
     * @throws EchoNestException
     */
    public Map<String, String> getArtistUrls(Artist artist) throws EchoNestException {
        Map<String, String> result;
        synchronized (sArtistUrlsCache) {
            result = sArtistUrlsCache.get(artist);
        }

        if (result == null) {
            result = artist.getUrls();
            synchronized (sArtistUrlsCache) {
                sArtistUrlsCache.put(artist, result);
            }
        }

        return result;
    }

    /**
     * Returns whether or not the similar artists for the provided artists are in cache (ie. a call
     * to {@link #getArtistSimilar(com.echonest.api.v4.Artist)} won't do any network operation)
     * @param artist The artist for which get similar results
     * @return True if in cache, false otherwise
     */
    public boolean hasArtistSimilarCached(Artist artist) {
        synchronized (sArtistSimilarCache) {
            return sArtistSimilarCache.containsKey(artist);
        }
    }

    /**
     * Returns a list of similar artists for the provided artist
     * @param artist The artist for which get similar results
     * @return A list of artists similar to the artist provided
     * @throws EchoNestException
     */
    public List<Artist> getArtistSimilar(Artist artist) throws EchoNestException {
        List<Artist> result;
        synchronized (sArtistSimilarCache) {
            result = sArtistSimilarCache.get(artist);
        }

        if (result == null) {
            // Get similar artists
            result = artist.getSimilar(6);
            synchronized (sArtistSimilarCache) {
                sArtistSimilarCache.put(artist, result);
            }

            // Put rosetta stone IDs for each for linking
            List<String> rosettaPrefixes = ProviderAggregator.getDefault().getRosettaStonePrefix();
            for (Artist similar : result) {
                Map<String, String> linksMap;
                synchronized (sArtistForeignIDs) {
                    linksMap = sArtistForeignIDs.get(similar);
                    if (linksMap == null) {
                        linksMap = new HashMap<>();
                        sArtistForeignIDs.put(similar, linksMap);
                    }
                }

                for (String prefix : rosettaPrefixes) {
                    try {
                        String rosettaLink = similar.getForeignID(prefix);
                        synchronized (sArtistForeignIDs) {
                            linksMap.put(prefix, rosettaLink);
                        }
                    } catch (Exception ignore) { }
                }
            }
        }

        return result;
    }

    /**
     * Returns the Rosetta ID for the provided artist and the provided rosetta prefix. This only
     * works for artists retrieved in getArtistSimilar.
     *
     * @param artist The artist for which get the foreign ID
     * @param prefix The rosetta prefix (e.g. "spotify")
     * @return The rosetta ID of the artist
     */
    public String getArtistForeignID(Artist artist, String prefix) {
        synchronized (sArtistForeignIDs) {
            Map<String, String> rosettaMap = sArtistForeignIDs.get(artist);
            if (rosettaMap != null) {
                return rosettaMap.get(prefix);
            }
        }
        return null;
    }


    /**
     * Creates a dynamic playlist based on user preferences. All parameters may be used, however
     * depending on the type parameter, at least one parameter has to be set:
     *  - If type is set to "artist-description", either mood, style or both must be set. Catalog
     *    might be used to further personalize the generated bucket.
     *  - If type is set to "catalog-radio", seedCatalog must be set. Mood and style might be
     *    used to further personalize the generated bucket.
     *
     * @param type One of: artist-description, catalog-radio
     * @param seedCatalog The ID of the catalog (taste profile) to use. May be null if type is
     *                    set to artist-description
     * @param mood An array of moods. May be null if type is set to catalog-radio
     * @param style An array of styles. May be null if type is set to catalog-radio
     * @return The created playlist session
     * @throws EchoNestException
     */
    public DynamicPlaylistSession createDynamicPlaylist(String type, String seedCatalog,
                                                        String[] mood, String[] style)
            throws EchoNestException {
        // Some checks
        if (!"artist-description".equals(type) && !"catalog-radio".equals(type)) {
            throw new EchoNestException(-1, "Only 'artist-description' and 'catalog-radio' type " +
                    "are supported");
        }

        if ((seedCatalog == null && mood == null && style == null)
                || ("artist-description".equals(type) && (mood == null || mood.length <= 0) && (style == null || style.length <= 0))
                || ("catalog-radio".equals(type) && (seedCatalog == null || seedCatalog.isEmpty()))) {
            throw new EchoNestException(-1, "seedCatalog, mood or style must be filled depending " +
                    "on the type");
        }

        // Everything looks plausible, let's craft the query
        DynamicPlaylistParams p = new DynamicPlaylistParams();
        p.add("type", type);
        if (seedCatalog != null) {
            p.addSeedCatalog(seedCatalog);
        }
        if (mood != null) {
            for (String m : mood) {
                p.addMood(m);
            }
        }
        if (style != null) {
            for (String s : style) {
                p.addStyle(s);
            }
        }

        String prefix = ProviderAggregator.getDefault().getPreferredRosettaStonePrefix();
        if (prefix != null) {
            p.addIDSpace(prefix);
            p.includeTracks();
        }

        // Send the query and get the session ID
        DynamicPlaylistSession session = mEchoNest.createDynamicPlaylist(p);
        Log.e(TAG, "Session ID: " + session.getSessionID());

        return session;
    }

    public List<String> createStaticPlaylist(String type, String seedCatalog, String[] style,
                                             String[] mood, float adventurous,
                                             String[] songTypes, float speechiness, float energy,
                                             float familiar)
            throws EchoNestException {
        // Some checks
        if (!"artist-description".equals(type) && !"catalog-radio".equals(type)) {
            throw new EchoNestException(-1, "Only 'artist-description' and 'catalog-radio' type " +
                    "are supported");
        }

        if ((seedCatalog == null && mood == null && style == null)
                || ("artist-description".equals(type) && (mood == null || mood.length <= 0) && (style == null || style.length <= 0))
                || ("catalog-radio".equals(type) && (seedCatalog == null || seedCatalog.isEmpty()))) {
            throw new EchoNestException(-1, "seedCatalog, mood or style must be filled depending " +
                    "on the type");
        }

        // Everything looks plausible, let's craft the query
        PlaylistParams p = new PlaylistParams();
        p.setResults(100);
        p.add("type", type);
        if (seedCatalog != null) {
            p.addSeedCatalog(seedCatalog);
        }
        if (mood != null) {
            for (String m : mood) {
                p.addMood(m);
            }
        }
        if (style != null) {
            for (String s : style) {
                p.addStyle(s);
            }
        }
        if (songTypes != null && songTypes.length > 0) {
            for (String songType : songTypes) {
                switch (songType) {
                    case "christmas":
                        p.addSongType(com.echonest.api.v4.Song.SongType.christmas,
                                com.echonest.api.v4.Song.SongTypeFlag.True);
                        break;
                    case "live":
                        p.addSongType(com.echonest.api.v4.Song.SongType.live,
                                com.echonest.api.v4.Song.SongTypeFlag.True);
                        break;
                    case "studio":
                        p.addSongType(com.echonest.api.v4.Song.SongType.studio,
                                com.echonest.api.v4.Song.SongTypeFlag.True);
                        break;
                    case "acoustic":
                        p.add("song_type", songType + ":true");
                        break;
                    case "electric":
                        p.add("song_type", songType + ":true");
                        break;
                    default:
                        Log.e(TAG, "Unrecognized song type: " + songType);
                        break;
                }
            }
        }

        if (adventurous >= 0) {
            p.setAdventurousness(adventurous);
        }
        if (speechiness >= 0) {
            p.add("target_speechiness", speechiness);
        }
        if (energy >= 0) {
            p.add("target_energy", energy);
        }
        if (familiar >= 0) {
            p.add("target_artist_familiarity", familiar);
        }

        String prefix = ProviderAggregator.getDefault().getPreferredRosettaStonePrefix();
        if (prefix != null) {
            Log.d(TAG, "Using rosetta prefix " + prefix);
            p.addIDSpace(prefix);
            p.includeTracks();
        }
        p.setLimit(true);

        // Send the query and get the playlist
        com.echonest.api.v4.Playlist playlist = mEchoNest.createStaticPlaylist(p);
        List<String> songs = new ArrayList<>();

        List<com.echonest.api.v4.Song> enSongs = playlist.getSongs();
        for (com.echonest.api.v4.Song song : enSongs) {
            songs.add(song.getTrack(prefix).getForeignID());
        }

        return songs;
    }

    /**
     * Creates a temporary taste profile.
     *
     * The EchoNest API is limited to 1,000 profiles for a non-commercial API key, so we create a
     * temporary profile, upload locally tracked data, do what we want with it, then remove it.
     * Our limitation then is that less than 1,000 people are creating a temporary bucket.
     *
     * Note that this method isn't limited to rosetta-enabled providers, as EchoNest supports
     * adding tracks from name instead of rosetta IDs or ENID. Tracks information will be added
     * by name and exclusive match against the local library will be done if no cloud provider is
     * available for playback.
     *
     * @return The temporary profile name
     */
    public GeneralCatalog createTemporaryTasteProfile() throws EchoNestException {
        // Generate some random name for the profile
        String profileName = Long.toString(SystemClock.uptimeMillis()) + new Random().nextLong()
                + System.currentTimeMillis();
        GeneralCatalog profile = mEchoNest.createGeneralCatalog(profileName);

        // Upload all playlists tracks
        CatalogUpdater updater = new CatalogUpdater();

        final ProviderAggregator aggregator = ProviderAggregator.getDefault();
        final List<Playlist> playlists = aggregator.getAllPlaylists();

        // For each playlist
        int tracksCount = 0;
        List<String> knownTracks = new ArrayList<>();
        for (Playlist p : playlists) {
            // For each song of each playlist
            Iterator<String> songIt = p.songs();
            while (songIt.hasNext()) {
                String songRef = songIt.next();

                if (knownTracks.contains(songRef)) {
                    continue;
                } else {
                    knownTracks.add(songRef);
                }

                // TODO: Add a way to know if references are rosetta IDs to avoid lookups from EN
                Song song = aggregator.retrieveSong(songRef, p.getProvider());
                if (song != null) {
                    // We have the song info, add it to our profile
                    SongCatalogItem item = new SongCatalogItem(songRef);

                    // If we have artist info, add it
                    com.fastbootmobile.encore.model.Artist artist = aggregator.retrieveArtist(song.getArtist(), p.getProvider());
                    if (artist != null && artist.isLoaded() && artist.getName() != null
                            && !artist.getName().trim().isEmpty()) {
                        item.setArtistName(artist.getName());
                    }

                    // If we have album info, add it
                    Album album = aggregator.retrieveAlbum(song.getAlbum(), p.getProvider());
                    if (album != null && album.isLoaded() && album.getName() != null) {
                        item.setRelease(album.getName());
                    }

                    // Add other regular info
                    item.setSongName(song.getTitle());
                    item.setUrl(songRef);

                    updater.update(item);
                    // TODO: play count, favorite, rating
                    tracksCount++;
                }
            }
        }

        // Push the data to EchoNest
        if (DEBUG) {
            Log.d(TAG, "Pushing " + tracksCount + " tracks data to EchoNest Profile");
        }

        long startTime = SystemClock.uptimeMillis();
        String ticket = profile.update(updater);
        if (profile.waitForUpdates(ticket, 10000)) {
            Log.i(TAG, "Profile update completed in " + (SystemClock.uptimeMillis() - startTime)
                    + " ms");
        } else {
            Log.w(TAG, "Profile update not done after 10 seconds! Bucket data might be wrong " +
                    "until it fully completes!");
        }

        return profile;
    }

    /**
     * Identifies a song via the EchoNest/EchoPrint API. You must generate an EchoPrint code first
     * from the audio input using {@link com.fastbootmobile.encore.framework.EchoPrint} class.
     * @param codePrint The output EchoPrint generated code
     * @return A list of matches
     * @throws EchoNestException
     */
    public List<com.echonest.api.v4.Song> identifySong(String codePrint) throws EchoNestException {
        IdentifySongParams params = new IdentifySongParams();
        params.setCode(codePrint);
        params.includeAudioSummary();

        return mEchoNest.identifySongs(params);
    }

}
