package org.omnirom.music.app.ui;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;
import org.omnirom.music.app.R;
/**
 * Created by h4o on 25/06/2014.
 */

public class ExpandableGridView extends ExpandableListView {

    /**
     * Disables stretching.
     *
     * @see #setStretchMode(int)
     */
    public static final int NO_STRETCH = 0;
    /**
     * Stretches the spacing between columns.
     *
     * @see #setStretchMode(int)
     */
    public static final int STRETCH_SPACING = 1;
    /**
     * Stretches columns.
     *
     * @see #setStretchMode(int)
     */
    public static final int STRETCH_COLUMN_WIDTH = 2;
    /**
     * Stretches the spacing between columns. The spacing is uniform.
     *
     * @see #setStretchMode(int)
     */
    public static final int STRETCH_SPACING_UNIFORM = 3;

    /**
     * Creates as many columns as can fit on screen.
     *
     * @see #setNumColumns(int)
     */
    public static final int AUTO_FIT = -1;

    private int mNumColumns = AUTO_FIT;

    private int mHorizontalSpacing = 0;
    private int mRequestedHorizontalSpacing;
    private int mVerticalSpacing = 0;
    private int mStretchMode = STRETCH_COLUMN_WIDTH;
    private int mColumnWidth;
    private int mRequestedColumnWidth;
    private int mRequestedNumColumns;

    public ExpandableGridView(Context context) {
        this(context, null);
    }

