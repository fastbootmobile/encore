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

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.preference.PreferenceFragment;
import android.webkit.WebView;
import android.widget.Toast;

import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.app.WelcomeActivity;
import com.fastbootmobile.encore.art.AlbumArtCache;
import com.fastbootmobile.encore.utils.SettingsKeys;

import java.util.Set;

/**
 * A simple {@link android.support.v4.app.Fragment} subclass to display app's preferences
 */
public class SettingsFragment extends PreferenceFragment {
    private static final String KEY_LIST_PROVIDERS_CONFIG = "list_providers_config";
    private static final String KEY_LIST_DSP_CONFIG = "list_dsp_config";
    private static final String KEY_CLEAR_CACHES = "pref_clear_caches";
    private static final String KEY_OPEN_SETUP_WIZARD = "pref_open_setup_wizard";
    private static final String KEY_LICENSES = "pref_licenses";
    /**
     * Use this factory method to create a new instance of
     * this fragment
     *
     * @return A new instance of fragment SettingsFragment.
     */
    public static SettingsFragment newInstance() {
        return new SettingsFragment();
    }

    /**
     * Default constructor
     */
    public SettingsFragment() {
    }

    private SharedPreferences getPrefs() {
        return getPreferenceManager().getSharedPreferences();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getPreferenceManager().setSharedPreferencesName(SettingsKeys.PREF_SETTINGS);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.settings);

        // Load Bluetooth paired devices
        final ListPreference btNameList = (ListPreference) findPreference(SettingsKeys.KEY_BLUETOOTH_AUTOCONNECT_NAME);
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

        if (adapter == null) {
            // No Bluetooth adapter, remove preferences
            getPreferenceScreen().removePreference(findPreference(SettingsKeys.KEY_BLUETOOTH_AUTOCONNECT_ENABLE));
            getPreferenceScreen().removePreference(findPreference(SettingsKeys.KEY_BLUETOOTH_AUTOCONNECT_ACTION));
            getPreferenceScreen().removePreference(findPreference(SettingsKeys.KEY_BLUETOOTH_AUTOCONNECT_NAME));
            getPreferenceScreen().removePreference(findPreference("category_bluetooth"));
        } else {
            Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();
            CharSequence[] entries = new CharSequence[pairedDevices.size()];

            String currentPreferred = getPrefs().getString(SettingsKeys.KEY_BLUETOOTH_AUTOCONNECT_NAME, null);

            int i = 0;
            for (BluetoothDevice device : pairedDevices) {
                entries[i] = device.getName();
                ++i;
            }
            btNameList.setEntries(entries);
            btNameList.setEntryValues(entries);

            if (currentPreferred != null) {
                btNameList.setDefaultValue(currentPreferred);
            }
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        final String prefKey = preference.getKey();

        if (prefKey == null) {
            return false;
        }

        switch (prefKey) {
            case KEY_CLEAR_CACHES: {
                AlbumArtCache.getDefault().clear();
                Toast.makeText(getActivity(), getString(R.string.cache_cleared), Toast.LENGTH_SHORT).show();
                return true;
            }
            case KEY_OPEN_SETUP_WIZARD: {
                startActivity(new Intent(getActivity(), WelcomeActivity.class));
                return true;
            }
            case KEY_LIST_DSP_CONFIG: {
                Fragment f = new DspProvidersFragment();
                openFragment(f);
                return true;
            }
            case KEY_LIST_PROVIDERS_CONFIG: {
                Fragment f = new SettingsProvidersFragment();
                openFragment(f);
                return true;
            }
            case KEY_LICENSES: {
                openLicenses();
                return true;
            }
            case SettingsKeys.KEY_FREE_ART: {
                AlbumArtCache.CREATIVE_COMMONS = ((CheckBoxPreference) preference).isChecked();
                return true;
            }
        }

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    private void openFragment(Fragment f) {
        final FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
        FragmentTransaction ft = fragmentManager.beginTransaction();
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
        ft.addToBackStack(f.toString());
        ft.replace(R.id.container, f);
        ft.commit();
    }

    private void openLicenses() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        String text =getString(R.string.licenses);
        text = "<pre>" + text + "</pre>";
        text = text.replaceAll("\n", "<br />");

        WebView view = new WebView(getActivity());
        view.loadData(text, "text/html", "UTF-8");

        builder.setView(view);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        builder.show();
    }
}
