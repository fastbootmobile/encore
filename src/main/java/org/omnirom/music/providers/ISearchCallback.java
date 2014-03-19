package org.omnirom.music.providers;


import org.omnirom.music.model.Song;

import java.util.List;

public interface ISearchCallback {
    public void onSearchStart();
    public void onSearchFinish(boolean error);
    public void onSearchResultsUpdate(List<Song> songs);
}
