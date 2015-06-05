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

package com.fastbootmobile.encore.app.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.fastbootmobile.encore.api.echonest.AutoMixManager;
import com.fastbootmobile.encore.app.AutomixCreateActivity;
import com.fastbootmobile.encore.app.MainActivity;
import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.app.adapters.BucketAdapter;
import com.fastbootmobile.encore.framework.PlaybackProxy;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.service.BasePlaybackCallback;

/**
 * Fragment showing the AutoMix buckets
 */
public class AutomixFragment extends Fragment {

    private static final String TAG = "AutomixFragment";

    private BucketAdapter mAdapter;
    private ProgressBar mProgressToHide;
    private AutoMixManager mAutoMixManager = AutoMixManager.getDefault();
    private TextView mNoBucketTextView;
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
        }
    };

    /**
     * Default empty constructor
     */
    public AutomixFragment() {
    }

    /**
     * Creates a new instance of this fragment
     * @return A new instance of {@link com.fastbootmobile.encore.app.fragments.AutomixFragment}
     */
    public static AutomixFragment newInstance() {
        return new AutomixFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_automix, container, false);
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

        mNoBucketTextView = (TextView) rootView.findViewById(R.id.txtNoBucket);

        return rootView;
    }

    private void onPressCreate() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.automix_create_dialog_title)
                .setItems(new CharSequence[]{getString(R.string.automix_type_static),
                        getString(R.string.automix_type_dynamic)}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(getActivity(), AutomixCreateActivity.class);
                        if (which == 0) {
                            // Static
                            intent.putExtra(AutomixCreateActivity.EXTRA_MODE, AutomixCreateActivity.MODE_STATIC);

                        } else {
                            // Dynamic
                            intent.putExtra(AutomixCreateActivity.EXTRA_MODE, AutomixCreateActivity.MODE_DYNAMIC);
                        }
                        startActivity(intent);
                    }
                });
        builder.show();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        MainActivity mainActivity = (MainActivity) activity;
        mainActivity.onSectionAttached(MainActivity.SECTION_AUTOMIX);

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

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.automix_list, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_create) {
            onPressCreate();
            return true;
        }

        return false;
    }

    /**
     * Updates the list of buckets
     */
    private void updateBuckets() {
        mAdapter.setBuckets(mAutoMixManager.getBuckets());
        mAdapter.notifyDataSetChanged();

        if (mAdapter.getCount() == 0) {
            mNoBucketTextView.setVisibility(View.VISIBLE);
        } else {
            mNoBucketTextView.setVisibility(View.GONE);
        }
    }
}
