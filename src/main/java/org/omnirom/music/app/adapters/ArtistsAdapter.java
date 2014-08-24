package org.omnirom.music.app.adapters;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;


import android.support.v7.graphics.Palette;
import android.support.v7.graphics.PaletteItem;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.omnirom.music.app.Utils;
import org.omnirom.music.app.ui.AlbumArtImageView;
import org.omnirom.music.framework.AlbumArtCache;
import org.omnirom.music.framework.ImageCache;
import org.omnirom.music.model.Artist;

import org.omnirom.music.app.R;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


/**
 * Created by h4o on 20/06/2014.
 */
public class ArtistsAdapter extends BaseAdapter {

    private final List<Artist> mArtists;
    private Handler mHandler;

    public static class ViewHolder {
        public LinearLayout llRoot;
        public AlbumArtImageView ivCover;
        public TextView tvTitle;
        public Bitmap srcBitmap;
        public int position;
        public Artist artist;
        public int itemColor;
    }

    public ArtistsAdapter() {
        mArtists = new ArrayList<Artist>();
        mHandler = new Handler();
    }

    private void sortList() {
        Collections.sort(mArtists, new Comparator<Artist>() {
            @Override
            public int compare(Artist artist, Artist artist2) {
                return artist.getName().compareTo(artist2.getName());
            }
        });
    }

    public void addItem(Artist a) {
        synchronized (mArtists) {
            mArtists.add(a);
            sortList();
        }
    }

    public void addItemUnique(Artist a) {
        synchronized (mArtists) {
            if (!mArtists.contains(a)) {
                mArtists.add(a);
                sortList();
            }
        }
    }

    public void addAll(List<Artist> ps) {
        synchronized (mArtists) {
            mArtists.addAll(ps);
            sortList();
        }
    }

    public void addAllUnique(List<Artist> ps) {
        synchronized (mArtists) {
            boolean didChange = false;
            for (Artist p : ps) {
                if (!mArtists.contains(p)) {
                    mArtists.add(p);
                    didChange = true;
                }
            }

            if (didChange) {
                sortList();
            }
        }
    }

    public boolean contains(Artist p) {
        synchronized (mArtists) {
            return mArtists.contains(p);
        }
    }

    @Override
    public int getCount() {
        synchronized (mArtists) {
            return mArtists.size();
        }
    }

    @Override
    public Artist getItem(int position) {
        synchronized (mArtists) {
            return mArtists.get(position);
        }
    }

    @Override
    public long getItemId(int position) {
        synchronized (mArtists) {
            return mArtists.get(position).getRef().hashCode();
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        synchronized (mArtists) {
            Context ctx = parent.getContext();
            assert ctx != null;

            View root = convertView;
            if (convertView == null) {
                // Recycle the existing view
                LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                root = inflater.inflate(R.layout.medium_card_one_line, parent, false);
                assert root != null;

                ViewHolder holder = new ViewHolder();
                holder.ivCover = (AlbumArtImageView) root.findViewById(R.id.ivCover);
                holder.tvTitle = (TextView) root.findViewById(R.id.tvTitle);
                holder.llRoot = (LinearLayout) root.findViewById(R.id.llRoot);
                holder.srcBitmap = ((BitmapDrawable) ctx.getResources().getDrawable(R.drawable.album_placeholder)).getBitmap();

                root.setTag(holder);
            }

            // Fill in the fields
            final Artist artist = getItem(position);
            final ViewHolder tag = (ViewHolder) root.getTag();

            // If we're not already displaying the right stuff, reset it and show it
            if (tag.artist == null || !tag.artist.isIdentical(artist)) {
                tag.artist = artist;
                tag.position = position;

                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                    // tag.ivCover.setViewName("grid:image:" + artist.getRef());
                    // tag.tvTitle.setViewName("grid:title:" + artist.getRef());
                }

                if (artist.isLoaded()) {
                    tag.tvTitle.setText(artist.getName());
                } else {
                    tag.tvTitle.setText("...");
                }

                // Load the artist art
                final Resources res = tag.llRoot.getResources();
                final int defaultColor = res.getColor(R.color.default_album_art_background);

                tag.llRoot.setBackgroundColor(defaultColor);
                tag.itemColor = defaultColor;
                tag.ivCover.loadArtForArtist(artist);
                tag.ivCover.setOnArtLoadedListener(new AlbumArtImageView.OnArtLoadedListener() {
                    @Override
                    public void onArtLoaded(AlbumArtImageView view, BitmapDrawable drawable) {
                        tag.srcBitmap = drawable.getBitmap();

                        Palette.generateAsync(drawable.getBitmap(), new Palette.PaletteAsyncListener() {
                            @Override
                            public void onGenerated(final Palette palette) {
                                mHandler.post(new Runnable() {
                                    public void run() {
                                        PaletteItem darkVibrantColor = palette.getDarkVibrantColor();
                                        PaletteItem darkMutedColor = palette.getDarkMutedColor();

                                        int targetColor = defaultColor;

                                        if (darkVibrantColor != null) {
                                            targetColor = darkVibrantColor.getRgb();
                                        } else if (darkMutedColor != null) {
                                            targetColor = darkMutedColor.getRgb();
                                        }

                                        if (targetColor != defaultColor) {
                                            ColorDrawable drawable1 = new ColorDrawable(defaultColor);
                                            ColorDrawable drawable2 = new ColorDrawable(targetColor);
                                            TransitionDrawable transitionDrawable
                                                    = new TransitionDrawable(new Drawable[]{drawable1, drawable2});
                                            Utils.setViewBackground(tag.llRoot, transitionDrawable);
                                            transitionDrawable.startTransition(1000);
                                            tag.itemColor = targetColor;
                                        } else {
                                            tag.llRoot.setBackgroundColor(targetColor);
                                        }

                                    }
                                });
                            }
                        });
                    }
                });
            }

            return root;
        }
    }

}