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

package com.fastbootmobile.encore.providers;

import android.content.ComponentName;
import android.content.Context;

import com.fastbootmobile.encore.service.NativeHub;

public class InjectedProviderConnection extends ProviderConnection {
    private IMusicProvider mInjected;

    /**
     * Constructor
     *
     * @param provider       The provider to be injected
     * @param ctx            The context to which this connection should be bound
     * @param providerName   The name of the provider (example: 'Spotify')
     * @param authorName     The provider company
     * @param pkg            The package in which the service can be found (example: org.example.music)
     * @param serviceName    The name of the service (example: .MusicService)
     * @param configActivity The name of the configuration activity in the aforementioned package
     */
    public InjectedProviderConnection(IMusicProvider provider, Context ctx, String providerName,
                                      String authorName, String pkg, String serviceName,
                                      String configActivity) {
        super(ctx, providerName, authorName, pkg, serviceName, configActivity);

        mInjected = provider;

        // Super will call bindService, but this stupid Java language doesn't allow you to
        // change mInjected before calling super. We set injected, then we retry to "bind" the
        // injected provider.
        bindService();
    }

    @Override
    public void bindService() {
        if (mInjected != null) {
            onServiceConnected(new ComponentName(getPackage(), getServiceName()),
                    mInjected.asBinder());
        }
    }

    @Override
    public void unbindService(NativeHub hub) {
        // Do nothing
    }
}
