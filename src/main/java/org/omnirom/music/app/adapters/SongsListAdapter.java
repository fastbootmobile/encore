package org.omnirom.music.app.adapters;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.TextView;

import org.omnirom.music.app.MainActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.app.fragments.PlaylistChooserFragment;
import org.omnirom.music.app.fragments.PlaylistViewFragment;
import org.omnirom.music.app.ui.VuMeterView;
import org.omnirom.music.framework.AlbumArtCache;
import org.omnirom.music.framework.BlurCache;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by h4o on 19/06/2014.
 */
public class SongsListAdapter  extends BaseAdapter{
    List<Song> mSongs;
    private int mItemWidth;
    private int mItemHeight;


    public SongsListAdapter(Context ctx){
        final Resources res = ctx.getResources();
        assert res != null;
        mSongs = new ArrayList<Song>();
        // Theoretically, we'd need the width and height of the root view. However, this thread
        // might (and probably will) run before the view has been layout'd by the system, thus
        // getMeasuredXxxx() returns 0, which is invalid for the thumbnail we want. Instead,
        // we use the defined height dimension, and the screen width.
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        mItemWidth = size.x;
        mItemHeight = res.getDimensionPixelSize(R.dimen.playlist_view_item_height);
    }
    private static class ViewHolder {
        public TextView tvTitle;
        public TextView tvArtist;
        public TextView tvDuration;
        public View vRoot;
        public int position;
        public Song song;
    }
    public void put(Song song){
        mSongs.add(song);
    }
    public void sortAll(){
        Collections.sort(mSongs, new Comparator<Song>() {
            @Override
            public int compare(Song lhs, Song rhs) {
                return lhs.getTitle().compareTo(rhs.getTitle());
            }
        });

    }
    private class BackgroundAsyncTask extends AsyncTask<ViewHolder, Void, BitmapDrawable> {
        private ViewHolder v;
        private int mPosition;
        private Song mSong;

        public BackgroundAsyncTask(int position) {
            mPosition = position;
        }

        @Override
        protected BitmapDrawable doInBackground(ViewHolder... params) {
            v = params[0];
            mSong = v.song;

            if (v.position != this.mPosition || mSong == null) {
                // Cancel, we moved
                return null;
            }

            final Resources res = v.vRoot.getResources();
            final Context ctx = v.vRoot.getContext().getApplicationContext();
            assert res != null;

            final ProviderCache cache = ProviderAggregator.getDefault().getCache();

            // Prepare the placeholder/default
            BitmapDrawable drawable = (BitmapDrawable) res.getDrawable(R.drawable.album_list_default_bg);
            assert drawable != null;
            drawable.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
            Bitmap bmp;

            try {
                bmp = PluginsLookup.getDefault().getProvider(mSong.getProvider()).getBinder().getSongArt(mSong);

            } catch (Exception e){
                bmp  = drawable.getBitmap();
            }
            String artKey;
            BitmapDrawable output;
            Bitmap blur = bmp;
            if(bmp == null) {
                // Download the art image
                artKey = cache.getSongArtKey(mSong);
                String artUrl = null;

                if (artKey == null) {
                    StringBuffer urlBuffer = new StringBuffer();
                    artKey = AlbumArtCache.getDefault().getArtKey(mSong, urlBuffer);
                    artUrl = urlBuffer.toString();
                }

                if (artKey != null && !artKey.equals(AlbumArtCache.DEFAULT_ART)) {
                    bmp = AlbumArtCache.getOrDownloadArt(artKey, artUrl, bmp);
                }

                if (v.position != this.mPosition) {
                    // Cancel, we moved
                    return null;
                }

                // We blurAndDim our bitmap, if another executor didn't do it already and if it's not
                // the default art

                if (artKey == null || artKey.equals(AlbumArtCache.DEFAULT_ART)) {
                    artKey = AlbumArtCache.DEFAULT_ART;
                    output = drawable;
                } else {
                    blur = BlurCache.getDefault().get(artKey);

                    if (blur == null) {
                        Bitmap thumb = ThumbnailUtils.extractThumbnail(bmp, mItemWidth, mItemHeight);

                        if (v.position != this.mPosition) {
                            // Cancel, we moved
                            return null;
                        }

                        blur = Utils.blurAndDim(ctx, thumb, 25);

                        if (v.position != this.mPosition) {
                            // Cancel, we moved
                            return null;
                        }

                        BlurCache.getDefault().put(artKey, blur, true);
                    }

                    if (v.position != this.mPosition) {
                        // Cancel, we moved
                        return null;
                    }


                }
            } else {
                artKey = mSong.getRef().replace(":","");
                Bitmap thumb = ThumbnailUtils.extractThumbnail(bmp, mItemWidth, mItemHeight);
                if (v.position != this.mPosition) {
                    // Cancel, we moved
                    return null;
                }

                blur = Utils.blurAndDim(ctx, thumb, 25);

                if (v.position != this.mPosition) {
                    // Cancel, we moved
                    return null;
                }

                BlurCache.getDefault().put(artKey, blur, true);
            }
            output = new BitmapDrawable(res, blur);
            cache.putSongArtKey(mSong, artKey);

            return output;
        }

