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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Build;
import android.os.Handler;
import android.support.v7.graphics.Palette;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.utils.Utils;
import com.fastbootmobile.encore.app.ui.AlbumArtImageView;
import com.fastbootmobile.encore.app.ui.MaterialTransitionDrawable;
import com.fastbootmobile.encore.model.Album;
import com.fastbootmobile.encore.providers.ProviderAggregator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Adapter for ListView to show a list of albums
 */
public class AlbumsAdapter extends BaseAdapter {
    private final List<Album> mAlbums;
    private final Handler mHandler;
    private final int mDefaultArtColor;

    private AlbumArtImageView.OnArtLoadedListener mArtListener = new AlbumArtImageView.OnArtLoadedListener() {
        @Override
        public void onArtLoaded(final AlbumArtImageView view, final BitmapDrawable drawable) {
            Palette.from(drawable.getBitmap()).generate(new Palette.PaletteAsyncListener() {
                @Override
                public void onGenerated(final Palette palette) {
                    final Palette.Swatch darkVibrantColor = palette.getDarkVibrantSwatch();
                    final Palette.Swatch darkMutedColor = palette.getDarkMutedSwatch();

                    mHandler.post(new Runnable() {
                        public void run() {
                            int targetColor = mDefaultArtColor;
                            ViewHolder tag = (ViewHolder) view.getTag();

                            if (darkVibrantColor != null) {
                                targetColor = darkVibrantColor.getRgb();
                            } else if (darkMutedColor != null) {
                                targetColor = darkMutedColor.getRgb();
                            }

                            if (targetColor != mDefaultArtColor) {
                                Drawable bg = tag.vRoot.getBackground();
                                TransitionDrawable background;
                                if (bg instanceof TransitionDrawable) {
                                    background = (TransitionDrawable) tag.vRoot.getBackground();
                                    ColorDrawable drawable1 = (ColorDrawable) background.getDrawable(0);
                                    ColorDrawable drawable2 = (ColorDrawable) background.getDrawable(1);
                                    drawable1.setColor(mDefaultArtColor);
                                    drawable2.setColor(targetColor);
                                } else {
                                    ColorDrawable drawable1 = new ColorDrawable(mDefaultArtColor);
                                    ColorDrawable drawable2 = new ColorDrawable(targetColor);
                                    background = new TransitionDrawable(new Drawable[]{drawable1, drawable2});
                                }

                                Utils.setViewBackground(tag.vRoot, background);
                                background.startTransition((int) MaterialTransitionDrawable.DEFAULT_DURATION);
                                tag.itemColor = targetColor;
                            } else {
                                tag.vRoot.setBackgroundColor(targetColor);
                            }

                        }
                    });
                }
            });
        }
    };

    private Comparator<Album> mComparator = new Comparator<Album>() {
        @Override
        public int compare(Album album, Album album2) {
            if (album.getName() != null && album2.getName() != null) {
                return album.getName().compareTo(album2.getName());
            } else if (album.getName() == null) {
                return -1;
            } else {
                return 1;
            }
        }
    };

    /**
     * ViewHolder class for Albums
     */
    public static class ViewHolder {
        public View vRoot;
        public AlbumArtImageView ivCover;
        public TextView tvTitle;
        public Album album;
        public int position;
        public int itemColor;
        public boolean offlineMode;

        public ViewHolder(View root) {
            vRoot = root;
            ivCover = (AlbumArtImageView) root.findViewById(R.id.ivCover);
            tvTitle = (TextView) root.findViewById(R.id.tvTitle);

            vRoot.setTag(this);
            ivCover.setTag(this);
        }
    }

    /**
     * Default constructor
     */
    public AlbumsAdapter(Resources res) {
        mAlbums = new ArrayList<>();
        mHandler = new Handler();
        mDefaultArtColor = res.getColor(R.color.default_album_art_background);
    }

    /**
     * Sorts the list of albums alphabetically
     */
    private void sortListLocked() {
        Collections.sort(mAlbums, mComparator);
    }

    /**
     * Adds an album to the adapter
     * @param a The album to add
     */
    public void addItem(Album a) {
        synchronized (mAlbums) {
            mAlbums.add(a);
            sortListLocked();
        }
    }

    /**
     * Adds an album to the adapter if it isn't already existing
     * @param a The album to add
     * @return True if the item has been added, false otherwise
     */
    public boolean addItemUnique(Album a) {
        synchronized (mAlbums) {
            if (!mAlbums.contains(a)) {
                mAlbums.add(a);
                sortListLocked();
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Add all the elements of the input collection into the adapter
     * @param ps The elements to add
     */
    public void addAll(Collection<Album> ps) {
        synchronized (mAlbums) {
            mAlbums.addAll(ps);
            sortListLocked();
        }
    }

    /**
     * Add all the elements of the input collection assuming they're not already in the adapter
     * @param ps The elements to add
     * @return True if at least one iteam has been aded
     */
    public boolean addAllUnique(List<Album> ps) {
        boolean didChange = false;

        synchronized (mAlbums) {
            for (Album p : ps) {
                if (!mAlbums.contains(p) && p.isLoaded()) {
                    mAlbums.add(p);
                    didChange = true;
                }
            }

            if (didChange) {
                sortListLocked();
            }
        }

        return didChange;
    }

    /**
     * Returns whether or not the adapter contains the provided album
     * @param p The album
     * @return true if the adapter contains the provided album
     */
    public boolean contains(Album p) {
        synchronized (mAlbums) {
            return mAlbums.contains(p);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCount() {
        synchronized (mAlbums) {
            return mAlbums.size();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Album getItem(int position) {
        synchronized (mAlbums) {
            return mAlbums.get(position);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getItemId(int position) {
        synchronized (mAlbums) {
            return mAlbums.get(position).getRef().hashCode();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Context ctx = parent.getContext();
        assert ctx != null;
        final Resources res = ctx.getResources();
        assert res != null;

        View root = convertView;
        if (convertView == null) {
            // Recycle the existing view
            LayoutInflater inflater = LayoutInflater.from(ctx);
            root = inflater.inflate(R.layout.medium_card_one_line, parent, false);
            assert root != null;
            root.setTag(new ViewHolder(root));
        }

        // Fill in the fields
        final Album album = getItem(position);
        final ViewHolder tag = (ViewHolder) root.getTag();
        final boolean offlineMode = ProviderAggregator.getDefault().isOfflineMode();

        if (tag.album == null || !tag.album.equals(album) || offlineMode != tag.offlineMode) {
            tag.offlineMode = offlineMode;
            tag.position = position;
            tag.vRoot = root;
            tag.album = album;


            tag.vRoot.setBackgroundColor(mDefaultArtColor);
            tag.itemColor = mDefaultArtColor;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tag.ivCover.setTransitionName("list:albums:cover:" + album.getRef());
                tag.tvTitle.setTransitionName("list:albums:title:" + album.getRef());
            }

            if (album.getName() != null && !album.getName().isEmpty()) {
                tag.tvTitle.setText(album.getName());
                tag.ivCover.setOnArtLoadedListener(mArtListener);
                tag.ivCover.loadArtForAlbum(album);
            } else {
                tag.tvTitle.setText(res.getString(R.string.loading));
            }
        }

        return root;
    }

}