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

package com.fastbootmobile.encore.app.tv;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v17.leanback.app.RowsFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.util.Log;
import android.widget.Toast;

import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.framework.PlaybackProxy;
import com.fastbootmobile.encore.framework.PluginsLookup;
import com.fastbootmobile.encore.providers.AbstractProviderConnection;
import com.fastbootmobile.encore.providers.DSPConnection;
import com.fastbootmobile.encore.providers.ProviderConnection;
import com.fastbootmobile.encore.providers.ProviderIdentifier;
import com.fastbootmobile.encore.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class TvProvidersFragment extends RowsFragment {
    private static final String TAG = "TvProviders";
    private ArrayObjectAdapter mRowsAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildRowsAdapter();

        setOnItemViewClickedListener(new OnItemViewClickedListener() {
            @Override
            public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                      RowPresenter.ViewHolder rowViewHolder, Row row) {
                final AbstractProviderConnection connection = (AbstractProviderConnection) item;

                CharSequence[] items;
                if (connection instanceof DSPConnection && connection.getConfigurationActivity() != null) {
                    List<ProviderIdentifier> dsp = PlaybackProxy.getDSPChain();
                    final boolean enabled = dsp.contains(connection.getIdentifier());

                    items = new CharSequence[]{
                            getString(enabled ? R.string.tv_action_disable : R.string.tv_action_enable),
                            getString(R.string.configure)
                    };

                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    builder.setItems(items, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            switch (which) {
                                case 0:
                                    toggleDSP((DSPConnection) connection);
                                    break;

                                case 1:
                                    runConfigurationActivity(connection);
                                    break;
                            }
                        }
                    });
                    builder.show();
                } else {
                    if (connection instanceof DSPConnection) {
                        toggleDSP((DSPConnection) connection);
                    } else {
                        runConfigurationActivity(connection);
                    }
                }
            }
        });
    }

    private void toggleDSP(DSPConnection connection) {
        final List<ProviderIdentifier> dsp = new ArrayList<>(PlaybackProxy.getDSPChain());
        final boolean enabled = dsp.contains(connection.getIdentifier());

        if (enabled) {
            dsp.remove(dsp.indexOf(connection.getIdentifier()));
        } else {
            dsp.add(connection.getIdentifier());
        }

        PlaybackProxy.setDSPChain(dsp);
    }

    private void runConfigurationActivity(AbstractProviderConnection connection) {
        if (connection.getConfigurationActivity() != null) {
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

    private void buildRowsAdapter() {
        mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());

        final boolean isDSPMode = getActivity().getIntent().hasExtra(TvProvidersActivity.EXTRA_DSP_MODE);
        List<AbstractProviderConnection> filteredProviders = new ArrayList<>();

        if (isDSPMode) {
            List<DSPConnection> providers = PluginsLookup.getDefault().getAvailableDSPs();
            filteredProviders.addAll(providers);
        } else {
            List<ProviderConnection> providers = PluginsLookup.getDefault().getAvailableProviders();
            for (ProviderConnection p : providers) {
                if (!p.getServiceName().equals("com.fastbootmobile.encore.providers.MultiProviderPlaylistProvider")) {
                    filteredProviders.add(p);
                }
            }
        }

        ArrayObjectAdapter providersAdapter = new ArrayObjectAdapter(new CardPresenter());
        providersAdapter.addAll(0, filteredProviders);
        HeaderItem header = new HeaderItem(0, getString(
                isDSPMode ? R.string.settings_dsp_config_title : R.string.settings_provider_config_title));
        mRowsAdapter.add(new ListRow(header, providersAdapter));

        setAdapter(mRowsAdapter);
    }
}
