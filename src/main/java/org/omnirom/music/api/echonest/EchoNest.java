package org.omnirom.music.api.echonest;

import android.content.SharedPreferences;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;

import com.echonest.api.v4.Artist;
import com.echonest.api.v4.Biography;
import com.echonest.api.v4.Blog;
import com.echonest.api.v4.CatalogUpdater;
import com.echonest.api.v4.DynamicPlaylistParams;
import com.echonest.api.v4.DynamicPlaylistSession;
import com.echonest.api.v4.EchoNestAPI;
import com.echonest.api.v4.EchoNestException;
import com.echonest.api.v4.GeneralCatalog;
import com.echonest.api.v4.Image;
import com.echonest.api.v4.News;
import com.echonest.api.v4.Params;
import com.echonest.api.v4.Review;
import com.echonest.api.v4.SongCatalogItem;
import com.echonest.api.v4.Video;

import org.omnirom.music.model.Album;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderCache;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
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
    private static final boolean DEBUG = true;

    private EchoNestAPI mEchoNest;
    private static final Map<String, Artist> sArtistSearchCache = new HashMap<String, Artist>();
    private static final Map<Artist, Biography> sArtistBiographyCache = new HashMap<Artist, Biography>();
    private static final Map<Artist, Map<String, String>> sArtistUrlsCache = new HashMap<Artist, Map<String, String>>();
    private static final Map<Artist, List<Artist>> sArtistSimilarCache = new HashMap<Artist, List<Artist>>();
    private static final Map<Artist, Map<String, String>> sArtistForeignIDs = new HashMap<Artist, Map<String, String>>();

    /**
     * Initializes an EchoNest API client with the EchoNest API key
     */
    public EchoNest() {
        // Read API Key from file (for now), or fallback to the public docs API key
        try {
            String apiKey;
            File keyFile = new File(Environment.getExternalStorageDirectory().getPath(),
                    "echonest.txt");
            if (keyFile.exists()) {
                FileInputStream fileInputStream = new FileInputStream(keyFile);
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fileInputStream));
                apiKey = bufferedReader.readLine();
            } else {
                // Fallback to docs api key
                apiKey = "FILDTEOIK2HBORODV";
            }

            mEchoNest = new EchoNestAPI(apiKey);
            mEchoNest.setTraceRecvs(DEBUG);
            mEchoNest.setTraceSends(DEBUG);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Cannot open API Key file", e);
        } catch (IOException e) {
            Log.e(TAG, "Error reading API key file", e);
        }
    }

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

    public Artist searchArtistByName(String name) throws EchoNestException {
        Params p = new Params();
        p.add("name", name);
        p.add("results", 1);

        Artist result;
        synchronized (sArtistSearchCache) {
            result = sArtistSearchCache.get(name);
        }

        if (result == null) {
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

    public boolean hasArtistBiographyCached(Artist artist) {
        synchronized (sArtistBiographyCache) {
            return sArtistBiographyCache.containsKey(artist);
        }
    }

    public Biography getArtistBiography(Artist artist) throws EchoNestException {
        Biography result;
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

            synchronized (sArtistBiographyCache) {
                sArtistBiographyCache.put(artist, result);
            }
        }

        return result;
    }

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

    public boolean hasArtistSimilarCached(Artist artist) {
        synchronized (sArtistSimilarCache) {
            return sArtistSimilarCache.containsKey(artist);
        }
    }

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
                        linksMap = new HashMap<String, String>();
                        sArtistForeignIDs.put(similar, linksMap);
                    }
                }

                for (String prefix : rosettaPrefixes) {
                    String rosettaLink = similar.getForeignID(prefix);
                    synchronized (sArtistForeignIDs) {
                        linksMap.put(prefix, rosettaLink);
                    }
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
                || ("artist-description".equals(type) && mood == null && style == null)
                || ("catalog-radio".equals(type) && seedCatalog == null)) {
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

        List<String> prefixes = ProviderAggregator.getDefault().getRosettaStonePrefix();
        if (prefixes != null && prefixes.size() > 0) {
            p.addIDSpace(prefixes.get(0));
            p.includeTracks();
        }

        // Send the query and get the session ID
        DynamicPlaylistSession session = mEchoNest.createDynamicPlaylist(p);
        Log.e(TAG, "Session ID: " + session.getSessionID());

        return session;
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

        List<Playlist> playlists = ProviderAggregator.getDefault().getAllPlaylists();
        ProviderCache cache = ProviderAggregator.getDefault().getCache();

        // For each playlist
        int tracksCount = 0;
        List<String> knownTracks = new ArrayList<String>();
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
                Song song = cache.getSong(songRef);
                if (song != null) {
                    // We have the song info, add it to our profile
                    SongCatalogItem item = new SongCatalogItem(songRef);

                    // If we have artist info, add it
                    org.omnirom.music.model.Artist artist = cache.getArtist(song.getArtist());
                    if (artist != null) {
                        item.setArtistName(artist.getName());
                    }

                    // If we have album info, add it
                    Album album = cache.getAlbum(song.getAlbum());
                    if (album != null) {
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

}
