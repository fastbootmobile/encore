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
import com.fastbootmobile.encore.framework.PluginsLookup;
import com.fastbootmobile.encore.model.Playlist;

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
