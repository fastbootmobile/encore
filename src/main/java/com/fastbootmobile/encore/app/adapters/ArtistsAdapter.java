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

package com.fastbootmobile.encore.app.adapters;

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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.fastbootmobile.encore.app.ArtistActivity;
import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.app.ui.AlbumArtImageView;
import com.fastbootmobile.encore.model.Artist;
import com.fastbootmobile.encore.utils.Utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


/**
 * Adapter for RecyclerView to display artists in a grid
 */
public class ArtistsAdapter extends RecyclerView.Adapter<ArtistsAdapter.ViewHolder> {

    /**
     * ViewHolder
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final LinearLayout llRoot;
        public final AlbumArtImageView ivCover;
        public final TextView tvTitle;
        public Bitmap srcBitmap;
        public int position;
        public Artist artist;
        public int itemColor;

        public ViewHolder(View item) {
            super(item);
            ivCover = (AlbumArtImageView) item.findViewById(R.id.ivCover);
            tvTitle = (TextView) item.findViewById(R.id.tvTitle);
            llRoot = (LinearLayout) item.findViewById(R.id.llRoot);
            srcBitmap = ((BitmapDrawable) item.getResources().getDrawable(R.drawable.album_placeholder)).getBitmap();

            ivCover.setTag(this);
            llRoot.setTag(this);
        }
    }

    private final View.OnClickListener mItemClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            final ArtistsAdapter.ViewHolder tag = (ArtistsAdapter.ViewHolder) v.getTag();
            final Context ctx = v.getContext();
            Artist artist = getItem(tag.position);
            Intent intent = ArtistActivity.craftIntent(ctx, tag.srcBitmap, artist.getRef(),
                    artist.getProvider(), tag.itemColor);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AlbumArtImageView ivCover = tag.ivCover;
                ActivityOptions opt = ActivityOptions.makeSceneTransitionAnimation((Activity) v.getContext(),
                        ivCover, "itemImage");

                ctx.startActivity(intent, opt.toBundle());
            } else {
                ctx.startActivity(intent);
            }
        }
    };




    private final AlbumArtImageView.OnArtLoadedListener mAlbumArtListener
            = new AlbumArtImageView.OnArtLoadedListener() {
        @Override
        public void onArtLoaded(final AlbumArtImageView view, final BitmapDrawable drawable) {
            final Resources res = view.getResources();

            Palette.from(drawable.getBitmap()).generate(new Palette.PaletteAsyncListener() {
                @Override
                public void onGenerated(Palette palette) {
                    final int defaultColor = res.getColor(R.color.default_album_art_background);

                    final Palette.Swatch darkVibrantColor = palette.getDarkVibrantSwatch();
                    final Palette.Swatch darkMutedColor = palette.getDarkMutedSwatch();

                    int targetColor = defaultColor;

                    if (darkVibrantColor != null) {
                        targetColor = darkVibrantColor.getRgb();
                    } else if (darkMutedColor != null) {
                        targetColor = darkMutedColor.getRgb();
                    }

                    final TransitionDrawable transition = new TransitionDrawable(new Drawable[]{
                            new ColorDrawable(res.getColor(R.color.default_album_art_background)),
                            new ColorDrawable(targetColor)
                    });

                    // Set the background in the UI thread
                    final int finalColor = targetColor;
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            final ViewHolder holder = (ViewHolder) view.getTag();
                            holder.srcBitmap = drawable.getBitmap();
                            holder.itemColor = finalColor;
                            if (finalColor != defaultColor) {
                                Utils.setViewBackground(holder.llRoot, transition);
                                transition.startTransition(1000);
                            } else {
                                holder.llRoot.setBackgroundColor(finalColor);
                            }
                        }
                    });
                }
            });
        }
    };

    private final List<Artist> mArtists;
    private final Handler mHandler;
    private final Comparator<Artist> mComparator;

    /**
     * Default constructor
     */
    public ArtistsAdapter() {
        mArtists = new ArrayList<>();
        mHandler = new Handler();
        mComparator = new Comparator<Artist>() {
            @Override
            public int compare(Artist artist, Artist artist2) {
                if (artist.isLoaded() && artist2.isLoaded() && artist.getName() != null
                        && artist2.getName() != null) {
                    return artist.getName().toLowerCase().compareTo(artist2.getName().toLowerCase());
                } else {
                    return artist.getRef().compareTo(artist2.getRef());
                }
            }
        };
    }

