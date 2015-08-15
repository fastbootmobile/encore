/*
 * Copyright (C) 2015 Fastboot Mobile, LLC.
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
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.app.adapters.DspAdapter;
import com.fastbootmobile.encore.framework.PlaybackProxy;
import com.fastbootmobile.encore.framework.PluginsLookup;
import com.fastbootmobile.encore.providers.DSPConnection;
import com.fastbootmobile.encore.providers.ProviderIdentifier;
import com.fastbootmobile.encore.utils.Utils;

import java.util.List;

/**
 * A simple {@link android.app.Fragment} subclass.
 * Use the {@link com.fastbootmobile.encore.app.fragments.DspProvidersFragment#newInstance} factory method to
 * create an instance of this fragment.
 *
 */
public class DspProvidersFragment extends ListFragment {
    private static final String TAG = "DspProvidersFragment";

    private DspAdapter mAdapter;
    private Handler mHandler;

    private DspAdapter.ClickListener mClickListener = new DspAdapter.ClickListener() {
        @Override
        public void onDeleteClicked(int position) {
            List<ProviderIdentifier> chain = PlaybackProxy.getDSPChain();
            if (position < chain.size()) {
                chain.remove(position);
                PlaybackProxy.setDSPChain(chain);
            } else {
                Log.w(TAG, "Invalid element position: " + position + " (size=" + chain.size() + ")");
            }

            postUpdateDspChain();
        }

        @Override
        public void onUpClicked(int position) {
            List<ProviderIdentifier> chain = PlaybackProxy.getDSPChain();
            ProviderIdentifier item = chain.remove(position);
            position = Math.max(position - 1, 0);
            chain.add(position, item);
            PlaybackProxy.setDSPChain(chain);

            postUpdateDspChain();
        }

        @Override
        public void onDownClicked(int position) {
            List<ProviderIdentifier> chain = PlaybackProxy.getDSPChain();
            ProviderIdentifier item = chain.remove(position);
            position = Math.min(chain.size(), position + 1);
            chain.add(position, item);
            PlaybackProxy.setDSPChain(chain);

            postUpdateDspChain();
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

    private void postUpdateDspChain() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                updateDspChain();
            }
        });
    }

    public void updateDspChain() {
        List<ProviderIdentifier> chain = PlaybackProxy.getDSPChain();

        if (mAdapter == null) {
            mAdapter = new DspAdapter(chain);
        } else {
            mAdapter.updateChain(chain);
        }

        mAdapter.setClickListener(mClickListener);
        setListAdapter(mAdapter);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        mHandler = new Handler();

        updateDspChain();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setEmptyText("Sound effects plugins allows you to enhance your sound experience (via bass boosting, equalizer, etc). Tap '+' to add an installed plugin to the chain, or tap the cloud icon to discover more effects.");
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

            case R.id.menu_download_effect:
                ProviderDownloadDialog.newInstance(true).show(getFragmentManager(), "DSPDownload");
                return true;

            default:
                return false;
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
            } catch (ActivityNotFoundException e) {
                Utils.shortToast(getActivity(), R.string.plugin_error);
                Log.e(TAG, "Unable to start configuration activity, as the activity wasn't found", e);
            }
        } else {
            Utils.shortToast(getActivity(), R.string.no_settings_dsp);
        }
    }

    public void showAddEffect() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.add_effect);

        // Lookup all the DSP effects providers that aren't already in the chain
        final List<DSPConnection> availableDsp = PluginsLookup.getDefault().getAvailableDSPs();
        final List<ProviderIdentifier> chain = PlaybackProxy.getDSPChain();
        final List<DSPConnection> dsps = PluginsLookup.getDefault().getAvailableDSPs();

        for (ProviderIdentifier pi : chain) {
            for (DSPConnection dsp : dsps) {
                if (dsp.getIdentifier().equals(pi)) {
                    availableDsp.remove(dsp);
                }
            }
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
            builder.setIcon(R.drawable.ic_nav_listen_now);

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
        final List<ProviderIdentifier> chain = PlaybackProxy.getDSPChain();
        chain.add(dsp.getIdentifier());
        PlaybackProxy.setDSPChain(chain);
    }
}
