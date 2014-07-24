package org.omnirom.music.app.fragments;



import android.app.ListFragment;
import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.omnirom.music.app.R;
import org.omnirom.music.app.adapters.ProvidersAdapter;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.providers.ProviderConnection;

import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link SettingsProvidersFragment#newInstance} factory method to
 * create an instance of this fragment.
 *
 */
public class SettingsProvidersFragment extends ListFragment {
    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.

     * @return A new instance of fragment SettingsProviders.
     */
    public static SettingsProvidersFragment newInstance() {
        SettingsProvidersFragment fragment = new SettingsProvidersFragment();
        return fragment;
    }
    public SettingsProvidersFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        List<ProviderConnection> providers = PluginsLookup.getDefault().getAvailableProviders();
        setListAdapter(new ProvidersAdapter(providers));
    }

}
