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
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.framework.PluginsLookup;
import com.fastbootmobile.encore.providers.DSPConnection;
import com.fastbootmobile.encore.providers.ProviderIdentifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter to display a ListView of DSP providers
 */
public class DspAdapter extends BaseAdapter {

    private List<DSPConnection> mProviders;
    private ClickListener mListener;

    /**
     * Interface for when item buttons are clicked
     */
    public interface ClickListener {
        public void onDeleteClicked(int position);
        public void onUpClicked(int position);
        public void onDownClicked(int position);
    }

    /**
     * ViewHolder for elements
     */
    private static class ViewHolder {
        TextView tvProviderName;
        TextView tvProviderAuthor;
        ImageView ivProviderIcon;
        ImageView btnUp;
        ImageView btnDown;
        ImageView btnDelete;
        int position;
    }

    private View.OnClickListener mDeleteClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (mListener != null) {
                int i = ((ViewHolder) view.getTag()).position;
                mListener.onDeleteClicked(i);
            }
        }
    };

    private View.OnClickListener mUpClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (mListener != null) {
                int i = ((ViewHolder) view.getTag()).position;
                mListener.onUpClicked(i);
            }
        }
    };

    private View.OnClickListener mDownClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (mListener != null) {
                int i = ((ViewHolder) view.getTag()).position;
                mListener.onDownClicked(i);
            }
        }
    };

    /**
     * Default constructor
     * @param list The list of ProviderIdentifier corresponding to DSPs to display
     */
    public DspAdapter(List<ProviderIdentifier> list) {
        mProviders = new ArrayList<>();
        for (ProviderIdentifier id : list) {
            mProviders.add(PluginsLookup.getDefault().getDSP(id));
        }
    }

    /**
     * Updates the list of DSP providers to show. This will call {@link #notifyDataSetChanged()}
     * automatically.
     * @param list The list of ProviderIdentifier corresponding to DSPs to display
     */
    public void updateChain(List<ProviderIdentifier> list) {
        mProviders.clear();
        for (ProviderIdentifier id : list) {
            mProviders.add(PluginsLookup.getDefault().getDSP(id));
        }
        notifyDataSetChanged();
    }

    /**
     * Sets the listener that will be called when events occur on the item's buttons
     * @param listener The listener to call, or null to call no listener
     */
    public void setClickListener(ClickListener listener) {
        mListener = listener;
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
    public DSPConnection getItem(int i) {
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
    public View getView(final int i, View view, ViewGroup viewGroup) {
        ViewHolder tag;
        Context context = viewGroup.getContext();

        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.item_dsp, viewGroup, false);
            tag = new ViewHolder();
            tag.tvProviderAuthor = (TextView) view.findViewById(R.id.tvProviderAuthor);
            tag.tvProviderName = (TextView) view.findViewById(R.id.tvProviderName);
            tag.ivProviderIcon = (ImageView) view.findViewById(R.id.ivProviderLogo);
            tag.btnDelete = (ImageView) view.findViewById(R.id.btnDelete);
            tag.btnUp = (ImageView) view.findViewById(R.id.btnUp);
            tag.btnDown = (ImageView) view.findViewById(R.id.btnDown);
            view.setTag(tag);
            tag.btnDelete.setTag(tag);
            tag.btnUp.setTag(tag);
            tag.btnDown.setTag(tag);
        } else {
            tag = (ViewHolder) view.getTag();
        }

        // Update views
        DSPConnection provider = getItem(i);
        tag.tvProviderName.setText(provider.getProviderName());
        tag.tvProviderAuthor.setText(provider.getAuthorName());

        tag.position = i;

        try {
            Drawable icon = context.getPackageManager().getApplicationIcon(provider.getPackage());
            tag.ivProviderIcon.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            // ignore
        }

        // Set buttons click listeners
        tag.btnDelete.setOnClickListener(mDeleteClickListener);
        tag.btnUp.setOnClickListener(mUpClickListener);
        tag.btnDown.setOnClickListener(mDownClickListener);

        return view;
    }
}
