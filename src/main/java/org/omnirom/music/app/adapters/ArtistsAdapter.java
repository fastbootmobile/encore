package org.omnirom.music.app.adapters;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Handler;


import android.support.v7.graphics.Palette;
import android.support.v7.graphics.PaletteItem;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.omnirom.music.app.Utils;
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

    private static final int DEFERRED_DELAY = 20;

    private List<Artist> mArtists;
    private Handler mHandler;
    private int mScrollState;

    public static class ViewHolder {
        public LinearLayout llRoot;
        public ImageView ivCover;
        public TextView tvTitle;
        public int position;
        public Artist artist;
    }

    private AbsListView.OnScrollListener mScrollListener = new AbsListView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            mScrollState = scrollState;
        }

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

        }
    };

    private class BackgroundAsyncTask extends AsyncTask<ViewHolder, Void, BitmapDrawable> {
        private ViewHolder v;
        private int mPosition;
        private Artist mArtist;

        public BackgroundAsyncTask(int position) {
            mPosition = position;
        }

        @Override
        protected BitmapDrawable doInBackground(ViewHolder... params) {
            v = params[0];
            mArtist = v.artist;
            if (mScrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING) {
                try {
                    this.wait(DEFERRED_DELAY);
                } catch (Exception e) {
                    return null;
                }
            }

            if (v.position != this.mPosition || mArtist == null) {
                // Cancel, we moved
                Log.e("ARTIST", "We moved 2");
                return null;
            }
            final Resources res = v.llRoot.getResources();
            final Context ctx = v.llRoot.getContext().getApplicationContext();
            assert res != null;

            final ProviderCache cache = ProviderAggregator.getDefault().getCache();

            // Prepare the placeholder/default
            BitmapDrawable drawable = (BitmapDrawable) res.getDrawable(R.drawable.album_placeholder);
            assert drawable != null;
            drawable.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
            Bitmap bmp = drawable.getBitmap();
            String artKey = cache.getArtistArtKey(mArtist);
            Bitmap cachedImage = null;
            if (artKey != null) {
                cachedImage = ImageCache.getDefault().get(artKey);
            }
            if (cachedImage != null) {
                bmp = cachedImage;
            } else {
                String artUrl = null;

                if (artKey == null) {
                    StringBuffer urlBuffer = new StringBuffer();
                    artKey = AlbumArtCache.getArtKey(mArtist, urlBuffer);
                    artUrl = urlBuffer.toString();
                }

                if (artKey != null && !artKey.equals(AlbumArtCache.DEFAULT_ART)) {
                    bmp = AlbumArtCache.getOrDownloadArt(artKey, artUrl, bmp);
                }

                if (v.position != this.mPosition) {
                    // Cancel, we moved
                    Log.e("ARTIST", "We moved 3");
                    return null;
                }

            }

            BitmapDrawable output = new BitmapDrawable(res, bmp);

            cache.putArtistArtKey(mArtist, artKey);

            return output;
        }

        @Override
        protected void onPostExecute(BitmapDrawable result) {
            super.onPostExecute(result);

            if (v.position == mPosition && v.artist == mArtist && result != null) {
                applyItemImage(v, result);
            }
        }
    }

    public ArtistsAdapter() {
        mArtists = new ArrayList<Artist>();
        mHandler = new Handler();
    }

    public void registerScrollListener(AbsListView listView) {
        listView.setOnScrollListener(mScrollListener);
    }

    private void applyItemImage(ViewHolder tag, BitmapDrawable result) {
        tag.ivCover.setImageDrawable(result);

        // TEST - PALETTE
        final Resources res = tag.llRoot.getResources();
        Palette palette = Palette.generate(result.getBitmap());
        PaletteItem darkVibrantColor = palette.getDarkVibrantColor();
        PaletteItem darkMutedColor = palette.getDarkMutedColor();

        if (darkVibrantColor != null) {
            tag.llRoot.setBackgroundColor(darkVibrantColor.getRgb());
        } else if (darkMutedColor != null) {
            tag.llRoot.setBackgroundColor(darkMutedColor.getRgb());
        } else {
            tag.llRoot.setBackgroundColor(res.getColor(R.color.default_album_art_background));
        }
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
        mArtists.add(a);
        sortList();
    }

    public void addItemUnique(Artist a) {
        if (!mArtists.contains(a)) {
            mArtists.add(a);
            sortList();
        }
    }

    public void addAll(List<Artist> ps) {
        mArtists.addAll(ps);
        sortList();
        notifyDataSetChanged();
    }

    public void addAllUnique(List<Artist> ps) {
        boolean didChange = false;
        for (Artist p : ps) {
            if (!mArtists.contains(p)) {
                mArtists.add(p);
                didChange = true;
            }
        }

        if (didChange) {
            sortList();
            notifyDataSetChanged();
        }
    }

    public boolean contains(Artist p) {
        return mArtists.contains(p);
    }

    @Override
    public int getCount() {
        return mArtists.size();
    }

    @Override
    public Artist getItem(int position) {
        return mArtists.get(position);
    }

    @Override
    public long getItemId(int position) {
        return mArtists.get(position).getRef().hashCode();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Context ctx = parent.getContext();
        assert ctx != null;

        View root = convertView;
        if (convertView == null) {
            // Recycle the existing view
            LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            root = inflater.inflate(R.layout.medium_card_one_line, parent, false);
            assert root != null;

            ViewHolder holder = new ViewHolder();
            holder.ivCover = (ImageView) root.findViewById(R.id.ivCover);
            holder.tvTitle = (TextView) root.findViewById(R.id.tvTitle);
            holder.llRoot = (LinearLayout) root.findViewById(R.id.llRoot);

            root.setTag(holder);
        }

        // Fill in the fields
        final Artist artist = getItem(position);
        final ViewHolder tag = (ViewHolder) root.getTag();

        tag.artist = artist;
        tag.position = position;

        tag.ivCover.setViewName("grid:image:" + artist.getRef());
        tag.tvTitle.setViewName("grid:title:" + artist.getRef());

        if (artist.isLoaded()) {
            tag.tvTitle.setText(artist.getName());
        } else {
            tag.tvTitle.setText("Loading");
        }

        tag.ivCover.setImageResource(R.drawable.album_placeholder);
        tag.llRoot.setBackgroundColor(tag.llRoot.getResources().getColor(R.color.default_album_art_background));
        BackgroundAsyncTask task = new BackgroundAsyncTask(position);
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, tag);

        return root;
    }

}