        @Override
        protected void onPostExecute(BitmapDrawable result) {
            super.onPostExecute(result);

            if (v.position == mPosition && v.song == mSong && result != null) {
                // If this item hasn't been recycled already, set and show the image
                // We do a smooth transition from a white/transparent background to our blurred one
               /* TransitionDrawable drawable = new TransitionDrawable(new Drawable[]{
                        v.vRoot.getBackground(),
                        result
                });
*/
                v.vRoot.setBackground(result);
                //drawable.startTransition(200);
            }
        }
    }
    @Override
    public int getCount(){
        return mSongs.size();
    }
    @Override
    public Song getItem(int i){
        return mSongs.get(i);
    }
    @Override
    public long getItemId(int i){
        return mSongs.get(i).getRef().hashCode();
    }
    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        final Context ctx = parent.getContext();
        assert ctx != null;

        View root = convertView;
        if (convertView == null) {
            // Recycle the existing view
            LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            root = inflater.inflate(R.layout.item_playlist_view, null);
            assert root != null;

            ViewHolder holder = new ViewHolder();
            holder.tvTitle = (TextView) root.findViewById(R.id.tvTitle);
            holder.tvArtist = (TextView) root.findViewById(R.id.tvArtist);
            holder.tvDuration = (TextView) root.findViewById(R.id.tvDuration);
            holder.vRoot = root;

            root.setTag(holder);
        }
        final Song song = getItem(position);
        final ViewHolder tag = (ViewHolder) root.getTag();
        final ProviderCache cache = ProviderAggregator.getDefault().getCache();

        // Update tag
        tag.position = position;
        tag.song = song;
        root.setTag(tag);
        TextView addButton = (TextView)tag.vRoot.findViewById(R.id.addbutton);
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlaylistChooserFragment fragment = PlaylistChooserFragment.newInstance(song);
                fragment.show(((Activity)ctx).getFragmentManager(),mSongs.get(position).getRef());
            }
        });
        // Fill fields
        if (song != null && song.isLoaded()) {
            tag.tvTitle.setText(song.getTitle());
            tag.tvDuration.setText(Utils.formatTrackLength(song.getDuration()));

            Artist artist = cache.getArtist(song.getArtist());
            if (artist != null) {
                tag.tvArtist.setText(artist.getName());
            } else {
                tag.tvArtist.setText("...");
            }
        } else {
            tag.tvTitle.setText("...");
            tag.tvDuration.setText("...");
            tag.tvArtist.setText("...");
        }

        // Fetch background art
        final String artKey = ProviderAggregator.getDefault().getCache().getSongArtKey(song);

        final Resources res = root.getResources();
        assert res != null;

        if (artKey != null) {
            // We already know the album art for this song (keyed in artKey)
            Bitmap cachedBlur = BlurCache.getDefault().get(artKey);

            if (cachedBlur != null) {
                root.setBackground(new BitmapDrawable(root.getResources(), cachedBlur));
            } else {
                root.setBackground(res.getDrawable(R.drawable.album_list_default_bg));
                BackgroundAsyncTask task = new BackgroundAsyncTask(position);
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, tag);
                //task.execute(tag);
            }
        } else {
            root.setBackground(res.getDrawable(R.drawable.album_list_default_bg));
            BackgroundAsyncTask task = new BackgroundAsyncTask(position);
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, tag);
            //task.execute(tag);
        }


        return root;
    }
}
