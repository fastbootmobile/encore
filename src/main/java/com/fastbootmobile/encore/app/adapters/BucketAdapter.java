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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.fastbootmobile.encore.api.echonest.AutoMixBucket;
import com.fastbootmobile.encore.api.echonest.AutoMixManager;
import com.fastbootmobile.encore.app.R;

import java.util.List;

/**
 * Adapter to display a ListView of AutoMix buckets
 */
public class BucketAdapter extends BaseAdapter {

    /**
     * View Holder
     */
    public static class ViewHolder {
        public TextView tvBucketName;
        public ProgressBar pbBucketSpinner;
        public ImageView ivOverflow;
    }

    private List<AutoMixBucket> mBuckets;

    /**
     * Sets the list of {@link com.fastbootmobile.encore.api.echonest.AutoMixBucket} to show
     * @param buckets The list of buckets to show
     */
    public void setBuckets(List<AutoMixBucket> buckets) {
        mBuckets = buckets;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCount() {
        return mBuckets == null ? 0 : mBuckets.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AutoMixBucket getItem(int i) {
        return mBuckets != null ? mBuckets.get(i) : null;
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

        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) viewGroup.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.item_bucket, viewGroup, false);
            tag = new ViewHolder();
            tag.tvBucketName = (TextView) view.findViewById(R.id.tvBucketName);
            tag.pbBucketSpinner = (ProgressBar) view.findViewById(R.id.pbBucketSpinner);
            tag.ivOverflow = (ImageView) view.findViewById(R.id.ivOverflow);
            view.setTag(tag);
        } else {
            tag = (ViewHolder) view.getTag();
        }

        final AutoMixBucket bucket = getItem(i);
        tag.tvBucketName.setText(bucket.getName());

        tag.ivOverflow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                PopupMenu popupMenu = new PopupMenu(v.getContext(), v);
                popupMenu.inflate(R.menu.bucket_overflow);
                popupMenu.show();

                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        switch (menuItem.getItemId()) {
                            case R.id.menu_play_now:
                                new Thread() {
                                    public void run() {
                                        AutoMixManager.getDefault().startPlay(bucket);
                                    }
                                }.start();
                                break;

                            case R.id.menu_delete:
                                confirmDelete(v.getContext(), bucket);
                                break;

                            default:
                                return false;
                        }
                        return true;
                    }
                });
            }
        });

        return view;
    }

    private void confirmDelete(final Context ctx, final AutoMixBucket bucket) {
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);

        builder.setMessage(R.string.automix_delete_confirm);
        builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                AutoMixManager.getDefault().destroyBucket(bucket);
                notifyDataSetChanged();
            }
        });
        builder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        builder.show();
    }
}
