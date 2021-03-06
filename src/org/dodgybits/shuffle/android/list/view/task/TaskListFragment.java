package org.dodgybits.shuffle.android.list.view.task;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.*;
import android.widget.AdapterView;
import android.widget.ListView;
import com.google.inject.Inject;
import org.dodgybits.android.shuffle.R;
import org.dodgybits.shuffle.android.actionbarcompat.ActionBarFragmentActivity;
import org.dodgybits.shuffle.android.actionbarcompat.ActionMode;
import org.dodgybits.shuffle.android.core.model.Project;
import org.dodgybits.shuffle.android.core.model.persistence.EntityCache;
import org.dodgybits.shuffle.android.core.model.persistence.TaskPersister;
import org.dodgybits.shuffle.android.core.util.IntentUtils;
import org.dodgybits.shuffle.android.core.util.UiUtilities;
import org.dodgybits.shuffle.android.list.event.*;
import org.dodgybits.shuffle.android.list.view.QuickAddController;
import org.dodgybits.shuffle.android.persistence.provider.TaskProvider;
import roboguice.event.EventManager;
import roboguice.event.Observes;
import roboguice.fragment.RoboListFragment;

import java.util.Set;

public class TaskListFragment extends RoboListFragment
        implements AdapterView.OnItemLongClickListener, TaskListAdaptor.Callback {
    private static final String TAG = "TaskListFragment"; 
    
    /** Argument name(s) */
    public static final String ARG_LIST_CONTEXT = "listContext";

    private static final String BUNDLE_LIST_STATE = "taskListFragment.state.listState";
    private static final String BUNDLE_KEY_SELECTED_TASK_ID
            = "taskListFragment.state.listState.selected_task_id";

    private static final String SELECTED_ITEM = "SELECTED_ITEM";

    // result codes
    private static final int FILTER_CONFIG = 600;
    
    private static final int LOADER_ID_TASK_LIST_LOADER = 1;

    private boolean mShowMoveActions = false;

    private boolean mIsFirstLoad;

    /** ID of the message to highlight. */
    private long mSelectedTaskId = -1;

    @Inject
    private TaskListAdaptor mListAdapter;

    @Inject
    private EventManager mEventManager;

    /**
     * {@link ActionMode} shown when 1 or more message is selected.
     */
    private ActionMode mSelectionMode;
    private SelectionModeCallback mLastSelectionModeCallback;

    private Parcelable mSavedListState;

    // UI Support
    private boolean mIsViewCreated;

    /** true between {@link #onResume} and {@link #onPause}. */
    private boolean mResumed;

    @Inject
    TaskPersister mPersister;

    @Inject
    EntityCache<org.dodgybits.shuffle.android.core.model.Context> mContextCache;

    @Inject
    EntityCache<Project> mProjectCache;

    @Inject
    private QuickAddController mQuickAddController;

    private TaskListContext mListContext;

    private void initializeArgCache() {
        if (mListContext != null) return;
        mListContext = getArguments().getParcelable(ARG_LIST_CONTEXT);
        mShowMoveActions = mListContext.showMoveActions();
    }

    protected ActionBarFragmentActivity getActionBarFragmentActivity() {
        return (ActionBarFragmentActivity)getActivity();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate with context " + getListContext());

        setHasOptionsMenu(true);

        mListAdapter.setCallback(this);
        mIsFirstLoad = true;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = super.onCreateView(inflater, container, savedInstanceState);

        mIsViewCreated = true;
        return root;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final ListView lv = getListView();
        lv.setOnItemLongClickListener(this);
        lv.setItemsCanFocus(false);
        lv.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        setEmptyText(getString(R.string.no_tasks));

        if (savedInstanceState != null) {
            // Fragment doesn't have this method.  Call it manually.
            restoreInstanceState(savedInstanceState);
        }

        startLoading();
    }

    @Override
    public void onResume() {
        super.onResume();

        mResumed = true;
        onVisibilityChange();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        mIsViewCreated = false;
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);

        onVisibilityChange();
    }

    @Override
    public void onPause() {
        mSavedListState = getListView().onSaveInstanceState();
        mResumed = false;

        Log.d(TAG, "onPause with context " + getListContext());
        super.onPause();
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        // Always toggle the item.
        TaskListItem listItem = (TaskListItem) view;
        boolean toggled = false;
        if (!mListAdapter.isSelected(listItem)) {
            toggleSelection(listItem);
            toggled = true;
        }

        return toggled;
    }

    /**
     * Called when a message is clicked.
     */
    @Override
    public void onListItemClick(ListView parent, View view, int position, long id) {
        Uri url = ContentUris.withAppendedId(TaskProvider.Tasks.CONTENT_URI, id);

        String action = getActivity().getIntent().getAction();
        if (Intent.ACTION_PICK.equals(action)
                || Intent.ACTION_GET_CONTENT.equals(action)) {
            // The caller is waiting for us to return a task selected by
            // the user. They have clicked on one, so return it now.
            Bundle bundle = new Bundle();
            bundle.putString(SELECTED_ITEM, url.toString());
            Intent intent = new Intent();
            intent.putExtras(bundle);
            getActivity().setResult(Activity.RESULT_OK, intent);
        } else {
            // Launch activity to view the currently selected item
            Intent intent = IntentUtils.createTaskViewIntent(getActivity(), mListContext, position);
            startActivity(intent);
        }
    }

    @Override
    public void onAdapterSelectedChanged(TaskListItem itemView, boolean newSelected, int mSelectedCount) {
        updateSelectionMode();
    }

    @Override
    public void onAdaptorSelectedRemoved(Set<Long> removedIds) {
        updateSelectionMode();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mListAdapter.onSaveInstanceState(outState);
        if (isViewCreated()) {
            outState.putParcelable(BUNDLE_LIST_STATE, getListView().onSaveInstanceState());
        }
        outState.putLong(BUNDLE_KEY_SELECTED_TASK_ID, mSelectedTaskId);
    }

    void restoreInstanceState(Bundle savedInstanceState) {
        mListAdapter.loadState(savedInstanceState);
        mSavedListState = savedInstanceState.getParcelable(BUNDLE_LIST_STATE);
        mSelectedTaskId = savedInstanceState.getLong(BUNDLE_KEY_SELECTED_TASK_ID);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.list_menu, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        String taskName = getString(R.string.task_name);
        String addTitle = getString(R.string.menu_insert, taskName);
        menu.findItem(R.id.action_add).setTitle(addTitle);

        MenuItem editMenu = menu.findItem(R.id.action_edit);
        MenuItem deleteMenu = menu.findItem(R.id.action_delete);
        MenuItem undeleteMenu = menu.findItem(R.id.action_undelete);
        if (getListContext().showEditActions()) {
            String entityName = getListContext().getEditEntityName(getActivity());
            boolean entityDeleted = getListContext().isEditEntityDeleted(getActivity(), mContextCache, mProjectCache);
            editMenu.setVisible(true);
            editMenu.setTitle(getString(R.string.menu_edit, entityName));
            deleteMenu.setVisible(!entityDeleted);
            deleteMenu.setTitle(getString(R.string.menu_delete_entity, entityName));
            undeleteMenu.setVisible(entityDeleted);
            undeleteMenu.setTitle(getString(R.string.menu_undelete_entity, entityName));
        } else {
            editMenu.setVisible(false);
            deleteMenu.setVisible(false);
            undeleteMenu.setVisible(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_add:
                Log.d(TAG, "adding task");
                mEventManager.fire(mListContext.createEditNewTaskEvent());
                return true;
            case R.id.action_help:
                Log.d(TAG, "Bringing up help");
                mEventManager.fire(new ViewHelpEvent(mListContext.getListQuery()));
                return true;
            case R.id.action_view_settings:
                Log.d(TAG, "Bringing up view settings");
                mEventManager.fire(new EditListSettingsEvent(mListContext.getListQuery(), this, FILTER_CONFIG));
                return true;
            case R.id.action_edit:
                mEventManager.fire(mListContext.createEditEvent());
                return true;
            case R.id.action_delete:
                mEventManager.fire(mListContext.createDeleteEvent(true));
                getActivity().finish();
                return true;
            case R.id.action_undelete:
                mEventManager.fire(mListContext.createDeleteEvent(false));
                getActivity().finish();
                return true;

        }
        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        Log.d(TAG, "Got resultCode " + resultCode + " with data " + data);
        switch (requestCode) {
            case FILTER_CONFIG:
                mEventManager.fire(new ListSettingsUpdatedEvent(getListContext().getListQuery()));
                break;

            default:
                Log.e(TAG, "Unknown requestCode: " + requestCode);
        }
    }

    protected void onVisibilityChange() {
        if (getUserVisibleHint()) {
            flushCaches();
            updateTitle();
            updateQuickAdd();
            getActionBarFragmentActivity().supportResetOptionsMenu();
        }
        updateSelectionMode();
    }

    private void flushCaches() {
        mContextCache.flush();
        mProjectCache.flush();
    }

    private void updateTitle() {
        getActivity().setTitle(getListContext().createTitle(getActivity(), mContextCache, mProjectCache));
    }

    public void onQuickAddEvent(@Observes QuickAddEvent event) {
        if (getUserVisibleHint() && mResumed) {
            mEventManager.fire(getListContext().createNewTaskEventWithDescription(event.getValue()));
        }
    }

    public void onListSettingsUpdated(@Observes ListSettingsUpdatedEvent event) {
        if (event.getListQuery().equals(getListContext().getListQuery())) {
            // our list settings changed - reload list (even if this list isn't currently visible)
            restartLoading();
        }
    }

    private void updateQuickAdd() {
        mQuickAddController.init(getActivity());
        mQuickAddController.setEnabled(getListContext().isQuickAddEnabled(getActivity()));
        mQuickAddController.setEntityName(getString(R.string.task_name));
    }

    private void startLoading() {
        Log.d(TAG, "Creating list cursor");
        final LoaderManager lm = getLoaderManager();
        lm.initLoader(LOADER_ID_TASK_LIST_LOADER, null, LOADER_CALLBACKS);
    }

    protected void restartLoading() {
        Log.d(TAG, "Refreshing list cursor");
        final LoaderManager lm = getLoaderManager();
        lm.restartLoader(LOADER_ID_TASK_LIST_LOADER, null, LOADER_CALLBACKS);
    }


    public TaskListContext getListContext() {
        initializeArgCache();
        return mListContext;
    }

    private boolean showMoveActions() {
        initializeArgCache();
        return mShowMoveActions;
    }

    private boolean doesSelectionContainIncompleteMessage() {
        Set<Long> selectedSet = mListAdapter.getSelectedSet();
        return testMultiple(selectedSet, false, new EntryMatcher() {
            @Override
            public boolean matches(Cursor c) {
                return mPersister.readComplete(c);
            }
        });
    }

    private boolean doesSelectionContainUndeletedMessage() {
        Set<Long> selectedSet = mListAdapter.getSelectedSet();
        return testMultiple(selectedSet, false, new EntryMatcher() {
            @Override
            public boolean matches(Cursor c) {
                return mPersister.readDeleted(c);
            }
        });
    }

    interface EntryMatcher {
        boolean matches(Cursor c);
    }
    
    /**
     * Test selected messages for showing appropriate labels
     * @param selectedSet
     * @param matcher
     * @param defaultFlag
     * @return true when the specified flagged message is selected
     */
    private boolean testMultiple(Set<Long> selectedSet, boolean defaultFlag, EntryMatcher matcher) {
        final Cursor c = mListAdapter.getCursor();
        if (c == null || c.isClosed()) {
            return false;
        }
        c.moveToPosition(-1);
        while (c.moveToNext()) {
            long id = mPersister.readLocalId(c).getId();
            if (selectedSet.contains(Long.valueOf(id))) {
                if (matcher.matches(c) == defaultFlag) {
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * Loader callbacks for message list.
     */
    private final LoaderManager.LoaderCallbacks<Cursor> LOADER_CALLBACKS =
            new LoaderManager.LoaderCallbacks<Cursor>() {

                @Override
                public Loader<Cursor> onCreateLoader(int id, Bundle args) {
                    final TaskListContext listContext = getListContext();

                    mIsFirstLoad = true;
                    return TaskListAdaptor.createLoader(getActivity(), listContext);
                }

                @Override
                public void onLoadFinished(Loader<Cursor> loader, Cursor c) {
                    // Update the list
                    mListAdapter.swapCursor(c);
                    setListAdapter(mListAdapter);

                    updateSelectionMode();

                    // We want to make visible the selection only for the first load.
                    // Re-load caused by content changed events shouldn't scroll the list.
                    highlightSelectedMessage(mIsFirstLoad);

                    // Restore the state -- this step has to be the last, because Some of the
                    // "post processing" seems to reset the scroll position.
                    if (mSavedListState != null) {
                        getListView().onRestoreInstanceState(mSavedListState);
                        mSavedListState = null;
                    }

                    mIsFirstLoad = false;
                }


                @Override
                public void onLoaderReset(Loader<Cursor> loader) {
                    mListAdapter.changeCursor(null);
                }
            };

    /**
     * @return true if the content view is created and not destroyed yet. (i.e. between
     * {@link #onCreateView} and {@link #onDestroyView}.
     */
    private boolean isViewCreated() {
        // Note that we don't use "getView() != null".  This method is used in updateSelectionMode()
        // to determine if CAB shold be shown.  But because it's called from onDestroyView(), at
        // this point the fragment still has views but we want to hide CAB, we can't use
        // getView() here.
        return mIsViewCreated;
    }


    private void toggleSelection(TaskListItem itemView) {
        itemView.invalidate();
        mListAdapter.toggleSelected(itemView);
    }

    private void onDeselectAll() {
        mListAdapter.clearSelection();
        if (isInSelectionMode()) {
            finishSelectionMode();
        }
    }
    /**
     * Show/hide the "selection" action mode, according to the number of selected messages and
     * the visibility of the fragment.
     * Also update the content (title and menus) if necessary.
     */
    public void updateSelectionMode() {
        final int numSelected = getSelectedCount();
        if ((numSelected == 0) || !isViewCreated() || !getUserVisibleHint()) {
            finishSelectionMode();
            return;
        }
        if (isInSelectionMode()) {
            updateSelectionModeView();
        } else {
            mLastSelectionModeCallback = new SelectionModeCallback();
            getActionBarFragmentActivity().startSupportedActionMode(mLastSelectionModeCallback);
        }
    }


    /**
     * Finish the "selection" action mode.
     *
     * Note this method finishes the contextual mode, but does *not* clear the selection.
     * If you want to do so use {@link #onDeselectAll()} instead.
     */
    private void finishSelectionMode() {
        if (isInSelectionMode()) {
            mLastSelectionModeCallback.mClosedByUser = false;
            mSelectionMode.finish();
        }
    }

    /** Update the "selection" action mode bar */
    private void updateSelectionModeView() {
        mSelectionMode.invalidate();
    }

    /**
     * @return the number of messages that are currently selected.
     */
    private int getSelectedCount() {
        return mListAdapter.getSelectedSet().size();
    }

    /**
     * @return true if the list is in the "selection" mode.
     */
    public boolean isInSelectionMode() {
        return mSelectionMode != null;
    }

    private class SelectionModeCallback implements ActionMode.Callback {
        private MenuItem mMarkComplete;
        private MenuItem mMarkIncomplete;
        private MenuItem mMarkDelete;
        private MenuItem mMarkUndelete;
        private MenuItem mMoveUp;
        private MenuItem mMoveDown;

        /* package */ boolean mClosedByUser = true;

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mSelectionMode = mode;

            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.task_cab_menu, menu);
            mMoveUp = menu.findItem(R.id.action_move_up);
            mMoveDown = menu.findItem(R.id.action_move_down);
            mMarkComplete = menu.findItem(R.id.action_mark_complete);
            mMarkIncomplete = menu.findItem(R.id.action_mark_incomplete);
            mMarkDelete = menu.findItem(R.id.action_delete);
            mMarkUndelete = menu.findItem(R.id.action_undelete);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            int num = getSelectedCount();
            // Set title -- "# selected"
            mSelectionMode.setTitle(getActivity().getResources().getQuantityString(
                    R.plurals.task_view_selected_message_count, num, num));

            // Show appropriate menu items.
            boolean incompleteExists = doesSelectionContainIncompleteMessage();
            boolean undeletedExists = doesSelectionContainUndeletedMessage();
            mMoveUp.setVisible(showMoveActions());
            mMoveUp.setEnabled(showMoveActions() && moveUpEnabled());
            mMoveDown.setVisible(showMoveActions());
            mMoveDown.setEnabled(showMoveActions() && moveDownEnabled());
            mMarkComplete.setVisible(incompleteExists);
            mMarkIncomplete.setVisible(!incompleteExists);
            mMarkDelete.setVisible(undeletedExists);
            mMarkUndelete.setVisible(!undeletedExists);
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            Set<Long> selectedTasks = mListAdapter.getSelectedSet();
            if (selectedTasks.isEmpty()) return true;
            switch (item.getItemId()) {
                case R.id.action_move_up:
                    if (moveUpEnabled()) {
                        mEventManager.fire(new MoveTasksEvent(selectedTasks, true, mListAdapter.getCursor()));
                    }
                    break;
                case R.id.action_move_down:
                    if (moveDownEnabled()) {
                        mEventManager.fire(new MoveTasksEvent(selectedTasks, false, mListAdapter.getCursor()));
                    }
                    break;
                case R.id.action_mark_complete:
                    mEventManager.fire(new UpdateTasksCompletedEvent(selectedTasks, true));
                    break;
                case R.id.action_mark_incomplete:
                    mEventManager.fire(new UpdateTasksCompletedEvent(selectedTasks, false));
                    break;
                case R.id.action_delete:
                    mEventManager.fire(new UpdateTasksDeletedEvent(selectedTasks, true));
                    break;
                case R.id.action_undelete:
                    mEventManager.fire(new UpdateTasksDeletedEvent(selectedTasks, false));
                    break;
            }
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            // Clear this before onDeselectAll() to prevent onDeselectAll() from trying to close the
            // contextual mode again.
            mSelectionMode = null;
            if (mClosedByUser) {
                // Clear selection, only when the contextual mode is explicitly closed by the user.
                //
                // We close the contextual mode when the fragment becomes temporary invisible
                // (i.e. mIsVisible == false) too, in which case we want to keep the selection.
                onDeselectAll();
            }
        }

        private boolean moveUpEnabled() {
            return !mListAdapter.isFirstTaskSelected();
        }

        private boolean moveDownEnabled() {
            return !mListAdapter.isLastTaskSelected();
        }
}

    /**
     * Highlight the selected message.
     */
    private void highlightSelectedMessage(boolean ensureSelectionVisible) {
        if (!isViewCreated()) {
            return;
        }

        final ListView lv = getListView();
        if (mSelectedTaskId == -1) {
            // No message selected
            lv.clearChoices();
            return;
        }

        final int count = lv.getCount();
        for (int i = 0; i < count; i++) {
            if (lv.getItemIdAtPosition(i) != mSelectedTaskId) {
                continue;
            }
            lv.setItemChecked(i, true);
            if (ensureSelectionVisible) {
                UiUtilities.listViewSmoothScrollToPosition(getActivity(), lv, i);
            }
            break;
        }
    }

}
