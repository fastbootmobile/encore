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

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Bundle;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.graphics.Palette;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.fastbootmobile.encore.app.AlbumActivity;
import com.fastbootmobile.encore.app.ArtistActivity;
import com.fastbootmobile.encore.app.PlaylistActivity;
import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.app.ui.AlbumArtImageView;
import com.fastbootmobile.encore.app.ui.MaterialTransitionDrawable;
import com.fastbootmobile.encore.model.Album;
import com.fastbootmobile.encore.model.Artist;
import com.fastbootmobile.encore.model.BoundEntity;
import com.fastbootmobile.encore.model.Playlist;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class ListenNowAdapter extends BaseAdapter {
    private static final String TAG = "ListenNowAdapter";

    private static final int VIEW_TYPE_SECTION_HEADER       = 0;
    private static final int VIEW_TYPE_SIMPLE               = 1;
    private static final int VIEW_TYPE_CARD                 = 2;
    private static final int VIEW_TYPE_CARD_ROW             = 3;
    private static final int VIEW_TYPE_SECTION_GET_STARTED  = 4;
    private static final int VIEW_TYPE_ITEM_CARD            = 5;
    private static final int VIEW_TYPE_COUNT                = VIEW_TYPE_ITEM_CARD + 1;

    private List<ListenNowItem> mItems;

    public ListenNowAdapter() {
        mItems = new ArrayList<>();
    }

    public void addItem(ListenNowItem item) {
        mItems.add(item);
    }

    public void removeItem(int index) {
        mItems.remove(index);
    }

    public void removeItem(ListenNowItem item) {
        mItems.remove(item);
    }

    public void clearItems() {
        mItems.clear();
    }

    @Override
    public int getCount() {
        return mItems.size();
    }

    @Override
    public ListenNowItem getItem(int position) {
        return mItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return mItems.get(position).getItemId();
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public int getItemViewType(int position) {
        return mItems.get(position).getItemViewType();
    }

    @Override
    public int getViewTypeCount() {
        return VIEW_TYPE_COUNT;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ListenNowItem item = getItem(position);
        convertView = item.getView(convertView, parent);
        return convertView;
    }

    // ------------------------------------------------------------------------------------
    // Items

    public static abstract class ListenNowItem {
        protected BaseViewHolder mViewHolder;

        public View getView(View convertView, ViewGroup parent) {
            if (convertView == null) {
                final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
                convertView = inflate(inflater, parent);
            }
            mViewHolder = getViewHolder(convertView);
            convertView.setTag(mViewHolder);
            bind();

            return convertView;
        }

        public void ensureViewHolder(View v) {
            mViewHolder = getViewHolder(v);
            v.setTag(mViewHolder);
        }

        protected abstract View inflate(LayoutInflater inflater, ViewGroup parent);
        protected abstract void bind();
        public abstract BaseViewHolder getViewHolder(View root);
        public abstract long getItemId();
        public abstract int getItemViewType();
    }

    public static class SectionHeaderItem extends ListenNowItem {
        private View.OnClickListener mAction;
        private String mActionText;
        private String mText;

        @DrawableRes
        private int mIcon;


        public SectionHeaderItem(String text, @DrawableRes int icon, @Nullable String actionText,
                                 @Nullable View.OnClickListener action) {
            mText = text;
            mActionText = actionText;
            mIcon = icon;
            mAction = action;
        }

        @Override
        public final View inflate(LayoutInflater inflater, ViewGroup parent) {
            return inflater.inflate(R.layout.item_ln_section_header, parent, false);
        }

        @Override
        protected final void bind() {
            SectionHeaderViewHolder sectionVh = (SectionHeaderViewHolder) mViewHolder;
            sectionVh.ivIcon.setImageResource(mIcon);
            sectionVh.tvText.setText(mText);
            if (mAction != null) {
                sectionVh.btnAction.setOnClickListener(mAction);
                sectionVh.btnAction.setText(mActionText);
                sectionVh.btnAction.setVisibility(View.VISIBLE);
            } else {
                sectionVh.btnAction.setOnClickListener(null);
                sectionVh.btnAction.setVisibility(View.GONE);
            }
        }

        @Override
        public final BaseViewHolder getViewHolder(View root) {
            if (root.getTag() != null && root.getTag() instanceof SectionHeaderViewHolder) {
                return (SectionHeaderViewHolder) root.getTag();
            } else {
                return new SectionHeaderViewHolder(this, root);
            }
        }

        @Override
        public final long getItemId() {
            return Long.valueOf("100" + String.valueOf(Math.abs(mText.hashCode())));
        }

        @Override
        public final int getItemViewType() {
            return VIEW_TYPE_SECTION_HEADER;
        }
    }

    public static class SimpleItem extends ListenNowItem {
        private View.OnClickListener mAction;
        private String mText;

        public SimpleItem(String text, @Nullable View.OnClickListener action) {
            mText = text;
            mAction = action;
        }

        @Override
        public final View inflate(LayoutInflater inflater, ViewGroup parent) {
            return inflater.inflate(R.layout.item_ln_simple, parent, false);
        }

        @Override
        protected final void bind() {
            SimpleViewHolder sectionVh = (SimpleViewHolder) mViewHolder;
            sectionVh.tvCaption.setText(mText);
            sectionVh.tvCaption.setOnClickListener(mAction);
        }

        @Override
        public final SimpleViewHolder getViewHolder(View root) {
            if (root.getTag() != null && root.getTag() instanceof SimpleViewHolder) {
                return (SimpleViewHolder) root.getTag();
            } else {
                return new SimpleViewHolder(this, root);
            }
        }

        @Override
        public final long getItemId() {
            return Long.valueOf("120" + String.valueOf(Math.abs(mText.hashCode())));
        }

        @Override
        public final int getItemViewType() {
            return VIEW_TYPE_SIMPLE;
        }
    }

    public static class ItemCardItem extends ListenNowItem {
        private BoundEntity mEntity;

        public ItemCardItem(@NonNull BoundEntity entity) {
            mEntity = entity;
        }

        @Override
        public final View inflate(LayoutInflater inflater, ViewGroup parent) {
            return inflater.inflate(R.layout.item_ln_item_card, parent, false);
        }

        @Override
        protected final void bind() {
            ItemCardViewHolder itemVh = (ItemCardViewHolder) mViewHolder;
            itemVh.llLnItemCard.setBackgroundColor(0xFF333333);

            if (mEntity instanceof Song) {
                itemVh.ivAlbumArt.loadArtForSong((Song) mEntity);
                itemVh.tvCaption.setText(((Song) mEntity).getTitle());
            } else if (mEntity instanceof Album) {
                itemVh.ivAlbumArt.loadArtForAlbum((Album) mEntity);
                itemVh.tvCaption.setText(((Album) mEntity).getName());
            } else if (mEntity instanceof Artist) {
                itemVh.ivAlbumArt.loadArtForArtist((Artist) mEntity);
                itemVh.tvCaption.setText(((Artist) mEntity).getName());
            } else if (mEntity instanceof Playlist) {
                itemVh.ivAlbumArt.loadArtForPlaylist((Playlist) mEntity);
                itemVh.tvCaption.setText(((Playlist) mEntity).getName());
            }
        }

        @Override
        public final ItemCardViewHolder getViewHolder(View root) {
            if (root.getTag() != null && root.getTag() instanceof ItemCardViewHolder) {
                return (ItemCardViewHolder) root.getTag();
            } else {
                return new ItemCardViewHolder(this, root);
            }
        }

        @Override
        public final long getItemId() {
            return Long.valueOf("200" + String.valueOf(Math.abs(mEntity.hashCode())));
        }

        @Override
        public final int getItemViewType() {
            return VIEW_TYPE_ITEM_CARD;
        }
    }

    public static class CardRowItem extends ListenNowItem {
        private ItemCardItem mItem1;
        private ItemCardItem mItem2;

        public CardRowItem(@NonNull ItemCardItem item1, @NonNull ItemCardItem item2) {
            mItem1 = item1;
            mItem2 = item2;
        }

        @Override
        public final View inflate(LayoutInflater inflater, ViewGroup parent) {
            return inflater.inflate(R.layout.item_ln_row_card, parent, false);
        }

        @Override
        protected final void bind() {
            CardRowViewHolder itemVh = (CardRowViewHolder) mViewHolder;

            // Ensure view holders and fill the values
            mItem1.ensureViewHolder(itemVh.card1);
            mItem1.bind();

            mItem2.ensureViewHolder(itemVh.card2);
            mItem2.bind();
        }

        @Override
        public final CardRowViewHolder getViewHolder(View root) {
            if (root.getTag() != null && root.getTag() instanceof CardRowViewHolder) {
                return (CardRowViewHolder) root.getTag();
            } else {
                return new CardRowViewHolder(this, root);
            }
        }

        @Override
        public final long getItemId() {
            return Long.valueOf("250" + String.valueOf(Math.abs(mItem1.hashCode())));
        }

        @Override
        public final int getItemViewType() {
            return VIEW_TYPE_CARD_ROW;
        }
    }

    public static class GetStartedItem extends ListenNowItem {
        private String mBody;
        private String mActionText;
        private View.OnClickListener mAction;

        public GetStartedItem(String body, String actionText, View.OnClickListener action) {
            mBody = body;
            mActionText = actionText;
            mAction = action;
        }

        @Override
        public final View inflate(LayoutInflater inflater, ViewGroup parent) {
            return inflater.inflate(R.layout.item_ln_section_getstarted, parent, false);
        }

        @Override
        protected final void bind() {
            GetStartedViewHolder itemVh = (GetStartedViewHolder) mViewHolder;
            itemVh.tvCaption.setText(mBody);
            itemVh.btnAction.setText(mActionText);
            itemVh.btnAction.setOnClickListener(mAction);
        }

        @Override
        public final BaseViewHolder getViewHolder(View root) {
            if (root.getTag() != null && root.getTag() instanceof GetStartedViewHolder) {
                return (GetStartedViewHolder) root.getTag();
            } else {
                return new GetStartedViewHolder(this, root);
            }
        }

        @Override
        public final long getItemId() {
            return Long.valueOf("300" + String.valueOf(Math.abs(mBody.hashCode())));
        }

        @Override
        public final int getItemViewType() {
            return VIEW_TYPE_SECTION_GET_STARTED;
        }
    }

    public static class CardItem extends ListenNowItem {
        private String mTitle;
        private String mBody;
        private String mPrimaryAction;
        private String mSecondaryAction;
        private View.OnClickListener mPrimaryListener;
        private View.OnClickListener mSecondaryListener;

        public CardItem(String title, String body, String primaryAction,
                        View.OnClickListener primaryIntent) {
            mTitle = title;
            mBody = body;
            mPrimaryAction = primaryAction;
            mPrimaryListener = primaryIntent;
        }

        public CardItem(String title, String body, String primaryAction,
                        View.OnClickListener primaryIntent, String secondaryAction,
                        View.OnClickListener secondaryIntent) {
            this(title, body, primaryAction, primaryIntent);
            mSecondaryAction = secondaryAction;
            mSecondaryListener = secondaryIntent;
        }

        @Override
        public final View inflate(LayoutInflater inflater, ViewGroup parent) {
            return inflater.inflate(R.layout.item_ln_card, parent, false);
        }

        @Override
        protected final void bind() {
            CardViewHolder itemVh = (CardViewHolder) mViewHolder;
            itemVh.tvTitle.setText(mTitle);
            itemVh.tvBody.setText(mBody);

            if (mPrimaryAction != null) {
                itemVh.btnPrimary.setText(mPrimaryAction);
                itemVh.btnPrimary.setVisibility(View.VISIBLE);
                itemVh.btnPrimary.setOnClickListener(mPrimaryListener);
                itemVh.btnPrimary.setTag(this);
            } else {
                itemVh.btnPrimary.setVisibility(View.GONE);
            }

            if (mSecondaryAction != null) {
                itemVh.btnSecondary.setText(mSecondaryAction);
                itemVh.btnSecondary.setVisibility(View.VISIBLE);
                itemVh.btnSecondary.setOnClickListener(mSecondaryListener);
                itemVh.btnSecondary.setTag(this);
            } else {
                itemVh.btnSecondary.setVisibility(View.GONE);
            }


        }

        @Override
        public final CardViewHolder getViewHolder(View root) {
            if (root.getTag() != null && root.getTag() instanceof CardViewHolder) {
                return (CardViewHolder) root.getTag();
            } else {
                return new CardViewHolder(this, root);
            }
        }

        @Override
        public final long getItemId() {
            return Long.valueOf("350" + String.valueOf(Math.abs(mBody.hashCode())));
        }

        @Override
        public final int getItemViewType() {
            return VIEW_TYPE_CARD;
        }
    }

    // ------------------------------------------------------------------------------------
    // View holders

    private abstract static class BaseViewHolder {
        ListenNowItem item;
        View vRoot;

        public BaseViewHolder(ListenNowItem item, View root) {
            this.item = item;
            vRoot = root;
        }
    }

    private static class SectionHeaderViewHolder extends BaseViewHolder {
        ImageView ivIcon;
        TextView tvText;
        Button btnAction;

        public SectionHeaderViewHolder(ListenNowItem item, View root) {
            super(item, root);
            ivIcon = (ImageView) vRoot.findViewById(R.id.ivIcon);
            tvText = (TextView) vRoot.findViewById(R.id.tvText);
            btnAction = (Button) vRoot.findViewById(R.id.btnAction);
        }
    }

    private static class ItemCardViewHolder extends BaseViewHolder {
        LinearLayout llLnItemCard;
        AlbumArtImageView ivAlbumArt;
        TextView tvCaption;
        int bgColor = 0xFF333333;

        public ItemCardViewHolder(final ItemCardItem item, View root) {
            super(item, root);
            ivAlbumArt = (AlbumArtImageView) vRoot.findViewById(R.id.ivAlbumArt);
            if (ivAlbumArt == null) throw new IllegalStateException("Album art view is null");

            tvCaption = (TextView) vRoot.findViewById(R.id.tvCaption);
            if (tvCaption == null) throw new IllegalStateException("Caption view is null");

            llLnItemCard = (LinearLayout) vRoot.findViewById(R.id.llLnItemCard);
            if (llLnItemCard == null) throw new IllegalStateException("Linear layout view is null");

            ivAlbumArt.setOnArtLoadedListener(new AlbumArtImageView.OnArtLoadedListener() {
                @Override
                public void onArtLoaded(AlbumArtImageView view, BitmapDrawable drawable) {
                    if (drawable != null) {
                        Palette.from(drawable.getBitmap()).generate(new Palette.PaletteAsyncListener() {
                            @Override
                            public void onGenerated(Palette palette) {
                                int color = palette.getDarkMutedColor(0xFF333333);
                                TransitionDrawable td = new TransitionDrawable(new Drawable[] {
                                        llLnItemCard.getBackground(),
                                        new ColorDrawable(color)
                                });
                                bgColor = color;
                                llLnItemCard.setBackground(td);
                                td.startTransition(300);
                            }
                        });
                    }
                }
            });

            vRoot.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (item.mEntity instanceof Song) {
                        // Play track?
                    } else if (item.mEntity instanceof Artist) {
                        Intent intent = ArtistActivity.craftIntent(v.getContext(),
                                ((MaterialTransitionDrawable) ivAlbumArt.getDrawable()).getFinalDrawable().getBitmap(),
                                item.mEntity.getRef(), item.mEntity.getProvider(), bgColor);

                        if (Utils.hasLollipop()) {
                            Bundle opts = ActivityOptions.makeSceneTransitionAnimation((Activity) v.getContext(),
                                    ivAlbumArt, "itemImage").toBundle();
                            v.getContext().startActivity(intent, opts);
                        } else {
                            v.getContext().startActivity(intent);
                        }
                    } else if (item.mEntity instanceof Album) {
                        Intent intent = AlbumActivity.craftIntent(v.getContext(),
                                ((MaterialTransitionDrawable) ivAlbumArt.getDrawable()).getFinalDrawable().getBitmap(),
                                item.mEntity.getRef(), item.mEntity.getProvider(), bgColor);

                        if (Utils.hasLollipop()) {
                            Bundle opts = ActivityOptions.makeSceneTransitionAnimation((Activity) v.getContext(),
                                    ivAlbumArt, "itemImage").toBundle();
                            v.getContext().startActivity(intent, opts);
                        } else {
                            v.getContext().startActivity(intent);
                        }
                    } else if (item.mEntity instanceof Playlist) {
                        Intent intent = PlaylistActivity.craftIntent(v.getContext(), item.mEntity.getRef(),
                                ((MaterialTransitionDrawable) ivAlbumArt.getDrawable()).getFinalDrawable().getBitmap());

                        if (Utils.hasLollipop()) {
                            Bundle opts = ActivityOptions.makeSceneTransitionAnimation((Activity) v.getContext(),
                                    ivAlbumArt, "itemImage").toBundle();
                            v.getContext().startActivity(intent, opts);
                        } else {
                            v.getContext().startActivity(intent);
                        }
                    }
                }
            });
        }
    }

    private static class CardRowViewHolder extends BaseViewHolder {
        View card1;
        View card2;

        public CardRowViewHolder(ListenNowItem item, View root) {
            super(item, root);
            card1 = vRoot.findViewById(R.id.card1);
            card2 = vRoot.findViewById(R.id.card2);
        }
    }

    private static class GetStartedViewHolder extends BaseViewHolder {
        TextView tvCaption;
        Button btnAction;

        public GetStartedViewHolder(ListenNowItem item, View root) {
            super(item, root);
            tvCaption = (TextView) vRoot.findViewById(R.id.tvCaption);
            btnAction = (Button) vRoot.findViewById(R.id.btnAction);
        }
    }

    private static class SimpleViewHolder extends BaseViewHolder {
        TextView tvCaption;

        public SimpleViewHolder(ListenNowItem item, View root) {
            super(item, root);
            tvCaption = (TextView) vRoot.findViewById(R.id.tvCaption);
        }
    }

    private static class CardViewHolder extends BaseViewHolder {
        TextView tvTitle;
        TextView tvBody;
        Button btnPrimary;
        Button btnSecondary;

        public CardViewHolder(ListenNowItem item, View root) {
            super(item, root);
            tvTitle = (TextView) vRoot.findViewById(R.id.tvTitle);
            tvBody = (TextView) vRoot.findViewById(R.id.tvBody);
            btnPrimary = (Button) vRoot.findViewById(R.id.btnPrimary);
            btnSecondary = (Button) vRoot.findViewById(R.id.btnSecondary);
        }
    }
}
