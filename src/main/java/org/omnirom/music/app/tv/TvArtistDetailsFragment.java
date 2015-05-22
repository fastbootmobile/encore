package org.omnirom.music.app.tv;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
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
import android.view.View;

import org.omnirom.music.app.R;
import org.omnirom.music.art.AlbumArtHelper;
import org.omnirom.music.art.AlbumArtTask;
import org.omnirom.music.art.RecyclingBitmapDrawable;
import org.omnirom.music.framework.PlaybackProxy;
import org.omnirom.music.framework.Suggestor;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.BoundEntity;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.service.BasePlaybackCallback;
import org.omnirom.music.utils.Utils;

import java.util.Iterator;
import java.util.List;

public class TvArtistDetailsFragment extends DetailsFragment {
    private static final String TAG = "ArtistDetailsFragment";

    private static final int DETAIL_THUMB_WIDTH = 274;
    private static final int DETAIL_THUMB_HEIGHT = 274;

    private static final int ACTION_START_RADIO = 1;

    private Artist mArtist;
    private ArrayObjectAdapter mAdapter;
    private ClassPresenterSelector mPresenterSelector;

    private BackgroundManager mBackgroundManager;
    private Drawable mDefaultBackground;
    private int mBackgroundColor;
    private DisplayMetrics mMetrics;

    private Action mActionStartRadio;
    private boolean mIsPlaying;
    private AlbumArtTask mArtTask;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActionStartRadio = new Action(ACTION_START_RADIO, getString(R.string.start_radio));

        prepareBackgroundManager();

        mArtist = getActivity().getIntent().getParcelableExtra(TvArtistDetailsActivity.EXTRA_ARTIST);
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
    }

    @Override
    public void onPause() {
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

        ArrayObjectAdapter albumsRowAdapter = new ArrayObjectAdapter(new CardPresenter());
        for (String albumRef : albums) {
            Album album = ProviderAggregator.getDefault().retrieveAlbum(albumRef, mArtist.getProvider());
            if (album != null) {
                albumsRowAdapter.add(album);
            }
        }

        mAdapter.add(new ListRow(new HeaderItem(getString(R.string.albums)), albumsRowAdapter));
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
}