    public ExpandableGridView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ExpandableGridView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.ExpandableGridView, defStyle, 0);

        int hSpacing = a.getDimensionPixelOffset(
                R.styleable.ExpandableGridView_gv_horizontalSpacing, 0);
        setHorizontalSpacing(hSpacing);

        int vSpacing = a.getDimensionPixelOffset(
                R.styleable.ExpandableGridView_gv_verticalSpacing, 0);
        setVerticalSpacing(vSpacing);

        int index = a.getInt(R.styleable.ExpandableGridView_stretchMode, STRETCH_COLUMN_WIDTH);
        if (index >= 0) {
            setStretchMode(index);
        }

        int columnWidth = a.getDimensionPixelOffset(R.styleable.ExpandableGridView_columnWidth, -1);
        if (columnWidth > 0) {
            setColumnWidth(columnWidth);
        }

        int numColumns = a.getInt(R.styleable.ExpandableGridView_numColumns, 1);
        setNumColumns(numColumns);

        //I haven't dealt with gravity yet, so this is commented for now...
        /*index = a.getInt(R.styleable.ExpandableGridView_gravity, -1);
        if (index >= 0) {
            setGravity(index);
        }*/

        a.recycle();
    }

    @Override
    public void setAdapter(ExpandableListAdapter adapter) {
        super.setAdapter(new ExpandableGridInnerAdapter(adapter));
    }

    /**
     * Set the amount of horizontal (x) spacing to place between each item
     * in the grid.
     *
     * @param horizontalSpacing The amount of horizontal space between items,
     * in pixels.
     *
     * @attr ref android.R.styleable#GridView_horizontalSpacing
     */
    public void setHorizontalSpacing(int horizontalSpacing) {
        if (horizontalSpacing != mRequestedHorizontalSpacing) {
            mRequestedHorizontalSpacing = horizontalSpacing;
            requestLayout();
        }
    }

    /**
     * Returns the amount of horizontal spacing currently used between each item in the grid.
     *
     * <p>This is only accurate for the current layout. If {@link #setHorizontalSpacing(int)}
     * has been called but layout is not yet complete, this method may return a stale value.
     * To get the horizontal spacing that was explicitly requested use
     * {@link #getRequestedHorizontalSpacing()}.</p>
     *
     * @return Current horizontal spacing between each item in pixels
     *
     * @see #setHorizontalSpacing(int)
     * @see #getRequestedHorizontalSpacing()
     *
     * @attr ref android.R.styleable#GridView_horizontalSpacing
     */
    public int getHorizontalSpacing() {
        return mHorizontalSpacing;
    }

    /**
     * Returns the requested amount of horizontal spacing between each item in the grid.
     *
     * <p>The value returned may have been supplied during inflation as part of a style,
     * the default GridView style, or by a call to {@link #setHorizontalSpacing(int)}.
     * If layout is not yet complete or if GridView calculated a different horizontal spacing
     * from what was requested, this may return a different value from
     * {@link #getHorizontalSpacing()}.</p>
     *
     * @return The currently requested horizontal spacing between items, in pixels
     *
     * @see #setHorizontalSpacing(int)
     * @see #getHorizontalSpacing()
     *
     * @attr ref android.R.styleable#GridView_horizontalSpacing
     */
    public int getRequestedHorizontalSpacing() {
        return mRequestedHorizontalSpacing;
    }

    /**
     * Set the amount of vertical (y) spacing to place between each item
     * in the grid.
     *
     * @param verticalSpacing The amount of vertical space between items,
     * in pixels.
     *
     * @see #getVerticalSpacing()
     *
     * @attr ref android.R.styleable#GridView_verticalSpacing
     */
    public void setVerticalSpacing(int verticalSpacing) {
        if (verticalSpacing != mVerticalSpacing) {
            mVerticalSpacing = verticalSpacing;
            requestLayout();
        }
    }

    /**
     * Returns the amount of vertical spacing between each item in the grid.
     *
     * @return The vertical spacing between items in pixels
     *
     * @see #setVerticalSpacing(int)
     *
     * @attr ref android.R.styleable#GridView_verticalSpacing
     */
    public int getVerticalSpacing() {
        return mVerticalSpacing;
    }

    /**
     * Control how items are stretched to fill their space.
     *
     * @param stretchMode Either {@link #NO_STRETCH},
     * {@link #STRETCH_SPACING}, {@link #STRETCH_SPACING_UNIFORM}, or {@link #STRETCH_COLUMN_WIDTH}.
     *
     * @attr ref android.R.styleable#GridView_stretchMode
     */
    public void setStretchMode(int stretchMode) {
        if (stretchMode != mStretchMode) {
            mStretchMode = stretchMode;
            requestLayout();
        }
    }

    public int getStretchMode() {
        return mStretchMode;
    }

    /**
     * Set the width of columns in the grid.
     *
     * @param columnWidth The column width, in pixels.
     *
     * @attr ref android.R.styleable#GridView_columnWidth
     */
    public void setColumnWidth(int columnWidth) {
        if (columnWidth != mRequestedColumnWidth) {
            mRequestedColumnWidth = columnWidth;
            requestLayout();
        }
    }

    /**
     * Return the width of a column in the grid.
     *
     * <p>This may not be valid yet if a layout is pending.</p>
     *
     * @return The column width in pixels
     *
     * @see #setColumnWidth(int)
     * @see #getRequestedColumnWidth()
     *
     * @attr ref android.R.styleable#GridView_columnWidth
     */
    public int getColumnWidth() {
        return mColumnWidth;
    }

    /**
     * Return the requested width of a column in the grid.
     *
     * <p>This may not be the actual column width used. Use {@link #getColumnWidth()}
     * to retrieve the current real width of a column.</p>
     *
     * @return The requested column width in pixels
     *
     * @see #setColumnWidth(int)
     * @see #getColumnWidth()
     *
     * @attr ref android.R.styleable#GridView_columnWidth
     */
    public int getRequestedColumnWidth() {
        return mRequestedColumnWidth;
    }

    /**
     * Set the number of columns in the grid
     *
     * @param numColumns The desired number of columns.
     *
     * @attr ref android.R.styleable#GridView_numColumns
     */
    public void setNumColumns(int numColumns) {
        if (numColumns != mRequestedNumColumns) {
            mRequestedNumColumns = numColumns;
            requestLayout();
        }
    }

    /**
     * Get the number of columns in the grid.
     * Returns {@link #AUTO_FIT} if the Grid has never been laid out.
     *
     * @attr ref android.R.styleable#GridView_numColumns
     *
     * @see #setNumColumns(int)
     */
    @ViewDebug.ExportedProperty
    public int getNumColumns() {
        return mNumColumns;
    }

    public ExpandableListAdapter getInnerAdapter() {
        return ((ExpandableGridInnerAdapter)getExpandableListAdapter()).mInnerAdapter;
    }

    private boolean determineColumns(int availableSpace) {
        final int requestedHorizontalSpacing = mRequestedHorizontalSpacing;
        final int stretchMode = mStretchMode;
        final int requestedColumnWidth = mRequestedColumnWidth;
        boolean didNotInitiallyFit = false;

        if (mRequestedNumColumns == AUTO_FIT) {
            if (requestedColumnWidth > 0) {
                // Client told us to pick the number of columns
                mNumColumns = (availableSpace + requestedHorizontalSpacing) /
                        (requestedColumnWidth + requestedHorizontalSpacing);
            } else {
                // Just make up a number if we don't have enough info
                mNumColumns = 2;
            }
        } else {
            // We picked the columns
            mNumColumns = mRequestedNumColumns;
        }

        if (mNumColumns <= 0) {
            mNumColumns = 1;
        }

        switch (stretchMode) {
            case NO_STRETCH:
                // Nobody stretches
                mColumnWidth = requestedColumnWidth;
                mHorizontalSpacing = requestedHorizontalSpacing;
                break;

            default:
                int spaceLeftOver = availableSpace - (mNumColumns * requestedColumnWidth) -
                        ((mNumColumns - 1) * requestedHorizontalSpacing);

                if (spaceLeftOver < 0) {
                    didNotInitiallyFit = true;
                }

                switch (stretchMode) {
                    case STRETCH_COLUMN_WIDTH:
                        // Stretch the columns
                        mColumnWidth = requestedColumnWidth + spaceLeftOver / mNumColumns;
                        mHorizontalSpacing = requestedHorizontalSpacing;
                        break;

                    case STRETCH_SPACING:
                        // Stretch the spacing between columns
                        mColumnWidth = requestedColumnWidth;
                        if (mNumColumns > 1) {
                            mHorizontalSpacing = requestedHorizontalSpacing +
                                    spaceLeftOver / (mNumColumns - 1);
                        } else {
                            mHorizontalSpacing = requestedHorizontalSpacing + spaceLeftOver;
                        }
                        break;

                    case STRETCH_SPACING_UNIFORM:
                        // Stretch the spacing between columns
                        mColumnWidth = requestedColumnWidth;
                        if (mNumColumns > 1) {
                            mHorizontalSpacing = requestedHorizontalSpacing +
                                    spaceLeftOver / (mNumColumns + 1);
                        } else {
                            mHorizontalSpacing = requestedHorizontalSpacing + spaceLeftOver;
                        }
                        break;
                }

                break;
        }
        return didNotInitiallyFit;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);

        if (widthMode == MeasureSpec.UNSPECIFIED) {
            if (mColumnWidth > 0) {
                widthSize = mColumnWidth + getPaddingLeft() + getPaddingRight();
            } else {
                widthSize = getPaddingLeft() + getPaddingRight();
            }
            widthSize += getVerticalScrollbarWidth();
        }

        int childWidth = widthSize - getPaddingLeft() - getPaddingRight();
        determineColumns(childWidth);
    }

    private class ExpandableGridInnerAdapter implements ExpandableListAdapter {

        private final ExpandableListAdapter mInnerAdapter;

        private ExpandableGridInnerAdapter(ExpandableListAdapter adapter) {
            this.mInnerAdapter = adapter;
        }

        @Override
        public int getGroupCount() {
            return mInnerAdapter.getGroupCount();
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            int realCount = mInnerAdapter.getChildrenCount(groupPosition);

            int count;
            if (mNumColumns != AUTO_FIT) {
                count = realCount > 0 ? (realCount + mNumColumns - 1) / mNumColumns : 0;
            } else {
                count = realCount;
            }

            return count;
        }

        @Override
        public Object getGroup(int groupPosition) {
            return mInnerAdapter.getGroup(groupPosition);
        }

        @Override
        public Object getChild(int groupPosition, int childPosition) {
            return mInnerAdapter.getChild(groupPosition, childPosition);
        }

        @Override
        public long getGroupId(int groupPosition) {
            return mInnerAdapter.getGroupId(groupPosition);
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return 0;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
            return mInnerAdapter.getGroupView(groupPosition, isExpanded, convertView, parent);
        }

        @SuppressLint("InlinedApi")
        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
            LinearLayout row = (LinearLayout) (convertView != null ? convertView : new LinearLayout(getContext()));

            if (row.getLayoutParams() == null) {
                row.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, AbsListView.LayoutParams.WRAP_CONTENT, AbsListView.ITEM_VIEW_TYPE_IGNORE));
                row.setPadding(0, mVerticalSpacing / 2, 0, mVerticalSpacing / 2);
                row.setGravity(Gravity.CENTER_HORIZONTAL);
            }

            int groupChildrenCount = mInnerAdapter.getChildrenCount(groupPosition);

            int index = 0;
            for (int i=mNumColumns * childPosition; i<(mNumColumns * (childPosition + 1)); i++, index++) {
                View child;

                View cachedChild = index < row.getChildCount() ? row.getChildAt(index) : null;

                if (i<groupChildrenCount) {
                    if (cachedChild != null && cachedChild.getTag() == null) {
                        ((ViewGroup)cachedChild.getParent()).removeView(cachedChild);
                        cachedChild = null;
                    }

                    child = mInnerAdapter.getChildView(groupPosition, i, i == (groupChildrenCount - 1), cachedChild, parent);
                    child.setTag(mInnerAdapter.getChild(groupPosition, i));
                } else {
                    if (cachedChild != null && cachedChild.getTag() != null) {
                        ((ViewGroup)cachedChild.getParent()).removeView(cachedChild);
                        cachedChild = null;
                    }

                    child = new View(getContext());
                    child.setTag(null);
                }

                if (!(child.getLayoutParams() instanceof LinearLayout.LayoutParams)) {
                    LinearLayout.LayoutParams params;
                    if (child.getLayoutParams() == null) {
                        params = new LinearLayout.LayoutParams(mColumnWidth, LayoutParams.WRAP_CONTENT, 1);
                    } else {
                        params = new LinearLayout.LayoutParams(mColumnWidth, child.getLayoutParams().height, 1);
                    }

                    child.setLayoutParams(params);
                }

                child.setPadding(mHorizontalSpacing / 2, 0, mHorizontalSpacing / 2, 0);

                if (index == row.getChildCount()) {
                    row.addView(child, index);
                } else {
                    child.invalidate();
                }
            }

            return row;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return false;
        }

        @Override
        public void registerDataSetObserver(DataSetObserver observer) {
            mInnerAdapter.registerDataSetObserver(observer);
        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver observer) {
            mInnerAdapter.unregisterDataSetObserver(observer);
        }

        @Override
        public boolean areAllItemsEnabled() {
            return mInnerAdapter.areAllItemsEnabled();
        }

        @Override
        public boolean isEmpty() {
            return mInnerAdapter.isEmpty();
        }

        @Override
        public void onGroupExpanded(int groupPosition) {
            mInnerAdapter.onGroupExpanded(groupPosition);
        }

        @Override
        public void onGroupCollapsed(int groupPosition) {
            mInnerAdapter.onGroupCollapsed(groupPosition);
        }

        /*@Override
        public long getCombinedChildId(long groupId, long childId) {
            return mInnerAdapter.getCombinedChildId(groupId, childId);
        }

        @Override
        public long getCombinedGroupId(long groupId) {
            return mInnerAdapter.getCombinedGroupId(groupId);
        }*/

        public long getCombinedChildId(long groupId, long childId) {
            return 0x8000000000000000L | ((groupId & 0x7FFFFFFF) << 32) | (childId & 0xFFFFFFFF);
        }

        public long getCombinedGroupId(long groupId) {
            return (groupId & 0x7FFFFFFF) << 32;
        }
    }
}
