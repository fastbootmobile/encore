package org.omnirom.music.app.adapters;

import android.content.Context;
import android.text.Layout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.omnirom.music.api.echonest.AutoMixBucket;
import org.omnirom.music.app.R;

import java.util.List;
import java.util.zip.Inflater;

/**
 * Created by Guigui on 15/08/2014.
 */
public class BucketAdapter extends BaseAdapter {

    public static class ViewHolder {
        public TextView tvBucketName;
        public ProgressBar pbBucketSpinner;
    }

    private List<AutoMixBucket> mBuckets;

    public void setBuckets(List<AutoMixBucket> buckets) {
        mBuckets = buckets;
    }

    @Override
    public int getCount() {
        return mBuckets == null ? 0 : mBuckets.size();
    }

    @Override
    public AutoMixBucket getItem(int i) {
        return mBuckets != null ? mBuckets.get(i) : null;
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        ViewHolder tag;

        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) viewGroup.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.item_bucket, viewGroup, false);
            tag = new ViewHolder();
            tag.tvBucketName = (TextView) view.findViewById(R.id.tvBucketName);
            tag.pbBucketSpinner = (ProgressBar) view.findViewById(R.id.pbBucketSpinner);
            view.setTag(tag);
        } else {
            tag = (ViewHolder) view.getTag();
        }

        AutoMixBucket bucket = getItem(i);
        tag.tvBucketName.setText(bucket.getName());

        return view;
    }
}