    /**
     * Sorts the list in alphabetical order
     */
    private void sortList() {
        Collections.sort(mArtists, mComparator);
    }

    /**
     * Adds an item to the adapter
     * @param a The artist to add
     */
    public void addItem(Artist a) {
        synchronized (mArtists) {
            mArtists.add(a);
            sortList();
        }
    }

    /**
     * Adds an item to the adapter if it's not already there
     * @param a The artist to add
     */
    public void addItemUnique(Artist a) {
        synchronized (mArtists) {
            if (!mArtists.contains(a)) {
                mArtists.add(a);
                sortList();
            }
        }
    }

    /**
     * Add all the elements of the collection to the adapter
     * @param ps The collection of Artist to add
     */
    public void addAll(Collection<Artist> ps) {
        synchronized (mArtists) {
            mArtists.addAll(ps);
            sortList();
        }
    }

    /**
     * Add all the elements of the collection to the adapter if they're not already there
     * @param ps The collection of Artist to add
     */
    public void addAllUnique(Collection<Artist> ps) {
        synchronized (mArtists) {
            boolean didChange = false;
            for (Artist p : ps) {
                if (!mArtists.contains(p) && p != null) {
                    mArtists.add(p);
                    didChange = true;
                }
            }

            if (didChange) {
                sortList();
            }
        }
    }

    /**
     * Returns whether or not the adapter contains the provided artist
     * @param p The artist to check
     * @return true if the adapter contains the item, false otherwise
     */
    public boolean contains(final Artist p) {
        synchronized (mArtists) {
            return mArtists.contains(p);
        }
    }

    /**
     * Returns the position of the artist in the list
     * @param a The artist to get the position
     * @return The index of the item, or -1 if not found
     */
    public int indexOf(final Artist a) {
        synchronized (mArtists) {
            return mArtists.indexOf(a);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        final LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
        final View view = inflater.inflate(R.layout.medium_card_one_line, viewGroup, false);
        final ViewHolder holder = new ViewHolder(view);

        // Setup album art listener
        holder.ivCover.setOnArtLoadedListener(mAlbumArtListener);

        return holder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onBindViewHolder(ArtistsAdapter.ViewHolder tag, int position) {
        // Fill in the fields
        final Artist artist = getItem(position);

        // If we're not already displaying the right stuff, reset it and show it
        tag.artist = artist;
        tag.position = position;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tag.ivCover.setTransitionName("grid:image:" + artist.getRef());
            tag.tvTitle.setTransitionName("grid:title:" + artist.getRef());
        }

        if (artist.isLoaded() || (artist.getName() != null && !artist.getName().isEmpty())) {
            tag.tvTitle.setText(artist.getName());
        } else {
            tag.tvTitle.setText(null);
        }

        // Set the event listener
        tag.llRoot.setOnClickListener(mItemClickListener);

        // Load the artist art
        final Resources res = tag.llRoot.getResources();
        final int defaultColor = res.getColor(R.color.default_album_art_background);

        tag.llRoot.setBackgroundColor(defaultColor);
        tag.itemColor = defaultColor;
        if (artist.getName() != null) {
            tag.ivCover.loadArtForArtist(artist);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getItemCount() {
        synchronized (mArtists) {
            return mArtists.size();
        }
    }

    /**
     * Returns the item at the provided position
     * @param position The position of the item
     * @return The {@link com.fastbootmobile.encore.model.Artist} at the provided position
     */
    public Artist getItem(int position) {
        synchronized (mArtists) {
            return mArtists.get(position);
        }
    }
}