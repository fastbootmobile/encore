package org.omnirom.music.app.fragments;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.omnirom.music.app.R;
import org.omnirom.music.app.SettingsActivity;
import org.omnirom.music.app.Utils;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.providers.ProviderConnection;

import java.util.List;


/**
 * A simple {@link android.support.v4.app.Fragment} subclass.
 */
public class SettingsFragment extends PreferenceFragment {

    private static final String TAG = "SettingsFragment";

    private static final String KEY_MULTISEL_PROVIDERS_ENABLE = "multisel_providers_enable";
    private static final String KEY_LIST_PROVIDERS_CONFIG = "list_providers_config";

    /**
     * Use this factory method to create a new instance of
     * this fragment
     *
     * @return A new instance of fragment SettingsFragment.
     */
    public static SettingsFragment newInstance() {
        SettingsFragment fragment = new SettingsFragment();
        return fragment;
    }
    public SettingsFragment() {

    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.settings);

        PreferenceManager pm = getPreferenceManager();
        assert pm != null;

        // Fill in the entries and values
        Preference listProvidersConfig =  pm.findPreference(KEY_LIST_PROVIDERS_CONFIG);
        assert listProvidersConfig != null;

        listProvidersConfig.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                FragmentManager fragmentManager = getActivity().getFragmentManager();
                Fragment f = new SettingsProvidersFragment();
                FragmentTransaction ft = fragmentManager.beginTransaction();
                ft.setCustomAnimations(R.animator.slide_in_left, R.animator.slide_out_left, R.animator.slide_in_right, R.animator.slide_out_right);
                ft.addToBackStack(f.toString());
                ft.replace(R.id.container, f);
                ft.commit();

                return true;
            }
        });
/*
        listProvidersConfig.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                int providerNum = Integer.parseInt((String) newValue);
                mProviderInConfig = mProviders.get(providerNum);

                if (mProviderInConfig.getConfigurationActivity() != null) {
                    Intent i = new Intent();
                    i.setClassName(mProviderInConfig.getPackage(),
                            mProviderInConfig.getConfigurationActivity());
                    startActivity(i);
                } else {
                    Utils.shortToast(getActivity(), R.string.no_settings_provider);
                }

                return true;
            }
        });*/
    }

}
