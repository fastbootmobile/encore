package org.omnirom.music.app.tv;

import android.content.res.Resources;
import android.support.v17.leanback.widget.AbstractDetailsDescriptionPresenter;

import org.omnirom.music.app.R;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.utils.Utils;

public class AlbumDetailsPresenter extends AbstractDetailsDescriptionPresenter {
    @Override
    protected void onBindDescription(ViewHolder vh, Object item) {
        Album album = (Album) item;

        if (album != null) {
            final Resources res = vh.getSubtitle().getResources();
            vh.getTitle().setText(album.getName());
            vh.getSubtitle().setText(res.getQuantityString(R.plurals.nb_tracks, album.getSongsCount(), album.getSongsCount()));

            // If we have artist info, add it
            String artistRef = Utils.getMainArtist(album);
            if (artistRef != null) {
                Artist artist = ProviderAggregator.getDefault().retrieveArtist(artistRef, album.getProvider());
                if (artist != null) {
                    vh.getSubtitle().setText(artist.getName() + " - " + res.getQuantityString(R.plurals.nb_tracks, album.getSongsCount(), album.getSongsCount()));
                }
            }
        }
    }
}
