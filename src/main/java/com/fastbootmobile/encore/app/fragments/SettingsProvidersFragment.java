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

package com.fastbootmobile.encore.app.fragments;

import android.content.ActivityNotFoundException;
import android.os.RemoteException;
import android.support.v4.app.ListFragment;
import android.content.Intent;
import android.os.Bundle;
import android.app.Fragment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.utils.Utils;
import com.fastbootmobile.encore.app.adapters.ProvidersAdapter;
import com.fastbootmobile.encore.framework.PluginsLookup;
import com.fastbootmobile.encore.providers.IMusicProvider;
import com.fastbootmobile.encore.providers.ProviderConnection;

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
        setHasOptionsMenu(true);

        // Get providers, filter out our MultiProvider playlist provider
        List<ProviderConnection> providers = PluginsLookup.getDefault().getAvailableProviders();
        List<ProviderConnection> filteredProviders = new ArrayList<>();
        for (ProviderConnection p : providers) {
            if (!p.getServiceName().equals("com.fastbootmobile.encore.providers.MultiProviderPlaylistProvider")) {
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
                Log.e(TAG, "Cannot start: Is your activity not exported?", e);
                Toast.makeText(getActivity(),
                        "Cannot start: Make sure you set 'exported=true' flag on your settings activity.",
                        Toast.LENGTH_LONG).show();
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "Cannot start: Settings activity not found in the package", e);
                Toast.makeText(getActivity(),
                        "Cannot start: Make sure your activity name is correct in the manifest.",
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

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.clear();
        inflater.inflate(R.menu.provider_settings, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_download_provider:
                ProviderDownloadDialog.newInstance(false).show(getFragmentManager(), "Download");
                return true;

            default:
                return false;
        }
    }
}
