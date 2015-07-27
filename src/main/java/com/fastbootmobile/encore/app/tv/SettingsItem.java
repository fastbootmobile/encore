/*
 * Copyright (C) 2015 Fastboot Mobile, LLC.
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
