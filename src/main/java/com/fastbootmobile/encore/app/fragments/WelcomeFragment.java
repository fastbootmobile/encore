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
import android.os.RemoteException;
import android.support.annotation.LayoutRes;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.fastbootmobile.encore.app.MainActivity;
import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.app.WelcomeActivity;
import com.fastbootmobile.encore.app.adapters.ProvidersAdapter;
import com.fastbootmobile.encore.framework.PluginsLookup;
import com.fastbootmobile.encore.providers.IMusicProvider;
import com.fastbootmobile.encore.providers.ProviderConnection;

import java.util.ArrayList;
import java.util.List;


/**
 * A placeholder fragment containing a simple view.
 */
public class WelcomeFragment extends Fragment {
    private static final String TAG = "WelcomeFragment";

    private static final int LAST_STEP = 4;

    public static WelcomeFragment newInstance(int step) {
        int layoutId;
        switch (step) {
            case 1:
                layoutId = R.layout.fragment_welcome1;
                break;

            case 2:
                layoutId = R.layout.fragment_welcome2;
                break;

            case 3:
                layoutId = R.layout.fragment_welcome3;
                break;

            case 4:
                layoutId = R.layout.fragment_welcome4;
                break;

            default:
                throw new IllegalArgumentException("Invalid step: " + step);
        }

        WelcomeFragment fragment = new WelcomeFragment();
        fragment.setLayoutId(layoutId);
        fragment.setStep(step);
        return fragment;
    }

    @LayoutRes
    private int mLayoutId;
    private int mStep;
    private ProviderConnection mConfiguringProvider;
    private ProvidersAdapter mStep3Adapter;

    public WelcomeFragment() {
    }

    public void setStep(int step) {
        mStep = step;
    }

    public void setLayoutId(@LayoutRes int id) {
        mLayoutId = id;
    }

    private void finishWizard() {
        Intent intent = new Intent(getActivity(), MainActivity.class);
        startActivity(intent);
        getActivity().finish();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        if (mLayoutId <= 0) {
            // Happened once, couldn't reproduce, so let's do a dumb fallback here
            setStep(1);
            setLayoutId(R.layout.fragment_welcome1);
        }

        View root = inflater.inflate(mLayoutId, container, false);
        root.findViewById(R.id.btnNext).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mStep == LAST_STEP) {
                    finishWizard();
                } else {
                    WelcomeActivity act = (WelcomeActivity) getActivity();
                    act.showStep(mStep + 1);
                }
            }
        });

        return root;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Step specifics
        if (mStep == 2) {
            View root = getView();
            if (root != null) {
                root.findViewById(R.id.btnSkip).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder dialog = new AlertDialog.Builder(getActivity());
                        dialog.setTitle(R.string.welcome_skip_dialog_title);
                        dialog.setMessage(R.string.welcome_skip_dialog_body);
                        dialog.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finishWizard();
                            }
                        });
                        dialog.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                        dialog.show();
                    }
                });

                root.findViewById(R.id.btnBrowse).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ProviderDownloadDialog.newInstance(false).show(getFragmentManager(), "Download");
                    }
                });
            }
        } else if (mStep == 3) {
            // Step 3: Configure plugins
            ListView lv = (ListView) view.findViewById(R.id.lvProviders);
            List<ProviderConnection> allProvs = PluginsLookup.getDefault().getAvailableProviders();
            final List<ProviderConnection> provs = new ArrayList<>();

            for (ProviderConnection prov : allProvs) {
                if (prov.getConfigurationActivity() != null
                        && !TextUtils.isEmpty(prov.getConfigurationActivity())) {
                    provs.add(prov);
                }
            }

            mStep3Adapter = new ProvidersAdapter(provs);
            mStep3Adapter.setWhite(true);
            mStep3Adapter.setWashOutConfigure(true);
            lv.setAdapter(mStep3Adapter);

            lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    ProviderConnection connection = provs.get(position);
                    Intent intent = new Intent();
                    intent.setPackage(connection.getPackage());
                    intent.setClassName(connection.getPackage(), connection.getConfigurationActivity());
                    mConfiguringProvider = connection;
                    try {
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(getActivity(), R.string.toast_retry_plugin_not_ready, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Update the list of plugins
        PluginsLookup.getDefault().requestUpdatePlugins();

        // If we were configuring a provider, update it
        if (mConfiguringProvider != null) {
            IMusicProvider provider = mConfiguringProvider.getBinder();
            if (provider != null) {
                try {
                    if (provider.isSetup()) {
                        provider.login();
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Remote exception while trying to login configured provider", e);
                }
            } else {
                Log.w(TAG, "Configured provider is null!");
            }

            mConfiguringProvider = null;
        }

        if (mStep3Adapter != null) {
            mStep3Adapter.notifyDataSetChanged();
        }
    }
}
