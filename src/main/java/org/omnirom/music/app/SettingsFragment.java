package org.omnirom.music.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.app.Fragment;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.omnirom.music.providers.ProviderConnection;

import java.util.HashMap;
import java.util.List;


/**
 * A simple {@link android.support.v4.app.Fragment} subclass.
 */
public class SettingsFragment extends PreferenceFragment {

    private static final String KEY_MULTISEL_PROVIDERS_ENABLE = "multisel_providers_enable";
    private static final String KEY_LIST_PROVIDERS_CONFIG = "list_providers_config";

    private PluginsLookup mPluginsLookup;
    private List<ProviderConnection> mProviders;

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
        mPluginsLookup = new PluginsLookup(activity);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.settings);

        PreferenceManager pm = getPreferenceManager();
        assert pm != null;

        // Fill in the entries and values
        MultiSelectListPreference mslProvidersEnable =
                (MultiSelectListPreference) pm.findPreference(KEY_MULTISEL_PROVIDERS_ENABLE);
        assert mslProvidersEnable != null;

        ListPreference listProvidersConfig = (ListPreference) pm.findPreference(KEY_LIST_PROVIDERS_CONFIG);
        assert listProvidersConfig != null;

        mProviders = mPluginsLookup.getAvailableProviders();

        if (mProviders.size() > 0) {
            CharSequence[] providerNames = new CharSequence[mProviders.size()];
            CharSequence[] providerValues = new CharSequence[mProviders.size()];
            int i = 0;
            for (ProviderConnection provider : mProviders) {
                providerNames[i] = provider.getProviderName();
                providerValues[i] = Integer.toString(i);
            }
            mslProvidersEnable.setEntries(providerNames);
            mslProvidersEnable.setEntryValues(providerValues);
            listProvidersConfig.setEntries(providerNames);
            listProvidersConfig.setEntryValues(providerValues);
        }


        listProvidersConfig.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                int providerNum = Integer.parseInt((String) newValue);
                ProviderConnection provider = mProviders.get(providerNum);

                Intent i = new Intent();
                i.setClassName(provider.getPackage(), provider.getConfigurationActivity());
                startActivity(i);

                return true;
            }
        });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        view.setBackgroundColor(getResources().getColor(R.color.default_fragment_background));

        return view;
    }
}
