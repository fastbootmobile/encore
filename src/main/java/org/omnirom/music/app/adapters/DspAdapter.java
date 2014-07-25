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
    private ClickListener mListener;

    public interface ClickListener {
        public void onDeleteClicked(int position);
        public void onUpClicked(int position);
        public void onDownClicked(int position);
    }

    private class ViewHolder {
        TextView tvProviderName;
        TextView tvProviderAuthor;
        ImageView ivProviderIcon;
        ImageView btnUp;
        ImageView btnDown;
        ImageView btnDelete;
    }

    public DspAdapter(List<ProviderIdentifier> list) {
        mProviders = new ArrayList<DSPConnection>();
        for (ProviderIdentifier id : list) {
            mProviders.add(PluginsLookup.getDefault().getDSP(id));
        }
    }

    public void updateChain(List<ProviderIdentifier> list) {
        mProviders.clear();
        for (ProviderIdentifier id : list) {
            mProviders.add(PluginsLookup.getDefault().getDSP(id));
        }
        notifyDataSetChanged();
    }

    public void addProvider(DSPConnection connection) {
        mProviders.add(connection);
    }

    public void setClickListener(ClickListener listener) {
        mListener = listener;
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

        tag.btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mListener != null) {
                    mListener.onDeleteClicked(i);
                }
            }
        });

        tag.btnUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mListener != null) {
                    mListener.onUpClicked(i);
                }
            }
        });

        tag.btnDown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mListener != null) {
                    mListener.onDownClicked(i);
                }
            }
        });

        return view;
    }
}
