package org.omnirom.music.app.adapters;

import android.content.Context;
import android.database.DataSetObserver;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListAdapter;

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

        LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View root = inflater.inflate(R.layout.medium_card, null);
        assert root != null;

        ImageView ivCover = (ImageView) root.findViewById(R.id.ivCover);
        ivCover.setImageResource(R.drawable.album_placeholder);

        return null;
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
