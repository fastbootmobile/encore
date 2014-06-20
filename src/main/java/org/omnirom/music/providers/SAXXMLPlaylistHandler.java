package org.omnirom.music.providers;

import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by h4o on 17/06/2014.
 */
public class SAXXMLPlaylistHandler extends DefaultHandler {
    private List<String> mSongs;
    private List<ProviderIdentifier> mIdentifiers;
    private HashMap<String,ProviderIdentifier> mSongProviders;
  //  private String tmpString;
    private Playlist mPlaylist;
    private String tmpSong;
    private ProviderIdentifier tmpProviderId;
    public SAXXMLPlaylistHandler() {
        mSongs = new ArrayList<String>();
        mIdentifiers = new ArrayList<ProviderIdentifier>();
    }
    public void setHashMap(HashMap<String,ProviderIdentifier> songProviders){
        mSongProviders = songProviders;
    }
    public void startElement(String uri, String localName, String qName,
                             Attributes attributes) throws SAXException {
        //tmpString = "";
        if(qName.equalsIgnoreCase("playlist")){
            mPlaylist = new Playlist(attributes.getValue("ref"));
            mPlaylist.setName(attributes.getValue("name"));
        } else if(qName.equalsIgnoreCase("song")){
            tmpSong = new String(attributes.getValue("ref"));

            tmpProviderId = new ProviderIdentifier(attributes.getValue("pck"),attributes.getValue("service"),attributes.getValue("providerName"));

        }
    }
    public void characters(char[] ch, int start, int length)
            throws SAXException {
      //  tmpString = new String(ch,start,length);
    }
    public void endElement(String uri, String localName, String qName)
            throws SAXException {
        if (qName.equalsIgnoreCase("song")) {
            mSongs.add(tmpSong);
            mIdentifiers.add(tmpProviderId);
            mSongProviders.put(tmpSong,tmpProviderId);
            mPlaylist.addSong(tmpSong);
        }

    }
    public HashMap<String,ProviderIdentifier> getSongProviders() {
        return mSongProviders;
    }
    public Playlist getPlaylist(){
        return mPlaylist;
    }
}
