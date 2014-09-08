package org.omnirom.music.app.fragments;

import android.app.ActionBar;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v7.graphics.Palette;
import android.support.v7.graphics.PaletteItem;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.TextView;

import org.omnirom.music.app.AlbumActivity;
import org.omnirom.music.app.ArtistActivity;
import org.omnirom.music.app.PlaylistActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.app.adapters.SearchAdapter;
import org.omnirom.music.app.ui.MaterialTransitionDrawable;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.SearchResult;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ILocalCallback;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.ProviderAggregator;

import java.util.List;


/**
 * Created by h4o on 22/07/2014.
 */
public class SearchFragment extends Fragment implements ILocalCallback {
    private SearchAdapter mAdapter;
    private SearchResult mSearchResult;
    private Handler mHandler;
    private static final String KEY_PLAYLIST = "playlist";
    private String TAG = "SearchFragment";

    public SearchFragment() {
        ProviderAggregator.getDefault().addUpdateCallback(this);
        mHandler = new Handler();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_search, container, false);
        assert root != null;

        getActivity().setProgressBarIndeterminate(true);
        getActivity().setProgressBarIndeterminateVisibility(true);

        mAdapter = new SearchAdapter();

        ExpandableListView listView = (ExpandableListView) root.findViewById(R.id.expandablelv_search);
        listView.setAdapter(mAdapter);
        listView.setGroupIndicator(null);
        for (int i = 0; i < 4; i++) {
            listView.expandGroup(i, false);
        }

