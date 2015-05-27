package com.fastbootmobile.encore.app.tv;

import android.content.res.Resources;
import android.support.v17.leanback.widget.AbstractDetailsDescriptionPresenter;

import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.model.Album;
import com.fastbootmobile.encore.model.Artist;
import com.fastbootmobile.encore.providers.ProviderAggregator;
import com.fastbootmobile.encore.utils.Utils;

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
