package org.omnirom.music.app.adapters;

import android.content.Context;
import android.content.res.Resources;
import android.os.RemoteException;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.app.ui.AlbumArtImageView;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.BoundEntity;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.service.IPlaybackService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by h4o on 19/06/2014.
 */
public class SongsListAdapter extends BaseAdapter {
    protected List<Song> mSongs;
    private boolean mShowAlbumArt;

    public static class ViewHolder {
        public TextView tvTitle;
        public TextView tvArtist;
        public TextView tvDuration;
        public ImageView ivOverflow;
        public AlbumArtImageView ivAlbumArt;
        public ViewGroup vRoot;
        public int position;
        public Song song;
        public View vCurrentIndicator;
    }

    private View.OnClickListener mOverflowClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            final ViewHolder tag = (ViewHolder) v.getTag();
            final Context context = tag.vRoot.getContext();
            Utils.showSongOverflow((FragmentActivity) context, tag.ivOverflow, tag.song);
        }
    };

    public SongsListAdapter(Context ctx, boolean showAlbumArt) {
        final Resources res = ctx.getResources();
        assert res != null;
        mSongs = new ArrayList<Song>();
        mShowAlbumArt = showAlbumArt;
    }

    public void clear() {
        mSongs.clear();
    }

    public void put(Song song) {
        mSongs.add(song);
    }

    public void sortAll() {
        Collections.sort(mSongs, new Comparator<Song>() {
            @Override
            public int compare(Song lhs, Song rhs) {
                return lhs.getTitle().compareTo(rhs.getTitle());
            }
        });
    }

    public boolean contains(Song s) {
        return mSongs.contains(s);
    }

    @Override
    public int getCount() {
        return mSongs.size();
    }

    @Override
    public Song getItem(int i) {
        return mSongs.get(i);
    }

    @Override
    public long getItemId(int i) {
        return mSongs.get(i).getRef().hashCode();
    }

    @Override
    public View getView(final int position, final View convertView, ViewGroup parent) {
        final Context ctx = parent.getContext();
        assert ctx != null;
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();

        View root = convertView;
        if (convertView == null) {
            // Recycle the existing view
            LayoutInflater inflater = LayoutInflater.from(ctx);
            root = inflater.inflate(R.layout.item_playlist_view, parent, false);
            assert root != null;

            ViewHolder holder = new ViewHolder();
            holder.tvTitle = (TextView) root.findViewById(R.id.tvTitle);
            holder.tvArtist = (TextView) root.findViewById(R.id.tvArtist);
            holder.tvDuration = (TextView) root.findViewById(R.id.tvDuration);
            holder.ivOverflow = (ImageView) root.findViewById(R.id.ivOverflow);
            holder.ivAlbumArt = (AlbumArtImageView) root.findViewById(R.id.ivAlbumArt);
            holder.vCurrentIndicator = root.findViewById(R.id.currentSongIndicator);
            holder.vRoot = (ViewGroup) root;

            if (mShowAlbumArt) {
                // Fixup some style stuff
                holder.ivAlbumArt.setVisibility(View.VISIBLE);
            } else {
                holder.ivAlbumArt.setVisibility(View.GONE);
            }

            holder.ivOverflow.setOnClickListener(mOverflowClickListener);
            holder.ivOverflow.setTag(holder);

            root.setTag(holder);
        }
        final Song song = getItem(position);
        final ViewHolder tag = (ViewHolder) root.getTag();

        // Update tag
        tag.position = position;
        tag.song = song;
        root.setTag(tag);

        // Fill fields
        if (song != null && song.isLoaded()) {
            tag.tvTitle.setText(song.getTitle());
            tag.tvDuration.setText(Utils.formatTrackLength(song.getDuration()));

            if (mShowAlbumArt) {
                tag.ivAlbumArt.loadArtForSong(song);
            }

            Artist artist = aggregator.retrieveArtist(song.getArtist(), song.getProvider());
            if (artist != null) {
                tag.tvArtist.setText(artist.getName());
            } else {
                tag.tvArtist.setText("...");
            }
        } else {
            tag.tvTitle.setText("...");
            tag.tvDuration.setText("...");
            tag.tvArtist.setText("...");

            if (mShowAlbumArt) {
                tag.ivAlbumArt.setDefaultArt();
            }
        }

        // Set current song indicator
        tag.vCurrentIndicator.setVisibility(View.INVISIBLE);

        final IPlaybackService pbService = PluginsLookup.getDefault().getPlaybackService();
        try {
            Song currentSong = pbService.getCurrentTrack();
            if (currentSong != null && currentSong.equals(tag.song)) {
                tag.vCurrentIndicator.setVisibility(View.VISIBLE);
            }
        } catch (RemoteException e) {
            // ignore
        }

        // Set alpha based on offline availability and mode
        if (aggregator.isOfflineMode() && song != null
                && song.getOfflineStatus() != BoundEntity.OFFLINE_STATUS_READY) {
            Utils.setChildrenAlpha(tag.vRoot,
                    Float.parseFloat(ctx.getResources().getString(R.string.unavailable_track_alpha)));
        } else {
            Utils.setChildrenAlpha(tag.vRoot, 1.0f);
        }

        return root;
    }

}
