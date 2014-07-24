package org.omnirom.music.app.fragments;



import android.app.AlertDialog;
import android.app.ListFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.app.adapters.DspAdapter;
import org.omnirom.music.app.adapters.ProvidersAdapter;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.providers.DSPConnection;
import org.omnirom.music.providers.ProviderConnection;
import org.omnirom.music.providers.ProviderIdentifier;
import org.omnirom.music.service.IPlaybackService;
import org.omnirom.music.service.PlaybackService;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple {@link android.app.Fragment} subclass.
 * Use the {@link org.omnirom.music.app.fragments.DspProvidersFragment#newInstance} factory method to
 * create an instance of this fragment.
 *
 */
public class DspProvidersFragment extends ListFragment {
    private static final String TAG = "DspProvidersFragment";

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.

     * @return A new instance of fragment SettingsProviders.
     */
    public static DspProvidersFragment newInstance() {
        DspProvidersFragment fragment = new DspProvidersFragment();
        return fragment;
    }
    public DspProvidersFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        IPlaybackService playbackService = PluginsLookup.getDefault().getPlaybackService();
        try {
            List<ProviderIdentifier> chain = playbackService.getDSPChain();
            setListAdapter(new DspAdapter(chain));
        } catch (RemoteException e) {
            Log.e(TAG, "Cannot get chain from playback service", e);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.clear();
        inflater.inflate(R.menu.dsp_settings, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_add_effect:
                showAddEffect();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        DSPConnection connection = ((DspAdapter) getListAdapter()).getItem(position);
        if (connection.getConfigurationActivity() != null) {
            Intent i = new Intent();
            i.setClassName(connection.getPackage(),
                    connection.getConfigurationActivity());
            startActivity(i);
        } else {
            Utils.shortToast(getActivity(), R.string.no_settings_provider);
        }
    }

    public void showAddEffect() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.add_effect);

        // Lookup all the DSP effects providers that aren't already in the chain
        IPlaybackService playbackService = PluginsLookup.getDefault().getPlaybackService();
        final List<DSPConnection> availableDsp = PluginsLookup.getDefault().getAvailableDSPs();
        try {
            List<ProviderIdentifier> chain = playbackService.getDSPChain();
            List<DSPConnection> dsps = PluginsLookup.getDefault().getAvailableDSPs();

            for (ProviderIdentifier pi : chain) {
                for (DSPConnection dsp : dsps) {
                    if (dsp.getIdentifier().equals(pi)) {
                        availableDsp.remove(dsp);
                    }
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Cannot get chain from playback service", e);
        }

        CharSequence[] items = new CharSequence[availableDsp.size()];
        int i = 0;
        for (DSPConnection dsp : availableDsp) {
            items[i] = dsp.getProviderName();
            i++;
        }

        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                addEffectToChain(availableDsp.get(i));
                dialogInterface.dismiss();
            }
        });
        builder.setIcon(R.drawable.ic_btn_play_red);

        builder.setCancelable(true);
        builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });

        builder.show();
    }

    public void addEffectToChain(DSPConnection dsp) {
        try {
            IPlaybackService pbService = PluginsLookup.getDefault().getPlaybackService();
            List<ProviderIdentifier> chain = pbService.getDSPChain();
            chain.add(dsp.getIdentifier());
            pbService.setDSPChain(chain);
        } catch (RemoteException e) {
            Log.e(TAG, "Cannot update DSP chain", e);
        }
    }
}
