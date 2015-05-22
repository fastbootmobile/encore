package org.omnirom.music.app.tv;

import android.content.res.Resources;
import android.support.v17.leanback.widget.AbstractDetailsDescriptionPresenter;

import org.omnirom.music.app.R;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Playlist;

public class PlaylistDetailsPresenter extends AbstractDetailsDescriptionPresenter {
    @Override
    protected void onBindDescription(ViewHolder vh, Object item) {
        Playlist playlist = (Playlist) item;

        if (playlist != null) {
            final Resources res = vh.getSubtitle().getResources();
            vh.getTitle().setText(playlist.getName());

            final String nbTracksStr = res.getQuantityString(R.plurals.nb_tracks, playlist.getSongsCount(), playlist.getSongsCount());
            final String providerName = PluginsLookup.getDefault().getProvider(playlist.getProvider()).getProviderName();

            vh.getSubtitle().setText(String.format("%s - %s", nbTracksStr, providerName));
        }
    }
}
