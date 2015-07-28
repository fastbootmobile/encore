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

package com.fastbootmobile.encore.app.fragments;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.TransactionTooLargeException;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.CardView;
import android.text.Spannable;
import android.text.SpannableString;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.TextView;

import com.fastbootmobile.encore.api.echonest.AutoMixBucket;
import com.fastbootmobile.encore.api.echonest.AutoMixManager;
import com.fastbootmobile.encore.app.AppActivity;
import com.fastbootmobile.encore.app.MainActivity;
import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.app.SearchActivity;
import com.fastbootmobile.encore.app.SettingsActivity;
import com.fastbootmobile.encore.app.adapters.ListenNowAdapter;
import com.fastbootmobile.encore.app.ui.ParallaxScrollListView;
import com.fastbootmobile.encore.app.ui.ScrollStatusBarColorListener;
import com.fastbootmobile.encore.framework.ListenLogger;
import com.fastbootmobile.encore.framework.PluginsLookup;
import com.fastbootmobile.encore.model.Album;
import com.fastbootmobile.encore.model.Artist;
import com.fastbootmobile.encore.model.Playlist;
import com.fastbootmobile.encore.model.SearchResult;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.ILocalCallback;
import com.fastbootmobile.encore.providers.IMusicProvider;
import com.fastbootmobile.encore.providers.ProviderAggregator;
import com.fastbootmobile.encore.providers.ProviderConnection;
import com.fastbootmobile.encore.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A simple {@link Fragment} subclass showing ideas of tracks and albums to listen to.
 * Use the {@link ListenNowFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ListenNowFragment extends Fragment implements ILocalCallback {
    private static final String TAG = "ListenNowFragment";
    private static final boolean DEBUG = false;

    private static final String PREFS = "listen_now";
    private static final String LANDCARD_NO_CUSTOM_PROVIDERS = "card_no_custom";
    private static final String LANDCARD_SOUND_EFFECTS = "card_sound_effects";

    private View mHeaderView;
    private int mBackgroundColor;
    private EditText mSearchBox;
    private CardView mCardSearchBox;
    private AbsListView.OnScrollListener mScrollListener;
    private ListenNowAdapter mAdapter;
    private Handler mHandler;
    private Thread mItemsSetupThread;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment ListenNowFragment.
     */
    public static ListenNowFragment newInstance() {
        return new ListenNowFragment();
    }

    /**
     * Default empty constructor
     */
    public ListenNowFragment() {
        mScrollListener = new ScrollStatusBarColorListener() {
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (view.getChildCount() == 0 || getActivity() == null) {
                    return;
                }

                final float heroHeight = mHeaderView.getMeasuredHeight();
                final float scrollY = getScroll(view);
                final float toolbarBgAlpha = Math.min(1, scrollY / heroHeight);
                final int toolbarAlphaInteger = (((int) (toolbarBgAlpha * 255)) << 24) | 0xFFFFFF;
                mColorDrawable.setColor(toolbarAlphaInteger & mBackgroundColor);

                SpannableString spannableTitle = new SpannableString(((MainActivity) getActivity()).getFragmentTitle());
                mAlphaSpan.setAlpha(toolbarBgAlpha);

                ActionBar actionbar = ((AppActivity) getActivity()).getSupportActionBar();
                if (actionbar != null) {
                    actionbar.setBackgroundDrawable(mColorDrawable);
                    spannableTitle.setSpan(mAlphaSpan, 0, spannableTitle.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    actionbar.setTitle(spannableTitle);
                }

                mCardSearchBox.setAlpha(1.0f - toolbarBgAlpha);
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBackgroundColor = getResources().getColor(R.color.primary);
        mHandler = new Handler();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    public float getToolbarAlpha() {
        return 1.0f - mCardSearchBox.getAlpha();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_listen_now, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        View root = getView();

        if (root != null) {
            ParallaxScrollListView listView = (ParallaxScrollListView) root;
            listView.setOnScrollListener(mScrollListener);
            setupHeader(listView);

            mAdapter = new ListenNowAdapter();
            listView.setAdapter(mAdapter);
            setupItems();
        }
    }

    @Override
    public void onDetach() {
        if (mItemsSetupThread != null && mItemsSetupThread.isAlive()) {
            mItemsSetupThread.interrupt();
        }
        super.onDetach();
    }

    private void setupHeader(ParallaxScrollListView listView) {
        LayoutInflater inflater = LayoutInflater.from(listView.getContext());
        mHeaderView = inflater.inflate(R.layout.header_listen_now, listView, false);
        mCardSearchBox = (CardView) mHeaderView.findViewById(R.id.cardSearchBox);
        mSearchBox = (EditText) mHeaderView.findViewById(R.id.ebSearch);
        mSearchBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                final String query = v.getText().toString();

                Intent intent = new Intent(getActivity(), SearchActivity.class);
                intent.setAction(Intent.ACTION_SEARCH);
                intent.putExtra(SearchManager.QUERY, query);
                v.getContext().startActivity(intent);

                // Clear the box once searched
                v.setText(null);

                return true;
            }
        });

        listView.addParallaxedHeaderView(mHeaderView);
    }

    private void setupItems() {
        mItemsSetupThread = new Thread() {
            public void run() {
                final Context context = getActivity();

                if (context == null) {
                    Log.e(TAG, "Invalid context when generating Listen Now items!");
                    return;
                }

                final List<ListenNowAdapter.ListenNowItem> items = new ArrayList<>();
                final ProviderAggregator aggregator = ProviderAggregator.getDefault();
                final PluginsLookup plugins = PluginsLookup.getDefault();
                final List<Playlist> playlists = aggregator.getAllPlaylists();
                final List<Song> songs = new ArrayList<>();

                // Put a card to notify of sound effects
                final SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
                if (!prefs.getBoolean(LANDCARD_SOUND_EFFECTS, false)) {
                    // Show the "You have no custom providers" card
                    final ListenNowAdapter.CardItem item = new ListenNowAdapter.CardItem(getString(R.string.ln_landcard_sfx_title),
                            getString(R.string.ln_landcard_sfx_body),
                            getString(R.string.browse), new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            prefs.edit().putBoolean(LANDCARD_SOUND_EFFECTS, true).apply();
                            v.getContext().startActivity(new Intent(v.getContext(), SettingsActivity.class));
                        }
                    },
                            getString(R.string.ln_landcard_dismiss), new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            prefs.edit().putBoolean(LANDCARD_SOUND_EFFECTS, true).apply();
                            // This item must always be the first of the list
                            mAdapter.removeItem((ListenNowAdapter.ListenNowItem) v.getTag());
                            mAdapter.notifyDataSetChanged();
                        }
                    });
                    mAdapter.addItem(item);
                }

                // Put cards for new providers
                Set<ProviderConnection> newPlugins = PluginsLookup.getDefault().getNewPlugins();
                if (newPlugins != null) {
                    for (final ProviderConnection plugin : newPlugins) {
                        final ListenNowAdapter.CardItem item;
                        if (plugin.getConfigurationActivity() == null) {
                            item = new ListenNowAdapter.CardItem(String.format(getString(R.string.ln_landcard_plugin_installed_title), plugin.getProviderName()),
                                    getString(R.string.ln_landcard_plugin_installed_body),
                                    getString(R.string.ln_landcard_dismiss), new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    prefs.edit().putBoolean(LANDCARD_SOUND_EFFECTS, true).apply();
                                    // This item must always be the first of the list
                                    mAdapter.removeItem((ListenNowAdapter.ListenNowItem) v.getTag());
                                    mAdapter.notifyDataSetChanged();
                                }
                            });
                        } else {
                            item = new ListenNowAdapter.CardItem(String.format(getString(R.string.ln_landcard_plugin_installed_title), plugin.getProviderName()),
                                    getString(R.string.ln_landcard_plugin_installed_body_configure),
                                    getString(R.string.ln_landcard_dismiss),
                                    new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            prefs.edit().putBoolean(LANDCARD_SOUND_EFFECTS, true).apply();
                                            // This item must always be the first of the list
                                            mAdapter.removeItem((ListenNowAdapter.ListenNowItem) v.getTag());
                                            mAdapter.notifyDataSetChanged();
                                        }
                                    }, getString(R.string.configure),
                                    new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            mAdapter.removeItem((ListenNowAdapter.ListenNowItem) v.getTag());
                                            mAdapter.notifyDataSetChanged();

                                            Intent intent = new Intent();
                                            intent.setClassName(plugin.getPackage(), plugin.getConfigurationActivity());
                                            startActivity(intent);
                                        }
                                    });
                        }
                        mAdapter.addItem(item);
                    }
                    PluginsLookup.getDefault().resetNewPlugins();
                }

                // Get the list of songs first
                final List<ProviderConnection> providers = plugins.getAvailableProviders();
                for (ProviderConnection provider : providers) {
                    int limit = 50;
                    int offset = 0;

                    while (!isInterrupted()) {
                        try {
                            List<Song> providerSongs = provider.getBinder().getSongs(offset, limit);

                            if (providerSongs != null) {
                                songs.addAll(providerSongs);
                                offset += providerSongs.size();

                                if (providerSongs.size() < limit) {
                                    if (DEBUG) Log.d(TAG, "Got " + providerSongs.size() + " instead of " + limit + ", assuming end of list");
                                    break;
                                }
                            } else {
                                break;
                            }
                        } catch (TransactionTooLargeException e) {
                            limit -= 5;
                            if (limit <= 0) {
                                Log.e(TAG, "Error getting songs from " + provider.getProviderName()
                                        + ": transaction too large even with limit = 5");
                                break;
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error getting songs from " + provider.getProviderName() + ": " + e.getMessage());
                            break;
                        }
                    }
                }

                if (isInterrupted() || isDetached()) return;

                // Add a card if we have local music, but no cloud providers
                if (providers.size() <= PluginsLookup.BUNDLED_PROVIDERS_COUNT && songs.size() > 0) {
                    if (!prefs.getBoolean(LANDCARD_NO_CUSTOM_PROVIDERS, false)) {
                        // Show the "You have no custom providers" card
                        final ListenNowAdapter.CardItem item = new ListenNowAdapter.CardItem(getString(R.string.ln_landcard_nocustomprovider_title),
                                getString(R.string.ln_landcard_nocustomprovider_body),
                                getString(R.string.browse), new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                ProviderDownloadDialog.newInstance(false).show(getFragmentManager(), "DOWN");
                            }
                        },
                                getString(R.string.ln_landcard_dismiss), new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                prefs.edit().putBoolean(LANDCARD_NO_CUSTOM_PROVIDERS, true).apply();
                                // This item must always be the first of the list
                                mAdapter.removeItem((ListenNowAdapter.ListenNowItem) v.getTag());
                                mAdapter.notifyDataSetChanged();
                            }
                        });

                        items.add(item);
                    }
                }

                if (isInterrupted() || isDetached()) return;

                // Add a card if there's no music at all (no songs and no playlists)
                if (providers.size() <= PluginsLookup.BUNDLED_PROVIDERS_COUNT && songs.size() == 0 && playlists.size() == 0) {
                    items.add(new ListenNowAdapter.CardItem(getString(R.string.ln_card_nothing_title),
                            getString(R.string.ln_card_nothing_body),
                            getString(R.string.browse),
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    ProviderDownloadDialog.newInstance(false).show(getFragmentManager(), "DOWN");
                                }
                            },
                            getString(R.string.configure),
                            new View.OnClickListener() {
                                public void onClick(View v) {
                                    ((MainActivity) context).openSection(MainActivity.SECTION_SETTINGS);
                                }
                            }));

                    items.add(new ListenNowAdapter.CardItem(getString(R.string.ln_card_nothinghint_title),
                            getString(R.string.ln_card_nothinghint_body), null, null));
                }

                if (isInterrupted() || isDetached()) return;

                // Add the "Recently played" section if we have recent tracks
                final ListenLogger logger = new ListenLogger(context);
                List<ListenLogger.LogEntry> logEntries = logger.getEntries(50);

                if (logEntries.size() > 0 && !isDetached()) {
                    items.add(new ListenNowAdapter.SectionHeaderItem(getString(R.string.ln_section_recents),
                            R.drawable.ic_nav_history_active, getString(R.string.more), new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            ((MainActivity) context).openSection(MainActivity.SECTION_HISTORY);
                        }
                    }));

                    int i = 0;
                    List<ListenNowAdapter.ItemCardItem> itemsCouple = new ArrayList<>();

                    for (ListenLogger.LogEntry entry : logEntries) {
                        if (i == 4) {
                            // Stop here, add remaining item
                            if (itemsCouple.size() > 0) {
                                for (ListenNowAdapter.ItemCardItem item : itemsCouple) {
                                    items.add(item);
                                }
                            }
                            break;
                        }

                        Song song = aggregator.retrieveSong(entry.getReference(), entry.getIdentifier());
                        if (song != null) {
                            int type = Utils.getRandom(2);
                            if (song.getAlbum() != null && (type == 0 || type == 1 && song.getArtist() == null)) {
                                Album album = aggregator.retrieveAlbum(song.getAlbum(), song.getProvider());

                                if (album != null) {
                                    itemsCouple.add(new ListenNowAdapter.ItemCardItem(album));
                                    ++i;
                                }
                            } else if (song.getArtist() != null) {
                                Artist artist = aggregator.retrieveArtist(song.getArtist(), song.getProvider());

                                if (artist != null) {
                                    itemsCouple.add(new ListenNowAdapter.ItemCardItem(artist));
                                    ++i;
                                }
                            }
                        }

                        if (itemsCouple.size() == 2) {
                            ListenNowAdapter.CardRowItem row = new ListenNowAdapter.CardRowItem(
                                    itemsCouple.get(0),
                                    itemsCouple.get(1)
                            );
                            items.add(row);
                            itemsCouple.clear();
                        }
                    }
                }

                if (isInterrupted() || isDetached()) return;

                // Add playlists section
                items.add(new ListenNowAdapter.SectionHeaderItem(getString(R.string.ln_section_playlists),
                        R.drawable.ic_nav_playlist_active, getString(R.string.browse), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ((MainActivity) context).openSection(MainActivity.SECTION_PLAYLISTS);
                    }
                }));

                if (playlists != null && playlists.size() > 0) {
                    int i = 0;
                    List<ListenNowAdapter.ItemCardItem> itemsCouple = new ArrayList<>();

                    for (Playlist playlist : playlists) {
                        if (i == 4) {
                            // Stop here, add remaining item
                            if (itemsCouple.size() > 0) {
                                for (ListenNowAdapter.ItemCardItem item : itemsCouple) {
                                    items.add(item);
                                }
                            }
                            break;
                        }

                        if (playlist != null) {
                            ListenNowAdapter.ItemCardItem item = new ListenNowAdapter.ItemCardItem(playlist);
                            itemsCouple.add(item);
                            ++i;
                        }

                        if (itemsCouple.size() == 2) {
                            ListenNowAdapter.CardRowItem row = new ListenNowAdapter.CardRowItem(
                                    itemsCouple.get(0),
                                    itemsCouple.get(1)
                            );
                            items.add(row);
                            itemsCouple.clear();
                        }
                    }
                }

                if (isInterrupted() || isDetached()) return;

                // Add automix section
                items.add(new ListenNowAdapter.SectionHeaderItem(getString(R.string.lb_section_automixes),
                        R.drawable.ic_nav_automix_active, getString(R.string.create), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ((MainActivity) context).openSection(MainActivity.SECTION_AUTOMIX);
                    }
                }));

                List<AutoMixBucket> buckets = AutoMixManager.getDefault().getBuckets();
                if (buckets == null || buckets.size() == 0) {
                    items.add(new ListenNowAdapter.GetStartedItem(getString(R.string.ln_automix_getstarted_body),
                            getString(R.string.ln_action_getstarted), new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            ((MainActivity) context).onNavigationDrawerItemSelected(MainActivity.SECTION_AUTOMIX);
                        }
                    }));
                } else {
                    for (final AutoMixBucket bucket : buckets) {
                        items.add(new ListenNowAdapter.SimpleItem(bucket.getName(),
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        new Thread() {
                                            public void run() {
                                                AutoMixManager.getDefault().startPlay(bucket);
                                            }
                                        }.start();
                                    }
                                }));
                    }
                }

                if (isInterrupted() || isDetached()) return;

                mHandler.post(new Runnable() {
                    public void run() {
                        for (ListenNowAdapter.ListenNowItem item : items) {
                            mAdapter.addItem(item);
                        }
                        mAdapter.notifyDataSetChanged();
                    }
                });
            }
        };

        mItemsSetupThread.start();
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        MainActivity mainActivity = (MainActivity) activity;
        mainActivity.onSectionAttached(MainActivity.SECTION_LISTEN_NOW);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onResume() {
        super.onResume();
        ProviderAggregator.getDefault().addUpdateCallback(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPause() {
        super.onPause();
        ProviderAggregator.getDefault().removeUpdateCallback(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSongUpdate(final List<Song> s) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAlbumUpdate(final List<Album> a) {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPlaylistUpdate(List<Playlist> p) {
    }

    @Override
    public void onPlaylistRemoved(String ref) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onArtistUpdate(final List<Artist> a) {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onProviderConnected(IMusicProvider provider) {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSearchResult(List<SearchResult> searchResult) {
    }
}
