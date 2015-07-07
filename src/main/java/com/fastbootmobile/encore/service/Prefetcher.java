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
import android.util.Log;

import com.fastbootmobile.encore.framework.PluginsLookup;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.IMusicProvider;
import com.fastbootmobile.encore.providers.ProviderConnection;

/**
 * Runnable handling the pre-fetch of the next track
 */
public class Prefetcher implements Runnable {
    private static final String TAG = "Prefetcher";

    private PlaybackService mService;


    public Prefetcher(PlaybackService service) {
        mService = service;
    }

    @Override
    public void run() {
        if (mService == null) {
            return;
        }

        // If we're still expecting this song next, pre-fetch it
        final Song nextSong = mService.getNextTrack();
        if (nextSong != null) {
            final ProviderConnection conn = PluginsLookup.getDefault().getProvider(nextSong.getProvider());
            if (conn != null) {
                final IMusicProvider provider = conn.getBinder();
                if (provider != null) {
                    // We prefetch in a thread, as if the provider blocks, this is blocking the UI
                    new Thread() {
                        public void run() {
                            try {
                                provider.prefetchSong(nextSong.getRef());
                            } catch (RemoteException e) {
                                Log.e(TAG, "Cannot pre-fetch song", e);
                            }
                        }
                    }.start();
                }
            }
        }
    }

    public void cancel() {
        mService = null;
    }
}
