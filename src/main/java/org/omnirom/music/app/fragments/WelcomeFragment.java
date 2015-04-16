package org.omnirom.music.app.fragments;

import android.content.Intent;
import android.os.RemoteException;
import android.support.annotation.LayoutRes;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import org.omnirom.music.app.MainActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.WelcomeActivity;
import org.omnirom.music.app.adapters.ProvidersAdapter;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.ProviderConnection;

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

    public WelcomeFragment() {
    }

    public void setStep(int step) {
        mStep = step;
    }

    public void setLayoutId(@LayoutRes int id) {
        mLayoutId = id;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(mLayoutId, container, false);
        root.findViewById(R.id.btnNext).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mStep == LAST_STEP) {
                    Intent intent = new Intent(getActivity(), MainActivity.class);
                    startActivity(intent);
                    getActivity().finish();
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
        if (mStep == 3) {
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

            ProvidersAdapter adapter = new ProvidersAdapter(provs);
            adapter.setWhite(true);
            lv.setAdapter(adapter);

            lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    ProviderConnection connection = provs.get(position);
                    Intent intent = new Intent();
                    intent.setPackage(connection.getPackage());
                    intent.setClassName(connection.getPackage(), connection.getConfigurationActivity());
                    mConfiguringProvider = connection;
                    startActivity(intent);
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
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
    }
}
