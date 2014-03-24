package org.omnirom.music.app.adapters;

import android.content.Context;
import android.database.DataSetObserver;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;

import org.omnirom.music.app.R;
import org.omnirom.music.model.Playlist;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class PlaylistAdapter implements ListAdapter {

    private List<Playlist> mPlaylists;
    private List<DataSetObserver> mObservers;

    public PlaylistAdapter() {
        mPlaylists = new ArrayList<Playlist>();
        mObservers = new ArrayList<DataSetObserver>();
    }

    public void notifyDataSetChanged() {
        Log.e("ADAPTER", "NOTIFY");
        for (DataSetObserver obs : mObservers) {
            obs.onChanged();
        }
    }

    public void addItem(Playlist p) {
        mPlaylists.add(p);
        notifyDataSetChanged();
    }

    public void addAll(List<Playlist> ps) {
        mPlaylists.addAll(ps);
        notifyDataSetChanged();
    }

    public void addAllUnique(List<Playlist> ps) {
        boolean hasChanged = false;

        for (Playlist p : ps) {
            if (!mPlaylists.contains(p)) {
                mPlaylists.add(p);
                hasChanged = true;
            }
        }

        if (hasChanged) {
            notifyDataSetChanged();
        }
    }

    public boolean contains(Playlist p) {
        return mPlaylists.contains(p);
    }

    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }

    @Override
    public boolean isEnabled(int position) {
        return true;
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        mObservers.add(observer);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        mObservers.remove(observer);
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
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Context ctx = parent.getContext();
        assert ctx != null;

        View root = convertView;
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            root = inflater.inflate(R.layout.medium_card, null);
        }
        assert root != null;

        // ImageView ivCover = (ImageView) root.findViewById(R.id.ivCover);
        // ivCover.setImageResource(R.drawable.album_placeholder);

        TextView tvPlaylistName = (TextView) root.findViewById(R.id.tvTitle);
        TextView tvStats = (TextView) root.findViewById(R.id.tvSubTitle);

        Playlist playlist = getItem(position);
        if (playlist.isLoaded()) {
            tvPlaylistName.setText(playlist.getName());
            tvStats.setText("" + playlist.getSongsCount() + " songs");
        } else {
            tvPlaylistName.setText("Loading");
            tvStats.setText("Loading");
        }

        return root;
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return mPlaylists.isEmpty();
    }
}
