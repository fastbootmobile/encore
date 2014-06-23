package org.omnirom.music.app.adapters;

import android.content.Context;
import android.os.Handler;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import org.omnirom.music.model.Artist;

import org.omnirom.music.app.R;
import org.omnirom.music.model.Artist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


/**
 * Created by h4o on 20/06/2014.
 */
public class ArtistsAdapter extends BaseAdapter {

    private static final int DEFERRED_DELAY = 20;

    private List<Artist> mArtists;
    private Handler mHandler;

    private static class ViewHolder {
        public ImageView ivCover;
        public TextView tvTitle;
        public TextView tvSubTitle;
    }

    public ArtistsAdapter() {
        mArtists = new ArrayList<Artist>();
        mHandler = new Handler();
    }

    private void sortList() {
        Collections.sort(mArtists, new Comparator<Artist>() {
            @Override
            public int compare(Artist artist, Artist artist2) {
                return artist.getName().compareTo(artist2.getName());
            }
        });
    }

    public void addItem(Artist a) {
        mArtists.add(a);
        sortList();
    }

    public void addItemUnique(Artist a) {
        if (!mArtists.contains(a)) {
            mArtists.add(a);
            sortList();
        }
    }

    public void addAll(List<Artist> ps) {
        mArtists.addAll(ps);
        sortList();
        notifyDataSetChanged();
    }

    public void addAllUnique(List<Artist> ps) {
        boolean didChange = false;
        for (Artist p : ps) {
            if (!mArtists.contains(p)) {
                mArtists.add(p);
                didChange = true;
            }
        }

        if (didChange) {
            sortList();
            notifyDataSetChanged();
        }
    }

    public boolean contains(Artist p) {
        return mArtists.contains(p);
    }

    @Override
    public int getCount() {
        return mArtists.size();
    }

    @Override
    public Artist getItem(int position) {
        return mArtists.get(position);
    }

    @Override
    public long getItemId(int position) {
        return mArtists.get(position).getRef().hashCode();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Context ctx = parent.getContext();
        assert ctx != null;

        View root = convertView;
        if (convertView == null) {
            // Recycle the existing view
            LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            root = inflater.inflate(R.layout.medium_card, null);
            assert root != null;

            ViewHolder holder = new ViewHolder();
            holder.ivCover = (ImageView) root.findViewById(R.id.ivCover);
            holder.tvTitle = (TextView) root.findViewById(R.id.tvTitle);
            holder.tvSubTitle = (TextView) root.findViewById(R.id.tvSubTitle);

            root.setTag(holder);
        }

        // Fill in the fields
        final Artist artist = getItem(position);
        final ViewHolder tag = (ViewHolder) root.getTag();

        tag.ivCover.setImageResource(R.drawable.album_placeholder);

        if (artist.isLoaded()) {
            tag.tvTitle.setText(artist.getName());
            tag.tvSubTitle.setText("");
        } else {
            tag.tvTitle.setText("Loading");
            tag.tvSubTitle.setText("");
        }

        return root;
    }

}