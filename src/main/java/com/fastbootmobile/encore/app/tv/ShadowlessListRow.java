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
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.ObjectAdapter;
import android.support.v17.leanback.widget.RowPresenter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.fastbootmobile.encore.app.R;


public class ShadowlessListRow extends ListRow {
    public ShadowlessListRow(long id, HeaderItem header, ObjectAdapter adapter) {
        super(id, header, adapter);
    }

    public ShadowlessListRow(HeaderItem header, ObjectAdapter adapter) {
        super(header, adapter);
    }

    public static ListRowPresenter createPresenter(Context context) {
        ListRowPresenter presenter = new ListRowPresenter(0) {
            @Override
            protected void onRowViewSelected(RowPresenter.ViewHolder holder, boolean selected) {
                super.onRowViewSelected(holder, selected);
                updateSelectedState(holder, selected);
            }

            @Override
            protected void onRowViewAttachedToWindow(RowPresenter.ViewHolder vh) {
                super.onRowViewAttachedToWindow(vh);
                updateSelectedState(vh, vh.isSelected());
            }

            private void updateSelectedState(RowPresenter.ViewHolder vh, boolean selected) {
                ListRowPresenter.ViewHolder lpr = (ListRowPresenter.ViewHolder) vh;
                ViewGroup host = lpr.getGridView();
                final int childCount = host.getChildCount();

                for (int i = 0; i < childCount; ++i) {
                    final View child = host.getChildAt(i);
                    final ImageView icon = (ImageView) child.findViewById(R.id.ivIcon);
                    final TextView label = (TextView) child.findViewById(R.id.tvLabel);

                    if (selected) {
                        if (icon != null) {
                            icon.setColorFilter(0);
                        }
                        if (label != null) {
                            label.setTextColor(0xFFFFFFFF);
                        }
                    } else {
                        if (icon != null) {
                            icon.setColorFilter(0xAA000000);
                        }
                        if (label != null) {
                            label.setTextColor(0x88FFFFFF);
                        }

                    }
                }
            }
        };
        presenter.setShadowEnabled(false);
        presenter.setSelectEffectEnabled(false);
        return presenter;
    }
}
