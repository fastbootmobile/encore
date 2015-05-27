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

package com.fastbootmobile.encore.service;

import android.os.RemoteException;

import com.fastbootmobile.encore.model.Song;

/**
 * Base empty implementation of {@link com.fastbootmobile.encore.service.IPlaybackCallback} interface
 */
public class BasePlaybackCallback extends IPlaybackCallback.Stub {
    @Override
    public int getIdentifier() throws RemoteException {
        return this.hashCode();
    }

    @Override
    public void onSongStarted(boolean buffering, Song s) throws RemoteException {

    }

    @Override
    public void onSongScrobble(int timeMs) throws RemoteException {

    }

    @Override
    public void onPlaybackPause() throws RemoteException {

    }

    @Override
    public void onPlaybackResume() throws RemoteException {

    }

    @Override
    public void onPlaybackQueueChanged() throws RemoteException {

    }
}
