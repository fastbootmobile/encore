package com.fastbootmobile.encore.app.tv;

import android.content.res.Resources;
import android.support.v17.leanback.widget.AbstractDetailsDescriptionPresenter;

import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.model.Artist;

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
