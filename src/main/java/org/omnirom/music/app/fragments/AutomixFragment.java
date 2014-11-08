/*
 * Copyright (C) 2014 Fastboot Mobile, LLC.
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

package org.omnirom.music.app.fragments;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;

import com.getbase.floatingactionbutton.FloatingActionsMenu;

import org.omnirom.music.api.echonest.AutoMixManager;
import org.omnirom.music.app.AutomixCreateActivity;
import org.omnirom.music.app.MainActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.adapters.BucketAdapter;
import org.omnirom.music.framework.PlaybackProxy;
import org.omnirom.music.model.Song;
import org.omnirom.music.service.BasePlaybackCallback;

/**
 * Fragment showing the AutoMix buckets
 */
public class AutomixFragment extends Fragment {

    private static final String TAG = "AutomixFragment";

    private BucketAdapter mAdapter;
    private FloatingActionsMenu mFabCreateMenu;
    private ImageButton mFabCreateDynamic;
    private ImageButton mFabCreateStatic;
    private ProgressBar mProgressToHide;
    private AutoMixManager mAutoMixManager = AutoMixManager.getDefault();
    private BasePlaybackCallback mPlaybackCallback = new BasePlaybackCallback() {
        @Override
        public void onSongStarted(boolean buffering, Song s) throws RemoteException {
            if (mProgressToHide != null) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mProgressToHide.setVisibility(View.INVISIBLE);
                        mProgressToHide = null;
                    }
                });
            }
            updateFabHeight();
        }
    };

    /**
     * Default empty constructor
     */
    public AutomixFragment() {
    }

    /**
     * Creates a new instance of this fragment
     * @return A new instance of {@link org.omnirom.music.app.fragments.AutomixFragment}
     */
    public static AutomixFragment newInstance() {
        return new AutomixFragment();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_automix, container, false);

        // Setup create FABs
        mFabCreateMenu = (FloatingActionsMenu) rootView.findViewById(R.id.fabCreateMenu);

        mFabCreateDynamic = (ImageButton) rootView.findViewById(R.id.fabCreateDynamic);
        mFabCreateDynamic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getActivity(), AutomixCreateActivity.class);
                intent.putExtra(AutomixCreateActivity.EXTRA_MODE, AutomixCreateActivity.MODE_DYNAMIC);
                startActivity(intent);
            }
        });

        mFabCreateStatic = (ImageButton) rootView.findViewById(R.id.fabCreateStatic);
        mFabCreateStatic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getActivity(), AutomixCreateActivity.class);
                intent.putExtra(AutomixCreateActivity.EXTRA_MODE, AutomixCreateActivity.MODE_STATIC);
                startActivity(intent);
            }
        });

        final ListView lvBuckets = (ListView) rootView.findViewById(R.id.lvBuckets);
        mAdapter = new BucketAdapter();
        lvBuckets.setAdapter(mAdapter);

        lvBuckets.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, final int i, long l) {
                BucketAdapter.ViewHolder holder = (BucketAdapter.ViewHolder) view.getTag();
                holder.pbBucketSpinner.setVisibility(View.VISIBLE);

                if (mProgressToHide != null) {
                    mProgressToHide.setVisibility(View.INVISIBLE);
                }

                mProgressToHide = holder.pbBucketSpinner;

                new Thread() {
                    public void run() {
                        AutoMixManager.getDefault().startPlay(mAdapter.getItem(i));
                    }
                }.start();
            }
        });

        return rootView;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        MainActivity mainActivity = (MainActivity) activity;
        mainActivity.onSectionAttached(MainActivity.SECTION_AUTOMIX);
        mainActivity.setContentShadowTop(0);

        // Register for playback events
        PlaybackProxy.addCallback(mPlaybackCallback);
    }

    @Override
    public void onDetach() {
        super.onDetach();

        // Unregister playback events
        PlaybackProxy.removeCallback(mPlaybackCallback);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onResume() {
        super.onResume();
        updateBuckets();
    }

    /**
     * Updates the list of buckets
     */
    private void updateBuckets() {
        mAdapter.setBuckets(mAutoMixManager.getBuckets());
        mAdapter.notifyDataSetChanged();

        updateFabHeight();
    }

    private void updateFabHeight() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Translate the FAB position to be above the playbar
                final MainActivity mainActivity = (MainActivity) getActivity();
                if (mainActivity.isPlayBarVisible()) {
                    mFabCreateMenu.animate()
                            .translationY(-getResources().getDimensionPixelSize(R.dimen.playing_bar_height))
                            .start();
                } else {
                    mFabCreateMenu.animate()
                            .translationY(0)
                            .start();
                }
            }
        });
    }
}
