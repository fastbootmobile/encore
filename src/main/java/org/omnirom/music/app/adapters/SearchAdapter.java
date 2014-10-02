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

package org.omnirom.music.app.adapters;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.omnirom.music.app.R;
import org.omnirom.music.app.ui.AlbumArtImageView;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.BoundEntity;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.SearchResult;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderIdentifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter that displays Search results in an Expandable ListView
 */
public class SearchAdapter extends BaseExpandableListAdapter {
    private static final String TAG = "SearchAdapter";

    public final static int ARTIST = 0;
    public final static int ALBUM = 1;
    public final static int SONG = 2;
    public final static int PLAYLIST = 3;
    public final static int COUNT = 4;

    private List<SearchResult> mSearchResults;
    private List<SearchEntry> mSongs;
    private List<SearchEntry> mArtists;
    private List<SearchEntry> mPlaylists;
    private List<SearchEntry> mAlbums;

    /**
     * ViewHolder for list items
     */
    public static class ViewHolder {
        public AlbumArtImageView albumArtImageView;
        public TextView tvTitle;
        public TextView tvSubtitle;
        public Object content;
        public TextView divider;
        public ImageView ivSource;
        public View vRoot;
    }

    /**
     * Class representing search entries
     */
    public class SearchEntry {
        SearchEntry(String ref, ProviderIdentifier id) {
            this.ref = ref;
            this.identifier = id;
        }

        public String ref;
        public ProviderIdentifier identifier;

        @Override
        public boolean equals(Object o) {
            if (o == null) {
                return false;
            } else if (o instanceof String) {
                return o.equals(ref);
            } else if (o instanceof BoundEntity) {
                return ((BoundEntity) o).getRef().equals(ref);
            } else if (o instanceof SearchEntry) {
                return ((SearchEntry) o).ref.equals(ref) &&
                        ((SearchEntry) o).identifier.equals(identifier);
            }

            return false;
        }
    }

    /**
     * Default constructor
     */
    public SearchAdapter() {
        mSearchResults = new ArrayList<SearchResult>();
        mSongs = new ArrayList<SearchEntry>();
        mArtists = new ArrayList<SearchEntry>();
        mPlaylists = new ArrayList<SearchEntry>();
        mAlbums = new ArrayList<SearchEntry>();
    }

