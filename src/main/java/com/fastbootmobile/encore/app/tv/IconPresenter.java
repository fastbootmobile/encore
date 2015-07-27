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

import android.content.Context;
import android.support.v17.leanback.widget.Presenter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.fastbootmobile.encore.app.R;

public class IconPresenter extends Presenter {
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.tv_icon_item, parent, false);
        v.setFocusable(true);
        v.setFocusableInTouchMode(true);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        if (item instanceof SettingsItem) {
            Context ctx = viewHolder.view.getContext();
            int icon;
            String label;

            switch (((SettingsItem) item).getType()) {
                case SettingsItem.ITEM_EFFECTS:
                    icon = R.drawable.ic_tv_effects;
                    label = ctx.getString(R.string.settings_dsp_config_title);
                    break;

                case SettingsItem.ITEM_PROVIDERS:
                    icon = R.drawable.ic_tv_providers;
                    label = ctx.getString(R.string.settings_provider_config_title);
                    break;

                case SettingsItem.ITEM_LICENSES:
                    icon = R.drawable.ic_tv_info;
                    label = ctx.getString(R.string.settings_licenses_title);
                    break;

                default:
                    icon = R.drawable.ic_tv_info;
                    label = "Unknown entry";
                    break;
            }

            ((TextView) viewHolder.view.findViewById(R.id.tvLabel)).setText(label);
            ((ImageView) viewHolder.view.findViewById(R.id.ivIcon)).setImageResource(icon);
        }
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {

    }
}
