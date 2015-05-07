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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Build;
import android.os.Handler;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lucasr.twowayview.widget.StaggeredGridLayoutManager;
import org.omnirom.music.app.AlbumActivity;
import org.omnirom.music.app.ArtistActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.ui.AlbumArtImageView;
import org.omnirom.music.app.ui.MaterialTransitionDrawable;
import org.omnirom.music.framework.PlaybackProxy;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.BoundEntity;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Guigui on 22/08/2014.
 */
public class ListenNowAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String TAG = "ListenNowAdapter";

    private static final int ITEM_TYPE_ENTRY = 1000;
    private static final int ITEM_TYPE_HEADER = 2000;

    /**
     * ViewHolder that holds references to the views
     */
    public static class EntryViewHolder extends RecyclerView.ViewHolder {
        public final LinearLayout llRoot;
        public final TextView tvTitle;
        public final TextView tvSubTitle;
        public final AlbumArtImageView ivCover;
        public int backColor;
        public BoundEntity entity;

        public EntryViewHolder(View itemView) {
            super(itemView);
            llRoot = (LinearLayout) itemView.findViewById(R.id.llRoot);
            tvTitle = (TextView) itemView.findViewById(R.id.tvTitle);
            tvSubTitle = (TextView) itemView.findViewById(R.id.tvSubTitle);
            ivCover = (AlbumArtImageView) itemView.findViewById(R.id.ivCover);

            // Attach the viewholder to the cover album art for the album art callback, and to
            // the root view for click listening
            ivCover.setTag(this);
            llRoot.setTag(this);
        }
    }

    public static class HeaderViewHolder extends RecyclerView.ViewHolder {
        public final FrameLayout flRoot;
        public final TextView tvHeader;

        public HeaderViewHolder(View itemView) {
            super(itemView);
            flRoot = (FrameLayout) itemView.findViewById(R.id.flRoot);
            tvHeader = (TextView) itemView.findViewById(R.id.tvHeader);
        }
    }

    /**
     * Entry representing the contents and the way the grid entry in Listen Now should be displayed
     */
    public static class ListenNowEntry {
        public static final int ENTRY_SIZE_LARGE = 0;
        public static final int ENTRY_SIZE_MEDIUM = 1;
        public static final int ENTRY_SIZE_SMALL = 2;

        /**
         * The size of the ListenNow entry. One of ListenNowEntry.ENTRY_SIZE_...
         */
        public int entrySize;

        /**
         * The entity to show
         */
        public BoundEntity entity;

        public ListenNowEntry(int entrySize, BoundEntity entity) {
            if (entity == null) {
                throw new IllegalArgumentException("Entity cannot be null");
            }

            this.entrySize = entrySize;
            this.entity = entity;
        }
    }

    private AlbumArtImageView.OnArtLoadedListener mAlbumArtListener
            = new AlbumArtImageView.OnArtLoadedListener() {
        @Override
        public void onArtLoaded(final AlbumArtImageView view, final BitmapDrawable drawable) {
            final Resources res = view.getResources();

            if (drawable != null && drawable.getBitmap() != null) {
                Palette.from(drawable.getBitmap()).generate(new Palette.PaletteAsyncListener() {
                    @Override
                    public void onGenerated(Palette palette) {
                        final Palette.Swatch item = palette.getDarkVibrantSwatch();
                        if (item == null) {
                            return;
                        }

                        final TransitionDrawable transition = new TransitionDrawable(new Drawable[]{
                                new ColorDrawable(res.getColor(R.color.default_album_art_background)),
                                new ColorDrawable(item.getRgb())
                        });

                        // Set the backgroud in the UI thread
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                final EntryViewHolder holder = (EntryViewHolder) view.getTag();
                                holder.backColor = item.getRgb();
                                Utils.setViewBackground(holder.llRoot, transition);
                                transition.startTransition(500);
                            }
                        });
                    }
                });
            }
        }
    };


    private View.OnClickListener mItemClickListener = new View.OnClickListener() {
        @Override
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public void onClick(View view) {
            final EntryViewHolder holder = (EntryViewHolder) view.getTag();

            // Get hero image and background color
            MaterialTransitionDrawable draw = (MaterialTransitionDrawable) holder.ivCover.getDrawable();
            final Bitmap hero = draw.getFinalDrawable().getBitmap();
            final int backColor = holder.backColor;
            Intent intent = null;
            ActivityOptions opt = null;
            final AlbumArtImageView ivCover = holder.ivCover;

            final Context ctx = holder.llRoot.getContext();
            if (holder.entity instanceof Album) {
                Album album = (Album) holder.entity;
                intent = AlbumActivity.craftIntent(ctx, hero, album.getRef(), album.getProvider(),
                        backColor);

                if (Utils.hasLollipop()) {
                    opt = ActivityOptions.makeSceneTransitionAnimation((Activity) ivCover.getContext(),
                            new Pair<View, String>(ivCover, "itemImage"));
                }
            } else if (holder.entity instanceof Artist) {
                Artist artist = (Artist) holder.entity;
                intent = ArtistActivity.craftIntent(ctx, hero, artist.getRef(),
                        artist.getProvider(), backColor);

                if (Utils.hasLollipop()) {
                    opt = ActivityOptions.makeSceneTransitionAnimation((Activity) ivCover.getContext(),
                            new Pair<View, String>(ivCover, "itemImage"));
                }
            } else if (holder.entity instanceof Song) {
                Song song = (Song) holder.entity;
                playSong(song);
            }

            if (intent != null) {
                if (opt != null) {
                    ctx.startActivity(intent, opt.toBundle());
                } else {
                    ctx.startActivity(intent);
                }
            }
        }
    };

    /**
     * The list of entries to show
     */
    private List<ListenNowEntry> mRecentEntries = new ArrayList<>();
    private List<ListenNowEntry> mEntries = new ArrayList<>();

    private Handler mHandler;

    public ListenNowAdapter() {
        mHandler = new Handler();
        setHasStableIds(true);
    }

    /**
     * Remove all entries
     */
    public void clearEntries() {
        mRecentEntries.clear();
        mEntries.clear();
        notifyDataSetChanged();
    }

    /**
     * Add an entry to the suggestions list
     * @param entry The entry
     */
    public void addEntry(ListenNowEntry entry) {
        mEntries.add(entry);
        notifyItemInserted(mEntries.size() - 1);
    }

    /**
     * Adds a recent entry to the list of recent activity
     * @param entry The entry
     */
    public void addRecentEntry(ListenNowEntry entry) {
        mRecentEntries.add(entry);
        notifyItemInserted(mRecentEntries.size());
    }

    /**
     * Returns whether or not the adapter contains the provided entity
     * @param entity The entity to check
     * @return The position of the item containing the provided entity, or -1 if not found
     */
    public int contains(BoundEntity entity) {
        int i = 1;
        for (ListenNowEntry entry : mRecentEntries) {
            if (entry.entity.equals(entity)) {
                return i;
            }
            ++i;
        }
        ++i;
        for (ListenNowEntry entry : mEntries) {
            if (entry.entity.equals(entity)) {
                return i;
            }
            ++i;
        }

        return -1;
    }

    private void playSong(Song s) {
        PlaybackProxy.playSong(s);
    }


    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            // Recent activity header
            return ITEM_TYPE_HEADER;
        } else if (position > 0 && position <= mRecentEntries.size()) {
            // Recent activity item
            return ITEM_TYPE_ENTRY;
        } else if (position == mRecentEntries.size() + 1) {
            // Suggestions header
            return ITEM_TYPE_HEADER;
        } else {
            // Suggestion item
            return ITEM_TYPE_ENTRY;
        }
    }

    @Override
    public long getItemId(int position) {
        if (position == 0) {
            // Recent activity header
            return 0;
        } else if (position > 0 && position <= mRecentEntries.size()) {
            if (mRecentEntries.size() > position - 1) {
                BoundEntity ent = mRecentEntries.get(position - 1).entity;
                if (ent != null) {
                    return ent.getRef().hashCode();
                } else {
                    return -1;
                }
            } else {
                return -1;
            }
        } else if (position == mRecentEntries.size() + 1) {
            return 1;
        } else {
            BoundEntity ent = mEntries.get(position - 2 - mRecentEntries.size()).entity;
            if (ent != null) {
                return ent.getRef().hashCode();
            } else {
                return -1;
            }
        }
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, final int viewType) {
        final LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());

        if (viewType == ITEM_TYPE_HEADER) {
            final View view = inflater.inflate(R.layout.item_listen_now_header, viewGroup, false);
            final HeaderViewHolder holder = new HeaderViewHolder(view);

            holder.flRoot.setAlpha(0);
            holder.flRoot.animate().alpha(1.0f).start();

            return holder;
        } else {
            final View view = inflater.inflate(R.layout.item_listen_now_entry, viewGroup, false);
            final EntryViewHolder holder = new EntryViewHolder(view);

            // Setup album art listener
            holder.ivCover.setOnArtLoadedListener(mAlbumArtListener);
            holder.llRoot.setOnClickListener(mItemClickListener);

            holder.llRoot.setAlpha(0.0f);
            holder.llRoot.animate().alpha(1).setDuration(300)
                    .setInterpolator(new DecelerateInterpolator(1.5f)).start();

            return holder;
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holderBase, int i) {
        final int viewType = getItemViewType(i);

        if (viewType == ITEM_TYPE_HEADER) {
            final HeaderViewHolder holder = (HeaderViewHolder) holderBase;
            if (i == 0) {
                holder.tvHeader.setText(R.string.recently_listened_to);
            } else {
                holder.tvHeader.setText(R.string.listen_now_suggestions_header);
            }

            // Update span status
            final StaggeredGridLayoutManager.LayoutParams lp =
                    (StaggeredGridLayoutManager.LayoutParams) holder.flRoot.getLayoutParams();
            lp.span = 2;
            holder.flRoot.setLayoutParams(lp);
        } else if (viewType == ITEM_TYPE_ENTRY) {
            final EntryViewHolder holder = (EntryViewHolder) holderBase;
            final ListenNowEntry entry = (i > 0 && i <= mRecentEntries.size()) ? mRecentEntries.get(i - 1)
                    : mEntries.get(i - 2 - mRecentEntries.size());

            // Update span status
            final StaggeredGridLayoutManager.LayoutParams lp =
                    (StaggeredGridLayoutManager.LayoutParams) holder.llRoot.getLayoutParams();

            int span = 1;
            int height = ViewGroup.LayoutParams.WRAP_CONTENT;
            switch (entry.entrySize) {
                case ListenNowEntry.ENTRY_SIZE_SMALL:
                    span = 1;
                    holder.llRoot.setOrientation(LinearLayout.VERTICAL);
                    height = Utils.dpToPx(Resources.getSystem(), 64);
                    holder.tvSubTitle.setVisibility(View.GONE);
                    break;

                case ListenNowEntry.ENTRY_SIZE_MEDIUM:
                    span = 1;
                    holder.llRoot.setOrientation(LinearLayout.VERTICAL);
                    holder.tvSubTitle.setVisibility(View.VISIBLE);
                    break;

                case ListenNowEntry.ENTRY_SIZE_LARGE:
                    span = 2;
                    holder.llRoot.setOrientation(LinearLayout.HORIZONTAL);
                    holder.tvSubTitle.setVisibility(View.VISIBLE);
                    break;
            }

            lp.span = span;
            holder.llRoot.setLayoutParams(lp);

            // Limit cover art size on small cards
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.ivCover.getLayoutParams();
            params.height = height;
            holder.ivCover.setForceDisableSquared(height != ViewGroup.LayoutParams.WRAP_CONTENT);
            holder.ivCover.setLayoutParams(params);

            final Resources res = holder.llRoot.getResources();

            if (!entry.entity.equals(holder.entity)) {
                // Reset root color
                final int defaultColor = res.getColor(R.color.default_album_art_background);
                holder.llRoot.setBackgroundColor(defaultColor);
                holder.backColor = defaultColor;
            }

            // Update entry contents
            if (entry.entity instanceof Playlist) {
                Playlist playlist = (Playlist) entry.entity;
                int count = playlist.getSongsCount();
                holder.tvTitle.setText(playlist.getName());
                holder.tvSubTitle.setText(res.getQuantityString(R.plurals.songs_count, count, count));
                holder.ivCover.loadArtForPlaylist(playlist);
            } else if (entry.entity instanceof Artist) {
                Artist artist = (Artist) entry.entity;
                holder.tvTitle.setText(artist.getName());
                holder.tvSubTitle.setText(null);
                holder.ivCover.loadArtForArtist(artist);
            } else if (entry.entity instanceof Album) {
                Album album = (Album) entry.entity;
                holder.tvTitle.setText(album.getName());
                String artistRef = Utils.getMainArtist(album);
                if (artistRef != null) {
                    Artist artist = ProviderAggregator.getDefault().retrieveArtist(artistRef, album.getProvider());
                    if (artist != null) {
                        holder.tvSubTitle.setText(artist.getName());
                    } else {
                        holder.tvSubTitle.setText("...");
                    }
                } else {
                    holder.tvSubTitle.setText(null);
                }
                holder.ivCover.loadArtForAlbum(album);
            } else if (entry.entity instanceof Song) {
                Song song = (Song) entry.entity;
                holder.tvTitle.setText(song.getTitle());
                holder.tvSubTitle.setText(song.getArtist());
                holder.ivCover.loadArtForSong(song);
            } else {
                Log.e(TAG, "Unsupported entity type: " + entry.entity);
            }

            holder.entity = entry.entity;
        }
    }

    @Override
    public int getItemCount() {
        return mRecentEntries.size() + mEntries.size() + 2;
    }

}
