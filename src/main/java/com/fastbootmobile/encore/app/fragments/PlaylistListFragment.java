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
import android.app.ActivityOptions;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;

import com.fastbootmobile.encore.app.MainActivity;
import com.fastbootmobile.encore.app.PlaylistActivity;
import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.app.adapters.PlaylistGridAdapter;
import com.fastbootmobile.encore.app.adapters.PlaylistListAdapter;
import com.fastbootmobile.encore.app.ui.MaterialTransitionDrawable;
import com.fastbootmobile.encore.model.Album;
import com.fastbootmobile.encore.model.Artist;
import com.fastbootmobile.encore.model.Playlist;
import com.fastbootmobile.encore.model.SearchResult;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.ILocalCallback;
import com.fastbootmobile.encore.providers.IMusicProvider;
import com.fastbootmobile.encore.providers.ProviderAggregator;
import com.h6ah4i.android.widget.advrecyclerview.animator.GeneralItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.animator.RefactoredDefaultItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.decoration.SimpleListDividerDecorator;
import com.h6ah4i.android.widget.advrecyclerview.draggable.RecyclerViewDragDropManager;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple {@link android.support.v4.app.Fragment} subclass, showing a list of playlists.
 * Use the {@link PlaylistListFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class PlaylistListFragment extends Fragment implements ILocalCallback {
    private static final String TAG = "PlaylistListFragment";

    private PlaylistListAdapter mAdapter;
    private PlaylistGridAdapter mGridAdapter;
    private LinearLayoutManager mLayoutManager;
    private RecyclerView mRecyclerView;
    private RecyclerViewDragDropManager mRecyclerViewDragDropManager;
    private RecyclerView.Adapter mWrappedAdapter;
    private Handler mHandler;
    private boolean mIsStandalone;
    private final ArrayList<Playlist> mPlaylistsUpdated = new ArrayList<>();

    private Runnable mUpdateListRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (mPlaylistsUpdated) {
                if (mAdapter != null) {
                    mAdapter.addAllUnique(mPlaylistsUpdated);
                }
                if (mGridAdapter != null) {
                    mGridAdapter.addAllUnique(mPlaylistsUpdated);
                }

                mPlaylistsUpdated.clear();
            }

            if (mAdapter != null && getActivity() != null) {
                try {
                    mAdapter.sortList(getActivity().getApplicationContext());
                } catch (JSONException e) {
                    Log.e(TAG, "Unable to sort playlists list");
                }
                mAdapter.notifyDataSetChanged();
            }
            if (mGridAdapter != null) {
                mGridAdapter.notifyDataSetChanged();
            }
        }
    };

    private Runnable mDataChangedRunnable = new Runnable() {
        @Override
        public void run() {
            if (mGridAdapter != null) {
                mGridAdapter.notifyDataSetChanged();
            }
            if (mAdapter != null) {
                mAdapter.notifyDataSetChanged();
            }
        }
    };

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param isStandalone Whether this fragment is embedded in My Songs or is the Playlists section
     * @return A new instance of fragment PlaylistListFragment.
     */
    public static PlaylistListFragment newInstance(boolean isStandalone) {
        PlaylistListFragment fragment = new PlaylistListFragment();
        fragment.setIsStandalone(isStandalone);
        return fragment;
    }

    /**
     * Default constructor. Use {@link #newInstance(boolean)} to create an instance of this fragment
     */
    public PlaylistListFragment() {
        mAdapter = new PlaylistListAdapter();
    }

    /**
     * Sets whether or not the fragment is displayed standalone (root of an activity's contents)
     * or within another container (e.g. a viewpager contents).
     * @param isStandalone Whether this fragment is embedded in My Songs or is the Playlists section
     */
    public void setIsStandalone(boolean isStandalone) {
        mIsStandalone = isStandalone;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_playlist, container, false);
        if (root instanceof GridView) {
            // We're in landscape, and thus have a GridView
            mGridAdapter = new PlaylistGridAdapter(root.getContext().getApplicationContext());
            mAdapter = null;
        } else {
            // We're in portrait with the recycler view
            mAdapter = new PlaylistListAdapter();
            mGridAdapter = null;
        }

        return root;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // If mAdapter isn't null, we're in portrait with the draggable list view.
        if (mAdapter != null) {
            mRecyclerView = (RecyclerView) view.findViewById(R.id.rvPlaylists);
            mLayoutManager = new LinearLayoutManager(getActivity());

            // drag & drop manager
            mRecyclerViewDragDropManager = new RecyclerViewDragDropManager();

            //adapter
            mWrappedAdapter = mRecyclerViewDragDropManager.createWrappedAdapter(mAdapter);      // wrap for dragging

            final GeneralItemAnimator animator = new RefactoredDefaultItemAnimator();

            mRecyclerView.setLayoutManager(mLayoutManager);
            mRecyclerView.setAdapter(mWrappedAdapter);  // requires *wrapped* adapter
            mRecyclerView.setItemAnimator(animator);

            // additional decorations
            mRecyclerView.addItemDecoration(new SimpleListDividerDecorator(getResources().getDrawable(R.drawable.list_divider), true));

            mRecyclerViewDragDropManager.attachRecyclerView(mRecyclerView);

            if (!mIsStandalone) {
                mRecyclerView.setPadding(0, 0, 0, 0);
            }
        } else {
            // We're in landscape with the grid view
            GridView root = (GridView) view.findViewById(R.id.gvPlaylists);
            root.setAdapter(mGridAdapter);

            if (!mIsStandalone) {
                root.setPadding(0, 0, 0, 0);
            }

            // Setup the click listener
            root.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    MainActivity act = (MainActivity) getActivity();
                    PlaylistGridAdapter.ViewHolder tag = (PlaylistGridAdapter.ViewHolder) view.getTag();
                    Intent intent = PlaylistActivity.craftIntent(act, mGridAdapter.getItem(position),
                            ((MaterialTransitionDrawable) tag.ivCover.getDrawable()).getFinalDrawable().getBitmap());

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        ActivityOptions opt = ActivityOptions.makeSceneTransitionAnimation(getActivity(),
                                tag.ivCover, "itemImage");
                        act.startActivity(intent, opt.toBundle());
                    } else {
                        act.startActivity(intent);
                    }
                }
            });
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (mIsStandalone) {
            MainActivity mainActivity = (MainActivity) activity;
            mainActivity.onSectionAttached(MainActivity.SECTION_PLAYLISTS);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ProviderAggregator.getDefault().addUpdateCallback(this);
        updatePlaylists();
    }

    @Override
    public void onPause() {
        super.onPause();
        ProviderAggregator.getDefault().removeUpdateCallback(this);
    }

    private void updatePlaylists() {
        new GetPlaylistsTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void onSongUpdate(List<Song> s) {
    }

    @Override
    public void onAlbumUpdate(List<Album> a) {
    }

    @Override
    public void onPlaylistUpdate(final List<Playlist> p) {
        if (p != null) {
            synchronized (mPlaylistsUpdated) {
                mPlaylistsUpdated.addAll(p);
            }

            mHandler.removeCallbacks(mUpdateListRunnable);
            mHandler.post(mUpdateListRunnable);
        }
    }

    @Override
    public void onPlaylistRemoved(String ref) {
        Log.d(TAG, "Playlist removed: " + ref);
        if (ref != null) {
            synchronized (mPlaylistsUpdated) {
                for (Playlist playlist : mPlaylistsUpdated) {
                    if (playlist.getRef().equals(ref)) {
                        mPlaylistsUpdated.remove(playlist);
                        break;
                    }
                }
            }

            mAdapter.remove(ref);
            mHandler.removeCallbacks(mDataChangedRunnable);
            mHandler.post(mDataChangedRunnable);
        }
    }

    @Override
    public void onArtistUpdate(List<Artist> a) {
    }

    @Override
    public void onProviderConnected(final IMusicProvider provider) {
        new Thread() {
            public void run() {
                // HACK: Wait a bit for the provider to be ready
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignore) { }

                try {
                    onPlaylistUpdate(provider.getPlaylists());
                } catch (RemoteException e) {
                    Log.e(TAG, "Cannot get playlists");
                }
            }
        }.start();
    }

    @Override
    public void onSearchResult(List<SearchResult> searchResult) {
    }

    private class GetPlaylistsTask extends AsyncTask<Void, Void, List<Playlist>> {
        @Override
        protected List<Playlist> doInBackground(Void... params) {
            Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            return ProviderAggregator.getDefault().getAllPlaylists();
        }

        @Override
        protected void onPostExecute(List<Playlist> playlists) {
            if (mAdapter != null) {
                mAdapter.clear();
                mAdapter.addAllUnique(playlists);
                try {
                    mAdapter.sortList(getActivity().getApplicationContext());
                } catch (JSONException e) {
                    Log.e(TAG, "Unable to sort playlists list");
                }
                mAdapter.notifyDataSetChanged();
            }

            if (mGridAdapter != null) {
                mGridAdapter.clear();
                mGridAdapter.addAllUnique(playlists);
                mGridAdapter.notifyDataSetChanged();
            }
        }
    }
}
