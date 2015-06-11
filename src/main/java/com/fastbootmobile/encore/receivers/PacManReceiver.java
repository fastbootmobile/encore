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

package com.fastbootmobile.encore.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import com.fastbootmobile.encore.framework.PlaybackProxy;
import com.fastbootmobile.encore.framework.PluginsLookup;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.ProviderAggregator;
import com.fastbootmobile.encore.providers.ProviderIdentifier;
import com.fastbootmobile.encore.service.PlaybackService;

/**
 * Broadcast receiver handling package manager events
 */
public class PacManReceiver extends BroadcastReceiver {
    private static final String TAG = "PacManReceiver";

    /**
     * {@inheritDoc}
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        final PackageManager pm = context.getPackageManager();

        if (intent.getAction() == null || pm == null) {
            Log.e(TAG, "Null action or package manager");
            return;
        }

        Log.i(TAG, "PacMan kicking in: " + intent.getAction());

        if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())
                || Intent.ACTION_PACKAGE_CHANGED.equals(intent.getAction())
                || Intent.ACTION_PACKAGE_REPLACED.equals(intent.getAction())) {
            // A new package has been installed, it might be a plugin so we reload them
            PluginsLookup.getDefault().requestUpdatePlugins();
        } else if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
            // A package has been removed - but might be replaced. If it's the currently playing
            // provider, we stop the playback.
            int uid = intent.getIntExtra(Intent.EXTRA_UID, 0);
            if (uid > 0) {
                String[] packages = pm.getPackagesForUid(uid);
                if (packages != null) {
                    stopPlayingTrack(packages);
                }
            }

            // TODO: If EXTRA_REPLACE is set, maybe just pause and try to resume playback after
            // the update?
        } else if (Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(intent.getAction())) {
            // A package has been fully removed, so if it's a provider we must remove all the
            // data associated with it from the cache.
            int uid = intent.getIntExtra(Intent.EXTRA_UID, 0);
            if (uid > 0) {
                String[] packages = pm.getPackagesForUid(uid);
                if (packages != null) {
                    ProviderIdentifier id = stopPlayingTrack(packages);

                    if (id != null) {
                        ProviderAggregator.getDefault().getCache().purgeCacheForProvider(id);
                    }
                }
            }

            // Update the list of plugins
            PluginsLookup.getDefault().requestUpdatePlugins();
        }
    }

    private ProviderIdentifier stopPlayingTrack(String[] packages) {
        if (packages == null) {
            return null;
        }

        if (PlaybackProxy.getState() == PlaybackService.STATE_PLAYING
                || PlaybackProxy.getState() == PlaybackService.STATE_BUFFERING) {
            Song currentTrack = PlaybackProxy.getCurrentTrack();
            if (currentTrack != null) {
                String providerPackage = currentTrack.getProvider().mPackage;

                for (String pack : packages) {
                    if (providerPackage.equals(pack)) {
                        PlaybackProxy.stop();
                        return currentTrack.getProvider();
                    }
                }
            }
        }

        return null;
    }
}