package org.omnirom.music.app.adapters;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Build;
import android.os.Handler;
import android.support.v7.graphics.Palette;
import android.support.v7.graphics.PaletteItem;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.app.ui.AlbumArtImageView;
import org.omnirom.music.model.Album;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by h4o on 20/06/2014.
 */
public class AlbumsAdapter extends BaseAdapter {

    private String TAG = "AlbumsAdapter";
    private List<Album> mAlbums;
    private Handler mHandler;

    public static class ViewHolder {
        public Album album;
        public AlbumArtImageView ivCover;
        public TextView tvTitle;
        public TextView tvSubTitle;
        public View vRoot;
        public int position;
        public int itemColor;
    }

    public AlbumsAdapter() {
        mAlbums = new ArrayList<Album>();
        mHandler = new Handler();
    }

    private void sortList() {
        Collections.sort(mAlbums, new Comparator<Album>() {
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
        });
    }

    public void addItem(Album a) {
        mAlbums.add(a);
        sortList();
    }

    public boolean addItemUnique(Album a) {
        if (!mAlbums.contains(a)) {
            mAlbums.add(a);
            sortList();
            return true;
        } else {
            return false;
        }
    }

    public void addAll(List<Album> ps) {
        mAlbums.addAll(ps);
        sortList();
    }

    public boolean addAllUnique(List<Album> ps) {
        boolean didChange = false;
        for (Album p : ps) {
            if (!mAlbums.contains(p) && p.isLoaded()) {
                mAlbums.add(p);
                didChange = true;
            }
        }

        if (didChange) {
            sortList();
        }

        return didChange;
    }

    public boolean contains(Album p) {
        return mAlbums.contains(p);
    }

    @Override
    public int getCount() {
        return mAlbums.size();
    }

    @Override
    public Album getItem(int position) {
        return mAlbums.get(position);
    }

    @Override
    public long getItemId(int position) {
        return mAlbums.get(position).getRef().hashCode();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Context ctx = parent.getContext();
        assert ctx != null;
        final Resources res = ctx.getResources();
        assert res != null;


        View root = convertView;
        if (convertView == null) {
            // Recycle the existing view
            LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            root = inflater.inflate(R.layout.medium_card_two_lines, parent, false);
            assert root != null;

            ViewHolder holder = new ViewHolder();
            holder.ivCover = (AlbumArtImageView) root.findViewById(R.id.ivCover);
            holder.tvTitle = (TextView) root.findViewById(R.id.tvTitle);
            holder.tvSubTitle = (TextView) root.findViewById(R.id.tvSubTitle);

            root.setTag(holder);
        }

        // Fill in the fields
        final Album album = getItem(position);
        final ViewHolder tag = (ViewHolder) root.getTag();

        if (tag.album == null || !tag.album.equals(album)) {
            tag.position = position;
            tag.vRoot = root;
            tag.album = album;

            final int defaultColor = res.getColor(R.color.default_album_art_background);
            tag.vRoot.setBackgroundColor(defaultColor);
            tag.itemColor = defaultColor;

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                // tag.ivCover.setViewName("list:albums:cover:" + album.getRef());
                // tag.tvTitle.setViewName("list:albums:title:" + album.getRef());
            }

            if (album.getName() != null && !album.getName().isEmpty()) {
                tag.tvTitle.setText(album.getName());

                if (album.getSongsCount() > 0) {
                    tag.tvSubTitle.setText(res.getQuantityString(R.plurals.songs_count, album.getSongsCount(), album.getSongsCount()));
                    tag.tvSubTitle.setVisibility(View.VISIBLE);
                } else {
                    tag.tvSubTitle.setVisibility(View.INVISIBLE);
                }

                tag.ivCover.loadArtForAlbum(album);
                tag.ivCover.setOnArtLoadedListener(new AlbumArtImageView.OnArtLoadedListener() {
                    @Override
                    public void onArtLoaded(AlbumArtImageView view, BitmapDrawable drawable) {
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
                                            Utils.setViewBackground(tag.vRoot, transitionDrawable);
                                            transitionDrawable.startTransition(1000);
                                            tag.itemColor = targetColor;
                                        } else {
                                            tag.vRoot.setBackgroundColor(targetColor);
                                        }

                                    }
                                });
                            }
                        });
                    }
                });
            } else {
                tag.tvTitle.setText(res.getString(R.string.loading));
                tag.tvSubTitle.setText("");
            }
        }

        return root;
    }

}