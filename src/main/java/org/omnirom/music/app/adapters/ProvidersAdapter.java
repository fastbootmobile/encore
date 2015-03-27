/*
 * Copyright (C) 2014 Fastboot Mobile, LLC.
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

package org.omnirom.music.app.adapters;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.omnirom.music.app.R;
import org.omnirom.music.providers.ProviderConnection;

import java.util.List;

/**
 * Adapter to display a list of providers in a ListView
 */
public class ProvidersAdapter extends BaseAdapter {

    private class ViewHolder {
        TextView tvProviderName;
        TextView tvProviderAuthor;
        ImageView ivProviderIcon;
    }

    private List<ProviderConnection> mProviders;
    private boolean mWhite = false;

    public ProvidersAdapter(List<ProviderConnection> list) {
        mProviders = list;
    }

    public void addProvider(ProviderConnection connection) {
        mProviders.add(connection);
    }

    public void setWhite(boolean white) {
        mWhite = white;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCount() {
        return mProviders.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProviderConnection getItem(int i) {
        return mProviders.get(i);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getItemId(int i) {
        return i;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        ViewHolder tag;
        Context context = viewGroup.getContext();

        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.item_provider, viewGroup, false);
            tag = new ViewHolder();
            tag.tvProviderAuthor = (TextView) view.findViewById(R.id.tvProviderAuthor);
            tag.tvProviderName = (TextView) view.findViewById(R.id.tvProviderName);
            tag.ivProviderIcon = (ImageView) view.findViewById(R.id.ivProviderLogo);
            view.setTag(tag);

            if (mWhite) {
                tag.tvProviderName.setTextColor(0xFFFFFFFF);
                tag.tvProviderAuthor.setTextColor(0xFFFFFFFF);
            }
        } else {
            tag = (ViewHolder) view.getTag();
        }

        ProviderConnection provider = getItem(i);
        tag.tvProviderName.setText(provider.getProviderName());
        tag.tvProviderAuthor.setText(provider.getAuthorName());

        try {
            Drawable icon = context.getPackageManager().getApplicationIcon(provider.getPackage());
            tag.ivProviderIcon.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            // set default icon
            tag.ivProviderIcon.setImageResource(R.mipmap.ic_launcher);
        }

        return view;
    }
}