        listView.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {

            @Override
            public boolean onChildClick(ExpandableListView expandableListView, View view, int i, int i2, long l) {
                if (mSearchResult != null) {
                    switch (i) {
                        case SearchAdapter.ARTIST:
                            onArtistClick(i2, view);
                            break;

                        case SearchAdapter.ALBUM:
                            onAlbumClick(i2, view);
                            break;

                        case SearchAdapter.SONG:
                            onSongClick(i2);
                            break;

                        case SearchAdapter.PLAYLIST:
                            onPlaylistClick(i2, view);
                            break;

                        default:
                            Log.e(TAG, "Unknown child group id " + i);
                            break;
                    }

                    return true;
                } else
                    return false;
            }
        });
        return root;
    }

    private void onSongClick(int i) {
        final List<String> list = mSearchResult.getSongsList();
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();

        if (i == Math.max(10, list.size() + 1)) {
            // More
        } else {
            Song song = aggregator.retrieveSong(list.get(i), mSearchResult.getIdentifier());
            if (song != null) {
                try {
                    PluginsLookup.getDefault().getPlaybackService().playSong(song);
                } catch (Exception e) {
                    Log.e(TAG, "Unable to play song", e);
                }
            }
        }

    }

    private void onAlbumClick(int i, View v) {
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();
        SearchAdapter.ViewHolder holder = (SearchAdapter.ViewHolder) v.getTag();
        Bitmap hero = ((MaterialTransitionDrawable) holder.albumArtImageView.getDrawable()).getFinalDrawable().getBitmap();
        int color = 0xffffff;
        if (hero != null) {
            Palette palette = Palette.generate(hero);
            PaletteItem darkVibrantColor = palette.getDarkVibrantColor();
            PaletteItem darkMutedColor = palette.getDarkMutedColor();

            if (darkVibrantColor != null) {
                color = darkVibrantColor.getRgb();
            } else if (darkMutedColor != null) {
                color = darkMutedColor.getRgb();
            } else {
                color = getResources().getColor(R.color.default_album_art_background);
            }
        }
        Album album = aggregator.retrieveAlbum((String) mAdapter.getChild(SearchAdapter.ALBUM, i),
                mSearchResult.getIdentifier());
        Intent intent = AlbumActivity.craftIntent(getActivity(), hero, album, color);

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
            /*
            ImageView ivCover = holder.albumArtImageView;
            TextView tvTitle = holder.divider;
            ActivityOptions opt = ActivityOptions.makeSceneTransitionAnimation(getActivity(),
                    new Pair<View, String>(ivCover, "itemImage"),
                    new Pair<View, String>(tvTitle, "albumName"));
            startActivity(intent, opt.toBundle());*/
        } else {
            startActivity(intent);
        }
    }

    private void onArtistClick(int i, View v) {
        SearchAdapter.ViewHolder holder = (SearchAdapter.ViewHolder) v.getTag();
        ImageView ivCover = holder.albumArtImageView;
        TextView tvTitle = holder.divider;
        Bitmap hero = ((MaterialTransitionDrawable) holder.albumArtImageView.getDrawable()).getFinalDrawable().getBitmap();
        int color = 0xffffff;
        if (hero != null) {
            Palette palette = Palette.generate(hero);
            PaletteItem darkVibrantColor = palette.getDarkVibrantColor();
            PaletteItem darkMutedColor = palette.getDarkMutedColor();

            if (darkVibrantColor != null) {
                color = darkVibrantColor.getRgb();
            } else if (darkMutedColor != null) {
                color = darkMutedColor.getRgb();
            } else {
                color = getResources().getColor(R.color.default_album_art_background);
            }
        }
        Intent intent = new Intent(getActivity(), ArtistActivity.class);
        intent.putExtra(ArtistActivity.EXTRA_ARTIST, (String) mAdapter.getChild(SearchAdapter.ARTIST, i));
        intent.putExtra(ArtistActivity.EXTRA_BACKGROUND_COLOR,
                color);
        Utils.queueBitmap(ArtistActivity.BITMAP_ARTIST_HERO, hero);

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
            /* ActivityOptions opt = ActivityOptions.makeSceneTransitionAnimation(getActivity(),
                    new Pair<View, String>(ivCover, "itemImage"),
                    new Pair<View, String>(tvTitle, "albumName"));
            startActivity(intent, opt.toBundle()); */
        } else {
            startActivity(intent);
        }
    }

    private void onPlaylistClick(int i, View v) {
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();
        Playlist playlist = aggregator.retrievePlaylist((String) mAdapter.getChild(SearchAdapter.PLAYLIST, i),
                mSearchResult.getIdentifier());
        Intent intent = PlaylistActivity.craftIntent(getActivity(), playlist);
        startActivity(intent);
    }

    @Override
    public void onSongUpdate(List<Song> s) {

    }

    @Override
    public void onAlbumUpdate(List<Album> a) {

    }

    @Override
    public void onPlaylistUpdate(List<Playlist> p) {

    }

    @Override
    public void onArtistUpdate(List<Artist> a) {

    }

    @Override
    public void onProviderConnected(IMusicProvider provider) {

    }

    @Override
    public void onSearchResult(final SearchResult searchResult) {
        Log.d(TAG, "search result received " + searchResult + " (" + searchResult.getIdentifier()
                + " / " + searchResult.mIdentifier + ")");

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (getActivity() != null) {
                    getActivity().setTitle("'" + searchResult.getQuery() + "'");
                    getActivity().setProgressBarIndeterminateVisibility(false);
                } else {
                    mHandler.post(this);
                }
            }
        });

        if (false) {
            Log.e(TAG, "============================");
            Log.e(TAG, "= Result contents: ");
            Log.e(TAG, "= Albums: " + searchResult.getAlbumsList().size());
            for (String ref : searchResult.getAlbumsList()) {
                Log.e(TAG, "=== " + ref);
            }
            Log.e(TAG, "= Artist: " + searchResult.getArtistList().size());
            for (String ref : searchResult.getArtistList()) {
                Log.e(TAG, "=== " + ref);
            }
            Log.e(TAG, "= Playlist: " + searchResult.getPlaylistList().size());
            Log.e(TAG, "= Songs: " + searchResult.getSongsList().size());
            Log.e(TAG, "============================");
        }

        mSearchResult = searchResult;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mAdapter != null) {
                    if (searchResult.getIdentifier() == null) {
                        Log.e(TAG, "Search provider identifier is null!");
                    } else {
                        mAdapter.updateSearchResults(searchResult, searchResult.getIdentifier());
                        mAdapter.notifyDataSetChanged();
                    }
                }
            }
        });
    }
}
