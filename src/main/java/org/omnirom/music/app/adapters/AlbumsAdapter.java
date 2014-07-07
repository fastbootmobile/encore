package org.omnirom.music.app.adapters;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.omnirom.music.app.R;
import org.omnirom.music.framework.AlbumArtCache;
import org.omnirom.music.framework.ImageCache;
import org.omnirom.music.model.Album;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by h4o on 20/06/2014.
 */
public class AlbumsAdapter extends BaseAdapter {

    private static final int DEFERRED_DELAY = 20;
    private String TAG = "AlbumsAdapter";
    private List<Album> mAlbums;
    private Handler mHandler;
    private int mScrollState;

    private static class ViewHolder {
        public Album album;
        public ImageView ivCover;
        public TextView tvTitle;
        public TextView tvSubTitle;
        public View vRoot;
        public int position;
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
        private Album mAlbum;

        public BackgroundAsyncTask(int position) {
            mPosition = position;
        }

        @Override
        protected BitmapDrawable doInBackground(ViewHolder... params) {
            v = params[0];
            mAlbum = v.album;
            if (mScrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING) {
                try {
                    this.wait(DEFERRED_DELAY);
                } catch (Exception e) {
                    return null;
                }
            }

            if (v.position != this.mPosition || mAlbum == null) {
                // Cancel, we moved
                return null;
            }
            final Resources res = v.vRoot.getResources();
            final Context ctx = v.vRoot.getContext().getApplicationContext();
            assert res != null;

            final ProviderCache cache = ProviderAggregator.getDefault().getCache();

            // Prepare the placeholder/default
            BitmapDrawable drawable = (BitmapDrawable) res.getDrawable(R.drawable.album_placeholder);
            assert drawable != null;
            drawable.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
            Bitmap bmp = drawable.getBitmap();
            String artKey = cache.getAlbumArtKey(mAlbum);
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
                    artKey = AlbumArtCache.getArtKey(mAlbum, urlBuffer);
                    artUrl = urlBuffer.toString();
                }

                if (artKey != null && !artKey.equals(AlbumArtCache.DEFAULT_ART)) {
                    bmp = AlbumArtCache.getOrDownloadArt(artKey, artUrl, bmp);
                }

                if (v.position != this.mPosition) {
                    // Cancel, we moved
                    return null;
                }

            }

            BitmapDrawable output = new BitmapDrawable(res, bmp);

            cache.putAlbumArtKey(mAlbum, artKey);

            return output;
        }

        @Override
        protected void onPostExecute(BitmapDrawable result) {
            super.onPostExecute(result);

            if (v.position == mPosition && v.album == mAlbum && result != null) {

                v.ivCover.setImageDrawable(result);
            } else if (result != null) {
                Log.e(TAG, "we have a result too late...");
            }
        }
    }

    public AlbumsAdapter() {
        mAlbums = new ArrayList<Album>();
        mHandler = new Handler();
    }

    private void sortList() {
        Collections.sort(mAlbums, new Comparator<Album>() {
            @Override
            public int compare(Album album, Album album2) {
                return album.getName().compareTo(album2.getName());
            }
        });
    }

    public void addItem(Album a) {
        mAlbums.add(a);
        sortList();
    }

    public void addItemUnique(Album a) {
        if (!mAlbums.contains(a)) {
            mAlbums.add(a);
            sortList();
        }
    }

    public void addAll(List<Album> ps) {
        mAlbums.addAll(ps);
        sortList();
        notifyDataSetChanged();
    }

    public void addAllUnique(List<Album> ps) {
        boolean didChange = false;
        for (Album p : ps) {
            if (!mAlbums.contains(p)) {
                mAlbums.add(p);
                didChange = true;
            }
        }

        if (didChange) {
            sortList();
            notifyDataSetChanged();
        }
    }

    public void registerScrollListener(AbsListView listView) {
        listView.setOnScrollListener(mScrollListener);
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
        Log.e(TAG, "we get view position " + position);
        View root = convertView;
        if (convertView == null) {
            // Recycle the existing view
            LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            root = inflater.inflate(R.layout.medium_card_two_lines, null);
            assert root != null;

            ViewHolder holder = new ViewHolder();
            holder.ivCover = (ImageView) root.findViewById(R.id.ivCover);
            holder.tvTitle = (TextView) root.findViewById(R.id.tvTitle);
            holder.tvSubTitle = (TextView) root.findViewById(R.id.tvSubTitle);

            root.setTag(holder);
        }

        // Fill in the fields
        final Album album = getItem(position);
        final ViewHolder tag = (ViewHolder) root.getTag();

        tag.ivCover.setImageResource(R.drawable.album_placeholder);
        tag.position = position;
        tag.vRoot = root;
        tag.album = album;
        if (album.isLoaded()) {
            tag.tvTitle.setText(album.getName());
            tag.tvSubTitle.setText(album.getSongsCount() + " songs");
        } else {
            tag.tvTitle.setText("Loading");
            tag.tvSubTitle.setText("");
        }
        final String artKey = ProviderAggregator.getDefault().getCache().getAlbumArtKey(album);

        final Resources res = root.getResources();
        assert res != null;

        if (artKey != null) {
            Log.e(TAG, "we have an art key " + artKey + " album: " + album.getName());
            // We already know the album art for this song (keyed in artKey)

            BackgroundAsyncTask task = new BackgroundAsyncTask(position);
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, tag);


        } else {
            BackgroundAsyncTask task = new BackgroundAsyncTask(position);
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, tag);
            //task.execute(tag);
        }

        return root;
    }

}