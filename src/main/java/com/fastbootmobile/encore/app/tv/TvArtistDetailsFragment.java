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
import android.os.RemoteException;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.app.DetailsFragment;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.support.v17.leanback.widget.DetailsOverviewRow;
import android.support.v17.leanback.widget.DetailsOverviewRowPresenter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnActionClickedListener;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v17.leanback.widget.SparseArrayObjectAdapter;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v7.graphics.Palette;
import android.util.DisplayMetrics;
import android.util.Log;

import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.art.AlbumArtHelper;
import com.fastbootmobile.encore.art.AlbumArtTask;
import com.fastbootmobile.encore.art.RecyclingBitmapDrawable;
import com.fastbootmobile.encore.framework.PlaybackProxy;
import com.fastbootmobile.encore.framework.PluginsLookup;
import com.fastbootmobile.encore.framework.Suggestor;
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

import java.util.List;

public class TvArtistDetailsFragment extends DetailsFragment {
    private static final String TAG = "ArtistDetailsFragment";

    private static final int DETAIL_THUMB_WIDTH = 274;
    private static final int DETAIL_THUMB_HEIGHT = 274;

    private static final int ACTION_START_RADIO = 1;

    private Artist mArtist;
    private ArrayObjectAdapter mAdapter;
    private ArrayObjectAdapter mAlbumsAdapter;
    private ClassPresenterSelector mPresenterSelector;

    private BackgroundManager mBackgroundManager;
    private Drawable mDefaultBackground;
    private int mBackgroundColor;
    private DisplayMetrics mMetrics;

    private Action mActionStartRadio;
    private boolean mIsPlaying;
    private AlbumArtTask mArtTask;
    private ArtistLocalCallback mCallback = new ArtistLocalCallback();
    private Handler mHandler = new Handler();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActionStartRadio = new Action(ACTION_START_RADIO, getString(R.string.start_radio));

        prepareBackgroundManager();

        mArtist = getActivity().getIntent().getParcelableExtra(TvArtistDetailsActivity.EXTRA_ARTIST);
        mArtist = ProviderAggregator.getDefault().retrieveArtist(mArtist.getRef(), mArtist.getProvider());
        mBackgroundColor = getActivity().getIntent().getIntExtra(TvArtistDetailsActivity.EXTRA_COLOR, getResources().getColor(R.color.primary));
        if (mArtist != null) {
            setupAdapter();
            setupDetailsOverviewRow();
            setupDetailsOverviewRowPresenter();
            setupAlbumListRow();
            setupAlbumListRowPresenter();
            setupFragmentBackground();
            setOnItemViewClickedListener(new ItemViewClickedListener());
        } else {
            Intent intent = new Intent(getActivity(), TvActivity.class);
            startActivity(intent);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ProviderAggregator.getDefault().addUpdateCallback(mCallback);
    }

    @Override
    public void onPause() {
        ProviderAggregator.getDefault().removeUpdateCallback(mCallback);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mArtTask != null) {
            mArtTask.cancel(true);
        }
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

