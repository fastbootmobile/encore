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

import android.app.Activity;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.preference.PreferenceFragment;

import org.omnirom.music.app.R;


/**
 * A simple {@link android.support.v4.app.Fragment} subclass to display app's preferences
 */
public class SettingsFragment extends PreferenceFragment {
    private static final String KEY_LIST_PROVIDERS_CONFIG = "list_providers_config";
    private static final String KEY_LIST_DSP_CONFIG = "list_dsp_config";

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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.settings);

        final PreferenceManager pm = getPreferenceManager();
        assert pm != null;

        // Fill in the entries and values
        Preference listProvidersConfig =  pm.findPreference(KEY_LIST_PROVIDERS_CONFIG);
        assert listProvidersConfig != null;

        Preference listDspConfig = pm.findPreference(KEY_LIST_DSP_CONFIG);
        assert listDspConfig != null;

        listProvidersConfig.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
                Fragment f = new SettingsProvidersFragment();
                FragmentTransaction ft = fragmentManager.beginTransaction();
                ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
                ft.addToBackStack(f.toString());
                ft.replace(R.id.container, f);
                ft.commit();

                return true;
            }
        });

        listDspConfig.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
                Fragment f = new DspProvidersFragment();
                FragmentTransaction ft = fragmentManager.beginTransaction();
                ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
                ft.addToBackStack(f.toString());
                ft.replace(R.id.container, f);
                ft.commit();

                return true;
            }
        });
    }
}
