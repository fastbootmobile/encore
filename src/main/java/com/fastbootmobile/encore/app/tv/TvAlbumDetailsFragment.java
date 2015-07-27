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

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.app.DetailsFragment;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.support.v17.leanback.widget.DetailsOverviewRow;
import android.support.v17.leanback.widget.DetailsOverviewRowPresenter;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnActionClickedListener;
import android.support.v17.leanback.widget.SparseArrayObjectAdapter;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;

import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.art.AlbumArtHelper;
import com.fastbootmobile.encore.art.RecyclingBitmapDrawable;
import com.fastbootmobile.encore.framework.PlaybackProxy;
import com.fastbootmobile.encore.framework.PluginsLookup;
import com.fastbootmobile.encore.model.Album;
import com.fastbootmobile.encore.model.Artist;
import com.fastbootmobile.encore.model.BoundEntity;
import com.fastbootmobile.encore.model.Playlist;
import com.fastbootmobile.encore.model.SearchResult;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.ILocalCallback;
import com.fastbootmobile.encore.providers.IMusicProvider;
import com.fastbootmobile.encore.providers.ProviderAggregator;
import com.fastbootmobile.encore.providers.ProviderConnection;
import com.fastbootmobile.encore.service.BasePlaybackCallback;
import com.fastbootmobile.encore.utils.Utils;

import java.util.Iterator;
import java.util.List;

public class TvAlbumDetailsFragment extends DetailsFragment {
    private static final String TAG = "AlbumDetailsFragment";

    private static final int DETAIL_THUMB_WIDTH = 274;
    private static final int DETAIL_THUMB_HEIGHT = 274;

    private static final int ACTION_PLAY = 1;
    private static final int ACTION_QUEUE = 2;
    private static final int ACTION_GO_TO_ARTIST = 3;

    private static final int MSG_UPDATE_ADAPTER = 1;

    private Album mAlbum;
    private ArrayObjectAdapter mAdapter;
    private ClassPresenterSelector mPresenterSelector;
    private SongRowPresenter mSongRowPresenter;

    private BackgroundManager mBackgroundManager;
    private Drawable mDefaultBackground;
    private int mBackgroundColor;
    private DisplayMetrics mMetrics;

    private Action mActionPlay;
    private Action mActionQueue;
    private Action mActionGoToArtist;
    private boolean mIsPlaying;