        ProviderConnection connection = PluginsLookup.getDefault().getProvider(mArtist.getProvider());
        if (connection != null) {
            IMusicProvider binder = connection.getBinder();
            if (binder != null) {
                try {
                    binder.fetchArtistAlbums(mArtist.getRef());
                } catch (RemoteException e) {
                    Log.e(TAG, "Cannot request album tracks", e);
                }
            }
        }
    }

    private void setupDetailsOverviewRow() {
        final Resources res = getResources();
        final DetailsOverviewRow row = new DetailsOverviewRow(mArtist);
        row.setImageDrawable(res.getDrawable(R.drawable.album_placeholder));

        SparseArrayObjectAdapter actionsAdapter = new SparseArrayObjectAdapter();
        actionsAdapter.set(ACTION_START_RADIO, mActionStartRadio);
        row.setActionsAdapter(actionsAdapter);

        mAdapter.add(row);

        mArtTask = AlbumArtHelper.retrieveAlbumArt(getResources(), new AlbumArtHelper.AlbumArtListener() {
            @Override
            public void onArtLoaded(RecyclingBitmapDrawable output, BoundEntity request) {
                if (output != null) {
                    row.setImageDrawable(output);
                    updateAdapter();
                }
            }
        }, mArtist, DETAIL_THUMB_WIDTH, false);
    }

    private void setupDetailsOverviewRowPresenter() {
        // Set detail background and style.
        DetailsOverviewRowPresenter detailsPresenter =
                new DetailsOverviewRowPresenter(new ArtistDetailsPresenter());
        detailsPresenter.setBackgroundColor(mBackgroundColor);
        detailsPresenter.setStyleLarge(false);

        // Hook up transition element.
        detailsPresenter.setSharedElementEnterTransition(getActivity(),
                TvArtistDetailsActivity.SHARED_ELEMENT_NAME);

        detailsPresenter.setOnActionClickedListener(new OnActionClickedListener() {
            @Override
            public void onActionClicked(Action action) {
                if (action.getId() == ACTION_START_RADIO) {
                    if (mIsPlaying) {
                        PlaybackProxy.pause();
                        mIsPlaying = false;
                    } else {
                        List<Song> radio = Suggestor.getInstance().buildArtistRadio(mArtist);
                        PlaybackProxy.clearQueue();
                        for (Song song : radio) {
                            PlaybackProxy.queueSong(song, false);
                        }
                        PlaybackProxy.playAtIndex(0);
                        mIsPlaying = true;
                    }
                }
            }
        });
        mPresenterSelector.addClassPresenter(DetailsOverviewRow.class, detailsPresenter);
    }

    private void setupAlbumListRow() {
        List<String> albums = mArtist.getAlbums();

        if (mAlbumsAdapter == null) {
            mAlbumsAdapter = new ArrayObjectAdapter(new CardPresenter());
            mAdapter.add(new ListRow(new HeaderItem(getString(R.string.albums)), mAlbumsAdapter));
        } else {
            mAlbumsAdapter.clear();
        }

        for (String albumRef : albums) {
            Album album = ProviderAggregator.getDefault().retrieveAlbum(albumRef, mArtist.getProvider());
            if (album != null) {
                mAlbumsAdapter.add(album);
            }
        }


    }

    private void setupAlbumListRowPresenter() {
        mPresenterSelector.addClassPresenter(ListRow.class, new ListRowPresenter());
    }

    private void setupFragmentBackground() {
        mArtTask = AlbumArtHelper.retrieveAlbumArt(getResources(), new AlbumArtHelper.AlbumArtListener() {
            @Override
            public void onArtLoaded(RecyclingBitmapDrawable output, BoundEntity request) {
                if (output != null) {
                    mBackgroundManager.setDrawable(output);
                }
            }
        }, mArtist, mMetrics.widthPixels, false);
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {

            if (item instanceof Album) {
                Album album = (Album) item;

                int color = getResources().getColor(R.color.primary);
                if (itemViewHolder.view.getTag() != null && itemViewHolder.view.getTag() instanceof Palette) {
                    color = ((Palette) itemViewHolder.view.getTag()).getDarkVibrantColor(color);
                }

                Intent intent = new Intent(getActivity(), TvAlbumDetailsActivity.class);
                intent.putExtra(TvAlbumDetailsActivity.EXTRA_ALBUM, album);
                intent.putExtra(TvAlbumDetailsActivity.EXTRA_COLOR, color);

                Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        getActivity(),
                        ((ImageCardView) itemViewHolder.view).getMainImageView(),
                        TvAlbumDetailsActivity.SHARED_ELEMENT_NAME).toBundle();
                getActivity().startActivity(intent, bundle);
            }
        }
    }


    private class ArtistLocalCallback implements ILocalCallback {
        @Override
        public void onSongUpdate(List<Song> s) {
            mHandler.post(new Runnable() {
                public void run() {
                    setupAlbumListRow();
                }
            });

        }

        @Override
        public void onAlbumUpdate(List<Album> a) {
            mHandler.post(new Runnable() {
                public void run() {
                    setupAlbumListRow();
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
            mHandler.post(new Runnable() {
                public void run() {
                    setupAlbumListRow();
                }
            });
        }

        @Override
        public void onProviderConnected(IMusicProvider provider) {

        }

        @Override
        public void onSearchResult(List<SearchResult> searchResult) {

        }
    }
}
