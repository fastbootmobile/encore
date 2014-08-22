package org.omnirom.music.app.adapters;

import android.content.res.Resources;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lucasr.twowayview.widget.StaggeredGridLayoutManager;
import org.omnirom.music.app.R;
import org.omnirom.music.app.ui.AlbumArtImageView;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.BoundEntity;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Guigui on 22/08/2014.
 */
public class ListenNowAdapter extends RecyclerView.Adapter<ListenNowAdapter.ViewHolder> {

    private static final String TAG = "ListenNowAdapter";

    /**
     * ViewHolder that holds references to the views
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final LinearLayout llRoot;
        public final TextView tvTitle;
        public final TextView tvSubTitle;
        public final AlbumArtImageView ivCover;

        public ViewHolder(View itemView) {
            super(itemView);
            llRoot = (LinearLayout) itemView.findViewById(R.id.llRoot);
            tvTitle = (TextView) itemView.findViewById(R.id.tvTitle);
            tvSubTitle = (TextView) itemView.findViewById(R.id.tvSubTitle);
            ivCover = (AlbumArtImageView) itemView.findViewById(R.id.ivCover);
        }
    }

    /**
     * Entry representing the contents and the way the grid entry in Listen Now should be displayed
     */
    public static class ListenNowEntry {
        public static final int ENTRY_SIZE_LARGE = 0;
        public static final int ENTRY_SIZE_MEDIUM = 1;

        /**
         * The size of the ListenNow entry. One of ListenNowEntry.ENTRY_SIZE_...
         */
        public int entrySize;

        /**
         * The entity to show
         */
        public BoundEntity entity;

        public ListenNowEntry(int entrySize, BoundEntity entity) {
            this.entrySize = entrySize;
            this.entity = entity;
        }
    }

    /**
     * The list of entries to show
     */
    private List<ListenNowEntry> mEntries = new ArrayList<ListenNowEntry>();

    /**
     * Remove all entries
     */
    public void clearEntries() {
        mEntries.clear();
        notifyDataSetChanged();
    }

    /**
     * Add an entry to the list
     * @param entry
     */
    public void addEntry(ListenNowEntry entry) {
        mEntries.add(entry);
        notifyItemInserted(mEntries.size() - 1);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        final LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
        final View view = inflater.inflate(R.layout.listen_now_entry, viewGroup, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int i) {
        final ListenNowEntry entry = mEntries.get(i);

        // Update span status
        final StaggeredGridLayoutManager.LayoutParams lp =
                (StaggeredGridLayoutManager.LayoutParams) holder.llRoot.getLayoutParams();

        int span = 1;
        switch (entry.entrySize) {
            case ListenNowEntry.ENTRY_SIZE_MEDIUM:
                span = 1;
                holder.llRoot.setOrientation(LinearLayout.VERTICAL);
                break;

            case ListenNowEntry.ENTRY_SIZE_LARGE:
                span = 2;
                holder.llRoot.setOrientation(LinearLayout.HORIZONTAL);
                break;
        }

        lp.span = span;
        holder.llRoot.setLayoutParams(lp);

        final Resources res = holder.llRoot.getResources();

        // Update entry contents
        if (entry.entity instanceof Playlist) {
            Playlist playlist = (Playlist) entry.entity;
            int count = playlist.getSongsCount();
            holder.tvTitle.setText(playlist.getName());
            holder.tvSubTitle.setText(res.getQuantityString(R.plurals.songs_count, count, count));
            holder.ivCover.loadArtForPlaylist(playlist);
        } else if (entry.entity instanceof Artist) {
            Artist artist = (Artist) entry.entity;
            int count = artist.getAlbums().size();
            holder.tvTitle.setText(artist.getName());
            holder.tvSubTitle.setText(res.getQuantityString(R.plurals.albums_count, count, count));
        } else if (entry.entity instanceof Album) {
            Album album = (Album) entry.entity;
            int count = album.getSongsCount();
            holder.tvTitle.setText(album.getName());
            holder.tvSubTitle.setText(res.getQuantityString(R.plurals.songs_count, count, count));
        } else if (entry.entity instanceof Song) {
            Song song = (Song) entry.entity;
            holder.tvTitle.setText(song.getTitle());
            holder.tvSubTitle.setText(song.getArtist());
        } else {
            Log.e(TAG, "Unsupported entity type: " + entry.entity);
        }
    }

    @Override
    public int getItemCount() {
        return mEntries.size();
    }

}
