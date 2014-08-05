package org.omnirom.music.app.fragments;



import android.animation.LayoutTransition;
import android.app.AlertDialog;
import android.support.v4.app.ListFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.app.adapters.DspAdapter;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.providers.DSPConnection;
import org.omnirom.music.providers.ProviderIdentifier;
import org.omnirom.music.service.IPlaybackService;

import java.util.List;

/**
 * A simple {@link android.app.Fragment} subclass.
 * Use the {@link org.omnirom.music.app.fragments.DspProvidersFragment#newInstance} factory method to
 * create an instance of this fragment.
 *
 */
public class DspProvidersFragment extends ListFragment {
    private static final String TAG = "DspProvidersFragment";

    private DspAdapter mAdapter;

    private DspAdapter.ClickListener mClickListener = new DspAdapter.ClickListener() {
        @Override
        public void onDeleteClicked(int position) {
            final IPlaybackService playbackService = PluginsLookup.getDefault().getPlaybackService();
            try {
                List<ProviderIdentifier> chain = playbackService.getDSPChain();
                chain.remove(position);
                playbackService.setDSPChain(chain);
            } catch (RemoteException e) {
                Log.e(TAG, "Cannot update chain of playback service", e);
            }

            updateDspChain();
        }

        @Override
        public void onUpClicked(int position) {
            final IPlaybackService playbackService = PluginsLookup.getDefault().getPlaybackService();
            try {
                List<ProviderIdentifier> chain = playbackService.getDSPChain();
                ProviderIdentifier item = chain.remove(position);
                position = Math.max(position - 1, 0);
                chain.add(position, item);
                playbackService.setDSPChain(chain);
            } catch (RemoteException e) {
                Log.e(TAG, "Cannot update chain of playback service", e);
            }

            updateDspChain();
        }

        @Override
        public void onDownClicked(int position) {
            final IPlaybackService playbackService = PluginsLookup.getDefault().getPlaybackService();
            try {
                List<ProviderIdentifier> chain = playbackService.getDSPChain();
                ProviderIdentifier item = chain.remove(position);
                position = Math.min(chain.size(), position + 1);
                chain.add(position, item);
                playbackService.setDSPChain(chain);
            } catch (RemoteException e) {
                Log.e(TAG, "Cannot update chain of playback service", e);
            }

            updateDspChain();
        }
    };


    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.

     * @return A new instance of fragment SettingsProviders.
     */
    public static DspProvidersFragment newInstance() {
        return new DspProvidersFragment();
    }
    public DspProvidersFragment() {
        // Required empty public constructor
    }

    public void updateDspChain() {
        final IPlaybackService playbackService = PluginsLookup.getDefault().getPlaybackService();
        try {
            List<ProviderIdentifier> chain = playbackService.getDSPChain();

            if (mAdapter == null) {
                mAdapter = new DspAdapter(chain);
            } else {
                mAdapter.updateChain(chain);
            }

            mAdapter.setClickListener(mClickListener);
            setListAdapter(mAdapter);
        } catch (RemoteException e) {
            Log.e(TAG, "Cannot get chain from playback service", e);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        updateDspChain();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        ListView lv = (ListView) view.findViewById(android.R.id.list);
        lv.setLayoutTransition(new LayoutTransition());
        return view;
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
            try {
                startActivity(i);
            } catch (SecurityException e) {
                Utils.shortToast(getActivity(), R.string.plugin_error);
                Log.e(TAG, "Unable to start configuration activity. Is it exported in the manifest?", e);
            }
        } else {
            Utils.shortToast(getActivity(), R.string.no_settings_dsp);
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

        if (availableDsp.size() > 0) {
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
                    updateDspChain();
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
        } else {
            Utils.shortToast(getActivity(), R.string.all_dsp_in_use);
        }
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
