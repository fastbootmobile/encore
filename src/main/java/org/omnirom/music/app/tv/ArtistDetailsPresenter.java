package org.omnirom.music.app.tv;

import android.content.res.Resources;
import android.support.v17.leanback.widget.AbstractDetailsDescriptionPresenter;

import org.omnirom.music.app.R;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.utils.Utils;

public class ArtistDetailsPresenter extends AbstractDetailsDescriptionPresenter {
    @Override
    protected void onBindDescription(ViewHolder vh, Object item) {
        Artist artist = (Artist) item;

        if (artist != null) {
            final Resources res = vh.getSubtitle().getResources();
            vh.getTitle().setText(artist.getName());
            vh.getSubtitle().setText(res.getQuantityString(R.plurals.albums_count, artist.getAlbums().size(), artist.getAlbums().size()));
        }
    }
}
