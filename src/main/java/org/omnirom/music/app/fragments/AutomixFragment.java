package org.omnirom.music.app.fragments;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListView;

import org.omnirom.music.api.echonest.AutoMixManager;
import org.omnirom.music.api.echonest.EchoNest;
import org.omnirom.music.app.AutomixCreateActivity;
import org.omnirom.music.app.MainActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.adapters.BucketAdapter;

/**
 * Created by Guigui on 11/08/2014.
 */
public class AutomixFragment extends Fragment {

    private static final String TAG = "AutomixFragment";

    private ListView mListView;
    private BucketAdapter mAdapter;
    private AutoMixManager mAutoMixManager = AutoMixManager.getDefault();

    public AutomixFragment() {

    }

    public static AutomixFragment newInstance() {
        return new AutomixFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_automix, container, false);

        ImageButton fabCreate = (ImageButton) rootView.findViewById(R.id.fabCreate);
        fabCreate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(getActivity(), AutomixCreateActivity.class));
            }
        });

        mListView = (ListView) rootView.findViewById(R.id.lvBuckets);
        mAdapter = new BucketAdapter();
        mListView.setAdapter(mAdapter);

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, final int i, long l) {
                new Thread() {
                    public void run() {
                        mAutoMixManager.startPlay(mAdapter.getItem(i));
                    }
                }.start();
            }
        });

        return rootView;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        MainActivity mainActivity = (MainActivity) activity;
        mainActivity.onSectionAttached(MainActivity.SECTION_AUTOMIX);
        mainActivity.setContentShadowTop(0);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateBuckets();
    }

    private void updateBuckets() {
        mAdapter.setBuckets(mAutoMixManager.getBuckets());
        mAdapter.notifyDataSetChanged();
    }
}
