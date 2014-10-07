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
 * Base empty implementation of {@link org.omnirom.music.providers.IProviderCallback} interface
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
