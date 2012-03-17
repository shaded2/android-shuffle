/*
 * Copyright 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dodgybits.shuffle.android.actionbarcompat;
/*
 * Copyright 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.app.Activity;
import android.content.Context;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.widget.*;
import org.dodgybits.android.shuffle.R;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * A class that implements the action bar pattern for pre-Honeycomb devices.
 */
public class ActionBarHelperBase extends ActionBarHelper {
    private static final String TAG = "ActionBarHelperBase";
    
    private static final String MENU_RES_NAMESPACE = "http://schemas.android.com/apk/res/android";
    private static final String MENU_ATTR_ID = "id";
    private static final String MENU_ATTR_SHOW_AS_ACTION = "showAsAction";

    private ImageButton mHomeButton = null;
    private TextView mTitleView;
    private Spinner mSpinner;
    
    private boolean mInitialized = false;

    private boolean mHasSplitBar = false;

    private SupportActionMode mActionMode;
    
    protected Set<Integer> mViewIds = new HashSet<Integer>();

    private int mNavigationMode = ActionBarHelper.NAVIGATION_MODE_STANDARD;
    
    private int mDisplayOptions = 0;
    
    private OnNavigationListener mNavigationListener;
    
    protected ActionBarHelperBase(Activity activity) {
        super(activity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        mActivity.requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onPostCreate");
        init();
    }

    @Override
    public void supportResetOptionsMenu() {
        Log.d(TAG, "supportResetOptionsMenu");
        if (mActionMode == null) {
            SimpleMenu menu = new SimpleMenu(mActivity);
            removeActionItems();
            mViewIds.clear();
            mActivity.onCreatePanelMenu(Window.FEATURE_OPTIONS_PANEL, menu);
            mActivity.onPreparePanel(Window.FEATURE_OPTIONS_PANEL, null, menu);
            addMenuItems(menu);
        } else {
            mActionMode.invalidate();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRefreshActionItemState(boolean refreshing) {
        View refreshButton = mActivity.findViewById(R.id.actionbar_compat_item_refresh);
        View refreshIndicator = mActivity.findViewById(
                R.id.actionbar_compat_item_refresh_progress);

        if (refreshButton != null) {
            refreshButton.setVisibility(refreshing ? View.GONE : View.VISIBLE);
        }
        if (refreshIndicator != null) {
            refreshIndicator.setVisibility(refreshing ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // Hides on-screen action items from the options menu.
        if (!(menu instanceof SimpleMenu)) {
            for (Integer id : mViewIds) {
                MenuItem item = menu.findItem(id);
                if (item != null) {
                    item.setVisible(false);
                } else {
                    Log.e(TAG, "Couldn't find menu id " + id + " in menu " + menu);
                }
            }
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onTitleChanged(CharSequence title, int color) {
        TextView titleView = (TextView) mActivity.findViewById(R.id.actionbar_compat_title);
        if (titleView != null) {
            titleView.setText(title);
        }
    }

    /**
     * Returns a {@link android.view.MenuInflater} that can read action bar metadata on
     * pre-Honeycomb devices.
     */
    @Override
    public MenuInflater getMenuInflater(MenuInflater superMenuInflater) {
        return new WrappedMenuInflater(mActivity, superMenuInflater);
    }

    @Override
    public void startSupportedActionMode(ActionMode.Callback callback) {
        // need to call init here as action mode can be setup prior to postCreate
        init();
        mActionMode = new SupportActionMode(callback);
        onModeChange(true);
    }

    @Override
    public int getDisplayOptions() {
        return mDisplayOptions;
    }

    @Override
    public void setDisplayOptions(int options) {
        mDisplayOptions = options;
    }

    @Override
    public void setCustomView(View view) {
    }

    @Override
    public void setDisplayOptions(int options, int mask) {
    }

    @Override
    public int getNavigationMode() {
        return mNavigationMode;
    }

    @Override
    public void setNavigationMode(int mode) {
        mNavigationMode = mode;
        updateTitleVisibility();
    }


    @Override
    public void setListNavigationCallbacks(SpinnerAdapter adapter, final OnNavigationListener callback) {
        mSpinner.setAdapter(adapter);
        mSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                callback.onNavigationItemSelected(position, id);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    @Override
    public void setSelectedNavigationItem(int position) {
        mSpinner.setSelection(position, false);
    }

    @Override
    public int getSelectedNavigationIndex() {
        return mSpinner.getSelectedItemPosition();
    }

    @Override
    public int getNavigationItemCount() {
        return mSpinner.getCount();
    }

    private void init() {
        if (!mInitialized) {
            mActivity.getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE,
                    R.layout.actionbar_compat);
            setupActionBar();
            supportResetOptionsMenu();
            mInitialized = true;
        }
    }

    private void addMenuItems(Menu menu) {
        for (int i = 0; i < menu.size(); i++) {
            SimpleMenuItem item = (SimpleMenuItem)menu.getItem(i);
            if (mViewIds.contains(item.getItemId())) {
                addActionItemCompatFromMenuItem(item, true);
            }
        }
    }

    /**
     * Sets up the compatibility action bar with the given title.
     */
    private void setupActionBar() {
        Log.d(TAG, "Setting up action bar");

        final ViewGroup actionBarCompat = getActionBarCompatTitleGroup();
        if (actionBarCompat == null) {
            return;
        }

        LinearLayout.LayoutParams springLayoutParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.FILL_PARENT);
        springLayoutParams.weight = 1;

        // Add Home button
        SimpleMenu tempMenu = new SimpleMenu(mActivity);
        SimpleMenuItem homeItem = new SimpleMenuItem(
                tempMenu, android.R.id.home, 0, mActivity.getString(R.string.app_name));
        homeItem.setIcon(R.drawable.shuffle_icon);
        mHomeButton = (ImageButton) addActionItemCompatFromMenuItem(homeItem, false);

        // Add title text
        mTitleView = new TextView(mActivity, null, R.attr.actionbarCompatTitleStyle);
        mTitleView.setLayoutParams(springLayoutParams);
        mTitleView.setText(mActivity.getTitle());
        actionBarCompat.addView(mTitleView);

        mSpinner = new Spinner(mActivity);
        mSpinner.setLayoutParams(springLayoutParams);

        final ViewGroup actionBarCompatMenu = getActionBarCompatMenuGroup();
        mHasSplitBar = actionBarCompatMenu != actionBarCompat;
        if (mHasSplitBar) {
            actionBarCompatMenu.setVisibility(View.VISIBLE);
        }
    }
    
    
    private void onModeChange(boolean actionMode) {
        if (actionMode) {
            getActionBarCompatTitleGroup().setBackgroundResource(R.drawable.cab_background_top_holo_light);
            if (mHasSplitBar) {
                getActionBarCompatMenuGroup().setBackgroundResource(R.drawable.cab_background_bottom_holo_light);
            }
            mHomeButton.setImageResource(R.drawable.ic_cab_done_holo_light);
        } else {
            getActionBarCompatTitleGroup().setBackgroundResource(R.drawable.ab_stacked_transparent_light_holo);
            if (mHasSplitBar) {
                getActionBarCompatMenuGroup().setBackgroundResource(R.drawable.ab_bottom_transparent_light_holo);
            }
            mHomeButton.setImageResource(R.drawable.shuffle_icon);
            mActionMode = null;
        }
        updateTitleVisibility();
        supportResetOptionsMenu();
    }

    private void updateTitleVisibility() {
        final ViewGroup actionBarCompat = getActionBarCompatTitleGroup();
        actionBarCompat.removeViewAt(1);
        if (mActionMode != null || mNavigationMode == ActionBarHelper.NAVIGATION_MODE_STANDARD) {
            actionBarCompat.addView(mTitleView, 1);
        } else {
            actionBarCompat.addView(mSpinner, 1);
        }
    }

    /**
     * Returns the {@link android.view.ViewGroup} for the action bar on phones (compatibility action
     * bar). Can return null, and will return null on Honeycomb.
     */
    private LinearLayout getActionBarCompatTitleGroup() {
        return (LinearLayout) mActivity.findViewById(R.id.actionbar_compat);
    }

    private LinearLayout mActionBarCompatMenuGroup;

    private LinearLayout getActionBarCompatMenuGroup() {
        if (mActionBarCompatMenuGroup == null) {
            ViewStub stub = (ViewStub) mActivity.findViewById(R.id.actionbar_split_compat_stub);
            if (stub == null) {
                mActionBarCompatMenuGroup = getActionBarCompatTitleGroup();
            } else {
                mActionBarCompatMenuGroup = (LinearLayout)stub.inflate();
            }
        }
        return mActionBarCompatMenuGroup;
    }


    /**
     * Adds an action button to the compatibility action bar, using menu information from a {@link
     * android.view.MenuItem}. If the menu item ID is <code>menu_refresh</code>, the menu item's
     * state can be changed to show a loading spinner using
     * {@link ActionBarHelperBase#setRefreshActionItemState(boolean)}.
     */
    private View addActionItemCompatFromMenuItem(final SimpleMenuItem item, boolean isMenu) {
        final int itemId = item.getItemId();

        final LinearLayout actionBar = isMenu ? getActionBarCompatMenuGroup() : getActionBarCompatTitleGroup();
        if (actionBar == null) {
            return null;
        }

        // Create the button
        ImageButton actionButton = new ImageButton(mActivity, null,
                itemId == android.R.id.home
                        ? R.attr.actionbarCompatItemHomeStyle
                        : R.attr.actionbarCompatItemStyle);
        if (itemId == android.R.id.home) {
            actionButton.setLayoutParams(new LinearLayout.LayoutParams(
                    (int) mActivity.getResources().getDimension(R.dimen.actionbar_compat_button_home_width),
                    ViewGroup.LayoutParams.FILL_PARENT));
        } else {
            actionButton.setLayoutParams(new LinearLayout.LayoutParams(
                    (int) mActivity.getResources().getDimension(R.dimen.actionbar_compat_button_width),
                    ViewGroup.LayoutParams.FILL_PARENT,
                    mHasSplitBar ? 1f : 0f // have icons fill panel when using split bar
            ));
        }
        if (itemId == R.id.menu_refresh) {
            actionButton.setId(R.id.actionbar_compat_item_refresh);
        }
        actionButton.setImageDrawable(item.getIcon());
        actionButton.setScaleType(ImageView.ScaleType.CENTER);
        actionButton.setContentDescription(item.getTitle());
        actionButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (mActionMode != null) {
                   mActionMode.onMenuClick(item);
                } else {
                    mActivity.onMenuItemSelected(Window.FEATURE_OPTIONS_PANEL, item);
                }
            }
        });

        item.setActionBarButton(actionButton);

        actionBar.addView(actionButton);

        if (item.getItemId() == R.id.menu_refresh) {
            // Refresh buttons should be stateful, and allow for indeterminate progress indicators,
            // so add those.
            ProgressBar indicator = new ProgressBar(mActivity, null,
                    R.attr.actionbarCompatProgressIndicatorStyle);

            final int buttonWidth = mActivity.getResources().getDimensionPixelSize(
                    R.dimen.actionbar_compat_button_width);
            final int buttonHeight = mActivity.getResources().getDimensionPixelSize(
                    R.dimen.actionbar_compat_height);
            final int progressIndicatorWidth = buttonWidth / 2;

            LinearLayout.LayoutParams indicatorLayoutParams = new LinearLayout.LayoutParams(
                    progressIndicatorWidth, progressIndicatorWidth);
            indicatorLayoutParams.setMargins(
                    (buttonWidth - progressIndicatorWidth) / 2,
                    (buttonHeight - progressIndicatorWidth) / 2,
                    (buttonWidth - progressIndicatorWidth) / 2,
                    0);
            indicator.setLayoutParams(indicatorLayoutParams);
            indicator.setVisibility(View.GONE);
            indicator.setId(R.id.actionbar_compat_item_refresh_progress);
            actionBar.addView(indicator);
        }

        return actionButton;
    }

    private void removeActionItems() {
        final ViewGroup actionBar = getActionBarCompatMenuGroup();
        if (actionBar == null) {
            return;
        }

        int count = actionBar.getChildCount();
        for (int i = count - 1; i >= 0; i--) {
            View view = actionBar.getChildAt(i);
            if (view instanceof ImageButton && view != mHomeButton) {
                actionBar.removeView(view);
            }
        }
    }

    /**
     * A {@link android.view.MenuInflater} that reads action bar metadata.
     */
    private class WrappedMenuInflater extends MenuInflater {
        MenuInflater mInflater;

        public WrappedMenuInflater(Context context, MenuInflater inflater) {
            super(context);
            mInflater = inflater;
        }

        @Override
        public void inflate(int menuRes, Menu menu) {
            loadActionBarMetadata(menuRes);
            mInflater.inflate(menuRes, menu);
        }

        /**
         * Loads action bar metadata from a menu resource, storing a list of menu item IDs that
         * should be shown on-screen (i.e. those with showAsAction set to always or ifRoom).
         *
         * @param menuResId
         */
        private void loadActionBarMetadata(int menuResId) {
            XmlResourceParser parser = null;
            try {
                parser = mActivity.getResources().getXml(menuResId);

                int eventType = parser.getEventType();
                int itemId;
                int showAsAction;

                boolean eof = false;
                while (!eof) {
                    switch (eventType) {
                        case XmlPullParser.START_TAG:
                            if (!parser.getName().equals("item")) {
                                break;
                            }

                            itemId = parser.getAttributeResourceValue(MENU_RES_NAMESPACE,
                                    MENU_ATTR_ID, 0);
                            if (itemId == 0) {
                                break;
                            }

                            showAsAction = parser.getAttributeIntValue(MENU_RES_NAMESPACE,
                                    MENU_ATTR_SHOW_AS_ACTION, -1);
                            if (includeMenuItem(showAsAction)) {
                                mViewIds.add(itemId);
                            }
                            break;

                        case XmlPullParser.END_DOCUMENT:
                            eof = true;
                            break;
                    }

                    eventType = parser.next();
                }
            } catch (XmlPullParserException e) {
                throw new InflateException("Error inflating menu XML", e);
            } catch (IOException e) {
                throw new InflateException("Error inflating menu XML", e);
            } finally {
                if (parser != null) {
                    parser.close();
                }
            }
        }

        private boolean includeMenuItem(int itemFlags) {
            return mActionMode != null ||
                    (itemFlags & (MenuItem.SHOW_AS_ACTION_ALWAYS + MenuItem.SHOW_AS_ACTION_IF_ROOM)) > 0;
        }

    }
    
    private class SupportActionMode extends ActionMode {
        Callback mCallback;
        Menu mMenu;
        CharSequence mOldTitle;

        private SupportActionMode(Callback callback) {
            mCallback = callback;
            mMenu = new SimpleMenu(mActivity);
            mOldTitle = mTitleView.getText();
            callback.onCreateActionMode(this, mMenu);
        }

        private void onMenuClick(MenuItem item) {
            if (item.getItemId() == android.R.id.home) {
                finish();
            } else {
                mCallback.onActionItemClicked(this, item);
            }
        }

        @Override
        public void setTitle(CharSequence title) {
            mTitleView.setText(title);
        }

        @Override
        public void setTitle(int resId) {
            mTitleView.setText(mActivity.getString(resId));
        }

        @Override
        public void setSubtitle(CharSequence subtitle) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setSubtitle(int resId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setCustomView(View view) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void invalidate() {
            mCallback.onPrepareActionMode(this, mMenu);
            removeActionItems();
            addMenuItems(mMenu);
        }

        @Override
        public void finish() {
            mCallback.onDestroyActionMode(this);
            mTitleView.setText(mOldTitle);
            onModeChange(false);
        }

        @Override
        public Menu getMenu() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CharSequence getTitle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CharSequence getSubtitle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public View getCustomView() {
            throw new UnsupportedOperationException();
        }

        @Override
        public MenuInflater getMenuInflater() {
            return new WrappedMenuInflater(mActivity, mActivity.getMenuInflater());
        }
    }
 }
