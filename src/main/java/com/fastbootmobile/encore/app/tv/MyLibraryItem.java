package com.fastbootmobile.encore.app.tv;

public class MyLibraryItem {
    public static final int TYPE_ARTISTS = 1;
    public static final int TYPE_ALBUMS = 2;

    private final int mType;

    public MyLibraryItem(int type) {
        mType = type;
    }

    public int getType() {
        return mType;
    }
}
