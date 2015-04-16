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
import android.content.Intent;
import android.content.res.Resources;
import android.os.RemoteException;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import org.omnirom.music.app.AlbumActivity;
import org.omnirom.music.app.ArtistActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.fragments.PlaylistChooserFragment;
import org.omnirom.music.app.ui.AlbumArtImageView;
import org.omnirom.music.framework.PlaybackProxy;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.art.RecyclingBitmapDrawable;
import org.omnirom.music.framework.Suggestor;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.BoundEntity;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.SearchResult;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderConnection;
import org.omnirom.music.providers.ProviderIdentifier;
import org.omnirom.music.utils.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private List<SearchEntry> mAllSongs;
    private List<SearchEntry> mAllArtists;
    private List<SearchEntry> mAllPlaylists;
    private List<SearchEntry> mAllAlbums;

    private List<SearchEntry> mSortedSongs;
    private List<SearchEntry> mSortedArtists;
    private List<SearchEntry> mSortedPlaylists;
    private List<SearchEntry> mSortedAlbums;

    private final View.OnClickListener mOverflowArtistClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            ViewHolder vh = (ViewHolder) v.getTag();
            Artist artist = (Artist) vh.content;
            showArtistOverflow(v.getContext(), v, artist);
        }
    };

    private final View.OnClickListener mOverflowAlbumClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            ViewHolder vh = (ViewHolder) v.getTag();
            Album album = (Album) vh.content;
            showAlbumOverflow(v.getContext(), v, album);
        }
    };

    private final View.OnClickListener mOverflowPlaylistClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            ViewHolder vh = (ViewHolder) v.getTag();
            Playlist playlist = (Playlist) vh.content;
            showPlaylistOverflow(v.getContext(), v, playlist);
        }
    };

    private final View.OnClickListener mOverflowSongClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            ViewHolder vh = (ViewHolder) v.getTag();
            Song song = (Song) vh.content;
            showSongOverflow(v.getContext(), v, song);
        }
    };

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
                return ref.equals(((BoundEntity) o).getRef());
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
        mSearchResults = new ArrayList<>();

        mAllSongs = new ArrayList<>();
        mAllArtists = new ArrayList<>();
        mAllPlaylists = new ArrayList<>();
        mAllAlbums = new ArrayList<>();

        mSortedSongs = new ArrayList<>();
        mSortedArtists = new ArrayList<>();
        mSortedPlaylists = new ArrayList<>();
        mSortedAlbums = new ArrayList<>();
    }

    /**
     * Clear all the results from the adapter
     */
    public void clear() {
        synchronized (this) {
            mSearchResults.clear();
            mAllSongs.clear();
            mAllArtists.clear();
            mAllPlaylists.clear();
            mAllAlbums.clear();
            mSortedSongs.clear();
            mSortedArtists.clear();
            mSortedPlaylists.clear();
            mSortedAlbums.clear();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getGroupCount() {
        synchronized (this) {
            if (mSearchResults.size() > 0) {
                return COUNT;
            } else {
                return 0;
            }
        }
    }

    /**
     * Add the results to the current adapter's results
     *
     * @param searchResults The results to append
     */
    public void appendResults(List<SearchResult> searchResults) {
        mSearchResults.addAll(searchResults);

        for (SearchResult searchResult : searchResults) {
            final ProviderIdentifier id = searchResult.getIdentifier();

            final List<String> songs = searchResult.getSongsList();
            final List<String> artists = searchResult.getArtistList();
            final List<String> playlists = searchResult.getPlaylistList();
            final List<String> albums = searchResult.getAlbumsList();

            for (String song : songs) {
                SearchEntry entry = new SearchEntry(song, id);
                if (!mAllSongs.contains(entry)) {
                    mAllSongs.add(entry);
                }
            }

            for (String artist : artists) {
                SearchEntry entry = new SearchEntry(artist, id);
                if (!mAllArtists.contains(entry)) {
                    mAllArtists.add(entry);
                }
            }

            for (String playlist : playlists) {
                SearchEntry entry = new SearchEntry(playlist, id);
                if (!mAllPlaylists.contains(entry)) {
                    mAllPlaylists.add(entry);
                }
            }

            for (String album : albums) {
                SearchEntry entry = new SearchEntry(album, id);
                if (!mAllAlbums.contains(entry)) {
                    mAllAlbums.add(entry);
                }
            }
        }

        synchronized (this) {
            computeResultsList();
        }
    }

    private void computeResultsList() {
        mSortedArtists.clear();
        mSortedAlbums.clear();
        mSortedPlaylists.clear();
        mSortedSongs.clear();

        Map<ProviderIdentifier, Integer> songsPerProvider = new HashMap<>();
        Map<ProviderIdentifier, Integer> albumsPerProvider = new HashMap<>();
        Map<ProviderIdentifier, Integer> playlistsPerProvider = new HashMap<>();
        Map<ProviderIdentifier, Integer> artistsPerProvider = new HashMap<>();

        // Use 5 results from each provider for each category
        final int maxResults = 5;

        for (SearchEntry song : mAllSongs) {
            Integer providerCountInteger = songsPerProvider.get(song.identifier);
            int providerCount = providerCountInteger == null ? 0 : providerCountInteger;

            if (providerCount < maxResults) {
                mSortedSongs.add(song);
                songsPerProvider.put(song.identifier, providerCount + 1);
            }
        }

        for (SearchEntry album : mAllAlbums) {
            Integer providerCountInteger = albumsPerProvider.get(album.identifier);
            int providerCount = providerCountInteger == null ? 0 : providerCountInteger;

            if (providerCount < maxResults) {
                mSortedAlbums.add(album);
                albumsPerProvider.put(album.identifier, providerCount + 1);
            }
        }

        for (SearchEntry artist : mAllArtists) {
            Integer providerCountInteger = artistsPerProvider.get(artist.identifier);
            int providerCount = providerCountInteger == null ? 0 : providerCountInteger;

            if (providerCount < maxResults) {
                mSortedArtists.add(artist);
                artistsPerProvider.put(artist.identifier, providerCount + 1);
            }
        }

        for (SearchEntry playlist : mAllPlaylists) {
            Integer providerCountInteger = playlistsPerProvider.get(playlist.identifier);
            int providerCount = providerCountInteger == null ? 0 : providerCountInteger;

            if (providerCount < maxResults) {
                mSortedPlaylists.add(playlist);
                playlistsPerProvider.put(playlist.identifier, providerCount + 1);
            }
        }
    }

    /**
     * Returns whether or not the search results contains the provided entity
     *
     * @param ent The entity to check
     * @return True if the search results contains the entity, false otherwise
     */
    public boolean contains(BoundEntity ent) {
        synchronized (this) {
            SearchEntry compare = new SearchEntry(ent.getRef(), ent.getProvider());

            if (ent instanceof Song) {
                return mAllSongs.contains(compare);
            } else if (ent instanceof Artist) {
                return mAllArtists.contains(compare);
            } else if (ent instanceof Album) {
                return mAllAlbums.contains(compare);
            } else if (ent instanceof Playlist) {
                return mAllPlaylists.contains(compare);
            }

            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getChildrenCount(int i) {
        synchronized (this) {
            List children = getGroup(i);

            if (children != null) {
                return children.size();
            } else {
                return 0;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SearchEntry> getGroup(int i) {
        synchronized (this) {
            switch (i) {
                case ARTIST:
                    return mSortedArtists;

                case ALBUM:
                    return mSortedAlbums;

                case SONG:
                    return mSortedSongs;

                case PLAYLIST:
                    return mSortedPlaylists;

                default:
                    return null;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SearchEntry getChild(int i, int i2) {
        synchronized (this) {
            return getGroup(i).get(i2);
        }
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
        synchronized (this) {
            if (i < getGroupCount() && i2 < getChildrenCount(i)) {
                try {
                    return (long) getChild(i, i2).hashCode();
                } catch (IndexOutOfBoundsException e) {
                    // It's the 'more' item
                    // TODO: Better heuristic for that
                    return -2;
                }
            } else {
                return -1;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasStableIds() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getGroupView(int i, boolean b, View view, ViewGroup parent) {
        synchronized (this) {
            final Context ctx = parent.getContext();
            assert ctx != null;

            GroupViewHolder holder;

            if (view == null) {
                LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(R.layout.item_group_separator, parent, false);
                holder = new GroupViewHolder();
                holder.tvSearchSeparator = (TextView) view.findViewById(R.id.tv_search_separator);
                view.setTag(holder);
            } else {
                holder = (GroupViewHolder) view.getTag();
            }

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

            holder.tvSearchSeparator.setText(title);
            if (getChildrenCount(i) == 0) {
                view.setVisibility(View.GONE);
            } else {
                view.setVisibility(View.VISIBLE);
            }
            return view;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getChildView(int i, int i2, boolean b, View root, ViewGroup parent) {
        synchronized (this) {
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
                holder.ivOverflow = (ImageView) root.findViewById(R.id.ivOverflow);
                holder.vRoot = root;

                holder.ivOverflow.setTag(holder);
                root.setTag(holder);
            }

            final ViewHolder tag = (ViewHolder) root.getTag();

            if (i2 == getChildrenCount(i) - 1) {
                tag.albumArtImageView.setVisibility(View.INVISIBLE);
                tag.ivSource.setVisibility(View.GONE);
                tag.tvTitle.setText(R.string.more);
                tag.tvSubtitle.setText(null);

                // TODO: More is not working for now, we'll see that later, so hide it
                tag.vRoot.setVisibility(View.GONE);
            } else {
                tag.vRoot.setVisibility(View.VISIBLE);
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
    }

    /**
     * Updates the tag fields considering the entry is a song
     *
     * @param i   The item index
     * @param tag The tag of the view
     */
    private void updateSongTag(int i, ViewHolder tag) {
        final SearchEntry entry = mSortedSongs.get(i);
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();
        Song song = aggregator.retrieveSong(entry.ref, entry.identifier);

        if (song != null && song.equals(tag.content)) {
            // We're already displaying it
            return;
        }

        if (song != null && song.isLoaded()) {
            tag.tvTitle.setText(song.getTitle());
            Artist artist = aggregator.retrieveArtist(song.getArtist(), song.getProvider());
            if (artist != null) {
                tag.tvSubtitle.setText(artist.getName());
            } else {
                tag.tvSubtitle.setText(null);
            }
            tag.albumArtImageView.loadArtForSong(song);
            tag.sourceLogo = PluginsLookup.getDefault().getCachedLogo(tag.vRoot.getResources(), song);
            tag.ivSource.setImageDrawable(tag.sourceLogo);
            tag.content = song;
            tag.ivOverflow.setOnClickListener(mOverflowSongClickListener);
        } else {
            tag.tvTitle.setText(R.string.loading);
            tag.tvSubtitle.setText(null);
            tag.ivSource.setImageDrawable(null);
            tag.albumArtImageView.setDefaultArt();
            tag.ivOverflow.setOnClickListener(null);
        }
    }

    /**
     * Updates the tag fields considering the entry is an artist
     *
     * @param i   The item index
     * @param tag The tag of the view
     */
    private void updateArtistTag(int i, ViewHolder tag) {
        final SearchEntry entry = mSortedArtists.get(i);
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();
        Artist artist = aggregator.retrieveArtist(entry.ref, entry.identifier);

        if (artist != null && artist.equals(tag.content)) {
            // We're already displaying it
            return;
        }

        if (artist != null && artist.isLoaded()) {
            tag.tvTitle.setText(artist.getName());
            tag.tvSubtitle.setText("");
            tag.albumArtImageView.loadArtForArtist(artist);
            tag.content = artist;
            tag.sourceLogo = PluginsLookup.getDefault().getCachedLogo(tag.vRoot.getResources(), artist);
            tag.ivSource.setImageDrawable(tag.sourceLogo);
            tag.ivOverflow.setOnClickListener(mOverflowArtistClickListener);
        } else {
            tag.tvTitle.setText(R.string.loading);
            tag.tvSubtitle.setText(null);
            tag.ivSource.setImageDrawable(null);
            tag.albumArtImageView.setDefaultArt();
            tag.ivOverflow.setOnClickListener(null);
        }
    }

    /**
     * Updates the tag fields considering the entry is an album
     *
     * @param i   The item index
     * @param tag The tag of the view
     */
    private void updateAlbumTag(int i, ViewHolder tag) {
        final SearchEntry entry = mSortedAlbums.get(i);
        ProviderAggregator aggregator = ProviderAggregator.getDefault();
        Album album = aggregator.retrieveAlbum(entry.ref, entry.identifier);

        if (album != null && album.equals(tag.content)) {
            // We're already displaying it
            return;
        }

        if (album != null) {
            tag.tvTitle.setText(album.getName());
            if (album.getYear() > 0) {
                tag.tvSubtitle.setText("" + album.getYear());
            } else {
                tag.tvSubtitle.setText("");
            }

            tag.albumArtImageView.loadArtForAlbum(album);
            tag.sourceLogo = PluginsLookup.getDefault().getCachedLogo(tag.vRoot.getResources(), album);
            tag.ivSource.setImageDrawable(tag.sourceLogo);
            tag.content = album;
            tag.ivOverflow.setOnClickListener(mOverflowAlbumClickListener);

            // Ensure album contents are fetched so that we can do something with it
            ProviderConnection conn = PluginsLookup.getDefault().getProvider(album.getProvider());
            if (conn != null) {
                IMusicProvider binder = conn.getBinder();
                if (binder != null) {
                    try {
                        binder.fetchAlbumTracks(album.getRef());
                    } catch (RemoteException e) {
                        Log.e(TAG, "Cannot fetch album tracks");
                    }
                }
            }
        } else {
            tag.tvTitle.setText(R.string.loading);
            tag.tvSubtitle.setText(null);
            tag.albumArtImageView.setDefaultArt();
            tag.ivOverflow.setOnClickListener(null);
        }
    }

    /**
     * Updates the tag fields considering the entry is a playlist
     *
     * @param i   The item index
     * @param tag The tag of the view
     */
    private void updatePlaylistTag(int i, ViewHolder tag) {
        final SearchEntry entry = mSortedPlaylists.get(i);
        final Playlist playlist = ProviderAggregator.getDefault().retrievePlaylist(entry.ref, entry.identifier);
        final Resources res = tag.vRoot.getResources();

        if (playlist != null && playlist.equals(tag.content)) {
            // We're already displaying it
            return;
        }

        if (playlist != null && (playlist.isLoaded() || playlist.getName() != null)) {
            tag.tvTitle.setText(playlist.getName());
            tag.tvSubtitle.setText(res.getQuantityString(R.plurals.xx_songs, playlist.getSongsCount()));
            tag.content = playlist;
            tag.sourceLogo = PluginsLookup.getDefault().getCachedLogo(tag.vRoot.getResources(), playlist);
            tag.ivSource.setImageDrawable(tag.sourceLogo);
            tag.ivOverflow.setOnClickListener(mOverflowPlaylistClickListener);
        } else {
            tag.tvTitle.setText(R.string.loading);
            tag.tvSubtitle.setText(null);
            tag.ivSource.setImageDrawable(null);
            tag.albumArtImageView.setDefaultArt();
            tag.ivOverflow.setOnClickListener(null);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isChildSelectable(int i, int i2) {
        synchronized (this) {
            return mSearchResults.size() > 0 && getGroup(i) != null;
        }
    }

    private void showArtistOverflow(final Context context, View parent, final Artist artist) {
        PopupMenu popupMenu = new PopupMenu(context, parent);
        popupMenu.inflate(R.menu.search_res_artist);
        popupMenu.show();

        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case R.id.menu_play_now:
                        List<Song> radio = Suggestor.getInstance().buildArtistRadio(artist);
                        PlaybackProxy.clearQueue();
                        for (Song song : radio) {
                            PlaybackProxy.queueSong(song, false);
                        }
                        PlaybackProxy.playAtIndex(0);
                        break;

                    default:
                        return false;
                }
                return true;
            }
        });
    }

    private void showAlbumOverflow(final Context context, View parent, final Album album) {
        PopupMenu popupMenu = new PopupMenu(context, parent);
        popupMenu.inflate(R.menu.search_res_album);
        popupMenu.show();

        final String artist = Utils.getMainArtist(album);

        if (artist == null) {
            // No artist could be found for this album, don't show the entry
            popupMenu.getMenu().removeItem(R.id.menu_open_artist_page);
        }

        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case R.id.menu_play_now:
                        PlaybackProxy.playAlbum(album);
                        break;

                    case R.id.menu_add_to_queue:
                        PlaybackProxy.queueAlbum(album, false);
                        break;

                    case R.id.menu_open_artist_page:
                        if (artist != null) {
                            Intent intent = ArtistActivity.craftIntent(context, null, artist,
                                    album.getProvider(), 0xFF333333);
                            context.startActivity(intent);
                        }
                        break;

                    case R.id.menu_add_to_playlist:
                        PlaylistChooserFragment fragment = PlaylistChooserFragment.newInstance(album);
                        fragment.show(((FragmentActivity) context).getSupportFragmentManager(),
                                album.getRef());
                        break;

                    default:
                        return false;
                }
                return true;
            }
        });
    }

    private void showSongOverflow(final Context context, View parent, final Song song) {
        PopupMenu popupMenu = new PopupMenu(context, parent);
        popupMenu.inflate(R.menu.search_res_song);
        popupMenu.show();

        if (song.getArtist() == null) {
            // No attached artist, don't show the menu entry
            popupMenu.getMenu().removeItem(R.id.menu_open_artist_page);
        }

        if (song.getAlbum() == null) {
            // No attached album, don't show the menu entry
            popupMenu.getMenu().removeItem(R.id.menu_open_album_page);
        }

        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                Intent intent;
                switch (menuItem.getItemId()) {
                    case R.id.menu_play_now:
                        PlaybackProxy.playSong(song);
                        break;

                    case R.id.menu_add_to_queue:
                        PlaybackProxy.queueSong(song, false);
                        break;

                    case R.id.menu_open_artist_page:
                        if (song.getArtist() != null) {
                            intent = ArtistActivity.craftIntent(context, null, song.getArtist(),
                                    song.getProvider(), 0xFF333333);
                            context.startActivity(intent);
                        }
                        break;

                    case R.id.menu_open_album_page:
                        if (song.getAlbum() != null) {
                            intent = AlbumActivity.craftIntent(context, null, song.getAlbum(),
                                    song.getProvider(), 0xFF333333);
                            context.startActivity(intent);
                        }
                        break;

                    case R.id.menu_add_to_playlist:
                        PlaylistChooserFragment fragment = PlaylistChooserFragment.newInstance(song);
                        fragment.show(((FragmentActivity) context).getSupportFragmentManager(),
                                song.getRef());
                        break;

                    default:
                        return false;
                }
                return true;
            }
        });
    }

    private void showPlaylistOverflow(final Context context, View parent, final Playlist playlist) {
        PopupMenu popupMenu = new PopupMenu(context, parent);
        popupMenu.inflate(R.menu.search_res_playlist);
        popupMenu.show();

        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case R.id.menu_play_now:
                        PlaybackProxy.playPlaylist(playlist);
                        break;

                    case R.id.menu_add_to_queue:
                        PlaybackProxy.queuePlaylist(playlist, false);
                        break;

                    case R.id.menu_add_to_playlist:
                        PlaylistChooserFragment fragment = PlaylistChooserFragment.newInstance(playlist);
                        fragment.show(((FragmentActivity) context).getSupportFragmentManager(),
                                playlist.getRef());
                        break;

                    default:
                        return false;
                }
                return true;
            }
        });
    }



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
        public RecyclingBitmapDrawable sourceLogo;
        public ImageView ivOverflow;
    }

    /**
     * ViewHolder for group headers
     */
    private static class GroupViewHolder {
        public TextView tvSearchSeparator;
    }
}
