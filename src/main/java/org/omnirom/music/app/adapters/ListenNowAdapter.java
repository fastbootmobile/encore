package org.omnirom.music.app.adapters;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v7.graphics.Palette;
import android.support.v7.graphics.PaletteItem;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lucasr.twowayview.widget.StaggeredGridLayoutManager;
import org.omnirom.music.app.AlbumActivity;
import org.omnirom.music.app.ArtistActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.app.ui.AlbumArtImageView;
import org.omnirom.music.app.ui.MaterialTransitionDrawable;
import org.omnirom.music.framework.PlaybackProxy;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.BoundEntity;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.service.IPlaybackService;

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
        public int backColor;

        public ViewHolder(View itemView) {
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

    private AlbumArtImageView.OnArtLoadedListener mAlbumArtListener
            = new AlbumArtImageView.OnArtLoadedListener() {
        @Override
        public void onArtLoaded(final AlbumArtImageView view, final BitmapDrawable drawable) {
            final Resources res = view.getResources();

            if (drawable != null && drawable.getBitmap() != null) {
                Palette.generateAsync(drawable.getBitmap(), new Palette.PaletteAsyncListener() {
                    @Override
                    public void onGenerated(Palette palette) {
                        final PaletteItem item = palette.getDarkVibrantColor();
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
                                final ViewHolder holder = (ViewHolder) view.getTag();
                                holder.backColor = item.getRgb();
                                Utils.setViewBackground(holder.llRoot, transition);
                                transition.startTransition(1000);
                            }
                        });
                    }
                });
            }
        }
    };

    private View.OnClickListener mItemClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            ViewHolder holder = (ViewHolder) view.getTag();
            final int pos = holder.getPosition();
            final ListenNowEntry entry = mEntries.get(pos);

            // Get hero image and background color
            final Context ctx = holder.llRoot.getContext();
            MaterialTransitionDrawable draw = (MaterialTransitionDrawable) holder.ivCover.getDrawable();
            final Bitmap hero = draw.getFinalDrawable().getBitmap();
            final int backColor = holder.backColor;

            Intent intent = null;

            if (entry.entity instanceof Album) {
                Album album = (Album) entry.entity;
                intent = AlbumActivity.craftIntent(ctx, hero, album, backColor);
            } else if (entry.entity instanceof Artist) {
                Artist artist = (Artist) entry.entity;
                intent = ArtistActivity.craftIntent(ctx, hero, artist.getRef(), backColor);
            } else if (entry.entity instanceof Song) {
                Song song = (Song) entry.entity;
                playSong(song);
            }

            if (intent != null) {
                ctx.startActivity(intent);
            }
        }
    };

    /**
     * The list of entries to show
     */
    private List<ListenNowEntry> mEntries = new ArrayList<ListenNowEntry>();

    private Handler mHandler;

    public ListenNowAdapter() {
        mHandler = new Handler();
    }

    /**
     * Remove all entries
     */
    public void clearEntries() {
        mEntries.clear();
        notifyDataSetChanged();
    }

    /**
     * Add an entry to the list
     *
     * @param entry
     */
    public void addEntry(ListenNowEntry entry) {
        mEntries.add(entry);
        notifyItemInserted(mEntries.size() - 1);
    }

    /**
     * Returns whether or not the adapter contains the provided entity
     * @param entity The entity to check
     * @return The position of the item containing the provided entity, or -1 if not found
     */
    public int contains(BoundEntity entity) {
        int i = 0;
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
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        final LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
        final View view = inflater.inflate(R.layout.listen_now_entry, viewGroup, false);
        final ViewHolder holder = new ViewHolder(view);

        // Setup album art listener
        holder.ivCover.setOnArtLoadedListener(mAlbumArtListener);
        holder.llRoot.setOnClickListener(mItemClickListener);

        return holder;
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

        // Reset root color
        final int defaultColor = res.getColor(R.color.default_album_art_background);
        holder.llRoot.setBackgroundColor(defaultColor);
        holder.backColor = defaultColor;

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
                    holder.tvSubTitle.setText(null);
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
    }

    @Override
    public int getItemCount() {
        return mEntries.size();
    }

}
