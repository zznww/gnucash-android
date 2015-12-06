package org.gnucash.android.ui.transaction;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.DatabaseCursorLoader;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.ScheduledActionDbAdapter;
import org.gnucash.android.db.TransactionsDbAdapter;
import org.gnucash.android.model.ScheduledAction;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.ui.common.FormActivity;
import org.gnucash.android.ui.common.UxArgument;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/**
 * Created by zz on 2015/12/5.
 */
public class TemplateListFragment extends ListFragment implements
        LoaderManager.LoaderCallbacks<Cursor> {

    /**
     * Logging tag
     */
    protected static final String TAG = "TemplateListFragment";

    private TransactionsDbAdapter mTransactionsDbAdapter;
    private SimpleCursorAdapter mCursorAdapter;
    private ActionMode mActionMode = null;

    /**
     * Flag which is set when a transaction is selected
     */
    private boolean mInEditMode = false;


    /**
     * Callbacks for the menu items in the Context ActionBar (CAB) in action mode
     */
    private ActionMode.Callback mActionModeCallbacks = new ActionMode.Callback() {

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.transactions_context_menu, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            //nothing to see here, move along
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            finishEditMode();
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.context_menu_delete:
                    for (long id : getListView().getCheckedItemIds()) {
                        Log.i(TAG, "Deleting template transaction(s)");
                        if (mTransactionsDbAdapter.deleteRecord(id)){
                            Toast.makeText(getActivity(), R.string.toast_recurring_transaction_deleted, Toast.LENGTH_SHORT).show();
                        }
                    }
                    mode.finish();
                    setDefaultStatusBarColor();
                    getLoaderManager().destroyLoader(0);
                    refreshList();
                    return true;

                default:
                    setDefaultStatusBarColor();
                    return false;
            }
        }
    };


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mTransactionsDbAdapter = TransactionsDbAdapter.getInstance();
        mCursorAdapter = new TemplateTransactionsCursorAdapter(
                getActivity().getApplicationContext(),
                R.layout.list_item_scheduled_trxn, null,
                new String[] {DatabaseSchema.TransactionEntry.COLUMN_DESCRIPTION},
                new int[] {R.id.primary_text});
        setListAdapter(mCursorAdapter);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scheduled_events_list, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);

        setHasOptionsMenu(true);
        getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        ((TextView)getListView().getEmptyView()).setTextColor(getResources().getColor(R.color.theme_accent));
    }

    /**
     * Reload the list of transactions and recompute account balances
     */
    public void refreshList(){
        getLoaderManager().restartLoader(0, null, this);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshList();
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        if (mActionMode != null){
            CheckBox checkbox = (CheckBox) v.findViewById(R.id.checkbox);
            checkbox.setChecked(!checkbox.isChecked());
            return;
        }

        Transaction transaction = mTransactionsDbAdapter.getRecord(id);

        //this should actually never happen, but has happened once. So perform check for the future
        if (transaction.getSplits().size() == 0){
            Toast.makeText(getActivity(), "The selected transaction has no splits and cannot be opened", Toast.LENGTH_SHORT).show();
            return;
        }

        String accountUID = transaction.getSplits().get(0).getAccountUID();
        openTransactionForEdit(accountUID, mTransactionsDbAdapter.getUID(id));
    }

    /**
     * Opens the transaction editor to enable editing of the transaction
     * @param accountUID GUID of account to which transaction belongs
     * @param transactionUID GUID of transaction to be edited
     */
    public void openTransactionForEdit(String accountUID, String transactionUID){
        Intent createTransactionIntent = new Intent(getActivity(), FormActivity.class);
        createTransactionIntent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        createTransactionIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name());
        createTransactionIntent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID);
        createTransactionIntent.putExtra(UxArgument.SELECTED_TRANSACTION_UID, transactionUID);
        startActivity(createTransactionIntent);
    }

    private void setDefaultStatusBarColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getActivity().getWindow().setStatusBarColor(getResources().getColor(R.color.theme_primary_dark));
        }
    }


    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Log.d(TAG, "Creating transactions loader");
        return new TemplateTransactionsCursorLoader(getActivity());
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        Log.d(TAG, "Transactions loader finished. Swapping in cursor");
        mCursorAdapter.swapCursor(data);
        mCursorAdapter.notifyDataSetChanged();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        Log.d(TAG, "Resetting transactions loader");
        mCursorAdapter.swapCursor(null);
    }

    /**
     * Finishes the edit mode in the transactions list.
     * Edit mode is started when at least one transaction is selected
     */
    public void finishEditMode(){
        mInEditMode = false;
        uncheckAllItems();
        mActionMode = null;
    }

    /**
     * Sets the title of the Context ActionBar when in action mode.
     * It sets the number highlighted items
     */
    public void setActionModeTitle(){
        int count = getListView().getCheckedItemIds().length; //mSelectedIds.size();
        if (count > 0){
            mActionMode.setTitle(getResources().getString(R.string.title_selected, count));
        }
    }

    /**
     * Unchecks all the checked items in the list
     */
    private void uncheckAllItems() {
        SparseBooleanArray checkedPositions = getListView().getCheckedItemPositions();
        ListView listView = getListView();
        for (int i = 0; i < checkedPositions.size(); i++) {
            int position = checkedPositions.keyAt(i);
            listView.setItemChecked(position, false);
        }
    }

    /**
     * Starts action mode and activates the Context ActionBar (CAB)
     * Action mode is initiated as soon as at least one transaction is selected (highlighted)
     */
    private void startActionMode(){
        if (mActionMode != null) {
            return;
        }
        mInEditMode = true;
        // Start the CAB using the ActionMode.Callback defined above
        mActionMode = ((AppCompatActivity) getActivity())
                .startSupportActionMode(mActionModeCallbacks);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
            getActivity().getWindow().setStatusBarColor(getResources().getColor(android.R.color.darker_gray));
        }
    }

    /**
     * Stops action mode and deselects all selected transactions.
     * This method only has effect if the number of checked items is greater than 0 and {@link #mActionMode} is not null
     */
    private void stopActionMode(){
        int checkedCount = getListView().getCheckedItemIds().length;
        if (checkedCount <= 0 && mActionMode != null) {
            mActionMode.finish();
            setDefaultStatusBarColor();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            refreshList();
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    /**
     * Extends a simple cursor adapter to bind transaction attributes to views
     * @author Ngewi Fet <ngewif@gmail.com>
     */
    protected class TemplateTransactionsCursorAdapter extends SimpleCursorAdapter {

        public TemplateTransactionsCursorAdapter(Context context, int layout, Cursor c,
                                                  String[] from, int[] to) {
            super(context, layout, c, from, to, 0);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final View view = super.getView(position, convertView, parent);
            final int itemPosition = position;
            CheckBox checkbox = (CheckBox) view.findViewById(R.id.checkbox);
            //TODO: Revisit this if we ever change the application theme
            int id = Resources.getSystem().getIdentifier("btn_check_holo_light", "drawable", "android");
            checkbox.setButtonDrawable(id);

            final TextView secondaryText = (TextView) view.findViewById(R.id.secondary_text);

            checkbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    getListView().setItemChecked(itemPosition, isChecked);
                    if (isChecked) {
                        startActionMode();
                    } else {
                        stopActionMode();
                    }
                    setActionModeTitle();
                }
            });


            ListView listView = (ListView) parent;
            if (mInEditMode && listView.isItemChecked(position)){
                view.setBackgroundColor(getResources().getColor(R.color.abs__holo_blue_light));
                secondaryText.setTextColor(getResources().getColor(android.R.color.white));
            } else {
                view.setBackgroundColor(getResources().getColor(android.R.color.transparent));
                secondaryText.setTextColor(getResources().getColor(android.R.color.secondary_text_light_nodisable));
                checkbox.setChecked(false);
            }

            final View checkBoxView = checkbox;
            final View parentView = view;
            parentView.post(new Runnable() {
                @Override
                public void run() {
                    if (isAdded()){ //may be run when fragment has been unbound from activity
                        float extraPadding = getResources().getDimension(R.dimen.edge_padding);
                        final android.graphics.Rect hitRect = new Rect();
                        checkBoxView.getHitRect(hitRect);
                        hitRect.right   += extraPadding;
                        hitRect.bottom  += 3*extraPadding;
                        hitRect.top     -= extraPadding;
                        hitRect.left    -= 2*extraPadding;
                        parentView.setTouchDelegate(new TouchDelegate(hitRect, checkBoxView));
                    }
                }
            });

            return view;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            super.bindView(view, context, cursor);

            Transaction transaction = mTransactionsDbAdapter.buildModelInstance(cursor);

            TextView amountTextView = (TextView) view.findViewById(R.id.right_text);
            if (transaction.getSplits().size() == 2){
                if (transaction.getSplits().get(0).isPairOf(transaction.getSplits().get(1))){
                    amountTextView.setText(transaction.getSplits().get(0).getValue().formattedString());
                }
            } else {
                amountTextView.setText(transaction.getSplits().size() + " splits");
            }
            TextView descTextView = (TextView) view.findViewById(R.id.secondary_text);
            if(transaction.getNote()!=null)
                descTextView.setText(transaction.getNote());
        }
    }

    /**
     * {@link DatabaseCursorLoader} for loading template transactions asynchronously from the database
     * @author zz
     */
    protected static class TemplateTransactionsCursorLoader extends DatabaseCursorLoader {

        public TemplateTransactionsCursorLoader(Context context) {
            super(context);
        }

        @Override
        public Cursor loadInBackground() {
            mDatabaseAdapter = TransactionsDbAdapter.getInstance();

            Cursor c = ((TransactionsDbAdapter) mDatabaseAdapter).fetchAllTemplateTransactions();

            registerContentObserver(c);
            return c;
        }
    }
}