    private Handler mHandler;
    private BasePlaybackCallback mPlaybackCallback;
    private View.OnClickListener mSongClickListener;
    private AlbumLocalCallback mCallback = new AlbumLocalCallback();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_UPDATE_ADAPTER:
                        updateAdapter();
                        break;
                }
            }
        };

        mPlaybackCallback = new BasePlaybackCallback() {
            @Override
            public void onSongStarted(boolean buffering, Song s) throws RemoteException {
                mSongRowPresenter.setCurrentSong(PlaybackProxy.getCurrentTrack());
                mHandler.sendEmptyMessage(MSG_UPDATE_ADAPTER);
            }
        };

        mSongClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SongRow row = (SongRow) v.getTag();
                PlaybackProxy.clearQueue();
                PlaybackProxy.queueAlbum(mAlbum, false);
                PlaybackProxy.playAtIndex(row.getPosition());
                mIsPlaying = true;
                mActionPlay.setLabel1(getString(R.string.pause));
                updateAdapter();
            }
        };

        mActionPlay = new Action(ACTION_PLAY, getString(R.string.play));
        mActionQueue = new Action(ACTION_QUEUE, getString(R.string.tv_action_queue));
        mActionGoToArtist = new Action(ACTION_GO_TO_ARTIST, getString(R.string.menu_open_artist_page));

        prepareBackgroundManager();

        mAlbum = getActivity().getIntent().getParcelableExtra(TvAlbumDetailsActivity.EXTRA_ALBUM);
        mAlbum = ProviderAggregator.getDefault().retrieveAlbum(mAlbum.getRef(), mAlbum.getProvider());
        mBackgroundColor = getActivity().getIntent().getIntExtra(TvAlbumDetailsActivity.EXTRA_COLOR, getResources().getColor(R.color.primary));
        if (mAlbum != null) {
            setupAdapter();
            setupDetailsOverviewRow();
            setupDetailsOverviewRowPresenter();
            setupTrackListRow();
            setupTrackListRowPresenter();
            setupFragmentBackground();
        } else {
            Intent intent = new Intent(getActivity(), TvActivity.class);
            startActivity(intent);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        PlaybackProxy.addCallback(mPlaybackCallback);
        ProviderAggregator.getDefault().addUpdateCallback(mCallback);
        mSongRowPresenter.setCurrentSong(PlaybackProxy.getCurrentTrack());
        updateAdapter();
    }

    @Override
    public void onPause() {
        super.onPause();
        PlaybackProxy.removeCallback(mPlaybackCallback);
        ProviderAggregator.getDefault().removeUpdateCallback(mCallback);
    }

    private void updateAdapter() {
        mAdapter.notifyArrayItemRangeChanged(0, mAdapter.size());
    }

    private void prepareBackgroundManager() {
        mBackgroundManager = BackgroundManager.getInstance(getActivity());
        mBackgroundManager.attach(getActivity().getWindow());
        mDefaultBackground = getResources().getDrawable(R.drawable.bg_welcome_top);
        mMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
    }

    private void setupAdapter() {
        mPresenterSelector = new ClassPresenterSelector();
        mAdapter = new ArrayObjectAdapter(mPresenterSelector);
        setAdapter(mAdapter);

        ProviderConnection connection = PluginsLookup.getDefault().getProvider(mAlbum.getProvider());
        if (connection != null) {
            IMusicProvider binder = connection.getBinder();
            if (binder != null) {
                try {
                    binder.fetchAlbumTracks(mAlbum.getRef());
                } catch (RemoteException e) {
                    Log.e(TAG, "Cannot request album tracks", e);
                }
            }
        }
    }

    private void setupDetailsOverviewRow() {
        final Resources res = getResources();
        final DetailsOverviewRow row = new DetailsOverviewRow(mAlbum);
        row.setImageDrawable(res.getDrawable(R.drawable.album_placeholder));

        SparseArrayObjectAdapter actionsAdapter = new SparseArrayObjectAdapter();
        actionsAdapter.set(ACTION_PLAY, mActionPlay);
        actionsAdapter.set(ACTION_QUEUE, mActionQueue);
        actionsAdapter.set(ACTION_GO_TO_ARTIST, mActionGoToArtist);
        row.setActionsAdapter(actionsAdapter);

        mAdapter.add(row);

        AlbumArtHelper.retrieveAlbumArt(getResources(), new AlbumArtHelper.AlbumArtListener() {
            @Override
            public void onArtLoaded(RecyclingBitmapDrawable output, BoundEntity request) {
                if (output != null) {
                    row.setImageDrawable(output);
                    updateAdapter();
                }
            }
        }, mAlbum, DETAIL_THUMB_WIDTH, false);
    }

    private void setupDetailsOverviewRowPresenter() {
        // Set detail background and style.
        DetailsOverviewRowPresenter detailsPresenter =
                new DetailsOverviewRowPresenter(new AlbumDetailsPresenter());
        detailsPresenter.setBackgroundColor(mBackgroundColor);
        detailsPresenter.setStyleLarge(false);

        // Hook up transition element.
        detailsPresenter.setSharedElementEnterTransition(getActivity(),
                TvAlbumDetailsActivity.SHARED_ELEMENT_NAME);

        detailsPresenter.setOnActionClickedListener(new OnActionClickedListener() {
            @Override
            public void onActionClicked(Action action) {
                if (action.getId() == ACTION_PLAY) {
                    if (mIsPlaying) {
                        PlaybackProxy.pause();
                        mIsPlaying = false;
                    } else {
                        PlaybackProxy.playAlbum(mAlbum);
                        mIsPlaying = true;
                    }

                    mActionPlay.setLabel1(getString(mIsPlaying ? R.string.pause : R.string.play));
                    updateAdapter();
                } else if (action.getId() == ACTION_QUEUE) {
                    PlaybackProxy.queueAlbum(mAlbum, false);
                } else if (action.getId() == ACTION_GO_TO_ARTIST) {
                    String artistRef = Utils.getMainArtist(mAlbum);
                    if (artistRef != null) {
                        Artist artist = ProviderAggregator.getDefault().retrieveArtist(artistRef, mAlbum.getProvider());
                        final int color = getResources().getColor(R.color.primary);

                        Intent intent = new Intent(getActivity(), TvArtistDetailsActivity.class);
                        intent.putExtra(TvArtistDetailsActivity.EXTRA_ARTIST, artist);
                        intent.putExtra(TvArtistDetailsActivity.EXTRA_COLOR, color);

                        startActivity(intent);
                    }
                }
            }
        });
        mPresenterSelector.addClassPresenter(DetailsOverviewRow.class, detailsPresenter);
    }

    private void setupTrackListRow() {
        mAdapter.removeItems(1, mAdapter.size() - 1);
        Iterator<String> it = mAlbum.songs();

        int index = 0;
        while (it.hasNext()) {
            String trackRef = it.next();
            Song song = ProviderAggregator.getDefault().retrieveSong(trackRef, mAlbum.getProvider());
            if (song != null) {
                mAdapter.add(new SongRow(song, index++));
            }
        }
    }

    private void setupTrackListRowPresenter() {
        mSongRowPresenter = new SongRowPresenter(mSongClickListener);
        mPresenterSelector.addClassPresenter(ListRow.class, new ListRowPresenter());
        mPresenterSelector.addClassPresenter(SongRow.class, mSongRowPresenter);
    }

    private void setupFragmentBackground() {
        String artistRef = Utils.getMainArtist(mAlbum);

        if (artistRef != null) {
            Artist artist = ProviderAggregator.getDefault().retrieveArtist(artistRef, mAlbum.getProvider());

            AlbumArtHelper.retrieveAlbumArt(getResources(), new AlbumArtHelper.AlbumArtListener() {
                @Override
                public void onArtLoaded(RecyclingBitmapDrawable output, BoundEntity request) {
                    if (output != null) {
                        mBackgroundManager.setDrawable(output);
                    }
                }
            }, artist, mMetrics.widthPixels, false);
        }
    }

    private class AlbumLocalCallback implements ILocalCallback {
        @Override
        public void onSongUpdate(List<Song> s) {
            mHandler.post(new Runnable() {
                public void run() {
                    setupTrackListRow();
                }
            });
        }

        @Override
        public void onAlbumUpdate(List<Album> a) {
            mHandler.post(new Runnable() {
                public void run() {
                    setupTrackListRow();
                }
            });
        }

        @Override
        public void onPlaylistUpdate(List<Playlist> p) {

        }

        @Override
        public void onPlaylistRemoved(String ref) {

        }

        @Override
        public void onArtistUpdate(List<Artist> a) {

        }

        @Override
        public void onProviderConnected(IMusicProvider provider) {

        }

        @Override
        public void onSearchResult(List<SearchResult> searchResult) {

        }
    }
}
