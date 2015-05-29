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

package com.fastbootmobile.encore.app.adapters;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.providers.IMusicProvider;
import com.fastbootmobile.encore.providers.ProviderConnection;

import java.util.List;

/**
 * Adapter to display a list of providers in a ListView
 */
public class ProvidersAdapter extends BaseAdapter {

    private class ViewHolder {
        ViewGroup vRoot;
        TextView tvProviderName;
        TextView tvProviderAuthor;
        ImageView ivProviderIcon;
        ImageView ivChecked;
        ImageView ivDelete;
        ProviderConnection provider;
    }

    private List<ProviderConnection> mProviders;
    private boolean mWhite = false;
    private boolean mWashOutConfigure = false;

    public ProvidersAdapter(List<ProviderConnection> list) {
        mProviders = list;
    }

    public void addProvider(ProviderConnection connection) {
        mProviders.add(connection);
    }

    /**
     * Sets whether or not to display the text in white
     * @param white true to set the text to white, false otherwise
     */
    public void setWhite(boolean white) {
        mWhite = white;
    }

    /**
     * Sets whether or not the configured providers should be less visible than the non-configured
     * providers in the list.
     * @param washout True to fade out, false otherwise
     */
    public void setWashOutConfigure(boolean washout) {
        mWashOutConfigure = washout;
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
            view = inflater.inflate(mWhite ? R.layout.item_provider_card : R.layout.item_provider, viewGroup, false);
            tag = new ViewHolder();
            tag.vRoot = (ViewGroup) view;
            tag.tvProviderAuthor = (TextView) view.findViewById(R.id.tvProviderAuthor);
            tag.tvProviderName = (TextView) view.findViewById(R.id.tvProviderName);
            tag.ivProviderIcon = (ImageView) view.findViewById(R.id.ivProviderLogo);
            tag.ivChecked = (ImageView) view.findViewById(R.id.ivChecked);
            tag.ivDelete = (ImageView) view.findViewById(R.id.ivDelete);

            tag.ivDelete.setTag(tag);
            view.setTag(tag);

            tag.ivDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ViewHolder tag = (ViewHolder) v.getTag();
                    Uri packageUri = Uri.parse("package:" + tag.provider.getPackage());
                    Intent uninstallIntent =
                            new Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri);
                    v.getContext().startActivity(uninstallIntent);
                }
            });
        } else {
            tag = (ViewHolder) view.getTag();
        }

        ProviderConnection provider = getItem(i);
        tag.tvProviderName.setText(provider.getProviderName());
        tag.tvProviderAuthor.setText(provider.getAuthorName());
        tag.provider = provider;

        if (mWhite || provider.getPackage().equals("com.fastbootmobile.encore.app")) {
            tag.ivDelete.setVisibility(View.GONE);
        } else {
            tag.ivDelete.setVisibility(View.VISIBLE);
        }

        try {
            Drawable icon = context.getPackageManager().getApplicationIcon(provider.getPackage());
            tag.ivProviderIcon.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            // set default icon
            tag.ivProviderIcon.setImageResource(R.mipmap.ic_launcher);
        }

        IMusicProvider binder = provider.getBinder();
        try {
            if (mWashOutConfigure && binder != null && binder.isSetup()) {
                tag.ivChecked.setVisibility(View.VISIBLE);
            } else {
                tag.ivChecked.setVisibility(View.GONE);
            }
        } catch (RemoteException ignore) {
            tag.ivChecked.setVisibility(View.GONE);
        }

        return view;
    }
}
