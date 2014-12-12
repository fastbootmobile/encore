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
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import org.omnirom.music.app.MainActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.adapters.HistoryAdapter;
import org.omnirom.music.framework.ListenLogger;
import org.omnirom.music.framework.PlaybackProxy;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;

/**
 * A fragment containing a simple view for history.
 */
public class HistoryFragment extends Fragment {
    private HistoryAdapter mAdapter;

    public static HistoryFragment newInstance() {
        return new HistoryFragment();
    }

    public HistoryFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ListView rootView = (ListView) inflater.inflate(R.layout.fragment_history, container, false);
        mAdapter = new HistoryAdapter(container.getContext());
        rootView.setAdapter(mAdapter);
        rootView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ListenLogger.LogEntry entry = mAdapter.getItem(position);
                Song song = ProviderAggregator.getDefault().retrieveSong(entry.getReference(), entry.getIdentifier());
                PlaybackProxy.playSong(song);
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
        mainActivity.onSectionAttached(MainActivity.SECTION_HISTORY);
        mainActivity.setContentShadowTop(0);
    }
}