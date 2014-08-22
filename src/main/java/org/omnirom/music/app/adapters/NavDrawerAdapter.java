package org.omnirom.music.app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.omnirom.music.app.R;

/**
 * Created by Guigui on 21/08/2014.
 */
public class NavDrawerAdapter extends BaseAdapter {

    public static class ViewHolder {
        public TextView tvText;
        public ImageView ivLogo;
    }

    @Override
    public int getCount() {
        return 5;
    }

    @Override
    public Object getItem(int i) {
        return null;
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        ViewHolder tag;
        if (view == null) {
            LayoutInflater inflater =
                    (LayoutInflater) viewGroup.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.nav_drawer_list_item_activated, viewGroup, false);

            tag = new ViewHolder();
            tag.tvText = (TextView) view.findViewById(android.R.id.text1);
            tag.ivLogo = (ImageView) view.findViewById(android.R.id.icon);
            view.setTag(tag);
        } else {
            tag = (ViewHolder) view.getTag();
        }

        switch (i) {
            case 0:
                tag.tvText.setText(R.string.title_section_listen_now);
                tag.ivLogo.setImageResource(R.drawable.ic_nav_listen_now);
                break;

            case 1:
                tag.tvText.setText(R.string.title_section_my_songs);
                tag.ivLogo.setImageResource(R.drawable.ic_nav_library);
                break;

            case 2:
                tag.tvText.setText(R.string.title_section_playlists);
                tag.ivLogo.setImageResource(R.drawable.ic_nav_playlist);
                break;

            case 3:
                tag.tvText.setText(R.string.title_section_automix);
                tag.ivLogo.setImageResource(R.drawable.ic_nav_automix);
                break;

            case 4:
                tag.tvText.setText(R.string.title_activity_playback_queue);
                tag.ivLogo.setImageResource(R.drawable.ic_nav_nowplaying);
        }

        return view;
    }
}
