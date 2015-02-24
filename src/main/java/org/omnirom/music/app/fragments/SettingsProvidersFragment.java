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

package org.omnirom.music.app.fragments;

import android.os.RemoteException;
import android.support.v4.app.ListFragment;
import android.content.Intent;
import android.os.Bundle;
import android.app.Fragment;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

import org.omnirom.music.app.R;
import org.omnirom.music.utils.Utils;
import org.omnirom.music.app.adapters.ProvidersAdapter;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.ProviderConnection;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple {@link Fragment} subclass displaying providers.
 * Use the {@link SettingsProvidersFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class SettingsProvidersFragment extends ListFragment {
    private static final String TAG = "SettingsProvidersFragment";

    private ProviderConnection mSettingsConnection;


    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.

     * @return A new instance of fragment SettingsProviders.
     */
    public static SettingsProvidersFragment newInstance() {
        return new SettingsProvidersFragment();
    }

    /**
     * Default constructor
     */
    public SettingsProvidersFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get providers, filter out our MultiProvider playlist provider
        List<ProviderConnection> providers = PluginsLookup.getDefault().getAvailableProviders();
        List<ProviderConnection> filteredProviders = new ArrayList<ProviderConnection>();
        for (ProviderConnection p : providers) {
            if (!p.getServiceName().equals("org.omnirom.music.providers.MultiProviderPlaylistProvider")) {
                filteredProviders.add(p);
            }
        }
        setListAdapter(new ProvidersAdapter(filteredProviders));

    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        ProviderConnection connection = ((ProvidersAdapter) getListAdapter()).getItem(position);
        if (connection.getConfigurationActivity() != null) {
            mSettingsConnection = connection;
            Intent i = new Intent();
            i.setClassName(connection.getPackage(), connection.getConfigurationActivity());
            try {
                startActivity(i);
            } catch (SecurityException e) {
                Log.e(TAG, "Cannot start: Is your activity not exported?");
                Toast.makeText(getActivity(),
                        "Cannot start: Make sure you set 'exported=true' flag on your settings activity.",
                        Toast.LENGTH_LONG).show();
            }
        } else {
            Utils.shortToast(getActivity(), R.string.no_settings_provider);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // If we come back from configuring a provider, let's see if it's ready now and log it in
        if (mSettingsConnection != null) {
            final IMusicProvider provider = mSettingsConnection.getBinder();
            try {
                if (provider != null && provider.isSetup() && !provider.isAuthenticated()) {
                    mSettingsConnection.getBinder().login();
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Cannot login newly setup provider", e);
            }
        }
    }
}
