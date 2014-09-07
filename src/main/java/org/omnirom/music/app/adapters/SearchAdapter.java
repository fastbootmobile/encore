package org.omnirom.music.app.adapters;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;

import org.omnirom.music.app.R;
import org.omnirom.music.app.ui.AlbumArtImageView;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.SearchResult;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderIdentifier;

import java.util.List;

/**
 * Created by h4o on 22/07/2014.
 */
public class SearchAdapter extends BaseExpandableListAdapter {
    private SearchResult mSearchResult;
    private ProviderIdentifier mSearchSource;
    private String TAG = "SearchAdapter";
    public final static int ARTIST = 0;
    public final static int ALBUM = 1;
    public final static int SONG = 2;
    public final static int PLAYLIST = 3;


    public static class ViewHolder {
        public AlbumArtImageView albumArtImageView;
        public TextView tvTitle;
        public TextView tvSubtitle;
        public Object content;
        public TextView divider;
        public View vRoot;

    }

    @Override
    public int getGroupCount() {
        if (mSearchResult != null) {
            return 4;
        } else {
            return 0;
        }
    }

    public void updateSearchResults(SearchResult searchResult, ProviderIdentifier src) {
        // TODO: Multi-provider searches!!!
        mSearchResult = searchResult;
        mSearchSource = src;
    }

    @Override
    public int getChildrenCount(int i) {
        List childs = getGroup(i);

        if (childs != null) {
            if (childs.size() == 0) {
                return 0;
            } else {
                return Math.min(10, childs.size() + 1);
            }
        }

        return 0;
    }

    @Override
    public List getGroup(int i) {
        if (mSearchResult != null) {
            switch (i) {
                case ARTIST:
                    return mSearchResult.getArtistList();
                case ALBUM:
                    return mSearchResult.getAlbumsList();
                case SONG:
                    return mSearchResult.getSongsList();
                case PLAYLIST:
                    return mSearchResult.getPlaylistList();
            }
        }
        return null;
    }

    @Override
    public Object getChild(int i, int i2) {
        Log.e(TAG, "GetChild(" + i + ", " + i2 + ")");
        return getGroup(i).get(i2);
    }

    @Override
    public long getGroupId(int i) {
        return i;
    }

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

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public View getGroupView(int i, boolean b, View view, ViewGroup parent) {
        final Context ctx = parent.getContext();
        assert ctx != null;
        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.item_group_separator, parent, false);
        }
        TextView textView = (TextView) view.findViewById(R.id.tv_search_separator);
        String title;
        switch (i) {
            case ARTIST:
                title = "Artists";
                break;
            case ALBUM:
                title = "Albums";
                break;
            case SONG:
                title = "Songs";
                break;
            case PLAYLIST:
                title = "Playlists";
                break;
            default:
                title = "error";
                break;
        }
        textView.setText(title);
        if (getChildrenCount(i) == 0) {
            view.setVisibility(View.GONE);
        } else {
            view.setVisibility(View.VISIBLE);
        }
        return view;
    }

    @Override
    public View getChildView(int i, int i2, boolean b, View root, ViewGroup parent) {
        Log.e(TAG, "getChildView(" + i + ", " + i2 + ")");
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
            holder.vRoot = root;
           /* root.setFocusable(false);
            holder.tvTitle.setFocusable(false);
            holder.tvSubtitle.setFocusable(false);
            holder.albumArtImageView.setFocusable(false);*/
            root.setTag(holder);
        }

        final ViewHolder tag = (ViewHolder) root.getTag();
        tag.albumArtImageView.setDefaultArt();

        if (i2 == getChildrenCount(i) - 1) {
            tag.albumArtImageView.setVisibility(View.INVISIBLE);
            tag.tvTitle.setText("More");
            tag.tvSubtitle.setText("");
        } else {
            tag.albumArtImageView.setVisibility(View.VISIBLE);

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
                default:
                    Log.e(TAG, "Unknown group " + i);
                    break;
            }
        }
        return root;
    }

    private void updateSongTag(int i, ViewHolder tag) {
        String songRef = mSearchResult.getSongsList().get(i);
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();

        Song song = aggregator.retrieveSong(songRef, mSearchSource);
        if (song != null && song.isLoaded()) {
            tag.tvTitle.setText(song.getTitle());
            Artist artist = aggregator.retrieveArtist(song.getArtist(), song.getProvider());
            if (artist != null) {
                tag.tvSubtitle.setText(artist.getName());
            }
            tag.albumArtImageView.loadArtForSong(song);
            tag.content = song;
        }
    }

    private void updateArtistTag(int i, ViewHolder tag) {
        final String artistRef = mSearchResult.getArtistList().get(i);
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();
        Artist artist = aggregator.retrieveArtist(artistRef, mSearchSource);

        if (artist != null && artist.isLoaded()) {
            tag.tvTitle.setText(artist.getName());
            tag.tvSubtitle.setText("");
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                // tag.albumArtImageView.setViewName("local:artist:cover:" + artistRef);
                // tag.divider.setViewName("local:artist:name:" + artistRef);
            }
            tag.albumArtImageView.loadArtForArtist(artist);
            tag.content = artist;
        } else {
            tag.tvTitle.setText(tag.tvTitle.getContext().getString(R.string.loading));
            tag.tvSubtitle.setText("");
            tag.albumArtImageView.setDefaultArt();
        }
    }

    private void updateAlbumTag(int i, ViewHolder tag) {
        String albumRef = mSearchResult.getAlbumsList().get(i);
        ProviderAggregator aggregator = ProviderAggregator.getDefault();
        Album album = aggregator.retrieveAlbum(albumRef, mSearchSource);

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
            tag.content = album;
        } else {
            tag.tvTitle.setText(tag.tvTitle.getContext().getString(R.string.loading));
            tag.tvSubtitle.setText("");
            tag.albumArtImageView.setDefaultArt();
        }
    }

    private void updatePlaylistTag(int i, ViewHolder tag) {
        String playlistRef = mSearchResult.getPlaylistList().get(i);
        Playlist playlist = ProviderAggregator.getDefault().retrievePlaylist(playlistRef, mSearchSource);

        if (playlist != null && playlist.isLoaded()) {
            tag.tvTitle.setText(playlist.getName());
            tag.tvSubtitle.setText(playlist.getSongsCount() + " songs");
            tag.content = playlist;
        } else {
            tag.tvTitle.setText(tag.tvTitle.getContext().getString(R.string.loading));
            tag.tvSubtitle.setText("");
            tag.albumArtImageView.setDefaultArt();
        }
    }

    @Override
    public boolean isChildSelectable(int i, int i2) {
        return mSearchResult != null && getGroup(i) != null;//i < getGroupCount() && i2 < getChildrenCount(i);

    }
}
