package org.omnirom.music.api.echonest;

import android.os.Environment;
import android.util.Log;

import com.echonest.api.v4.Artist;
import com.echonest.api.v4.Biography;
import com.echonest.api.v4.Blog;
import com.echonest.api.v4.EchoNestAPI;
import com.echonest.api.v4.EchoNestException;
import com.echonest.api.v4.Image;
import com.echonest.api.v4.News;
import com.echonest.api.v4.Params;
import com.echonest.api.v4.Review;
import com.echonest.api.v4.Video;
import com.echonest.api.v4.examples.ArtistExamples;

import org.omnirom.music.providers.ProviderAggregator;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public EchoNestAPI getApi() {
        return mEchoNest;
    }

    public void dumpArtist(Artist artist) throws EchoNestException {
        System.out.printf("%s\n", artist.getName());
        System.out.printf("   hottt %.3f\n", artist.getHotttnesss());
        System.out.printf("   fam   %.3f\n", artist.getFamiliarity());

        System.out.println(" =========  urls ======== ");
        for (String key : artist.getUrls().keySet()) {
            System.out.printf("   %10s %s\n", key, artist.getUrls().get(key));
        }


        System.out.println(" =========  bios ======== ");
        List<Biography> bios = artist.getBiographies();
        for (Biography bio : bios) {
            bio.dump();
        }

        System.out.println(" =========  blogs ======== ");
        List<Blog> blogs = artist.getBlogs();
        for (Blog blog : blogs) {
            blog.dump();
        }

        System.out.println(" =========  images ======== ");
        List<Image> images = artist.getImages();
        for (Image image : images) {
            image.dump();
        }

        System.out.println(" =========  news ======== ");
        List<News> newsList = artist.getNews();
        for (News news : newsList) {
            news.dump();
        }

        System.out.println(" =========  reviews ======== ");
        List<Review> reviews = artist.getReviews();
        for (Review review : reviews) {
            review.dump();
        }

        System.out.println(" =========  videos ======== ");
        List<Video> videos = artist.getVideos();
        for (Video video : videos) {
            video.dump();
        }
    }

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
}
