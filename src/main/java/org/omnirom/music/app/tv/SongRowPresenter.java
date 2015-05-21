package org.omnirom.music.app.tv;

import android.support.v17.leanback.widget.RowPresenter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.omnirom.music.app.R;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.utils.Utils;

public class SongRowPresenter extends RowPresenter {

    private View.OnClickListener mSongClickListener;
    private Song mCurrentSong;

    SongRowPresenter(View.OnClickListener songClickListener) {
        setHeaderPresenter(null);
        setSelectEffectEnabled(false);
        mSongClickListener = songClickListener;
    }

    public void setCurrentSong(Song song) {
        mCurrentSong = song;
    }

    @Override
    protected ViewHolder createRowViewHolder(ViewGroup parent) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.tv_song_row, parent, false));
    }

    @Override
    protected void onBindRowViewHolder(ViewHolder vh, Object item) {
        super.onBindRowViewHolder(vh, item);
        final SongRow songRow = (SongRow) item;
        final Song song = songRow.getSong();
        final String trackTitle = song.getTitle();
        final long trackDuration = song.getDuration();
        final String artistName = getArtistName(song);

        // Don't display the divider on first row
        vh.view.findViewById(R.id.song_row_separator)
                .setVisibility(songRow.getPosition() == 0 ? View.GONE : View.VISIBLE);

        if (mCurrentSong != null && mCurrentSong.equals(song)) {
            vh.view.findViewById(R.id.track_playing_icon).setVisibility(View.VISIBLE);
            vh.view.findViewById(R.id.track_number).setVisibility(View.GONE);
        } else {
            vh.view.findViewById(R.id.track_playing_icon).setVisibility(View.GONE);
            vh.view.findViewById(R.id.track_number).setVisibility(View.VISIBLE);
        }

        View background = vh.view.findViewById(R.id.background);
        if (Utils.hasLollipop()) {
            background.setClipToOutline(true);
        }
        background.setOnClickListener(mSongClickListener);
        background.setTag(songRow);

        final String trackDurationStr = Utils.formatTrackLength(trackDuration);

        ((TextView) vh.view.findViewById(R.id.track_title)).setText(trackTitle);
        ((TextView) vh.view.findViewById(R.id.track_artist)).setText(String.format(" / %s", artistName));
        ((TextView) vh.view.findViewById(R.id.track_number)).setText(Integer.toString(songRow.getPosition() + 1));
        ((TextView) vh.view.findViewById(R.id.track_duration)).setText(trackDurationStr);

    }

    private String getArtistName(Song song) {
        String artistName;

        if (song.getArtist() != null) {
            Artist artist = ProviderAggregator.getDefault().retrieveArtist(song.getArtist(), song.getProvider());
            if (artist != null) {
                artistName = artist.getName();
            } else {
                artistName = null;
            }
        } else {
            artistName = null;
        }

        return artistName;
    }
}
