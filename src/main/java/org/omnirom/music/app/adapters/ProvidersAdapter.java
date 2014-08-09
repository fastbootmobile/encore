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

import java.util.ArrayList;
import java.util.List;
import java.util.zip.Inflater;

/**
 * Created by Guigui on 24/07/2014.
 */
public class ProvidersAdapter extends BaseAdapter {

    private List<ProviderConnection> mProviders;

    private class ViewHolder {
        TextView tvProviderName;
        TextView tvProviderAuthor;
        ImageView ivProviderIcon;
    }

    public ProvidersAdapter(List<ProviderConnection> list) {
        mProviders = list;
    }

    public void addProvider(ProviderConnection connection) {
        mProviders.add(connection);
    }

    @Override
    public int getCount() {
        return mProviders.size();
    }

    @Override
    public ProviderConnection getItem(int i) {
        return mProviders.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

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
            tag.ivProviderIcon.setImageResource(R.drawable.ic_launcher);
        }

        return view;
    }
}
