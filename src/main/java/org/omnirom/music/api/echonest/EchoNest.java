package org.omnirom.music.api.echonest;

import android.content.SharedPreferences;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;

import com.echonest.api.v4.Artist;
import com.echonest.api.v4.Biography;
import com.echonest.api.v4.Blog;
import com.echonest.api.v4.CatalogUpdater;
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
        // Read API Key from file for now... ;)
        try {
            FileInputStream fileInputStream = new FileInputStream(Environment.getExternalStorageDirectory().getPath() + "/echonest.txt");
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fileInputStream));
            String apiKey = bufferedReader.readLine();
            mEchoNest = new EchoNestAPI(apiKey);
            mEchoNest.setTraceRecvs(true);
            mEchoNest.setTraceSends(true);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Cannot open API Key file", e);
        } catch (IOException e) {
            Log.e(TAG, "Error reading API key file", e);
        }
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

    public String getArtistForeignID(Artist artist, String prefix) {
        synchronized (sArtistForeignIDs) {
            Map<String, String> rosettaMap = sArtistForeignIDs.get(artist);
            if (rosettaMap != null) {
                return rosettaMap.get(prefix);
            }
        }
        return null;
    }


    public DynamicPlaylistSession createDynamicPlaylist(SharedPreferences sp) throws EchoNestException {
        Params p = new Params();
        DynamicPlaylistSession session = mEchoNest.createDynamicPlaylist(p);
        try {
            Field sessionIdField = DynamicPlaylistSession.class.getField("sessionID");
            String sessionId = (String) sessionIdField.get(session);
            Log.e(TAG, "Session ID: " + sessionId);
        } catch (NoSuchFieldException e) {
            Log.e(TAG, "Unable to find sessionID field!", e);
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Illegal Access", e);
        }
        return session;
    }

    public List<DynamicPlaylistSession> getDynamicPlaylists() {
        return null;
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

                    //item.setSongID(songRef);

                    // Add other regular info
                    item.setSongName(song.getTitle());
                    // item.setUrl(songRef);

                    updater.update(item);
                    // TODO: play count, favorite, rating
                    tracksCount++;
                }
            }
        }

        // Push the data to EchoNest
        Log.e(TAG, "Pushing " + tracksCount + " tracks data to EchoNest Profile");
        long startTime = SystemClock.uptimeMillis();
        String ticket = profile.update(updater);
        if (profile.waitForUpdates(ticket, 180000)) {
            Log.e(TAG, "Profile update completed in " + (SystemClock.uptimeMillis() - startTime) + " ms");
        } else {
            Log.e(TAG, "Profile update not done after 30 seconds!");
        }

        return profile;
    }

}
