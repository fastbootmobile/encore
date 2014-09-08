package org.omnirom.music.app.adapters;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Build;
import android.os.Handler;
import android.support.v7.graphics.Palette;
import android.support.v7.graphics.PaletteItem;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.app.ui.AlbumArtImageView;
import org.omnirom.music.model.Artist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


/**
 * Created by h4o on 20/06/2014.
 */
public class ArtistsAdapter extends RecyclerView.Adapter<ArtistsAdapter.ViewHolder> {

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

    private final AlbumArtImageView.OnArtLoadedListener mAlbumArtListener = new AlbumArtImageView.OnArtLoadedListener() {
        @Override
        public void onArtLoaded(final AlbumArtImageView view, final BitmapDrawable drawable) {
            final Resources res = view.getResources();

            Palette.generateAsync(drawable.getBitmap(), new Palette.PaletteAsyncListener() {
                @Override
                public void onGenerated(Palette palette) {
                    final int defaultColor = res.getColor(R.color.default_album_art_background);

                    final PaletteItem darkVibrantColor = palette.getDarkVibrantColor();
                    final PaletteItem darkMutedColor = palette.getDarkMutedColor();

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

    public ArtistsAdapter() {
        mArtists = new ArrayList<Artist>();
        mHandler = new Handler();
        mComparator = new Comparator<Artist>() {
            @Override
            public int compare(Artist artist, Artist artist2) {
                if (artist.isLoaded() && artist2.isLoaded()) {
                    return artist.getName().compareTo(artist2.getName());
                } else {
                    return 0;
                }
            }
        };
    }

    private void sortList() {
        Collections.sort(mArtists, mComparator);
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

    public boolean contains(final Artist p) {
        synchronized (mArtists) {
            return mArtists.contains(p);
        }
    }

    public int indexOf(final Artist a) {
        synchronized (mArtists) {
            return mArtists.indexOf(a);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        final LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
        final View view = inflater.inflate(R.layout.medium_card_one_line, viewGroup, false);
        final ViewHolder holder = new ViewHolder(view);

        // Setup album art listener
        holder.ivCover.setOnArtLoadedListener(mAlbumArtListener);

        return holder;
    }

    @Override
    public void onBindViewHolder(ArtistsAdapter.ViewHolder tag, int position) {
        // Fill in the fields
        final Artist artist = getItem(position);

        // If we're not already displaying the right stuff, reset it and show it
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
    }

    @Override
    public int getItemCount() {
        synchronized (mArtists) {
            return mArtists.size();
        }
    }

    public Artist getItem(int position) {
        synchronized (mArtists) {
            return mArtists.get(position);
        }
    }
}