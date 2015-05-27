package com.fastbootmobile.encore.app.tv;

import android.support.v17.leanback.widget.Presenter;
import android.view.ViewGroup;
import android.widget.TextView;

// TODO: Test class, mutate/remove me!
public class StringPresenter extends Presenter {
    private static final String TAG = "StringPresenter";

    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        TextView textView = new TextView(parent.getContext());
        textView.setFocusable(true);
        textView.setFocusableInTouchMode(true);
        /*textView.setBackground(
                parent.getContext().getResources().getDrawable(R.drawable.text_bg));*/
        return new ViewHolder(textView);
    }

    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        ((TextView) viewHolder.view).setText(item.toString());
    }

    public void onUnbindViewHolder(ViewHolder viewHolder) {
        // no op
    }
}
