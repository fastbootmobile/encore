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

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.providers.DSPConnection;
import org.omnirom.music.providers.ProviderIdentifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Class responsible for grabbing the audio from a provider, pushing it through the DSP chain,
 * and playing it to a sink
 */
public class DSPProcessor {
    private static final String TAG = "DSPProcessor";

    private static final String PREFS_DSP_CHAIN = "DSP_Chain";
    private static final String PREF_KEY_CHAIN = "chain";

    private List<ProviderIdentifier> mDSPChain;
    private PlaybackService mPlaybackService;

    /**
     * Default constructor
     */
    public DSPProcessor(PlaybackService pbs) {
        mPlaybackService = pbs;
        mDSPChain = new ArrayList<>();
    }

    /**
     * Returns the current RMS level of the last 1/60 * sampleRate frames
     * @return The RMS level
     */
    public int getRms() {
        // TODO: reimplement
        return 0;
    }

    /**
     * Sets the current active DSP plugins chain and saves it
     * @param ctx A valid context
     * @param chain The chain of plugins to use
     */
    public void setActiveChain(Context ctx, List<ProviderIdentifier> chain, NativeHub hub) {
        // We make a copy to avoid any external modification
        mDSPChain = new ArrayList<>(chain);

        // Save it
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_DSP_CHAIN, 0);
        Set<String> identifiers = new TreeSet<>();

        for (ProviderIdentifier identifier : chain) {
            identifiers.add(identifier.serialize());
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(PREF_KEY_CHAIN, identifiers);
        editor.apply();

        // Bind (if necessary) the new services, and unbind unused DSP services
        final PluginsLookup plugins = PluginsLookup.getDefault();
        final List<DSPConnection> list = new ArrayList<>(plugins.getAvailableDSPs());
        for (ProviderIdentifier id : chain) {
            DSPConnection conn = plugins.getDSP(id);
            conn.bindService();
            list.remove(conn);
        }

        for (DSPConnection conn : list) {
            Log.e(TAG, "Unbinding: " + conn.getProviderName());
            conn.unbindService(hub);
        }

        updateHubDspChain();
    }

    /**
     * @return The current active chain of DSP plugins
     */
    public List<ProviderIdentifier> getActiveChain() {
        return mDSPChain;
    }

    /**
     * Restores the saved chain
     * @param ctx A valid context
     */
    public void restoreChain(Context ctx) {
        final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_DSP_CHAIN, 0);
        final Set<String> identifiers = prefs.getStringSet(PREF_KEY_CHAIN, null);
        final PluginsLookup plugins = PluginsLookup.getDefault();

        mDSPChain = new ArrayList<>();

        if (identifiers != null) {
            for (String id : identifiers) {
                ProviderIdentifier identifier = ProviderIdentifier.fromSerialized(id);
                if (identifier != null) {
                    mDSPChain.add(identifier);
                    plugins.getDSP(identifier).bindService();
                } else {
                    Log.e(TAG, "Cannot restore from serialized string " + id);
                }
            }
        }

        // Push the new chain to the NativeHub
        new Thread() {
            public void run() {
                updateHubDspChain();
            }
        }.start();
    }

    /**
     * Updates the DSP chain on the native hub
     */
    private void updateHubDspChain() {
        NativeHub hub = mPlaybackService.getNativeHub();
        String[] sockets = new String[mDSPChain.size()];
        int index = 0;
        for (ProviderIdentifier id : mDSPChain) {
            DSPConnection conn = PluginsLookup.getDefault().getDSP(id);
            if (conn != null) {
                String socketName = conn.getAudioSocketName();
                if (socketName == null) {
                    socketName = mPlaybackService.assignProviderAudioSocket(conn);
                }

                if (socketName == null) {
                    Log.e(TAG, "======== SOCKET NAME STILL NULL AFTER ASSIGNPROVIDERAUDIOSOCKET");
                } else {
                    Log.e(TAG, "SOCKET " + index + ": " + socketName);
                }
                sockets[index] = socketName;
            } else {
                Log.e(TAG, "============================================");
                Log.e(TAG, "= FIXMEFIXMEFIXMEFIXMEFIXMEFIXMEFIXMEFIXME =");
                Log.e(TAG, "= DSP in the chain, but not yet connected! =");
                Log.e(TAG, "= FIXMEFIXMEFIXMEFIXMEFIXMEFIXMEFIXMEFIXME =");
                Log.e(TAG, "============================================");
            }

            index++;
        }

        hub.setDSPChain(sockets);
    }
}
