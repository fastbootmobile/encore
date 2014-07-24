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
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.providers.DSPConnection;
import org.omnirom.music.providers.ProviderConnection;
import org.omnirom.music.providers.ProviderIdentifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Guigui on 24/07/2014.
 */
public class DspAdapter extends BaseAdapter {

    private List<DSPConnection> mProviders;

    private class ViewHolder {
        TextView tvProviderName;
        TextView tvProviderAuthor;
        ImageView ivProviderIcon;
    }

    public DspAdapter(List<ProviderIdentifier> list) {
        mProviders = new ArrayList<DSPConnection>();
        for (ProviderIdentifier id : list) {
            mProviders.add(PluginsLookup.getDefault().getDSP(id));
        }
    }

    public void addProvider(DSPConnection connection) {
        mProviders.add(connection);
    }

    @Override
    public int getCount() {
        return mProviders.size();
    }

    @Override
    public DSPConnection getItem(int i) {
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

        DSPConnection provider = getItem(i);
        tag.tvProviderName.setText(provider.getProviderName());
        tag.tvProviderAuthor.setText(provider.getAuthorName());

        try {
            Drawable icon = context.getPackageManager().getApplicationIcon(provider.getPackage());
            tag.ivProviderIcon.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            // ignore
        }

        return view;
    }
}
