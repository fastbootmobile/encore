package org.omnirom.music.app.adapters;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.app.ui.VuMeterView;
import org.omnirom.music.framework.BlurCache;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderCache;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by h4o on 19/06/2014.
 */
public class SongsListAdapter  extends BaseAdapter{
    List<Song> mSongs;

    public SongsListAdapter() {
        mSongs = new ArrayList<Song>();
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
    public View getView(int position, View convertView, ViewGroup parent) {
        Context ctx = parent.getContext();
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

            }
        } else {
            root.setBackground(res.getDrawable(R.drawable.album_list_default_bg));

            //task.execute(tag);
        }


        return root;
    }
}
