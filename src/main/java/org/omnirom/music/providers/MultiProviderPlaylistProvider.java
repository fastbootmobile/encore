package org.omnirom.music.providers;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaFormat;
import android.os.DeadObjectException;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Genre;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.SearchResult;
import org.omnirom.music.model.Song;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Created by h4o on 17/06/2014.
 */
public class MultiProviderPlaylistProvider extends IMusicProvider.Stub {


    Handler mHandler = new Handler();
    private HashMap<String,Playlist> mPlaylists;
    private HashMap<String,ProviderIdentifier> mSongsProviders;
    private ProviderIdentifier mProviderIdentifier;
    private Context mContext;
    private String TAG = "MultiProviderPlaylistProvider";
    private final List<IProviderCallback> mCallbacks;
    private MultiProviderDatabaseHelper mMultiProviderDatabaseHelper;
    public MultiProviderPlaylistProvider(Context context){
        mContext = context;
        mPlaylists = new HashMap<String, Playlist>();
        mMultiProviderDatabaseHelper = new MultiProviderDatabaseHelper(mContext,mLocalCallback);
        mCallbacks = new ArrayList<IProviderCallback>();
    }

    private IMusicProvider getBinder(ProviderIdentifier id) {
        return PluginsLookup.getDefault().getProvider(id).getBinder();
    }
    @Override
    public int getVersion() throws RemoteException {
        return 0;
    }

    @Override
    public void setIdentifier(ProviderIdentifier identifier) throws RemoteException {
        mProviderIdentifier = identifier;
        mMultiProviderDatabaseHelper.setIdentifier(identifier);
    }

    @Override
    public void registerCallback(IProviderCallback cb) throws RemoteException {
        synchronized (mCallbacks) {
            mCallbacks.add(cb);
        }
    }

    @Override
    public void unregisterCallback(IProviderCallback cb) throws RemoteException {
        synchronized (mCallbacks) {
            mCallbacks.remove(cb);
        }
    }

    @Override
    public boolean isSetup() throws RemoteException {
        return mMultiProviderDatabaseHelper.isSetup();
    }

    @Override
    public boolean login() throws RemoteException {
        return true;
    }

    @Override
    public boolean isAuthenticated() throws RemoteException {
        return true;
    }

    @Override
    public boolean isInfinite() throws RemoteException {
        return false;
    }

    @Override
    public List<Album> getAlbums() throws RemoteException {
        return null;
    }

    @Override
    public List<Artist> getArtists() throws RemoteException {
        return null;
    }

    @Override
    public List<Song> getSongs() throws RemoteException {
        return null;
    }

    @Override
    public List<Playlist> getPlaylists() throws RemoteException {
        return mMultiProviderDatabaseHelper.getPlaylists();
    }

    @Override
    public Song getSong(String ref) throws RemoteException {
        return mMultiProviderDatabaseHelper.getSong(ref);
    }

    @Override
    public Artist getArtist(String ref) throws RemoteException {
        return null;
    }

    @Override
    public Album getAlbum(String ref) throws RemoteException {
        return null;
    }

    @Override
    public Playlist getPlaylist(String ref) throws RemoteException {
        return null;
    }

    @Override
    public boolean fetchArtistAlbums(String artistRef) {
        return false;
    }

    @Override
    public boolean fetchAlbumTracks(String albumRef) throws RemoteException {
        return false;
    }

    @Override
    public void setAudioSocketName(String socketName) throws RemoteException {

    }

    @Override
    public long getPrefetchDelay() throws RemoteException {
        return 0;
    }

    @Override
    public void prefetchSong(String ref) throws RemoteException {

    }

    @Override
    public boolean playSong(String ref) throws RemoteException {
        ProviderIdentifier providerId = mSongsProviders.get(ref);
        return getBinder(providerId).playSong(ref);
    }

    @Override
    public void pause() throws RemoteException {
        Log.e(TAG, "NOT IMPLEMENTED");
    }

    @Override
    public void resume() throws RemoteException {
        Log.e(TAG, "NOT IMPLEMENTED");
    }

    @Override
    public void seek(long timeMs) { Log.e(TAG, "NOT IMPLEMENTED"); }

    @Override
    public boolean onUserSwapPlaylistItem(int oldPosition, int newPosition, String playlistRef) throws RemoteException {
           return mMultiProviderDatabaseHelper.swapPlaylistItem(oldPosition,newPosition,playlistRef);
    }

    @Override
    public boolean deletePlaylist(String playlistRef) throws RemoteException {
        return mMultiProviderDatabaseHelper.deletePlaylist(playlistRef);
    }

    @Override
    public boolean deleteSongFromPlaylist(int songPosition, String playlistRef) throws RemoteException {
        return mMultiProviderDatabaseHelper.deleteSongFromPlaylist(songPosition, playlistRef);
    }

    @Override
    public boolean addSongToPlaylist(String songRef, String playlistRef,ProviderIdentifier providerIdentifier) throws RemoteException {
        return mMultiProviderDatabaseHelper.addSongToPlaylist(songRef,playlistRef,providerIdentifier);
    }

    @Override
    public String addPlaylist(String playlistName) throws RemoteException {
         return mMultiProviderDatabaseHelper.addPlaylist(playlistName);
    }

    @Override
    public Bitmap getSongArt(Song song) throws RemoteException {
        return PluginsLookup.getDefault().getProvider(song.getProvider()).getBinder().getSongArt(song);
    }

    @Override
    public List<Genre> getGenres(){
        return null;
    }

    private void removeCallback(final IProviderCallback cb) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {

            }
        });
    }

    MultiProviderDatabaseHelper.LocalCallback mLocalCallback = new MultiProviderDatabaseHelper.LocalCallback() {
        @Override
        public void playlistUpdated(final Playlist playlist) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    synchronized (mCallbacks) {
                        for (IProviderCallback cb : mCallbacks) {
                            try {
                                cb.onPlaylistAddedOrUpdated(mProviderIdentifier,playlist);
                            } catch (DeadObjectException e) {
                                removeCallback(cb);
                            } catch (RemoteException e) {
                                Log.e(TAG, "RemoteException when notifying a callback", e);
                            }
                        }
                    }
                }
            });
        }

        @Override
        public void searchFinished(final SearchResult searchResult){
            if(searchResult != null) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (mCallbacks) {
                            for (IProviderCallback cb : mCallbacks) {
                                try {
                                    cb.onSearchResult(searchResult);
                                } catch (DeadObjectException e) {
                                    removeCallback(cb);
                                } catch (RemoteException e) {
                                    Log.e(TAG, "RemoteException when notifying a callback", e);
                                }
                            }
                        }
                    }
                });
            }
        }
    };

    public void startSearch(String query){
        mMultiProviderDatabaseHelper.startSearch(query);
    }

    @Override
    public Bitmap getLogo(String ref) throws RemoteException {
        return null;
    }

    @Override
    public List<String> getSupportedRosettaPrefix() throws RemoteException {
        return null;
    }

}
