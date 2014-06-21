package org.omnirom.music.providers;

import android.content.Context;
import android.media.MediaFormat;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Playlist;
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
public class MultiProviderPlaylistProvider implements IMusicProvider {
    private HashMap<String,Playlist> mPlaylists;
    private HashMap<String,ProviderIdentifier> mSongsProviders;
    private ProviderIdentifier mProviderIdentifier;
    private Context mContext;
    private String TAG = "MultiProviderPlaylistProvider";

    MultiProviderPlaylistProvider(Context context){
        mContext = context;
        File[] fileList = mContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC).listFiles(filenameFilter);
        mPlaylists = new HashMap<String, Playlist>();
        XMLReader xmlReader;
        try {
           xmlReader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
        } catch (Exception e){
            Log.d(TAG,"Error initialising xmlReader");
            return;
        }

        for(int i = 0; i< fileList.length;i++) {
            try {
                SAXXMLPlaylistHandler saxxmlPlaylistHandler = new SAXXMLPlaylistHandler();
                xmlReader.setContentHandler(saxxmlPlaylistHandler);
                mSongsProviders = new HashMap<String, ProviderIdentifier>();
                saxxmlPlaylistHandler.setHashMap(mSongsProviders);
                xmlReader.parse(new InputSource(new FileInputStream(fileList[i])));
                mSongsProviders = saxxmlPlaylistHandler.getSongProviders();
                Playlist pl = saxxmlPlaylistHandler.getPlaylist();
                mPlaylists.put(pl.getRef(), pl);
            } catch (Exception ex) {
                Log.d(TAG, ex.getMessage());
            }
        }


    }
    FilenameFilter filenameFilter = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String filename) {
            if(filename.endsWith(".playlist.xml"))
                return true;
            return false;
        }
    };
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
    }

    @Override
    public void registerCallback(IProviderCallback cb) throws RemoteException {

    }

    @Override
    public void unregisterCallback(IProviderCallback cb) throws RemoteException {

    }

    @Override
    public boolean isSetup() throws RemoteException {
        return true;
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
        List<Playlist> playlists = new ArrayList<Playlist>();
        for(Playlist pl : mPlaylists.values()){
            playlists.add(pl);
        }
        return playlists;
    }

    @Override
    public Song getSong(String ref) throws RemoteException {
        ProviderIdentifier providerId = mSongsProviders.get(ref);
        return getBinder(providerId).getSong(ref);
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
    public boolean onUserSwapPlaylistItem(int oldPosition, int newPosition, String playlistRef) throws RemoteException {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Playlist pl = mPlaylists.get(playlistRef);
            File playlistFile = new File(Environment.DIRECTORY_MUSIC, MD5(pl.getName()) + ".playlist.xml");
            if (playlistFile.exists()) {
                Document document = documentBuilder.parse(new FileInputStream(playlistFile));
                Node oldSong = document.getElementsByTagName("Song").item(oldPosition);
                Node NewSong = document.getElementsByTagName("Song").item(newPosition);
                Node tmpNode = NewSong.cloneNode(true);
                document.replaceChild(oldSong,NewSong);
                document.replaceChild(tmpNode,oldSong);
                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                Transformer transformer = transformerFactory.newTransformer();
                DOMSource domSource = new DOMSource(document);
                StreamResult streamResult = new StreamResult(playlistFile);
                transformer.transform(domSource, streamResult);
                return true;
            }
        } catch (Exception e){
            return false;
        }
        return false;
    }

    @Override
    public boolean deletePlaylist(String playlistRef) throws RemoteException {
        Playlist pl = mPlaylists.get(playlistRef);
        File playlistFile = new File(Environment.DIRECTORY_MUSIC,MD5(pl.getName())+".playlist.xml");
        if(playlistFile.exists()) {
            try {
                playlistFile.delete();
            } catch (Exception e) {
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean deleteSongFromPlaylist(int songPosition, String playlistRef) throws RemoteException {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Playlist pl = mPlaylists.get(playlistRef);
            File playlistFile = new File(Environment.DIRECTORY_MUSIC, MD5(pl.getName()) + ".playlist.xml");
            if (playlistFile.exists()) {
                Document document = documentBuilder.parse(new FileInputStream(playlistFile));
                Node Song = document.getElementsByTagName("Song").item(songPosition);
                document.removeChild(Song);
                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                Transformer transformer = transformerFactory.newTransformer();
                DOMSource domSource = new DOMSource(document);
                StreamResult streamResult = new StreamResult(playlistFile);
                transformer.transform(domSource, streamResult);
                return true;
            }
        } catch (Exception e){
            return false;
        }
        return false;
    }

    @Override
    public boolean addSongToPlaylist(String songRef, String playlistRef,ProviderIdentifier providerIdentifier) throws RemoteException {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Playlist pl = mPlaylists.get(playlistRef);
            File playlistFile = new File(Environment.DIRECTORY_MUSIC, MD5(pl.getName()) + ".playlist.xml");
            if (playlistFile.exists()) {
                Document document = documentBuilder.parse(new FileInputStream(playlistFile));
                Element Song = document.createElement("song");
                Song.setAttribute("ref",songRef);
                Song.setAttribute("providerName",providerIdentifier.mName);
                Song.setAttribute("pck",providerIdentifier.mPackage);
                Song.setAttribute("service",providerIdentifier.mService);
                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                Transformer transformer = transformerFactory.newTransformer();
                DOMSource domSource = new DOMSource(document);
                StreamResult streamResult = new StreamResult(playlistFile);
                transformer.transform(domSource, streamResult);
                return true;
            }
        } catch (Exception e){
            return false;
        }
        return false;
    }

    @Override
    public boolean addPlaylist(String playlistName) throws RemoteException {
        String ref = MD5(playlistName);
        File playlistFile = new File(Environment.DIRECTORY_MUSIC,playlistName+".playlist.xml");
        if(!playlistFile.exists()){
            try {
                playlistFile.createNewFile();
                FileOutputStream fosw = new FileOutputStream(playlistFile);
                OutputStreamWriter osw = new OutputStreamWriter(fosw);
                String basicPlaylist  = "<?xml version='1.0' encoding='UTF-8'?>" +
                                        "<playlist name = \""+playlistName+"\" ref = \"omni:playlist:"+ref+"\">" +
                                        "</playlist>";
                osw.write(basicPlaylist);
                osw.flush();
                osw.close();

            } catch(Exception e){
                return false;
            }
            return  true;
        }else
            return false;
    }
    public String MD5(String md5) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] array = md.digest(md5.getBytes());
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < array.length; ++i) {
                sb.append(Integer.toHexString((array[i] & 0xFF) | 0x100).substring(1,3));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
        }
        return null;
    }

    @Override
    public IBinder asBinder() {
        return null;
    }
}
