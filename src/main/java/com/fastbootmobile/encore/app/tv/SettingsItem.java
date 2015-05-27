package com.fastbootmobile.encore.app.tv;

public class SettingsItem {
    public static final int ITEM_PROVIDERS = 1;
    public static final int ITEM_EFFECTS = 2;
    public static final int ITEM_LICENSES = 3;

    private final int mType;

    public SettingsItem(int type) {
        mType = type;
    }

    public int getType() {
        return mType;
    }
}
