package org.omnirom.music.app.adapters;

import android.content.Context;
import android.opengl.Visibility;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;

import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.app.ui.AlbumArtImageView;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.SearchResult;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderCache;
import org.w3c.dom.Text;

import java.util.List;

/**
 * Created by h4o on 22/07/2014.
 */
public class SearchAdapter extends BaseExpandableListAdapter{
    private SearchResult searchResult;
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
        if(searchResult != null)
            return 4;
        else
            return 0;
    }
    public void updateSearchResults(SearchResult searchResult){
        this.searchResult = searchResult;
    }
    @Override
    public int getChildrenCount(int i) {

        List childs = getGroup(i);
        if(childs != null){
            if(childs.size() == 0)
                return 0;
            else
                return Math.min(10, childs.size() + 1);
        }
        return 0;
    }

    @Override
    public List getGroup(int i) {
        if(searchResult != null) {
            switch (i) {
                case ARTIST:
                    return searchResult.getArtistList();
                case ALBUM:
                    return searchResult.getAlbumsList();
                case SONG:
                    return searchResult.getSongsList();
                case PLAYLIST:
                    return searchResult.getPlaylistList();
            }
        }
        return null;
    }

    @Override
    public Object getChild(int i, int i2) {
        return getGroup(i).get(i2);
    }

    @Override
    public long getGroupId(int i) {
        return 0;
    }

    @Override
    public long getChildId(int i, int i2) {
        return 0;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public View getGroupView(int i, boolean b, View view, ViewGroup parent) {
        final Context ctx = parent.getContext();
        assert ctx != null;
        if(view == null){
            LayoutInflater inflater =(LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.item_group_separator,null);
        }
        TextView textView = (TextView) view.findViewById(R.id.tv_search_separator);
        String title;
        switch (i){
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
        if(getChildrenCount(i) == 0)
            view.setVisibility(View.GONE);
        return view;
    }

    @Override
    public View getChildView(int i, int i2, boolean b, View root, ViewGroup parent) {
        final Context ctx = parent.getContext();
        assert ctx != null;
        if(root == null){
            LayoutInflater inflater =(LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            root = inflater.inflate(R.layout.item_search_element,null);
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
       if(i2 == getChildrenCount(i)-1){
            tag.albumArtImageView.setVisibility(View.INVISIBLE);
            tag.tvTitle.setText("More");
            tag.tvSubtitle.setText("");

        } else {
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
                    break;
            }
        }
        return root;
    }
    private void updateSongTag(int i, ViewHolder tag){
        String songRef = searchResult.getSongsList().get(i);
        Song song = ProviderAggregator.getDefault().getCache().getSong(songRef);
        if(song != null && song.isLoaded()){
            tag.tvTitle.setText(song.getTitle());
            tag.tvSubtitle.setText(ProviderAggregator.getDefault().getCache().getArtist(song.getArtist()).getName());
            tag.albumArtImageView.setVisibility(View.VISIBLE);
            tag.albumArtImageView.loadArtForSong(song);
            tag.content = song;
        }
    }
    private void updateArtistTag(int i, ViewHolder tag){
        String artistRef = searchResult.getArtistList().get(i);
        Artist artist = ProviderAggregator.getDefault().getCache().getArtist(artistRef);
        if(artist != null && artist.isLoaded()){
            tag.tvTitle.setText(artist.getName());
            tag.tvSubtitle.setText("");
            tag.albumArtImageView.setViewName("local:artist:cover:"+artistRef);
            tag.divider.setViewName("local:artist:name:"+artistRef);
            tag.albumArtImageView.setVisibility(View.VISIBLE);
            tag.albumArtImageView.loadArtForArtist(artist);
            tag.content = artist;
        }
    }
    private void updateAlbumTag(int i, ViewHolder tag){
        String albumRef = searchResult.getAlbumsList().get(i);
        Album album = ProviderAggregator.getDefault().getCache().getAlbum(albumRef);
        Log.d(TAG,albumRef + " album : "+album);
        if(album != null){
            tag.tvTitle.setText(album.getName());
                tag.tvSubtitle.setText(""+album.getYear());
            tag.albumArtImageView.setViewName("local:album:cover:"+albumRef);
            tag.albumArtImageView.setVisibility(View.VISIBLE);
            tag.divider.setViewName("local:album:title:"+albumRef);
            tag.albumArtImageView.loadArtForAlbum(album);
            tag.content = album;
        }
    }
    private void updatePlaylistTag(int i, ViewHolder tag){
        String playlistRef = searchResult.getPlaylistList().get(i);
        Playlist playlist = ProviderAggregator.getDefault().getCache().getPlaylist(playlistRef);
        if(playlist != null && playlist.isLoaded()){
            tag.tvTitle.setText(playlist.getName());
            tag.tvSubtitle.setText(playlist.getSongsCount()+" songs");
            tag.albumArtImageView.setVisibility(View.INVISIBLE);
            tag.content = playlist;
        }
    }
    @Override
    public boolean isChildSelectable(int i, int i2) {
        return  searchResult != null && getGroup(i) != null;//i < getGroupCount() && i2 < getChildrenCount(i);

    }
}
