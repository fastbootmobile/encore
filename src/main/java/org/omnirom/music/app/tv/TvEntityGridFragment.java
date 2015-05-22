package org.omnirom.music.app.tv;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.RemoteException;
import android.support.v17.leanback.app.VerticalGridFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v17.leanback.widget.VerticalGridPresenter;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.os.AsyncTaskCompat;
import android.util.Log;
import android.view.View;

import org.omnirom.music.app.R;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderConnection;

import java.util.ArrayList;
import java.util.List;

public class TvEntityGridFragment extends VerticalGridFragment {
    private static final String TAG = "VerticalGridFragment";

    private static final int NUM_COLUMNS = 5;

    private ArrayObjectAdapter mAdapter;
    private int mMode;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mMode = getActivity().getIntent().getIntExtra(TvEntityGridActivity.EXTRA_MODE,
                TvEntityGridActivity.MODE_ALBUM);

        setTitle(getString(mMode == TvEntityGridActivity.MODE_ALBUM ? R.string.tab_albums : R.string.tab_artists));
        setSearchAffordanceColor(getResources().getColor(R.color.primary_dark));

        setupFragment();

        if (mMode == TvEntityGridActivity.MODE_ALBUM) {
            AsyncTaskCompat.executeParallel(new GetAlbumsTask());
        } else if (mMode == TvEntityGridActivity.MODE_ARTIST) {
            AsyncTaskCompat.executeParallel(new GetArtistsTask());
        }
    }

    private void setupFragment() {
        VerticalGridPresenter gridPresenter = new VerticalGridPresenter();
        gridPresenter.setNumberOfColumns(NUM_COLUMNS);
        setGridPresenter(gridPresenter);

        mAdapter = new ArrayObjectAdapter(new CardPresenter());
        setAdapter(mAdapter);

        setOnSearchClickedListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getActivity(), TvSearchActivity.class);
                startActivity(intent);
            }
        });

        setOnItemViewClickedListener(new ItemViewClickedListener());
        setOnItemViewSelectedListener(new ItemViewSelectedListener());
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {

            if (item instanceof Album) {
                Album album = (Album) item;
                Intent intent = new Intent(getActivity(), TvAlbumDetailsActivity.class);
                intent.putExtra(TvAlbumDetailsActivity.EXTRA_ALBUM, album);

                Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        getActivity(),
                        ((ImageCardView) itemViewHolder.view).getMainImageView(),
                        TvAlbumDetailsActivity.SHARED_ELEMENT_NAME).toBundle();
                getActivity().startActivity(intent, bundle);
            } else if (item instanceof Artist) {
                Artist artist = (Artist) item;
                Intent intent = new Intent(getActivity(), TvArtistDetailsActivity.class);
                intent.putExtra(TvArtistDetailsActivity.EXTRA_ARTIST, artist);

                Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        getActivity(),
                        ((ImageCardView) itemViewHolder.view).getMainImageView(),
                        TvArtistDetailsActivity.SHARED_ELEMENT_NAME).toBundle();
                getActivity().startActivity(intent, bundle);
            }
        }
    }


    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                   RowPresenter.ViewHolder rowViewHolder, Row row) {
        }
    }


    private class GetAlbumsTask extends AsyncTask<Void, Void, List<Album>> {

        @Override
        protected List<Album> doInBackground(Void... params) {
            List<Album> outputList = new ArrayList<>();
            List<Album> cachedAlbums = ProviderAggregator.getDefault().getCache().getAllAlbums();

            for (Album album : cachedAlbums) {
                outputList.add(ProviderAggregator.getDefault().retrieveAlbum(album.getRef(),
                        album.getProvider()));
            }

            return outputList;
        }

        @Override
        protected void onPostExecute(List<Album> albums) {
            mAdapter.addAll(0, albums);
            mAdapter.notifyArrayItemRangeChanged(0, mAdapter.size());
        }
    }

    private class GetArtistsTask extends AsyncTask<Void, Void, List<Artist>> {

        @Override
        protected List<Artist> doInBackground(Void... params) {
            List<ProviderConnection> providers = PluginsLookup.getDefault().getAvailableProviders();
            final List<Artist> artists = new ArrayList<>();
            for (ProviderConnection providerConnection : providers) {
                try {
                    IMusicProvider provider = providerConnection.getBinder();
                    if (provider != null) {
                        List<Artist> providerArtists = provider.getArtists();
                        if (providerArtists != null) {
                            artists.addAll(providerArtists);
                        }
                    }
                } catch (DeadObjectException e) {
                    Log.e(TAG, "Provider died while getting artists");
                } catch (RemoteException e) {
                    Log.w(TAG, "Cannot get artists from a provider", e);
                }
            }

            return artists;
        }

        @Override
        protected void onPostExecute(List<Artist> artists) {
            mAdapter.addAll(0, artists);
            mAdapter.notifyArrayItemRangeChanged(0, mAdapter.size());
        }
    }
}
