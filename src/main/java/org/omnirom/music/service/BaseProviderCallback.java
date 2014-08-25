package org.omnirom.music.service;

import android.os.RemoteException;

import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Genre;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.SearchResult;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.IProviderCallback;
import org.omnirom.music.providers.ProviderIdentifier;

/**
 * Created by Guigui on 25/08/2014.
 */
public class BaseProviderCallback extends IProviderCallback.Stub {
    @Override
    public void onLoggedIn(ProviderIdentifier provider, boolean success) throws RemoteException {

    }

    @Override
    public void onLoggedOut(ProviderIdentifier provider) throws RemoteException {

    }

    @Override
    public void onPlaylistAddedOrUpdated(ProviderIdentifier provider, Playlist p) throws RemoteException {

    }

    @Override
    public void onSongUpdate(ProviderIdentifier provider, Song s) throws RemoteException {

    }

    @Override
    public void onAlbumUpdate(ProviderIdentifier provider, Album a) throws RemoteException {

    }

    @Override
    public void onArtistUpdate(ProviderIdentifier provider, Artist a) throws RemoteException {

    }

    @Override
    public void onGenreUpdate(ProviderIdentifier provider, Genre g) throws RemoteException {

    }

    @Override
    public void onSongPlaying(ProviderIdentifier provider) throws RemoteException {

    }

    @Override
    public void onSongPaused(ProviderIdentifier provider) throws RemoteException {

    }

    @Override
    public void onTrackEnded(ProviderIdentifier provider) throws RemoteException {

    }

    @Override
    public void onSearchResult(SearchResult searchResult) throws RemoteException {

    }
}
