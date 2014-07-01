package org.omnirom.music.app.adapters;

import android.content.Context;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.omnirom.music.app.R;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 *
 */
public class PlaylistListAdapter extends BaseAdapter {

    private static final int DEFERRED_DELAY = 20;

    private List<Playlist> mPlaylists;
    private Handler mHandler;

    private static class ViewHolder {
        public ImageView ivCover;
        public TextView tvTitle;
        public TextView tvSubTitle;
    }

    public PlaylistListAdapter() {
        mPlaylists = new ArrayList<Playlist>();
        mHandler = new Handler();
    }

    private void sortList() {
       /* Collections.sort(mPlaylists, new Comparator<Playlist>() {
            @Override
            public int compare(Playlist playlist, Playlist playlist2) {
                if(playlist != null && playlist2 != null)
                    return playlist.getName().compareTo(playlist2.getName());
                else
                    return 0;
            }
        });*/
    }

    public void addItem(Playlist p) {
        mPlaylists.add(p);
        sortList();
    }

    public void addItemUnique(Playlist p) {
        if (!mPlaylists.contains(p)) {
            mPlaylists.add(p);
            sortList();
        }
    }

    public void addAll(List<Playlist> ps) {
        mPlaylists.addAll(ps);
        sortList();
        notifyDataSetChanged();
    }

    public void addAllUnique(List<Playlist> ps) {
        boolean didChange = false;
        for (Playlist p : ps) {
            if (!mPlaylists.contains(p)) {
                mPlaylists.add(p);
                didChange = true;
            }
        }

        if (didChange) {
            sortList();
            notifyDataSetChanged();
        }
    }

    public boolean contains(Playlist p) {
        return mPlaylists.contains(p);
    }

    @Override
    public int getCount() {
        return mPlaylists.size();
    }

    @Override
    public Playlist getItem(int position) {
        return mPlaylists.get(position);
    }

    @Override
    public long getItemId(int position) {
        return mPlaylists.get(position).getRef().hashCode();
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
        final Playlist playlist = getItem(position);
        final ViewHolder tag = (ViewHolder) root.getTag();

        tag.ivCover.setImageResource(R.drawable.album_placeholder);

        if (playlist.isLoaded()) {
            tag.tvTitle.setText(playlist.getName());
            tag.tvSubTitle.setText("" + playlist.getSongsCount() + " songs");
        } else {
            tag.tvTitle.setText("Loading");
            tag.tvSubTitle.setText("Loading");
        }

        return root;
    }

}
