package org.omnirom.music.app.tv;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.Presenter;
import android.support.v4.graphics.ColorUtils;
import android.support.v7.graphics.Palette;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.omnirom.music.app.R;
import org.omnirom.music.art.AlbumArtHelper;
import org.omnirom.music.art.RecyclingBitmapDrawable;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.BoundEntity;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderConnection;
import org.omnirom.music.utils.Utils;

public class CardPresenter extends Presenter {
    private static final String TAG = "CardPresenter";
    private static final int CARD_WIDTH = 212;
    private static final int CARD_HEIGHT = 176;
    private static int sSelectedBackgroundColor;
    private static int sDefaultBackgroundColor;

    private Context mContext;
    private Drawable mDefaultCardImage;

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        sDefaultBackgroundColor = parent.getResources().getColor(R.color.primary_dark);
        sSelectedBackgroundColor = parent.getResources().getColor(R.color.primary);

        mContext = parent.getContext();
        mDefaultCardImage = mContext.getResources().getDrawable(R.drawable.album_placeholder);

        ImageCardView cardView = new ImageCardView(mContext) {
            @Override
            public void setSelected(boolean selected) {
                updateCardBackgroundColor(this, selected);
                super.setSelected(selected);
            }
        };

        cardView.setFocusable(true);
        cardView.setFocusableInTouchMode(true);
        updateCardBackgroundColor(cardView, false);

        return new ViewHolder(cardView);
    }

    private static void updateCardBackgroundColor(ImageCardView view, boolean selected) {
        int color = selected ? sSelectedBackgroundColor : sDefaultBackgroundColor;

        if (view.getTag() != null && view.getTag() instanceof Palette) {
            Palette palette = (Palette) view.getTag();
            final int darkVibColor = palette.getDarkVibrantColor(sSelectedBackgroundColor);
            final int darkVibColorDim = ColorUtils.compositeColors(0xA0FFFFFF & darkVibColor, 0xFF000000);
            color = selected ? darkVibColor : darkVibColorDim;
        }

        // Both background colors should be set because the view's background is temporarily visible
        // during animations.
        view.setBackgroundColor(color);
        view.findViewById(R.id.info_field).setBackgroundColor(color);
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        final ImageCardView cardView = (ImageCardView) viewHolder.view;
        final Context ctx = viewHolder.view.getContext();

        if (item instanceof Artist) {
            Artist artist = (Artist) item;
            cardView.setTitleText(artist.getName());
            cardView.setContentText(mContext.getString(R.string.artist));
        } else if (item instanceof Album) {
            Album album = (Album) item;
            cardView.setTitleText(album.getName());
            cardView.setContentText(mContext.getString(R.string.album));

            String artistRef = Utils.getMainArtist(album);
            if (artistRef != null) {
                Artist artist = ProviderAggregator.getDefault().retrieveArtist(artistRef, album.getProvider());
                if (artist != null && artist.getName() != null && !TextUtils.isEmpty(artist.getName())) {
                    cardView.setContentText(artist.getName());
                }
            }
        } else if (item instanceof Playlist) {
            Playlist playlist = (Playlist) item;
            cardView.setTitleText(playlist.getName());
            cardView.setContentText(ctx.getResources().getQuantityString(R.plurals.nb_tracks,
                    playlist.getSongsCount(), playlist.getSongsCount()));
        } else if (item instanceof MyLibraryItem) {
            MyLibraryItem libraryItem = (MyLibraryItem) item;
            switch (libraryItem.getType()) {
                case MyLibraryItem.TYPE_ALBUMS:
                    cardView.setTitleText(ctx.getString(R.string.tab_albums));
                    break;

                case MyLibraryItem.TYPE_ARTISTS:
                    cardView.setTitleText(ctx.getString(R.string.tab_artists));
                    break;
            }

            cardView.setMainImage(ctx.getResources().getDrawable(R.drawable.album_placeholder));
            updateCardBackgroundColor(cardView, cardView.isSelected());
        } else if (item instanceof ProviderConnection) {
            ProviderConnection connection = (ProviderConnection) item;
            try {
                Drawable icon = ctx.getPackageManager().getApplicationIcon(connection.getPackage());
                cardView.setMainImage(icon);
            } catch (PackageManager.NameNotFoundException e) {
                // set default icon
                cardView.setMainImage(ctx.getResources().getDrawable(R.mipmap.ic_launcher));
            }
            cardView.setTitleText(connection.getProviderName());
            cardView.setContentText(connection.getAuthorName());
            cardView.setMainImageScaleType(ImageView.ScaleType.CENTER_INSIDE);
            cardView.getMainImageView().setBackgroundColor(0xFFFFFFFF);
        }

        cardView.setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT);

        if (item instanceof BoundEntity) {
            AlbumArtHelper.retrieveAlbumArt(mContext.getResources(), new AlbumArtHelper.AlbumArtListener() {
                @Override
                public void onArtLoaded(RecyclingBitmapDrawable output, BoundEntity request) {
                    if (output != null) {
                        Palette palette = Palette.from(output.getBitmap()).generate();
                        cardView.setMainImage(output, true);
                        cardView.setTag(palette);
                        updateCardBackgroundColor(cardView, cardView.isSelected());
                    }
                }
            }, (BoundEntity) item, CARD_WIDTH, false);
        }
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        ImageCardView cardView = (ImageCardView) viewHolder.view;
        // Remove references to images so that the garbage collector can free up memory
        cardView.setBadgeImage(null);
        cardView.setMainImage(mDefaultCardImage);
    }
}
