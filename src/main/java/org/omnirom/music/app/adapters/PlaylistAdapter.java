package org.omnirom.music.app.adapters;

import android.content.Context;
import android.database.DataSetObserver;
import android.os.Handler;
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
    private List<Playlist> mPendingPlaylists;
    private Handler mHandler;

    private Runnable mDeferredUpdate = new Runnable() {
        @Override
        public void run() {
            if (mPendingPlaylists.size() > 0) {
                addItem(mPendingPlaylists.get(0));
                mPendingPlaylists.remove(0);

                if (mPendingPlaylists.size() > 0) {
                    mHandler.postDelayed(mDeferredUpdate, 50);
                }
            }
        }
    };

    private static class ViewHolder {
        public ImageView ivCover;
        public TextView tvTitle;
        public TextView tvSubTitle;
    }

    public PlaylistAdapter() {
        mPlaylists = new ArrayList<Playlist>();
        mPendingPlaylists = new ArrayList<Playlist>();
        mObservers = new ArrayList<DataSetObserver>();
        mHandler = new Handler();
    }

    public void notifyDataSetChanged() {
        for (DataSetObserver obs : mObservers) {
            obs.onChanged();
        }
    }

    public void addItem(Playlist p) {
        mPlaylists.add(p);
        notifyDataSetChanged();
    }

    public void addAll(List<Playlist> ps) {
        mPendingPlaylists.addAll(ps);
        mHandler.postDelayed(mDeferredUpdate, 50);
    }

    public void addAllUnique(List<Playlist> ps) {
        for (Playlist p : ps) {
            if (!mPlaylists.contains(p)) {
                mPendingPlaylists.add(p);
            }
        }

        mHandler.postDelayed(mDeferredUpdate, 50);
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
        Playlist playlist = getItem(position);
        ViewHolder tag = (ViewHolder) root.getTag();

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
