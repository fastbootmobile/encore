/*
 * Copyright (C) 2015 Fastboot Mobile, LLC.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program;
 * if not, see <http://www.gnu.org/licenses>.
 */

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
