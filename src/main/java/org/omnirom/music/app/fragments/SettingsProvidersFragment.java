package org.omnirom.music.app.fragments;



import android.os.RemoteException;
import android.support.v4.app.ListFragment;
import android.content.Intent;
import android.os.Bundle;
import android.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.app.adapters.ProvidersAdapter;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.ProviderConnection;
import org.omnirom.music.providers.ProviderIdentifier;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link SettingsProvidersFragment#newInstance} factory method to
 * create an instance of this fragment.
 *
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
        SettingsProvidersFragment fragment = new SettingsProvidersFragment();
        return fragment;
    }
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
            startActivity(i);
        } else {
            Utils.shortToast(getActivity(), R.string.no_settings_provider);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mSettingsConnection != null) {
            IMusicProvider provider = mSettingsConnection.getBinder();
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