    /**
     * Clear all the results from the adapter
     */
    public void clear() {
        mSearchResults.clear();
        mSongs.clear();
        mArtists.clear();
        mPlaylists.clear();
        mAlbums.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getGroupCount() {
        if (mSearchResults.size() > 0) {
            return COUNT;
        } else {
            return 0;
        }
    }

    /**
     * Add the results to the current adapter's restults
     * @param searchResult The results to append
     */
    public void appendResults(SearchResult searchResult) {
        mSearchResults.add(searchResult);

        final ProviderIdentifier id = searchResult.getIdentifier();

        final List<String> songs = searchResult.getSongsList();
        final List<String> artists = searchResult.getArtistList();
        final List<String> playlists = searchResult.getPlaylistList();
        final List<String> albums = searchResult.getAlbumsList();

        for (String song : songs) {
            mSongs.add(new SearchEntry(song, id));
        }

        for (String artist : artists) {
            mArtists.add(new SearchEntry(artist, id));
        }

        for (String playlist : playlists) {
            mPlaylists.add(new SearchEntry(playlist, id));
        }

        for (String album : albums) {
            mAlbums.add(new SearchEntry(album, id));
        }
    }

    /**
     * Returns whether or not the search results contains the provided entity
     * @param ent The entity to check
     * @return True if the search results contains the entity, false otherwise
     */
    public boolean contains(BoundEntity ent) {
        SearchEntry compare = new SearchEntry(ent.getRef(), ent.getProvider());

        if (ent instanceof Song) {
            return mSongs.contains(compare);
        } else if (ent instanceof Artist) {
            return mArtists.contains(compare);
        } else if (ent instanceof Album) {
            return mAlbums.contains(compare);
        } else if (ent instanceof Playlist) {
            return mPlaylists.contains(compare);
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getChildrenCount(int i) {
        List children = getGroup(i);

        if (children != null) {
            if (children.size() == 0) {
                return 0;
            } else {
                return Math.min(10, children.size() + 1);
            }
        }

        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SearchEntry> getGroup(int i) {
        if (mSearchResults.size() > 0) {
            switch (i) {
                case ARTIST: return mArtists;
                case ALBUM: return mAlbums;
                case SONG: return mSongs;
                case PLAYLIST: return mPlaylists;
            }
        }

        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SearchEntry getChild(int i, int i2) {
        return getGroup(i).get(i2);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getGroupId(int i) {
        return i;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getChildId(int i, int i2) {
        if (i < getGroupCount() && i2 < getChildrenCount(i)) {
            try {
                long code = getChild(i, i2).hashCode();
                Log.e(TAG, "Child " + i + ", " + i2 + ": " + code);
                return code;
            } catch (IndexOutOfBoundsException e) {
                // It's the 'more' item
                // TODO: Better heuristic for that
                return -2;
            }
        } else {
            return -1;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasStableIds() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getGroupView(int i, boolean b, View view, ViewGroup parent) {
        final Context ctx = parent.getContext();
        assert ctx != null;
        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.item_group_separator, parent, false);
        }
        TextView textView = (TextView) view.findViewById(R.id.tv_search_separator);
        int title;
        switch (i) {
            case ARTIST:
                title = R.string.tab_artists;
                break;
            case ALBUM:
                title = R.string.albums;
                break;
            case SONG:
                title = R.string.songs;
                break;
            case PLAYLIST:
                title = R.string.tab_playlists;
                break;
            default:
                throw new RuntimeException("Unknown group index: " + i);
        }

        textView.setText(title);
        if (getChildrenCount(i) == 0) {
            view.setVisibility(View.GONE);
        } else {
            view.setVisibility(View.VISIBLE);
        }
        return view;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getChildView(int i, int i2, boolean b, View root, ViewGroup parent) {
        final Context ctx = parent.getContext();
        assert ctx != null;
        if (root == null) {
            LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            root = inflater.inflate(R.layout.item_search_element, parent, false);
            ViewHolder holder = new ViewHolder();
            holder.albumArtImageView = (AlbumArtImageView) root.findViewById(R.id.ivCover);
            holder.tvTitle = (TextView) root.findViewById(R.id.tvTitle);
            holder.tvSubtitle = (TextView) root.findViewById(R.id.tvSubTitle);
            holder.divider = (TextView) root.findViewById(R.id.divider);
            holder.ivSource = (ImageView) root.findViewById(R.id.ivSource);
            holder.vRoot = root;
            root.setTag(holder);
        }

        final ViewHolder tag = (ViewHolder) root.getTag();
        tag.albumArtImageView.setDefaultArt();

        if (i2 == getChildrenCount(i) - 1) {
            tag.albumArtImageView.setVisibility(View.INVISIBLE);
            tag.ivSource.setVisibility(View.GONE);
            tag.tvTitle.setText(R.string.more);
            tag.tvSubtitle.setText(null);
        } else {
            tag.albumArtImageView.setVisibility(View.VISIBLE);
            tag.ivSource.setVisibility(View.VISIBLE);

            switch (i) {
                case ARTIST:
                    updateArtistTag(i2, tag);
                    break;
                case ALBUM:
                    updateAlbumTag(i2, tag);
                    break;
                case SONG:
                    updateSongTag(i2, tag);
                    break;
                case PLAYLIST:
                    updatePlaylistTag(i2, tag);
                    break;
                default:
                    Log.e(TAG, "Unknown group " + i);
                    break;
            }
        }
        return root;
    }

    /**
     * Updates the tag fields considering the entry is a song
     * @param i The item index
     * @param tag The tag of the view
     */
    private void updateSongTag(int i, ViewHolder tag) {
        final SearchEntry entry = mSongs.get(i);
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();

        Song song = aggregator.retrieveSong(entry.ref, entry.identifier);
        if (song != null && song.isLoaded()) {
            tag.tvTitle.setText(song.getTitle());
            Artist artist = aggregator.retrieveArtist(song.getArtist(), song.getProvider());
            if (artist != null) {
                tag.tvSubtitle.setText(artist.getName());
            }
            tag.albumArtImageView.loadArtForSong(song);
            tag.ivSource.setImageBitmap(PluginsLookup.getDefault().getCachedLogo(song));
            tag.content = song;
        } else {
            tag.tvTitle.setText(R.string.loading);
            tag.tvSubtitle.setText(null);
            tag.albumArtImageView.setDefaultArt();
        }
    }

    /**
     * Updates the tag fields considering the entry is an artist
     * @param i The item index
     * @param tag The tag of the view
     */
    private void updateArtistTag(int i, ViewHolder tag) {
        final SearchEntry entry = mArtists.get(i);
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();
        Artist artist = aggregator.retrieveArtist(entry.ref, entry.identifier);

        if (artist != null && artist.isLoaded()) {
            tag.tvTitle.setText(artist.getName());
            tag.tvSubtitle.setText("");
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                // tag.albumArtImageView.setViewName("local:artist:cover:" + artistRef);
                // tag.divider.setViewName("local:artist:name:" + artistRef);
            }
            tag.albumArtImageView.loadArtForArtist(artist);
            tag.content = artist;
            tag.ivSource.setImageBitmap(PluginsLookup.getDefault().getCachedLogo(artist));
        } else {
            tag.tvTitle.setText(R.string.loading);
            tag.tvSubtitle.setText(null);
            tag.albumArtImageView.setDefaultArt();
        }
    }

    /**
     * Updates the tag fields considering the entry is an album
     * @param i The item index
     * @param tag The tag of the view
     */
    private void updateAlbumTag(int i, ViewHolder tag) {
        final SearchEntry entry = mAlbums.get(i);
        ProviderAggregator aggregator = ProviderAggregator.getDefault();
        Album album = aggregator.retrieveAlbum(entry.ref, entry.identifier);

        if (album != null) {
            tag.tvTitle.setText(album.getName());
            if (album.getYear() > 0) {
                tag.tvSubtitle.setText("" + album.getYear());
            } else {
                tag.tvSubtitle.setText("");
            }

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                // tag.albumArtImageView.setViewName("local:album:cover:" + albumRef);
                // tag.divider.setViewName("local:album:title:" + albumRef);
            }
            tag.albumArtImageView.loadArtForAlbum(album);
            tag.ivSource.setImageBitmap(PluginsLookup.getDefault().getCachedLogo(album));
            tag.content = album;
        } else {
            tag.tvTitle.setText(R.string.loading);
            tag.tvSubtitle.setText(null);
            tag.albumArtImageView.setDefaultArt();
        }
    }

    /**
     * Updates the tag fields considering the entry is a playlist
     * @param i The item index
     * @param tag The tag of the view
     */
    private void updatePlaylistTag(int i, ViewHolder tag) {
        final SearchEntry entry = mPlaylists.get(i);
        Playlist playlist = ProviderAggregator.getDefault().retrievePlaylist(entry.ref, entry.identifier);

        if (playlist != null && playlist.isLoaded()) {
            tag.tvTitle.setText(playlist.getName());
            tag.tvSubtitle.setText(playlist.getSongsCount() + " songs");
            tag.content = playlist;
            tag.ivSource.setImageBitmap(PluginsLookup.getDefault().getCachedLogo(playlist));
        } else {
            tag.tvTitle.setText(R.string.loading);
            tag.tvSubtitle.setText(null);
            tag.albumArtImageView.setDefaultArt();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isChildSelectable(int i, int i2) {
        return mSearchResults.size() > 0 && getGroup(i) != null;
    }
}
