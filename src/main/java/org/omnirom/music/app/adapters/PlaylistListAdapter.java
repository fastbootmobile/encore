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

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.h6ah4i.android.widget.advrecyclerview.draggable.DraggableItemAdapter;
import com.h6ah4i.android.widget.advrecyclerview.draggable.ItemDraggableRange;
import com.h6ah4i.android.widget.advrecyclerview.draggable.RecyclerViewDragDropManager;
import com.h6ah4i.android.widget.advrecyclerview.utils.AbstractDraggableItemViewHolder;

import org.json.JSONException;
import org.omnirom.music.app.PlaylistActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.ui.AlbumArtImageView;
import org.omnirom.music.app.ui.MaterialTransitionDrawable;
import org.omnirom.music.framework.PlaylistOrderer;
import org.omnirom.music.model.BoundEntity;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.utils.Utils;
import org.omnirom.music.utils.ViewUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Adapter to display a list of {@link org.omnirom.music.model.Playlist} in a RecyclerView
 */
public class PlaylistListAdapter extends RecyclerView.Adapter<PlaylistListAdapter.ViewHolder>
        implements DraggableItemAdapter<PlaylistListAdapter.ViewHolder> {

    private List<Playlist> mPlaylists;
    private PlaylistOrderer mOrderer;

    /**
     * Default constructor
     */
    public PlaylistListAdapter() {
        mPlaylists = new ArrayList<>();

        // DraggableItemAdapter requires stable ID
        setHasStableIds(true);
    }

    private void ensureOrderer(Context context) {
        if (mOrderer == null && context != null) {
            mOrderer = new PlaylistOrderer(context);
        } else if (mOrderer == null) {
            throw new IllegalArgumentException("Orderer context is null and orderer is null too!");
        }
    }

    /**
     * Sorts the list alphabetically
     */
    public void sortList(Context context) throws JSONException {
        ensureOrderer(context);
        final Map<String, Integer> order = mOrderer.getOrder();

        Collections.sort(mPlaylists, new Comparator<Playlist>() {
            public int compare(Playlist o1, Playlist o2) {
                int index1 = order.containsKey(o1.getRef()) ? order.get(o1.getRef()) : -1;
                int index2 = order.containsKey(o2.getRef()) ? order.get(o2.getRef()) : -1;
                return index1 - index2;
            }
        });
    }

    /**
     * Adds the playlist to the adapter if it's not already there
     *
     * @param p The playlist to add
     */
    public boolean addItemUnique(Playlist p) {
        if (!mPlaylists.contains(p)) {
            mPlaylists.add(p);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Add all the elements into the adapter if they're not already there
     *
     * @param ps The collection of {@link org.omnirom.music.model.Playlist} to add
     */
    public void addAllUnique(Collection<Playlist> ps) {
        for (Playlist p : ps) {
            if (p != null && !mPlaylists.contains(p)) {
                mPlaylists.add(p);
            }
        }
    }

    /**
     * Returns whether or not the adapter contains the provided Playlist
     *
     * @param p The playlist to check
     * @return True if the adapter already has the playlist, false otherwise
     */
    public boolean contains(Playlist p) {
        return mPlaylists.contains(p);
    }

    @Override
    public long getItemId(int position) {
        return mPlaylists.get(position).getRef().hashCode();
    }

    @Override
    public int getItemViewType(int position) {
        return 1;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        final View root = inflater.inflate(R.layout.item_playlist_list, parent, false);
        ensureOrderer(parent.getContext());
        return new ViewHolder(root);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        final Playlist item = mPlaylists.get(position);
        holder.tvPlaylistName.setText(item.getName());
        holder.ivCover.loadArtForPlaylist(item);

        if (item.isLoaded() || item.getSongsCount() > 0) {
            holder.tvPlaylistDesc.setText(holder.tvPlaylistDesc.getContext().getString(R.string.xx_songs,
                    item.getSongsCount()));

            holder.ivOfflineStatus.setVisibility(View.VISIBLE);

            switch (item.getOfflineStatus()) {
                case BoundEntity.OFFLINE_STATUS_NO:
                    holder.ivOfflineStatus.setVisibility(View.GONE);
                    break;

                case BoundEntity.OFFLINE_STATUS_DOWNLOADING:
                    holder.ivOfflineStatus.setImageResource(R.drawable.ic_sync_in_progress);
                    break;

                case BoundEntity.OFFLINE_STATUS_ERROR:
                    holder.ivOfflineStatus.setImageResource(R.drawable.ic_sync_problem);
                    break;

                case BoundEntity.OFFLINE_STATUS_PENDING:
                    holder.ivOfflineStatus.setImageResource(R.drawable.ic_track_download_pending);
                    break;

                case BoundEntity.OFFLINE_STATUS_READY:
                    holder.ivOfflineStatus.setImageResource(R.drawable.ic_track_downloaded);
                    break;
            }
        } else {
            holder.tvPlaylistDesc.setText(null);
            holder.ivOfflineStatus.setVisibility(View.GONE);
        }

        final int dragState = holder.getDragStateFlags();

        if (((dragState & RecyclerViewDragDropManager.STATE_FLAG_IS_UPDATED) != 0)) {
            int bgColor = 0;

            if ((dragState & RecyclerViewDragDropManager.STATE_FLAG_IS_ACTIVE) != 0) {
                bgColor = 0xFFDDDDDD;
            } else if ((dragState & RecyclerViewDragDropManager.STATE_FLAG_DRAGGING) != 0) {
                bgColor = 0xFFAAAAAA;
            }

            if (bgColor != 0) {
                holder.container.setBackgroundColor(bgColor);
            } else {
                int[] attrs = new int[] { android.R.attr.selectableItemBackground /* index 0 */};
                TypedArray ta = holder.container.getContext().obtainStyledAttributes(attrs);
                Drawable drawableFromTheme = ta.getDrawable(0 /* index */);
                ta.recycle();
                holder.container.setBackground(drawableFromTheme);
            }
        }

        holder.container.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Context ctx = v.getContext();
                Intent intent = PlaylistActivity.craftIntent(ctx, item,
                        ((MaterialTransitionDrawable) holder.ivCover.getDrawable()).getFinalDrawable().getBitmap());

                if (Utils.hasLollipop()) {
                    ActivityOptions opt = ActivityOptions.makeSceneTransitionAnimation((Activity) ctx,
                            holder.ivCover, "itemImage");
                    ctx.startActivity(intent, opt.toBundle());
                } else {
                    ctx.startActivity(intent);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return mPlaylists.size();
    }

    @Override
    public void onMoveItem(int fromPosition, int toPosition) {
        if (fromPosition == toPosition) {
            return;
        }

        Playlist p = mPlaylists.remove(fromPosition);
        mPlaylists.add(toPosition, p);
        ensureOrderer(null);
        mOrderer.setOrder(mPlaylists);

        notifyItemMoved(fromPosition, toPosition);
    }

    @Override
    public boolean onCheckCanStartDrag(ViewHolder holder, int x, int y) {
        // x, y --- relative from the itemView's top-left
        final View containerView = holder.container;
        final View dragHandleView = holder.ivDragHandle;

        final int offsetX = containerView.getLeft() + (int) (ViewCompat.getTranslationX(containerView) + 0.5f);
        final int offsetY = containerView.getTop() + (int) (ViewCompat.getTranslationY(containerView) + 0.5f);

        return ViewUtils.hitTest(dragHandleView, x - offsetX, y - offsetY);
    }

    @Override
    public ItemDraggableRange onGetItemDraggableRange(ViewHolder holder) {
        // no drag-sortable range specified
        return null;
    }


    /**
     * ViewHolder for items
     */
    public static class ViewHolder extends AbstractDraggableItemViewHolder {
        public ViewGroup container;
        public View ivDragHandle;
        public AlbumArtImageView ivCover;
        public ImageView ivOfflineStatus;
        public TextView tvPlaylistName;
        public TextView tvPlaylistDesc;

        public ViewHolder(View v) {
            super(v);
            container = (ViewGroup) v.findViewById(R.id.container);
            ivDragHandle = v.findViewById(R.id.ivDragHandle);
            ivCover = (AlbumArtImageView) v.findViewById(R.id.ivCover);
            ivOfflineStatus = (ImageView) v.findViewById(R.id.ivOfflineStatus);
            tvPlaylistName = (TextView) v.findViewById(R.id.tvPlaylistName);
            tvPlaylistDesc = (TextView) v.findViewById(R.id.tvPlaylistDesc);
        }
    }

}
