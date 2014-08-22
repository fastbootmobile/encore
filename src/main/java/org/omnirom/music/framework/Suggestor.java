package org.omnirom.music.framework;

import android.os.RemoteException;
import android.util.Log;

import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderCache;
import org.omnirom.music.providers.ProviderConnection;

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
            ProviderCache cache = ProviderAggregator.getDefault().getCache();
            Album album = cache.getAlbum(albums.next());

            if (album.isLoaded() && album.getSongsCount() > 0) {
                Iterator<String> songs = album.songs();
                while (songs.hasNext()) {
                    String songRef = songs.next();
                    Song song = cache.getSong(songRef);

                    if (song == null) {
                        ProviderConnection pc = PluginsLookup.getDefault().getProvider(artist.getProvider());
                        try {
                            song = pc.getBinder().getSong(songRef);
                        } catch (RemoteException e) {
                            Log.e(TAG, "Error while getting album track!!", e);
                        }
                    }

                    if (song != null) {
                        if (song.getArtist().equals(artist.getRef())) {
                            return song;
                        }
                    }
                }
            }
        }

        return null;
    }

}
