package org.omnirom.music.app.adapters;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.omnirom.music.app.R;
import org.omnirom.music.app.ui.AlbumArtImageView;
import org.omnirom.music.framework.AlbumArtCache;
import org.omnirom.music.framework.ImageCache;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Album;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by h4o on 20/06/2014.
 */
public class AlbumsAdapter extends BaseAdapter {

    private String TAG = "AlbumsAdapter";
    private List<Album> mAlbums;

    private static class ViewHolder {
        public Album album;
        public AlbumArtImageView ivCover;
        public TextView tvTitle;
        public TextView tvSubTitle;
        public View vRoot;
        public int position;
    }

    public AlbumsAdapter() {
        mAlbums = new ArrayList<Album>();
    }

    private void sortList() {
        Collections.sort(mAlbums, new Comparator<Album>() {
            @Override
            public int compare(Album album, Album album2) {
                return album.getName().compareTo(album2.getName());
            }
        });
    }

    public void addItem(Album a) {
        mAlbums.add(a);
        sortList();
    }

    public void addItemUnique(Album a) {
        if (!mAlbums.contains(a)) {
            mAlbums.add(a);
            sortList();
        }
    }

    public void addAll(List<Album> ps) {
        mAlbums.addAll(ps);
        sortList();
    }

    public void addAllUnique(List<Album> ps) {
        boolean didChange = false;
        for (Album p : ps) {
            if (!mAlbums.contains(p)) {
                mAlbums.add(p);
                didChange = true;
            }
        }

        if (didChange) {
            sortList();
        }
    }

    public boolean contains(Album p) {
        return mAlbums.contains(p);
    }

    @Override
    public int getCount() {
        return mAlbums.size();
    }

    @Override
    public Album getItem(int position) {
        return mAlbums.get(position);
    }

    @Override
    public long getItemId(int position) {
        return mAlbums.get(position).getRef().hashCode();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Context ctx = parent.getContext();
        assert ctx != null;
        final Resources res = ctx.getResources();
        assert res != null;


        View root = convertView;
        if (convertView == null) {
            // Recycle the existing view
            LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            root = inflater.inflate(R.layout.medium_card_two_lines, parent, false);
            assert root != null;

            ViewHolder holder = new ViewHolder();
            holder.ivCover = (AlbumArtImageView) root.findViewById(R.id.ivCover);
            holder.tvTitle = (TextView) root.findViewById(R.id.tvTitle);
            holder.tvSubTitle = (TextView) root.findViewById(R.id.tvSubTitle);

            root.setTag(holder);
        }

        // Fill in the fields
        final Album album = getItem(position);
        final ViewHolder tag = (ViewHolder) root.getTag();

        tag.ivCover.setImageResource(R.drawable.album_placeholder);
        tag.position = position;
        tag.vRoot = root;
        tag.album = album;

        if (album.getName() != null && !album.getName().isEmpty()) {
            tag.tvTitle.setText(album.getName());

            if (album.getSongsCount() > 0) {
                tag.tvSubTitle.setText(album.getSongsCount() + " songs");
                tag.tvSubTitle.setVisibility(View.VISIBLE);
            } else {
                tag.tvSubTitle.setVisibility(View.INVISIBLE);
            }
            tag.ivCover.loadArtForAlbum(album);
        } else {
            tag.tvTitle.setText(res.getString(R.string.loading));
            tag.tvSubTitle.setText("");
        }

        return root;
    }

}